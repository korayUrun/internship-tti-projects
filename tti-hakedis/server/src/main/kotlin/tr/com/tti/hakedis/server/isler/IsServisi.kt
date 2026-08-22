package tr.com.tti.hakedis.server.isler

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tr.com.tti.hakedis.domain.document.DocumentFileNamer
import tr.com.tti.hakedis.domain.pricing.PricingCalculator
import tr.com.tti.hakedis.domain.workorder.Region
import tr.com.tti.hakedis.domain.workorder.Scope
import tr.com.tti.hakedis.domain.workorder.WorkOrder
import tr.com.tti.hakedis.domain.workorder.WorkOrderField
import tr.com.tti.hakedis.domain.workorder.WorkOrderTransition
import tr.com.tti.hakedis.domain.workorder.WorkOrderValidator
import tr.com.tti.hakedis.server.belge.BelgeOzetYaniti
import tr.com.tti.hakedis.server.belge.BelgeSorgulari
import tr.com.tti.hakedis.server.belge.ozet
import tr.com.tti.hakedis.server.cikti.CiktiEtiketleri
import tr.com.tti.hakedis.server.durum.DurumKaydiSorgulari
import tr.com.tti.hakedis.server.durum.DurumKaydiYaniti
import tr.com.tti.hakedis.server.kable.KonumGirdisi
import tr.com.tti.hakedis.server.lookup.LookupSorgulari
import tr.com.tti.hakedis.server.web.BulunamadiException
import tr.com.tti.hakedis.server.web.GecersizIstekException
import tr.com.tti.hakedis.server.web.KuralIhlaliException
import tr.com.tti.hakedis.server.yetki.OturumKullanicisi
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Service
class IsServisi(
    private val isler: IsSorgulari,
    private val belgeler: BelgeSorgulari,
    private val lookup: LookupSorgulari,
    private val numaraUretici: IsNumaraUretici,
    private val mtcUretici: MtcNumaraUretici,
    private val durumKayitlari: DurumKaydiSorgulari,
    /** İş listesi Excel'inin başlıkları; belge çıktılarıyla aynı sözlükten gelir (§13). */
    private val etiketler: CiktiEtiketleri,
) {
    fun listele(spNo: String?, kableNosid: String?, durum: IsDurumu?, bolgeKodu: String?, ara: String?): List<IsYaniti> {
        val suzgec = OturumKullanicisi.bolgeSuzgeci()
        val satirlar = isler.listele(suzgec, spNo, kableNosid, durum, bolgeKodu, ara)
        val belgeEslemesi = asamalar(suzgec, satirlar.map { it.id }).groupBy { it.first }

        return satirlar.map { satir ->
            satir.yanit(belgeEslemesi[satir.id].orEmpty().map { it.second })
        }
    }

    fun bul(id: Long): IsYaniti {
        val suzgec = OturumKullanicisi.bolgeSuzgeci()
        val satir = isler.bul(suzgec, id) ?: throw BulunamadiException("Proje bulunamadı: $id")
        return satir.yanit(asamalar(suzgec, listOf(id)).map { it.second })
    }

    /**
     * Ekrandaki listenin `.xlsx` hâli — **aynı süzgeçlerle**.
     *
     * Süzgeçler istemciden aynen geçirilir ve yine [listele] çalıştırılır: dosya, ekranda görülen
     * listenin birebir aynısı olmalı. Ayrı bir sorgu yazılsaydı bölge yetkisi ikinci kez uygulanmak
     * zorunda kalırdı ve unutulduğunda hata sessiz olurdu (CLAUDE.md §7/3).
     *
     * Dosya adına **zaman damgası** giriyor (belge çıktılarının aksine): liste bir anlık görüntüdür,
     * ertesi gün indirilen dosya başka bir içeriktir ve ikisinin aynı adı taşıması yanıltıcı olurdu.
     */
    fun excel(spNo: String?, kableNosid: String?, durum: IsDurumu?, bolgeKodu: String?, ara: String?): ListeCiktisi {
        val kayitlar = listele(spNo, kableNosid, durum, bolgeKodu, ara)
        val kapsamAdlari = lookup.kapsamlar().associate { it.kod to it.ad }

        val bolgeEki = bolgeKodu?.takeIf { it.isNotBlank() }
            ?: OturumKullanicisi.zorunlu().bolgeId?.let { lookup.bolge(it)?.dosyaKodu }
            ?: TUM_BOLGELER

        return ListeCiktisi(
            dosyaAdi = "TTI_PROJELER_${DocumentFileNamer.sanitize(bolgeEki)}_${DOSYA_ZAMANI.format(LocalDateTime.now(ISTANBUL))}.xlsx",
            icerik = IsListesiExceli.uret(kayitlar, kapsamAdlari, etiketler),
        )
    }

    /**
     * İş listesi satır ekranının aşama satırları: tarih, son güncelleyen ve **toplam**.
     *
     * Toplam SQL'de `sum()` ile değil `PricingCalculator` ile hesaplanır — yuvarlama kuralı domain'de
     * tek yerde kalır (CLAUDE.md §5). Satırlar tek toplu sorguyla çekilir, belge başına sorgu açılmaz.
     */
    private fun asamalar(suzgec: Int?, isIdler: List<Long>): List<Pair<Long, BelgeOzetYaniti>> {
        val belgeSatirlari = belgeler.isBelgeleri(suzgec, isIdler)
        val satirEslemesi = belgeler.belgelerinSatirlari(belgeSatirlari.map { it.id })

        return belgeSatirlari.map { it.isId to it.ozet(PricingCalculator.total(satirEslemesi[it.id].orEmpty())) }
    }

    /**
     * Yeni iş.
     *
     * **İşin bölgesi işi açan kullanıcınındır** (kullanıcı kararı, 3 Ağustos 2026). Kable bölge kısıtı
     * kalktığı için bölge sorumlusu başka bölgenin kablosuyla da iş açabilir; iş yine kendi bölgesine
     * yazılır, dolayısıyla belge numarası ve SP No da onun bölgesinden üretilir. Yöneticinin bölgesi
     * olmadığı için iş açamaz — bu bilinçli: iş açmak saha işidir.
     *
     * SP No **sunucu üretir** (`IsNumaraUretici`); istemci gönderemez. ~~D1: aynı SP ile ikinci iş
     * açılabilmeli~~ düştü, numara artık tekil.
     */
    @Transactional
    fun olustur(istek: IsOlusturmaIstegi): IsYaniti {
        val bolgeId = OturumKullanicisi.zorunlu().bolgeId
            ?: throw KuralIhlaliException("Proje açmak için bir bölgeye bağlı olmanız gerekir; yönetici hesabıyla proje açılamaz.")
        val bolge = lookup.bolge(bolgeId) ?: throw BulunamadiException("Bölge bulunamadı: $bolgeId")

        val alanlar = dogrula(istek, bolge.kod)

        val kapsamId = lookup.kapsamId(alanlar.kapsamKodu)
            ?: throw BulunamadiException("Kapsam bulunamadı: ${alanlar.kapsamKodu}")

        val kablo = projeKablosu(alanlar, istek)

        val yil = LocalDate.now(ISTANBUL).year
        val spNo = numaraUretici.siradakiSpNo(bolgeId, bolge.dosyaKodu, yil)

        val id = isler.olustur(
            bolgeId = bolgeId,
            kablo = kablo,
            kapsamId = kapsamId,
            spNo = spNo,
            route = alanlar.route,
            ekAraligi = alanlar.ekAraligi,
            olusturanId = OturumKullanicisi.zorunlu().id,
        )

        // Uyarı yalnızca bilgilendirme: işlemi durdurmaz, onay diyaloğu yapılmaz (E7).
        // Uzun hatlarda aynı kabloda birden fazla işlem olabilir; hangi parça olduğunu Ek Aralığı ve
        // Route ayırt eder. Kullanıcı diğer işleri bilsin diye uyarılır.
        // Kablosuz projede (Yeni Tesis) sorulacak bir şey yok — blok tümüyle atlanır.
        val uyarilar = buildList {
            val ayniKable = kablo.kableId?.let { isler.ayniKableSayisi(bolgeId, it, bunuHaricTut = id) } ?: 0
            if (ayniKable > 0) {
                add(
                    UyariYaniti(
                        "AYNI_KABLE_VAR",
                        ayniKable,
                        "Bu kablo üzerinde $ayniKable proje daha var. Ek Aralığı ve Route alanlarının doğru olduğundan emin olun.",
                    ),
                )
            }
        }

        val satir = isler.bul(OturumKullanicisi.bolgeSuzgeci(), id)
            ?: throw IllegalStateException("Oluşturulan proje okunamadı: $id")
        return satir.yanit(belgeler = emptyList(), uyarilar = uyarilar)
    }

    /**
     * Yöneticinin elle yaptığı durum geçişi — **her durumdan her duruma** (Faz V).
     *
     * Otomatik geçişler (`null → ACIK`, hakediş onayında `→ KAPANDI`) buradan geçmez; onları belge
     * işlemleri tetikler (`BelgeServisi`).
     *
     * Gerekçe **her geçişte** zorunlu ve müdahale `durum_kaydi`'na yazılır: yetki geniş olduğu için
     * "bu kapanmış proje neden yeniden açıldı" sorusunun cevabı kayıtta durmak zorunda.
     *
     * ⚠️ İşleme alma, fizibilite hâlâ **taslakken** de yapılabilir ve o an fizibilite kilitlenir.
     * Kural kullanıcı kararıdır; arayüz bunu onay sorusuyla görünür kılar.
     *
     * ⚠️ İptali geri almak, iptalle birlikte kapatılmış **belgeleri geri getirmez**: her belge ayrı
     * bir karardır ve `POST /api/belgeler/{id}/durum` ile açılır. Toplu diriltme, iptal edilmiş
     * doğru bir taslağı da geri getirirdi.
     */
    @Transactional
    fun durumDegistir(id: Long, hedef: IsDurumu?, gerekce: String?): IsYaniti {
        val istenen = hedef ?: throw GecersizIstekException("Hedef durum belirtilmedi.")
        val neden = gerekce?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw GecersizIstekException("Durum değişikliği gerekçesi zorunludur.")
        val suzgec = OturumKullanicisi.bolgeSuzgeci()
        val satir = isler.bul(suzgec, id) ?: throw BulunamadiException("Proje bulunamadı: $id")

        if (!WorkOrderTransition.izinliMi(satir.durum?.domain(), istenen.domain())) {
            throw KuralIhlaliException("${satir.spNo} projesi zaten \"${IsMetinleri.durumMetni(istenen)}\" durumunda.")
        }

        if (istenen == IsDurumu.IPTAL) {
            iptalEt(id, satir, neden)
        } else if (isler.durumYaz(id, satir.durum, istenen) == 0) {
            throw KuralIhlaliException("Projenin durumu değişmiş olabilir. Sayfayı yenileyin.")
        }

        durumKayitlari.isYaz(id, satir.durum?.name, istenen.name, neden, OturumKullanicisi.zorunlu().id)

        return bul(id)
    }

    /** Projenin durum müdahalesi geçmişi — kim, ne zaman, hangi gerekçeyle değiştirdi. */
    fun durumGecmisi(id: Long): List<DurumKaydiYaniti> {
        isler.bul(OturumKullanicisi.bolgeSuzgeci(), id) ?: throw BulunamadiException("Proje bulunamadı: $id")
        return durumKayitlari.isKayitlari(id)
    }

    /**
     * İşi iptal eder — düşmüş ya da yanlış açılmış bir işin sistemden çıkış yolu (NEWPHASES.md D5).
     *
     * Kural sertlikleri bilinçli:
     * - Yetki **yalnızca yöneticide** (`GuvenlikYapilandirmasi`).
     * - Gerekçe **zorunlu**; kim ve ne zaman iptal etti kaydedilir (veritabanı kısıtı da gerekçesiz
     *   iptali kabul etmez).
     * - Kayıt **silinmez**, tamamlanmış belgelerine dokunulmaz: ön fizibilitesi bitip düşen bir proje
     *   meşru bir sondur ve o belge geçerliliğini korur.
     * - İşin **açık belgeleri** (taslak ve onay bekleyen) birlikte iptal edilir.
     *
     * Gerekçe doğrulaması ve kayıt yazımı çağıranda ([durumDegistir]); burada yalnızca iptale özgü
     * iki yazma var.
     */
    private fun iptalEt(id: Long, satir: IsSatiri, neden: String) {
        val iptalEden = OturumKullanicisi.zorunlu().id

        if (isler.iptalEt(id, neden, iptalEden) == 0) {
            throw KuralIhlaliException("${satir.spNo} projesi iptal edilemedi; durumu değişmiş olabilir.")
        }
        belgeler.isinTaslaklariniIptalEt(id, neden, iptalEden)
    }

    /**
     * Projeye dondurulacak kablo bilgisi (Faz W).
     *
     * **Kapsama göre iki ayrı yol:**
     * - **Kablo zorunluysa** (dört kapsam) kimlik envanterde aranır ve dört alan da oradan gelir;
     *   istekteki uç segmentleri **yok sayılır**. Adres, kablonun projeye dondurulmuş kopyasıdır
     *   (CLAUDE.md §7/5); elle yazılana izin vermek belgedeki adresin envanterdekinden sessizce
     *   ayrışmasına kapı açardı.
     * - **Zorunlu değilse** ("Yeni Tesis") envanterde **hiç aranmaz**: iş belirli bir kablo
     *   kimliğinin kapsamında değil. `kable_nosid`'e **sistem üretir** `mtc#####` (12 Ağustos 2026;
     *   `MtcNumaraUretici`, mtc90000'den), `kable_id` boş kalır ve uçlar kullanıcının girdiği
     *   segmentlerden kurulur (ülke/şehir/adres artık zorunlu — doğrulama bunu garanti eder).
     *
     * ⚠️ Üretilen `mtc` kimliği envantere **eklenmez** (sadece proje etiketi, kullanıcı kararı):
     * kablo aramasında çıkmaz ve `AYNI_KABLE_VAR` uyarısı onlar için çalışmaz — o iş zaten belirli
     * bir kablonun kapsamında değil. İstekteki serbest `kableNosid` (varsa) yok sayılır.
     */
    private fun projeKablosu(alanlar: DogrulanmisAlanlar, istek: IsOlusturmaIstegi): ProjeKablosu {
        if (!alanlar.kableZorunlu) {
            return ProjeKablosu(
                nosid = mtcUretici.siradaki(),
                adresA = KonumGirdisi.yol(istek.ucA),
                adresB = KonumGirdisi.yol(istek.ucB),
            )
        }

        // Bölge süzgeci YOK (kullanıcı kararı): her kullanıcı her kabloyu seçebilir.
        val nosid = alanlar.kableNosid
        val kable = isler.kableSec(nosid!!) ?: throw BulunamadiException("Kable bulunamadı: $nosid")
        return ProjeKablosu(
            kableId = kable.kableId,
            nosid = kable.nosid,
            adresA = kable.adresA,
            adresB = kable.adresB,
        )
    }

    /**
     * Doğrulama `:core:domain`'deki [WorkOrderValidator] ile yapılır — kural mobil ekranla birebir aynı
     * kalsın, iki yerde yazılmasın (CLAUDE.md §7/1). Kablonun zorunlu olup olmadığına da orası karar
     * verir (`Scope.kableZorunluMu`); burada kapsam kodu karşılaştırması **yapılmaz**.
     */
    private fun dogrula(istek: IsOlusturmaIstegi, bolgeKodu: String): DogrulanmisAlanlar {
        val workOrder = WorkOrder(
            // Bölge oturumdan gelir, istemciden değil; doğrulayıcı yine de alanı bekliyor.
            region = Region.entries.firstOrNull { it.name == bolgeKodu },
            scope = Scope.entries.firstOrNull { it.name == istek.kapsamKodu },
            contractId = null,
            route = istek.route.orEmpty(),
            ekAraligi = istek.ekAraligi.orEmpty(),
            kableId = istek.kableNosid.orEmpty(),
            // Yeni Tesis'te uçların (ülke/şehir/adres) zorunluluğunu doğrulayıcı bu segmentlerden görür.
            ucASegmentleri = istek.ucA?.segmentler.orEmpty(),
            ucBSegmentleri = istek.ucB?.segmentler.orEmpty(),
        )

        val sonuc = WorkOrderValidator.validate(workOrder)
        if (!sonuc.isValid) {
            throw GecersizIstekException(
                "Eksik veya geçersiz alan: " + sonuc.errors.keys.sortedBy { it.ordinal }.joinToString(", ", transform = ::alanAdi),
            )
        }

        return DogrulanmisAlanlar(
            kapsamKodu = workOrder.scope!!.name,
            // Kararı domain veriyor; servis kapsam kodu karşılaştırması yapmaz (CLAUDE.md §7/1).
            kableZorunlu = workOrder.scope!!.kableZorunluMu,
            // Boş kablo kodu `null`'a iner: "seçilmedi" ile "boş metin gönderildi" aynı şey.
            kableNosid = workOrder.kableId.trim().takeIf { it.isNotEmpty() },
            route = workOrder.route.trim(),
            ekAraligi = workOrder.ekAraligi.trim(),
        )
    }

    private fun alanAdi(alan: WorkOrderField) = when (alan) {
        WorkOrderField.REGION -> "bölge"
        WorkOrderField.SCOPE -> "kapsam"
        WorkOrderField.ROUTE -> "route"
        WorkOrderField.EK_ARALIGI -> "ek aralığı"
        WorkOrderField.KABLE_ID -> "KableID"
        WorkOrderField.UC_A -> "Uç A (ülke, şehir, adres)"
        WorkOrderField.UC_B -> "Uç B (ülke, şehir, adres)"
    }

    private data class DogrulanmisAlanlar(
        val kapsamKodu: String,
        val kableZorunlu: Boolean,
        val kableNosid: String?,
        val route: String,
        val ekAraligi: String,
    )

    private companion object {
        val ISTANBUL: ZoneId = ZoneId.of("Europe/Istanbul")

        /** Yöneticinin süzgeçsiz indirdiği dosyanın bölge eki. */
        const val TUM_BOLGELER = "TUMU"

        /** Dosya adında `Locale.ROOT`: yerel ayara bağlı rakam biçimi dosya adını bozmamalı (§5). */
        val DOSYA_ZAMANI: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm", Locale.ROOT)
    }
}

/** İndirilecek liste dosyası: adı sunucuda belirlenir, istemci `Content-Disposition`'dan okur (§9). */
data class ListeCiktisi(val dosyaAdi: String, val icerik: ByteArray)

private fun IsSatiri.yanit(belgeler: List<BelgeOzetYaniti>, uyarilar: List<UyariYaniti> = emptyList()) = IsYaniti(
    id = id,
    bolgeKodu = bolgeKodu,
    bolgeAdi = bolgeAdi,
    kapsamKodu = kapsamKodu,
    kableNosid = kableNosid,
    adresA = adresA,
    adresB = adresB,
    spNo = spNo,
    route = route,
    ekAraligi = ekAraligi,
    yil = yil,
    olusturma = olusturma,
    durum = durum,
    durumMetni = IsMetinleri.durumMetni(durum),
    iptalGerekce = iptalGerekce,
    belgeler = belgeler,
    uyarilar = uyarilar,
)
