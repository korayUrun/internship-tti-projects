package tr.com.tti.hakedis.server.belge

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tr.com.tti.hakedis.domain.document.DocumentFileNamer
import tr.com.tti.hakedis.domain.document.DocumentStageRule
import tr.com.tti.hakedis.domain.document.DocumentStageState
import tr.com.tti.hakedis.domain.document.DocumentStatus
import tr.com.tti.hakedis.domain.document.DocumentType
import tr.com.tti.hakedis.domain.document.StageDecision
import tr.com.tti.hakedis.domain.document.StageRejection
import tr.com.tti.hakedis.domain.pricing.MoneyFormatter
import tr.com.tti.hakedis.domain.pricing.PricedRow
import tr.com.tti.hakedis.domain.pricing.PricingCalculator
import tr.com.tti.hakedis.server.cikti.BelgeKesinlestiOlayi
import tr.com.tti.hakedis.server.durum.DurumKaydiSorgulari
import tr.com.tti.hakedis.server.durum.DurumKaydiYaniti
import tr.com.tti.hakedis.server.ek.EkSorgulari
import tr.com.tti.hakedis.server.ek.EkTuru
import tr.com.tti.hakedis.server.isler.IsDurumu
import tr.com.tti.hakedis.server.isler.IsMetinleri
import tr.com.tti.hakedis.server.isler.IsOzeti
import tr.com.tti.hakedis.server.isler.IsSorgulari
import tr.com.tti.hakedis.server.sozlesme.PozYaniti
import tr.com.tti.hakedis.server.sozlesme.SozlesmeSorgulari
import tr.com.tti.hakedis.server.web.BulunamadiException
import tr.com.tti.hakedis.server.web.GecersizIstekException
import tr.com.tti.hakedis.server.web.KuralIhlaliException
import tr.com.tti.hakedis.server.yetki.OturumKullanicisi
import java.math.BigDecimal

/**
 * Belge yaşam döngüsü: oluşturma → miktar girişi → gönderme.
 *
 * Üç kural burada birleşir:
 * 1. **Aşama kuralı** — karar `:core:domain`'deki `DocumentStageRule`'a ait; buradaki iş yalnızca
 *    durumu veritabanından toplamak ve reddi Türkçe mesaja çevirmek.
 * 2. **Numaralandırma** — `BelgeNumaraUretici`; boşluksuzluk için aynı transaction içinde.
 * 3. **Fiyat dondurma** — belge açılırken sözleşmenin **referans** fiyat sürümüne çivilenir, satır
 *    yazılırken birim fiyat satıra kopyalanır.
 *
 * ~~Yönetici onayı ve işe özel pazarlık fiyat listesi~~ Faz M/N'de kaldırıldı.
 *
 * Tutar hiçbir yerde saklanmaz; her yanıtta `PricingCalculator` ile türetilir.
 */
@Service
class BelgeServisi(
    private val belgeler: BelgeSorgulari,
    private val isler: IsSorgulari,
    private val sozlesmeler: SozlesmeSorgulari,
    private val numaraUretici: BelgeNumaraUretici,
    private val ekler: EkSorgulari,
    private val durumKayitlari: DurumKaydiSorgulari,
    /**
     * Belge kesinleştiğinde çıktısının arşive yazılması için olay yayımlanır (`OtomatikArsivleme`).
     * Doğrudan çağrı yerine olay: bu servisin çıktı üretimini tanıması gerekmiyor ve dinleyici
     * commit'ten **sonra** çalışıyor — geri alınan bir gönderimin belgesi arşive düşmemeli.
     */
    private val olaylar: ApplicationEventPublisher,
) {
    /**
     * Yeni belge. Numara üretimi ile belge insert'i **aynı transaction**'da: aşama kuralı ya da kısıt
     * ihlali insert'i düşürürse sayaç artışı da geri alınır ve seride boşluk kalmaz.
     */
    @Transactional
    fun olustur(isId: Long, tur: DocumentType, sozlesmeKodu: String?): BelgeYaniti {
        val suzgec = OturumKullanicisi.bolgeSuzgeci()
        val isOzeti = isler.ozet(suzgec, isId) ?: throw BulunamadiException("Proje bulunamadı: $isId")

        // ⚠️ `isinAcikAsamalari`, `isinBelgeDurumlari` DEĞİL. İkincisi iptal edilmiş satırları da
        // çekiyor ve `toMap()` aynı tür için son satırı tutuyor. Bir aşama iade edilip yeniden
        // açıldığında o türde iki satır olur (biri `IPTAL`, biri aktif); haritada iptal olan kalırsa
        // aşama kapısı "önceki aşama yok" diye kapanır ve kullanıcı hiç açamaz. Kapı zaten iptal
        // edilmiş aşamayı "yok" saymalı, ki bu süzgeç tam olarak onu yapıyor.
        val mevcut = belgeler.isinAcikAsamalari(isId).associate { it.tur to it.durum }

        val karar = DocumentStageRule.acilabilirMi(tur, DocumentStageState(isOzeti.durum?.domain(), mevcut))
        if (karar is StageDecision.Reddedildi) throw KuralIhlaliException(retMesaji(tur, karar, mevcut, isOzeti))

        val versiyonId = fiyatVersiyonu(sozlesmeKodu)

        val siraNo = numaraUretici.siradakiNo(isOzeti.bolgeId, isOzeti.yil, tur)
        val belgeNo = DocumentFileNamer.documentNo(isOzeti.bolgeDosyaKodu, isOzeti.yil, tur, siraNo)

        val belgeId = belgeler.olustur(
            isId = isId,
            tur = tur,
            bolgeId = isOzeti.bolgeId,
            yil = isOzeti.yil,
            siraNo = siraNo,
            belgeNo = belgeNo,
            fiyatVersiyonuId = versiyonId,
            olusturanId = OturumKullanicisi.zorunlu().id,
        )

        // Yeni aşama açıldığı an, bir önceki aşama (yönetici işleme almış, ISLENIYOR) TAMAMLANDI olur:
        // "1. keşif girişini yaptığında proje fizibilite tamamlandı olur" (müdür kararı, 17-3).
        tur.oncekiAsamalar.lastOrNull()?.let { hemenOnceki ->
            if (belgeler.asamaTamamla(isId, hemenOnceki) == 0) {
                throw KuralIhlaliException("Önceki aşamanın durumu değişmiş olabilir. Sayfayı yenileyin.")
            }
        }
        // Proje durumu güncel aşamayı yansıtır: yeni açılan aşama onay bekliyor.
        isler.durumGuncelle(isId, IsDurumu.ONAY_BEKLIYOR)

        return detay(belgeId)
    }

    /**
     * Miktar girişi — **tam değiştirme**: istekte olmayan poz belgeden silinir. "Girilmemiş" ile
     * "0 girilmiş" ayrımı satırın varlığıyla korunduğu için silme, alanı boşaltmanın tek doğru yoludur.
     *
     * Yalnızca `TASLAK` belgede çalışır (D2).
     */
    @Transactional
    fun satirlariYaz(belgeId: Long, girdiler: List<SatirGirdisi>): BelgeYaniti {
        val belge = duzenlenebilirBelge(belgeId)

        val tekrarlayan = girdiler.groupingBy { it.pozId }.eachCount().filterValues { it > 1 }.keys
        if (tekrarlayan.isNotEmpty()) {
            throw GecersizIstekException("Aynı poz birden fazla kez gönderildi: ${tekrarlayan.sorted().joinToString(", ")}")
        }
        girdiler.firstOrNull { it.miktar.signum() < 0 }?.let {
            throw GecersizIstekException("Miktar negatif olamaz: ${it.pozId}")
        }

        belgeler.satirlariSil(belgeId)

        val yazilamayan = girdiler.filter { girdi ->
            belgeler.satirYaz(belgeId, belge.sozlesmeId, belge.fiyatVersiyonuId, girdi.pozId, girdi.miktar) == 0
        }.map { it.pozId }
        if (yazilamayan.isNotEmpty()) {
            // Fail loud: sessizce atlanan bir poz, kullanıcının girdiği miktarın kaybolması demektir.
            throw GecersizIstekException(
                "Şu pozlara miktar girilemez (bu sözleşmede yok, grup başlığı ya da fiyatlandırılmamış): " +
                    yazilamayan.sorted().joinToString(", "),
            )
        }

        belgeler.guncellemeIzi(belgeId, OturumKullanicisi.zorunlu().id)

        return detay(belgeId)
    }

    /**
     * "Gönder" — durum değiştirmez, gönderim izini atar: aşama `ONAY_BEKLIYOR` kalır ama artık
     * yönetici işleme alabilir. İçerik onaya kadar düzeltilebilir (müdür kararı: geri gönderme yok).
     *
     * Boş belge gönderilemez ve EK1 zorunlu — bu güvenceler gönderim anında işletilir.
     */
    @Transactional
    fun gonder(belgeId: Long): BelgeYaniti {
        duzenlenebilirBelge(belgeId)

        if (belgeler.satirSayisi(belgeId) == 0) {
            throw KuralIhlaliException("Miktar girilmemiş belge gönderilemez.")
        }
        ekleriDogrula(belgeId)

        if (belgeler.gonderIsaretle(belgeId, OturumKullanicisi.zorunlu().id) == 0) {
            throw KuralIhlaliException("Belge gönderilemedi: durumu değişmiş olabilir. Sayfayı yenileyin.")
        }
        // Gönderilen aşamanın güncel çıktısı arşive yazılır; dosya adı kararlı olduğu için onayda
        // yeniden yazılır.
        arsivle(belgeId)

        return detay(belgeId)
    }

    /**
     * Yönetici "İşleme Al": `ONAY_BEKLIYOR → ISLENIYOR` (hakediş: → `TAMAMLANDI`). İçerik bu andan
     * sonra dondurulur ve bir sonraki aşamanın kapısı açılır. Proje durumu güncel aşamayı yansıtır.
     *
     * Ön koşullar: aşama **gönderilmiş** (kullanıcı hazır dedi), miktarlı ve EK1 dolu olmalı.
     */
    @Transactional
    fun onayla(belgeId: Long): BelgeYaniti {
        val belge = onaydakiBelge(belgeId)

        if (belge.gonderme == null) {
            throw KuralIhlaliException("${belge.belgeNo} belgesi henüz gönderilmedi; işleme alınamaz.")
        }
        if (belgeler.satirSayisi(belgeId) == 0) {
            throw KuralIhlaliException("Miktar girilmemiş belge işleme alınamaz.")
        }
        ekleriDogrula(belgeId)

        val hedef = DocumentStageRule.onaySonrasiDurum(belge.tur)
        if (belgeler.isleme(belgeId, OturumKullanicisi.zorunlu().id, hedef) == 0) {
            throw KuralIhlaliException("Belge işleme alınamadı: durumu değişmiş olabilir. Sayfayı yenileyin.")
        }

        isler.durumGuncelle(
            belge.isId,
            if (hedef == DocumentStatus.TAMAMLANDI) IsDurumu.TAMAMLANDI else IsDurumu.ISLENIYOR,
        )

        arsivle(belgeId)

        return detay(belgeId)
    }

    /**
     * Yöneticinin serbest durum müdahalesi — her durumdan her duruma (Faz V kullanıcı kararı).
     *
     * Onay/iade uçlarının yerini almaz, onların dışında kalan durumlar içindir: yanlışlıkla
     * onaylanmış bir hakedişi geri açmak, iptal edilmiş bir taslağı diriltmek gibi. Gerekçe zorunlu
     * ve müdahale `durum_kaydi`'na yazılır.
     */
    @Transactional
    fun durumDegistir(belgeId: Long, hedef: DocumentStatus?, gerekce: String?): BelgeYaniti {
        val istenen = hedef ?: throw GecersizIstekException("Hedef durum belirtilmedi.")
        val neden = zorunluGerekce(gerekce, "Durum değişikliği gerekçesi zorunludur.")

        val belge = belgeler.bul(OturumKullanicisi.bolgeSuzgeci(), belgeId)
            ?: throw BulunamadiException("Belge bulunamadı: $belgeId")
        if (belge.durum == istenen) {
            throw KuralIhlaliException("${belge.belgeNo} belgesi zaten \"${BelgeMetinleri.durumMetni(istenen)}\".")
        }

        if (belgeler.durumDegistir(belgeId, belge.durum, istenen, neden, OturumKullanicisi.zorunlu().id) == 0) {
            throw KuralIhlaliException("Belgenin durumu değişmiş olabilir. Sayfayı yenileyin.")
        }
        durumKayitlari.belgeYaz(belgeId, belge.durum.name, istenen.name, neden, OturumKullanicisi.zorunlu().id)

        // Proje durumu güncel aşamayı yansıttığı için serbest müdahaleden sonra tazelenir.
        projeDurumunuTazele(belge.isId)
        if (istenen == DocumentStatus.TAMAMLANDI) arsivle(belgeId)

        return detay(belgeId)
    }

    /** Belgenin durum müdahalesi geçmişi — "bu belge neden geri açıldı" sorusunun cevabı. */
    fun durumGecmisi(belgeId: Long): List<DurumKaydiYaniti> {
        belgeler.bul(OturumKullanicisi.bolgeSuzgeci(), belgeId) ?: throw BulunamadiException("Belge bulunamadı: $belgeId")
        return durumKayitlari.belgeKayitlari(belgeId)
    }

    /**
     * Yanlış açılmış bir belgeyi çöpe atar; aynı iş o türde yeni bir belge açabilir.
     *
     * Yeni belge **yeni bir numara** alır; iptal edilen numara seride iptal olarak kalır, yeniden
     * kullanılmaz (V5).
     */
    @Transactional
    fun iptalEt(belgeId: Long, gerekce: String?): BelgeYaniti {
        val neden = zorunluGerekce(gerekce, "İptal gerekçesi zorunludur.")

        val belge = belgeler.bul(OturumKullanicisi.bolgeSuzgeci(), belgeId)
            ?: throw BulunamadiException("Belge bulunamadı: $belgeId")

        if (belgeler.iptalEt(belgeId, neden, OturumKullanicisi.zorunlu().id) == 0) {
            throw KuralIhlaliException(
                "${belge.belgeNo} belgesi ${BelgeMetinleri.durumSifati(belge.durum)}; " +
                    "yalnızca açık belge iptal edilebilir.",
            )
        }
        durumKayitlari.belgeYaz(belgeId, belge.durum.name, DocumentStatus.IPTAL.name, neden, OturumKullanicisi.zorunlu().id)

        return detay(belgeId)
    }

    /** Belgenin güncel çıktısını arşive yazdırır (olay commit sonrası dinleniyor). */
    private fun arsivle(belgeId: Long) {
        olaylar.publishEvent(BelgeKesinlestiOlayi(belgeId))
    }

    /**
     * Proje durumunu güncel (en ileri, iptal olmayan) aşamanın durumundan türetip yazar (yeknesek
     * model). Hedef `IPTAL` olamaz — iptal ayrı bir karardır ve iz gerektirir; aktif aşama yoksa
     * projeye dokunulmaz.
     */
    private fun projeDurumunuTazele(isId: Long) {
        // `isinAcikAsamalari` kullanılıyor, `isinBelgeDurumlari` değil: ikincisi iptal edilmiş
        // satırları da çekiyor ve aynı türde iki satır varsa (iade edilip yeniden açılmış aşama)
        // aktif olanı gizleyebiliyor.
        val acik = belgeler.isinAcikAsamalari(isId).associateBy { it.tur }
        val enIleri = DocumentType.entries.lastOrNull { acik.containsKey(it) } ?: return
        val hedef = when (acik.getValue(enIleri).durum) {
            DocumentStatus.ONAY_BEKLIYOR -> IsDurumu.ONAY_BEKLIYOR
            DocumentStatus.ISLENIYOR -> IsDurumu.ISLENIYOR
            // ⚠️ Projenin TAMAMLANDI'sı **yalnızca hakedişin** tamamlanmasından doğar (§3). Ara bir
            // aşama "tamamlandı" ise proje bitmemiştir, bir sonraki aşamayı bekliyordur.
            //
            // Normal akışta ara aşama en ileri açık aşama OLAMAZ: keşif ancak hakediş açılınca
            // tamamlanır, o zaman da en ileri olan hakediştir. Bu dal **iade** ile ortaya çıktı —
            // hakediş geri alınınca en ileri açık aşama "tamamlanmış keşif" oluyor ve eski eşleme
            // projeyi "Tamamlandı" gösteriyordu: hakediş yeniden girilmeyi beklerken proje bitmiş
            // görünüyordu.
            DocumentStatus.TAMAMLANDI ->
                if (enIleri == DocumentType.HAKEDIS) IsDurumu.TAMAMLANDI else IsDurumu.ISLENIYOR
            DocumentStatus.IPTAL -> return
        }
        isler.durumGuncelle(isId, hedef)
    }

    /**
     * **Önceki aşamaya iade** — projenin en ileri açık aşamasını geri alır (kullanıcı isteği,
     * 18 Ağustos 2026).
     *
     * ~~İade Faz M–P'de kaldırılmıştı~~ ve CLAUDE.md §13'te "gelmeyecek" yazıyordu; kullanıcı kararı
     * bunu tersine çevirdi. Dönen hâli eskisinden farklı: "belgeyi düzeltilsin diye geri gönderme"
     * değil, **aşamayı sıfırlama**.
     *
     * Aşama SİLİNMEZ, `IPTAL` işaretlenir. Görünen sonuç istenen sonucun aynısı — `belge_is_tur_ux`
     * kısmi indeksi iptal edilmiş satırı saymadığı için o türde yeni belge açılabilir ve arayüz
     * iptal edilmiş belgeleri aşama satırlarından süzdüğü için aşama "Henüz açılmadı — sırada bu
     * adım var" hâline döner; bölge sorumlusu **her şeyi baştan girer**. Fark, geçmişin duruyor
     * olması: girilen miktarlar, ekler ve kim ne zaman ne yaptı bilgisi iz olarak kalır. Para
     * taşıyan bir kaydı geri dönüşsüz silmek §7/4'ün denetim izi ilkesine aykırı olurdu.
     *
     * Tekrar çağrıldıkça geriye yürür: hakediş → keşif → fizibilite.
     */
    @Transactional
    fun onceyeIade(isId: Long, gerekce: String?): Long {
        val neden = zorunluGerekce(gerekce, "İade gerekçesi zorunludur.")

        // Yetki süzgeci: başka bölgenin projesi 404 döner.
        val proje = isler.bul(OturumKullanicisi.bolgeSuzgeci(), isId)
            ?: throw BulunamadiException("Proje bulunamadı: $isId")
        val oncekiProjeDurumu = proje.durum

        val acik = belgeler.isinAcikAsamalari(isId).associateBy { it.tur }
        val enIleri = DocumentType.entries.lastOrNull { acik.containsKey(it) }
            ?: throw KuralIhlaliException("Bu projede iade edilecek bir aşama yok; henüz hiçbir aşama açılmamış.")

        val asama = acik.getValue(enIleri)
        val kullaniciId = OturumKullanicisi.zorunlu().id

        if (belgeler.iadeEt(asama.belgeId, neden, kullaniciId) == 0) {
            throw KuralIhlaliException("Aşama iade edilemedi; sayfayı yenileyip tekrar deneyin.")
        }
        durumKayitlari.belgeYaz(asama.belgeId, asama.durum.name, DocumentStatus.IPTAL.name, neden, kullaniciId)

        // Bir önceki aşama, bu aşama açıldığı anda `TAMAMLANDI` olmuştu; aşama geri alınınca o
        // "tamamlandı" için sebep kalmaz ve `ISLENIYOR`'a döner. Bu satır olmadan aşama kapısı
        // kapalı kalırdı (kapı bir önceki aşamanın `ISLENIYOR` olmasını arıyor) ve bölge sorumlusu
        // iade edilen aşamayı yeniden açamazdı — iadenin amacı boşa çıkardı.
        val oncekiTur = DocumentType.entries.getOrNull(DocumentType.entries.indexOf(enIleri) - 1)
        if (oncekiTur != null) belgeler.asamaGeriAc(isId, oncekiTur)

        // Proje durumu kalan aşamalardan yeniden türetilir.
        //
        // Son açık aşama da iade edildiyse geriye türetilecek bir şey kalmaz ve
        // `projeDurumunuTazele` erkenden döner — proje eski durumunda (ör. "Tamamlandı") takılı
        // kalırdı. Hiç aşaması olmayan proje `ONAY_BEKLIYOR` okunur (§3: `null` = henüz aşama yok),
        // bu yüzden açıkça oraya çekiliyor. İPTAL edilmiş projeye dokunulmuyor: iptal ayrı bir
        // karar ve iade onu geri almamalı.
        if (belgeler.isinAcikAsamalari(isId).isEmpty()) {
            if (oncekiProjeDurumu != IsDurumu.IPTAL) isler.durumGuncelle(isId, IsDurumu.ONAY_BEKLIYOR)
        } else {
            projeDurumunuTazele(isId)
        }

        // İade **projenin** durum geçmişine de yazılır (kullanıcı isteği, 19 Ağustos 2026).
        //
        // Yukarıdaki `belgeYaz` kaydı belgeye bağlı ve proje kartındaki "Durum geçmişi" düğmesi
        // yalnızca proje kayıtlarını okuyor (`isKayitlari`) — iade orada hiç görünmüyordu. Oysa
        // iade akış dışı, yıkıcı bir yönetici müdahalesi; projeye bakan kişinin aşamanın neden
        // baştan girildiğini görmesi gerekiyor.
        //
        // Gerekçeye aşama adı ekleniyor: proje geçmişinde yalnızca durum çiftleri var, hangi
        // aşamanın iade edildiği başka hiçbir yerden anlaşılmıyor.
        val sonrakiProjeDurumu = isler.bul(OturumKullanicisi.bolgeSuzgeci(), isId)?.durum
        durumKayitlari.isYaz(
            isId = isId,
            onceki = oncekiProjeDurumu?.name,
            yeni = (sonrakiProjeDurumu ?: IsDurumu.ONAY_BEKLIYOR).name,
            gerekce = "${BelgeMetinleri.turAdi(enIleri)} aşaması iade edildi — $neden",
            kullaniciId = kullaniciId,
        )

        return asama.belgeId
    }

    /** Onay ucunun ön koşulu: belge gerçekten onay bekliyor mu? */
    private fun onaydakiBelge(belgeId: Long): BelgeOzeti {
        val belge = belgeler.bul(OturumKullanicisi.bolgeSuzgeci(), belgeId)
            ?: throw BulunamadiException("Belge bulunamadı: $belgeId")
        if (belge.durum != DocumentStatus.ONAY_BEKLIYOR) {
            throw KuralIhlaliException(
                "${belge.belgeNo} belgesi ${BelgeMetinleri.durumSifati(belge.durum)}; onay bekleyen belge değil.",
            )
        }
        return belge
    }

    private fun zorunluGerekce(gerekce: String?, mesaj: String): String =
        gerekce?.trim()?.takeIf { it.isNotEmpty() } ?: throw GecersizIstekException(mesaj)

    /**
     * Belgenin sözleşmesini değiştirir — yalnızca taslakta.
     *
     * **Satırlar silinir.** Poz kimliği (`satir_kodu`) sözleşme içinde anlamlıdır; başka bir
     * sözleşmede aynı kod bambaşka bir kaleme denk gelir. Miktarları taşımaya çalışmak, kullanıcının
     * girdiği sayıların sessizce yanlış satıra yapışması demek olurdu.
     */
    @Transactional
    fun sozlesmeDegistir(belgeId: Long, sozlesmeKodu: String?): BelgeYaniti {
        val belge = duzenlenebilirBelge(belgeId)
        val versiyonId = fiyatVersiyonu(sozlesmeKodu)

        if (versiyonId != belge.fiyatVersiyonuId) {
            belgeler.satirlariSil(belgeId)
            if (belgeler.sozlesmeDegistir(belgeId, versiyonId, OturumKullanicisi.zorunlu().id) == 0) {
                throw KuralIhlaliException("Sözleşme değiştirilemedi: belgenin durumu değişmiş olabilir. Sayfayı yenileyin.")
            }
        }

        return detay(belgeId)
    }

    /**
     * Belgenin çivileneceği fiyat versiyonu: **belgenin kendi sözleşmesinin** yayındaki sürümü.
     *
     * ~~Türe göre dallanma~~ (fizibilite referans, keşif/hakediş işin pazarlık sürümü) Faz M/N'de
     * kalktı: onay akıştan çıkınca pazarlık sürümünü yazan tek kod yolu da öldü. Sözleşme artık işin
     * değil belgenin: her aşama kendi sözleşmesini seçer.
     *
     * Aktiflik süzgeci **burada** uygulanır: pasife çekilmiş sözleşmeyle yeni belge açılamaz, ama o
     * sözleşmenin var olan belgeleri çalışmaya devam eder (`SozlesmeSorgulari.sozlesmeId`).
     */
    private fun fiyatVersiyonu(sozlesmeKodu: String?): Long {
        val kod = sozlesmeKodu?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw GecersizIstekException("Sözleşme seçilmeden belge açılamaz.")

        val sozlesmeId = sozlesmeler.aktifSozlesmeId(kod)
            ?: throw BulunamadiException("Sözleşme bulunamadı ya da pasife çekilmiş: $kod")

        return sozlesmeler.referansFiyatVersiyonuId(sozlesmeId)
            ?: throw KuralIhlaliException("$kod sözleşmesinde yayında fiyat listesi yok; belge açılamaz.")
    }

    /**
     * Liste — web'in ana ekranı. Bölge süzgeci SQL'de; toplamlar tek toplu sorgu + `PricingCalculator`
     * ile hesaplanır (SQL'de `sum()` yok, §5).
     */
    @Suppress("LongParameterList")
    fun listele(
        tur: DocumentType?,
        durum: DocumentStatus?,
        bolgeKodu: String?,
        yil: Int?,
        ara: String?,
        limit: Int,
    ): List<BelgeListeYaniti> {
        val satirlar = belgeler.listele(OturumKullanicisi.bolgeSuzgeci(), tur, durum, bolgeKodu, yil, ara, limit)
        val satirEslemesi = belgeler.belgelerinSatirlari(satirlar.map { it.id })

        return satirlar.map { satir ->
            val toplam = PricingCalculator.total(satirEslemesi[satir.id].orEmpty())
            BelgeListeYaniti(
                id = satir.id,
                isId = satir.isId,
                belgeNo = satir.belgeNo,
                tur = satir.tur,
                durum = satir.durum,
                durumMetni = BelgeMetinleri.durumMetni(satir.durum),
                bolgeKodu = satir.bolgeKodu,
                bolgeAdi = satir.bolgeAdi,
                sorumluAdSoyad = satir.sorumluAdSoyad,
                olusturanAdSoyad = satir.olusturanAdSoyad,
                spNo = satir.spNo,
                kableNosid = satir.kableNosid,
                ekAraligi = satir.ekAraligi,
                route = satir.route,
                yil = satir.yil,
                siraNo = satir.siraNo,
                olusturma = satir.olusturma,
                gonderme = satir.gonderme,
                tamamlanma = satir.tamamlanma,
                sonGuncelleme = satir.sonGuncelleme?.an,
                sonGuncelleyenAdSoyad = satir.sonGuncelleme?.adSoyad,
                sonGuncelleyenRol = satir.sonGuncelleme?.rol,
                toplam = toplam,
                toplamGosterim = MoneyFormatter.money(toplam),
            )
        }
    }

    /** Belgenin içsel özeti — fiyat listesi uçları sözleşme ve iş kimliğine ihtiyaç duyuyor. */
    fun ozet(belgeId: Long): BelgeOzeti = belgeler.bul(OturumKullanicisi.bolgeSuzgeci(), belgeId)
        ?: throw BulunamadiException("Belge bulunamadı: $belgeId")

    /**
     * Belgenin poz listesi, **belgenin çivilendiği fiyat sürümünden** — belge ekranının tek kaynağı.
     *
     * Türe göre dallanma yok: fizibilite referans sürüme, keşif/hakediş işin pazarlık sürümüne
     * çivili olduğu için `belge.fiyat_versiyonu_id` her iki durumda da doğru fiyatı verir. Bölge
     * yetkisi önce belgenin kendisinde denetlenir; başka bölgenin belgesi 404 döner ve poz listesi
     * hiç üretilmez.
     */
    fun pozlar(belgeId: Long): List<PozYaniti> {
        belgeler.bul(OturumKullanicisi.bolgeSuzgeci(), belgeId) ?: throw BulunamadiException("Belge bulunamadı: $belgeId")
        return sozlesmeler.belgeninPozlari(belgeId)
    }

    fun detay(belgeId: Long): BelgeYaniti {
        val belge = belgeler.bul(OturumKullanicisi.bolgeSuzgeci(), belgeId)
            ?: throw BulunamadiException("Belge bulunamadı: $belgeId")
        val satirlar = belgeler.satirlar(belgeId)
        val toplam = PricingCalculator.total(satirlar.map { PricedRow(it.birimFiyat, it.miktar) })

        // Düzenlenebilirlik sunucuda türetilir; istemci durumdan kendi kuralını yazmaz (CLAUDE.md §7/1).
        val duzenlenebilir = DocumentStageRule.duzenlenebilirMi(belge.tur, belge.durum)

        return BelgeYaniti(
            id = belge.id,
            isId = belge.isId,
            belgeNo = belge.belgeNo,
            tur = belge.tur,
            durum = belge.durum,
            durumMetni = BelgeMetinleri.durumMetni(belge.durum),
            bolgeKodu = belge.bolgeKodu,
            yil = belge.yil,
            siraNo = belge.siraNo,
            sozlesmeKodu = belge.sozlesmeKodu,
            sozlesmeAdi = belge.sozlesmeAdi,
            fiyatVersiyonuId = belge.fiyatVersiyonuId,
            olusturma = belge.olusturma,
            gonderme = belge.gonderme,
            tamamlanma = belge.tamamlanma,
            sonGuncelleme = belge.sonGuncelleme.an,
            sonGuncelleyenAdSoyad = belge.sonGuncelleme.adSoyad,
            sonGuncelleyenRol = belge.sonGuncelleme.rol,
            iptalGerekce = belge.iptalGerekce,
            duzenlenebilir = duzenlenebilir,
            // Onay bekleyen (düzenlenebilir) aşama gönderilebilir; onaya kadar tekrar gönderilebilir.
            gonderilebilir = duzenlenebilir,
            satirlar = satirlar.map(::satirYaniti),
            toplam = toplam,
            toplamGosterim = MoneyFormatter.money(toplam),
        )
    }

    /**
     * Ek dosya kabul eden belge: **herhangi bir taslak**.
     *
     * ~~Yalnızca hakediş~~ Faz P'de genişledi: güzergâh dosyası üç aşamada da zorunlu, görsel üçünde
     * de opsiyonel. Kural burada tek yerde duruyor ki yükleme, listeleme ve silme uçlarının üçü de
     * aynı cevabı versin.
     */
    fun dosyaKabulEdenBelge(belgeId: Long): BelgeOzeti = duzenlenebilirBelge(belgeId)

    /**
     * Gönderimin ön koşulu: **EK1 zorunlu**, üç belge türünde de (Faz P).
     *
     * Gönderme geri alınamaz (D2), bu yüzden kontrol gönderme anında yapılır: eksik ekle gönderilmiş
     * bir belgenin düzeltme yolu yok, tek çıkışı yönetici iptali olurdu. EK2 opsiyoneldir.
     */
    private fun ekleriDogrula(belgeId: Long) {
        if (!ekler.varMi(belgeId, EkTuru.EK1_ROTA)) {
            throw KuralIhlaliException("Belge gönderilemez: güzergâh dosyası (.kmz/.kml) zorunludur.")
        }
    }

    /**
     * Değiştirilebilir belge: aşama yalnızca `ONAY_BEKLIYOR` iken düzenlenir; yönetici işleme aldıktan
     * (`ISLENIYOR`) sonra içerik dondurulur. Karar `:core:domain`'deki `DocumentStageRule`'da; buradaki
     * iş yalnızca durumu toplamak ve reddi Türkçeye çevirmek.
     */
    private fun duzenlenebilirBelge(belgeId: Long): BelgeOzeti {
        val belge = belgeler.bul(OturumKullanicisi.bolgeSuzgeci(), belgeId)
            ?: throw BulunamadiException("Belge bulunamadı: $belgeId")
        if (!DocumentStageRule.duzenlenebilirMi(belge.tur, belge.durum)) {
            throw KuralIhlaliException("${belge.belgeNo} belgesi ${BelgeMetinleri.durumSifati(belge.durum)}; artık değiştirilemez.")
        }
        return belge
    }

    /**
     * Tutar ve gösterim metni **sunucuda** üretilir: web ve mobil para aritmetiği yapmaz, formatlama da
     * tek yerde (`MoneyFormatter`) kalır (CLAUDE.md §5).
     */
    private fun satirYaniti(satir: BelgeSatiri): BelgeSatirYaniti {
        val tutar = PricingCalculator.rowAmount(satir.birimFiyat, satir.miktar)
        return BelgeSatirYaniti(
            pozId = satir.pozId,
            pozNo = satir.pozNo,
            ad = satir.ad,
            birim = satir.birim,
            miktar = satir.miktar,
            miktarGosterim = MoneyFormatter.quantity(satir.miktar),
            birimFiyat = satir.birimFiyat,
            // Satıra 2 haneli çivilendiği için gösterim de yuvarlanmış: gösterilen fiyat, hesapta
            // kullanılan fiyattır ve `fiyat × miktar = tutar` tam tutar (Faz P).
            birimFiyatGosterim = MoneyFormatter.money(satir.birimFiyat),
            tutar = tutar,
            tutarGosterim = MoneyFormatter.money(tutar),
        )
    }

    private fun retMesaji(
        tur: DocumentType,
        ret: StageDecision.Reddedildi,
        mevcut: Map<DocumentType, DocumentStatus>,
        isOzeti: IsOzeti,
    ): String {
        val turAdi = BelgeMetinleri.turAdi(tur)
        val hemenOnceki = tur.oncekiAsamalar.lastOrNull()
        return when (ret.gerekce) {
            StageRejection.ZATEN_VAR ->
                "Bu proje için $turAdi belgesi zaten var."

            StageRejection.ONCEKI_ASAMA_YOK -> {
                val eksik = tur.oncekiAsamalar.first { mevcut[it] == null || mevcut[it] == DocumentStatus.IPTAL }
                "$turAdi açılabilmesi için önce bu projenin ${BelgeMetinleri.turAdi(eksik)} belgesi açılmalı."
            }

            // Kullanıcının yapacak bir şeyi yok: yöneticinin bir önceki aşamayı işleme almasını bekliyor.
            StageRejection.ONCEKI_ASAMA_ISLEME_ALINMAMIS ->
                "$turAdi açılabilmesi için önce bu projenin ${hemenOnceki?.let(BelgeMetinleri::turAdi)} aşaması " +
                    "yönetici tarafından işleme alınmalı."

            StageRejection.ONCEKI_ASAMA_TAMAMLANMAMIS -> {
                val eksik = tur.oncekiAsamalar.first { mevcut[it] != DocumentStatus.TAMAMLANDI }
                "$turAdi açılabilmesi için önce bu projenin ${BelgeMetinleri.turAdi(eksik)} aşaması tamamlanmalı."
            }

            StageRejection.IS_KAPANMIS ->
                "${isOzeti.spNo} projesi \"${IsMetinleri.durumMetni(isOzeti.durum)}\"; yeni belge açılamaz."
        }
    }
}

/** İstemcinin gönderdiği tek satır: poz satır kodu (`PozItem.id`) + miktar. */
data class SatirGirdisi(val pozId: String, val miktar: BigDecimal)
