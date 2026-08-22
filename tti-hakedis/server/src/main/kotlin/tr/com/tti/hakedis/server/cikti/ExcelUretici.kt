package tr.com.tti.hakedis.server.cikti

import org.dhatim.fastexcel.PictureAnchor
import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.Worksheet
import org.springframework.stereotype.Component
import tr.com.tti.hakedis.domain.document.DocumentModel
import tr.com.tti.hakedis.domain.document.DocumentVariant
import tr.com.tti.hakedis.domain.pricing.PricedRow
import tr.com.tti.hakedis.domain.pricing.PricingCalculator
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Belgenin `.xlsx` çıktısı (CLAUDE.md §11).
 *
 * İki kural pazarlıksız:
 * 1. **Sözleşmenin tüm satırları, kaynak sırasıyla** yazılır — grup başlıkları dahil, miktarı
 *    girilmemiş kalemler dahil. Excel sözleşmenin aynası olmalı; PDF ise yalnızca dolu satırları
 *    listeler. Aynı belgenin iki çıktısı bilinçli olarak farklı sorulara cevap verir.
 * 2. **Tutarlar formül değil değer** olarak, gerçek `number` hücre tipiyle yazılır. Formül yazılsaydı
 *    dosyayı açan Excel sürümü yuvarlamayı kendi kuralıyla yapar ve uygulamadaki tutarla dosyadaki
 *    tutar ayrışabilirdi. Hesap tek yerde: `PricingCalculator`.
 *
 * Bilgi bloğu tablonun **üstünde** durur (kullanıcı kararı, 30 Temmuz 2026).
 */
@Component
class ExcelUretici(private val logo: MarkaLogosu) {
    fun uret(baslik: CiktiBasligi, satirlar: List<CiktiSatiri>, model: DocumentModel, etiketler: CiktiEtiketleri): ByteArray {
        val cikti = ByteArrayOutputStream(BASLANGIC_TAMPONU)
        val duzen = ExcelDuzeni.of(baslik.tur, etiketler)

        Workbook(cikti, etiketler.markaAdi, SURUM).use { workbook ->
            val sayfa = workbook.newWorksheet(sayfaAdi(baslik))
            logoyuYaz(sayfa, duzen)
            val tabloBasi = bilgiBlogu(sayfa, baslik, model, etiketler, duzen)
            basliklariYaz(sayfa, tabloBasi, duzen)
            val sonSatir = satirlariYaz(sayfa, tabloBasi + 1, satirlar, model.variant, duzen)
            val toplamSatiri = sonSatir + 1
            toplamYaz(sayfa, toplamSatiri, model, etiketler, duzen, kesifToplami(satirlar, model.variant))
            imzaBloguYaz(sayfa, toplamSatiri + 2, baslik, etiketler, duzen)
            sutunGenislikleri(sayfa, duzen)
        }

        return cikti.toByteArray()
    }

    /**
     * TTI logosu — sayfanın **sağ üst köşesine**, son iki sütunun üstündeki boş alana
     * (kullanıcı isteği, 19 Ağustos 2026). PDF'te logo baştan beri vardı, Excel'de yoktu.
     *
     * Sütun aralığı sabit değil `duzen.sonSutun`'dan türetiliyor: fizibilite 6, keşif/hakediş 8
     * sütun basıyor ve logo ikisinde de tablonun sağ kenarına yaslanmalı. Bilgi bloğu sol iki
     * sütunda kaldığı için çakışma yok.
     *
     * Logo yoksa **hiçbir şey basılmaz ve üretim aksamaz** — `MarkaLogosu` dosyayı isteğe bağlı
     * sayıyor (bkz. `logo/OKU.md`); PDF tarafındaki kuralın aynısı.
     */
    private fun logoyuYaz(sayfa: Worksheet, duzen: ExcelDuzeni) {
        val baytlar = logo.baytlar ?: return
        val ilkSutun = (duzen.sonSutun - 1).coerceAtLeast(0)

        // İki hücreli çapa: görsel bir hücre aralığına yaslanır ve sütun genişliği değişse de sağ
        // kenarda kalır.
        //
        // ⚠️ Parametre sırası **(satır, sütun, satırSon, sütunSon)** — fastexcel'in `value(satır,
        // sütun, …)` düzeniyle aynı, ama adı (`twoCellAnchor`) bunu söylemiyor ve belgelenmemiş.
        // Ters verildiğinde hata çıkmaz, logo sessizce sol üste düşer; sıra üretilen dosyanın
        // `xl/drawings/drawing1.xml` çapası okunarak doğrulandı.
        sayfa.addImage(
            PictureAnchor.twoCellAnchor(0, ilkSutun, LOGO_SATIR_YUKSEKLIGI, duzen.sonSutun + 1),
            baytlar,
        )
    }

    /** @return başlık satırının indeksi (bilgi bloğundan sonra bir boş satır bırakılır) */
    private fun bilgiBlogu(
        sayfa: Worksheet,
        baslik: CiktiBasligi,
        model: DocumentModel,
        etiketler: CiktiEtiketleri,
        duzen: ExcelDuzeni,
    ): Int {
        sayfa.value(0, 0, baslik.sozlesmeBasligi)
        sayfa.range(0, 0, 0, duzen.sonSutun).style().bold().fontSize(BASLIK_PUNTO).set()

        val alanlar = buildList {
            add(etiketler.belgeNo to "${baslik.belgeNo} · ${etiketler.turAdi(baslik.tur)}")
            add(etiketler.bolge to baslik.bolgeAdi)
            add(etiketler.kapsam to baslik.kapsamAdi)
            add(etiketler.sozlesme to baslik.sozlesmeAdi)
            add(etiketler.spNo to baslik.spNo)
            add(etiketler.route to baslik.route)
            add(etiketler.ekAraligi to baslik.ekAraligi)
            // Üçü de koşullu (Faz W): kablosuz "Yeni Tesis" projesinde boş bir etiket satırı
            // basmak eksik veri izlenimi verirdi. `adresB` bu deseni baştan beri kullanıyordu.
            baslik.kableNosid?.takeIf { it.isNotBlank() }?.let { add(etiketler.kableId to it) }
            baslik.adresA?.takeIf { it.isNotBlank() }?.let { add(etiketler.adresA to it) }
            baslik.adresB?.takeIf { it.isNotBlank() }?.let { add(etiketler.adresB to it) }
            add(etiketler.olusturulma to TARIH_BICIMI.format(model.createdAt))
            add(etiketler.ek1 to (model.attachments.routeFileName ?: etiketler.ek1Yok))
            add(etiketler.ek2 to etiketler.ek2Metni(model.attachments.imageCount))
        }

        alanlar.forEachIndexed { sira, (etiket, deger) ->
            val satir = ILK_BILGI_SATIRI + sira
            sayfa.value(satir, 0, etiket)
            sayfa.value(satir, 1, deger)
            sayfa.style(satir, 0).bold().set()
        }

        val ek3Sonu = ek3Yaz(sayfa, ILK_BILGI_SATIRI + alanlar.size, model, etiketler)
        return ek3Sonu + 1
    }

    /**
     * EK3 "TTI Kullanılan Malzemeler" — **EK2 satırının hemen altında**, her malzeme kendi satırında
     * (kullanıcı isteği, 18 Ağustos 2026).
     *
     * Malzeme yoksa hiçbir satır basılmaz ve blok tamamen görünmez: boş bir "EK3" başlığı, veri
     * girilmemiş mi yoksa aşama malzeme kabul etmiyor mu sorusunu cevapsız bırakırdı. Aynı desen
     * `adresB` ve kablo alanlarında da uygulanıyor (Faz W).
     *
     * @return yazılan son satırın indeksi (hiç yazılmadıysa [ilkSatir] - 1, yani bilgi bloğunun sonu)
     */
    private fun ek3Yaz(sayfa: Worksheet, ilkSatir: Int, model: DocumentModel, etiketler: CiktiEtiketleri): Int {
        val malzemeler = model.attachments.materials
        if (malzemeler.isEmpty()) return ilkSatir - 1

        // Başlık satırı: etiket sütununda "EK3 (…)", yanında alt sütun adları. Malzeme satırları
        // bilgi bloğunun etiket/değer düzenine değil, kendi üç sütununa yaslanıyor.
        sayfa.value(ilkSatir, 0, etiketler.ek3)
        sayfa.value(ilkSatir, 1, etiketler.ek3MalzemeAdi)
        sayfa.value(ilkSatir, 2, etiketler.ek3Miktar)
        sayfa.value(ilkSatir, 3, etiketler.ek3SeriNo)
        sayfa.range(ilkSatir, 0, ilkSatir, 3).style().bold().fillColor(BASLIK_DOLGUSU).borderStyle("thin").set()

        malzemeler.forEachIndexed { sira, malzeme ->
            val satir = ilkSatir + 1 + sira
            malzeme.ad?.let { sayfa.value(satir, 1, it) }
            // Miktar gerçek sayı hücresi (CLAUDE.md §11); para değil, bu yüzden para biçimi yok.
            malzeme.miktar?.let { sayfa.value(satir, 2, it) }
            malzeme.seriNo?.let { sayfa.value(satir, 3, it) }
            sayfa.style(satir, 2).format(MIKTAR_BICIMI).set()
        }

        return ilkSatir + malzemeler.size
    }

    private fun basliklariYaz(sayfa: Worksheet, satir: Int, duzen: ExcelDuzeni) {
        duzen.basliklar.forEachIndexed { sutun, metin -> sayfa.value(satir, sutun, metin) }
        sayfa.range(satir, 0, satir, duzen.sonSutun).style()
            .bold()
            .fillColor(BASLIK_DOLGUSU)
            .borderStyle("thin")
            .set()
    }

    /** @return yazılan son satırın indeksi */
    private fun satirlariYaz(
        sayfa: Worksheet,
        ilkSatir: Int,
        satirlar: List<CiktiSatiri>,
        varyant: DocumentVariant,
        duzen: ExcelDuzeni,
    ): Int {
        var satir = ilkSatir

        satirlar.forEach { kalem ->
            sayfa.value(satir, 0, kalem.pozNo)
            sayfa.value(satir, 1, kalem.ad)

            if (kalem.grupMu) {
                // Grup başlıkları kaynaktaki gibi kalın ve dolgulu kalır; fiyatsız, miktarsız, toplamsız.
                sayfa.range(satir, 0, satir, duzen.sonSutun).style().bold().fillColor(GRUP_DOLGUSU).set()
            } else {
                sayfa.value(satir, 2, kalem.birim)

                // TEKLIF varyantında birim fiyat ve tutar hücreleri BOŞ bırakılır: sütun düzeni aynı
                // kalır, firma kendi fiyatını doldurur (NEWPHASES.md E6).
                if (varyant.fiyatGoster && kalem.fiyatliMi) {
                    sayfa.value(satir, SUTUN_BIRIM_FIYAT, kalem.birimFiyat)
                }
                kalem.miktar?.let { miktar ->
                    sayfa.value(satir, duzen.miktarSutunu, miktar)
                    if (varyant.fiyatGoster) {
                        sayfa.value(satir, duzen.tutarSutunu, PricingCalculator.rowAmount(kalem.birimFiyat, miktar))
                    }
                }

                // Hakedişte aynı işin keşif miktarı ve tutarı yan sütunlara yazılır. Tutar keşifin
                // KENDİ dondurulmuş fiyatıyla hesaplanır; hakedişin fiyatı kullanılsa sütun keşif
                // belgesinde yazandan farklı çıkar ve karşılaştırma anlamını yitirirdi.
                duzen.kesifMiktarSutunu?.let { kesifMiktarSutunu ->
                    kalem.kesifMiktar?.let { kesifMiktar ->
                        sayfa.value(satir, kesifMiktarSutunu, kesifMiktar)
                        val kesifTutarSutunu = duzen.kesifTutarSutunu
                        if (varyant.fiyatGoster && kesifTutarSutunu != null && kalem.kesifBirimFiyat != null) {
                            sayfa.value(satir, kesifTutarSutunu, PricingCalculator.rowAmount(kalem.kesifBirimFiyat, kesifMiktar))
                        }
                    }
                    sayfa.style(satir, kesifMiktarSutunu).format(MIKTAR_BICIMI).set()
                    duzen.kesifTutarSutunu?.let { sayfa.style(satir, it).format(PARA_BICIMI).set() }
                }

                sayfa.style(satir, SUTUN_BIRIM_FIYAT).format(PARA_BICIMI).set()
                sayfa.style(satir, duzen.miktarSutunu).format(MIKTAR_BICIMI).set()
                sayfa.style(satir, duzen.tutarSutunu).format(PARA_BICIMI).set()
            }
            satir++
        }

        return satir - 1
    }

    /**
     * Toplam satırı.
     *
     * Etiket sütunu [ExcelDuzeni.toplamEtiketSutunu]'ndan gelir; sabit `tutarSutunu - 1` kullanmak
     * hakedişte etiketi tam "1.Keşif Tutar" sütununa yazıyordu (bkz. `ExcelDuzeni`).
     *
     * [kesifToplam] hakedişte aynı işin **keşif belgesinin toplamıdır** — o belgede yazan sayının
     * aynısı. Listelenen satırlardan yeniden toplanmaz, çünkü Excel sözleşmenin tüm satırlarını
     * yazıyor ve iki sayının farklı çıkması karşılaştırmayı bozardı.
     */
    private fun toplamYaz(
        sayfa: Worksheet,
        satir: Int,
        model: DocumentModel,
        etiketler: CiktiEtiketleri,
        duzen: ExcelDuzeni,
        kesifToplam: BigDecimal?,
    ) {
        sayfa.value(satir, duzen.toplamEtiketSutunu, etiketler.toplam)
        // TEKLIF varyantında toplam da yoktur (model `null` verir); hücre boş kalır.
        model.total?.let { sayfa.value(satir, duzen.tutarSutunu, it) }
        duzen.kesifTutarSutunu?.let { sutun ->
            kesifToplam?.let { sayfa.value(satir, sutun, it) }
            sayfa.style(satir, sutun).bold().fillColor(BASLIK_DOLGUSU).format(PARA_BICIMI).set()
        }

        sayfa.range(satir, 0, satir, duzen.sonSutun).style().bold().fillColor(BASLIK_DOLGUSU).set()
        sayfa.style(satir, duzen.tutarSutunu).bold().fillColor(BASLIK_DOLGUSU).format(PARA_BICIMI).set()
    }

    /**
     * Toplam satırının altındaki imza bloğu (kullanıcı isteği, 18 Ağustos 2026).
     *
     * Dört satır: firma/kurum adı · bölüm · unvanlar · imza satırı. Sol yarı yüklenici firmanın,
     * sağ yarı TTI'nin. Sol taraftaki üç unvan hücresi **boş** iner ve elle imzalanır.
     *
     * Sütun eşlemesi `duzen.sonSutun`'a göre hesaplanıyor, sabit değil: fizibilite 6 sütun,
     * keşif/hakediş 8 sütun basıyor ve blok ikisinde de tablonun tam genişliğini kaplamalı.
     * Altı mantıksal sütunun her biri en az bir gerçek sütun alır, artan sütunlar soldan dağıtılır.
     *
     * **Sistemden gelen tek isim bölge sorumlusudur**; şirket adı boş (projeden projeye değişiyor,
     * sistemde alanı yok) ve iki kontrol eden sabit metin (`CiktiEtiketleri`).
     */
    private fun imzaBloguYaz(sayfa: Worksheet, ilkSatir: Int, baslik: CiktiBasligi, etiketler: CiktiEtiketleri, duzen: ExcelDuzeni) {
        val sinirlar = imzaSutunSinirlari(duzen.sonSutun)
        val solIlk = sinirlar.first().first
        val solSon = sinirlar[2].second
        val sagIlk = sinirlar[3].first
        val sagSon = sinirlar[5].second

        // 1. satır: firma adı (boş, elle yazılır) | kurum adı
        sayfa.value(ilkSatir, solIlk, etiketler.imzaSirket)
        sayfa.value(ilkSatir, sagIlk, etiketler.imzaKurumAdi)
        sayfa.range(ilkSatir, solIlk, ilkSatir, solSon).merge()
        sayfa.range(ilkSatir, sagIlk, ilkSatir, sagSon).merge()

        // 2. satır: bölüm başlıkları
        sayfa.value(ilkSatir + 1, solIlk, etiketler.imzaSirketBolumu)
        sayfa.value(ilkSatir + 1, sagIlk, etiketler.imzaTtiBolumu)
        sayfa.range(ilkSatir + 1, solIlk, ilkSatir + 1, solSon).merge()
        sayfa.range(ilkSatir + 1, sagIlk, ilkSatir + 1, sagSon).merge()

        // 3. satır: unvanlar — altı mantıksal sütun.
        val unvanlar = listOf(
            etiketler.imzaSurvey,
            etiketler.imzaProjeHazirlama,
            etiketler.imzaProjeMuduru,
            etiketler.imzaBolgeSorumlusu,
            etiketler.imzaKontrolEden,
            etiketler.imzaKontrolEden,
        )
        // 4. satır: imza alanı. Sol üçü boş; sağda bölge sorumlusu sistemden, iki kontrol eden sabit.
        val adlar = listOf(null, null, null, baslik.bolgeSorumlusu, etiketler.imzaKontrolEden1, etiketler.imzaKontrolEden2)

        sinirlar.forEachIndexed { indeks, (ilk, son) ->
            sayfa.value(ilkSatir + 2, ilk, unvanlar[indeks])
            adlar[indeks]?.let { sayfa.value(ilkSatir + 3, ilk, it) }
            if (son > ilk) {
                sayfa.range(ilkSatir + 2, ilk, ilkSatir + 2, son).merge()
                sayfa.range(ilkSatir + 3, ilk, ilkSatir + 3, son).merge()
            }
        }

        // İlk üç satır kalın ve ortalı; imza satırı elle imzalanacağı için yüksek bırakılıyor.
        (0..2).forEach { sayfa.range(ilkSatir + it, solIlk, ilkSatir + it, sagSon).style().bold().set() }
        (0..3).forEach { sayfa.range(ilkSatir + it, solIlk, ilkSatir + it, sagSon).style().borderStyle("thin").set() }
        sayfa.rowHeight(ilkSatir + 3, IMZA_SATIR_YUKSEKLIGI)
    }

    /**
     * İmza bloğunun altı mantıksal sütununu gerçek sütun aralıklarına eşler.
     *
     * Her mantıksal sütun **en az bir** gerçek sütun alır; artan sütunlar soldan başlayarak birer
     * birer dağıtılır. `sonSutun` 5 (fizibilite) ise altı sütun bire bir, 7 (keşif/hakediş) ise ilk
     * ikisi çift genişlikte olur.
     */
    private fun imzaSutunSinirlari(sonSutun: Int): List<Pair<Int, Int>> {
        val toplam = sonSutun + 1
        val taban = toplam / IMZA_SUTUN_SAYISI
        val artan = toplam % IMZA_SUTUN_SAYISI

        var sonraki = 0
        return (0 until IMZA_SUTUN_SAYISI).map { indeks ->
            val genislik = taban + if (indeks < artan) 1 else 0
            val ilk = sonraki
            sonraki += genislik
            ilk to sonraki - 1
        }
    }

    /**
     * Keşif genel toplamı — hakediş çıktısı için. Grup başlıkları ve fiyatlandırılmamış kalemler
     * elenir; hesap `PricingCalculator` ile yapılır (CLAUDE.md §5: toplama burada elle yazılmaz).
     */
    private fun kesifToplami(satirlar: List<CiktiSatiri>, varyant: DocumentVariant): BigDecimal? {
        if (!varyant.fiyatGoster) return null
        val satirTutarlari = satirlar.mapNotNull { kalem ->
            if (kalem.grupMu || kalem.kesifBirimFiyat == null) return@mapNotNull null
            kalem.kesifMiktar?.let { PricedRow(kalem.kesifBirimFiyat, it) }
        }
        return if (satirTutarlari.isEmpty()) null else PricingCalculator.total(satirTutarlari)
    }

    private fun sutunGenislikleri(sayfa: Worksheet, duzen: ExcelDuzeni) {
        sayfa.width(0, 12.0)
        sayfa.width(1, 60.0)
        sayfa.width(2, 10.0)
        (3..duzen.sonSutun).forEach { sayfa.width(it, 18.0) }
    }

    /** Excel sayfa adı 31 karakteri geçemez ve `: \ / ? * [ ]` karakterlerini kabul etmez. */
    private fun sayfaAdi(baslik: CiktiBasligi): String = baslik.sozlesmeAdi
        .replace(Regex("[\\\\/:*?\\[\\]]"), "-")
        .take(EN_UZUN_SAYFA_ADI)
        .ifBlank { "Belge" }

    /** fastexcel'de `value(int, int, Number)` ve `value(int, int, String)` aşırı yüklemeleri var; ayrım açık yapılır. */
    private fun Worksheet.value(satir: Int, sutun: Int, deger: BigDecimal) {
        value(satir, sutun, deger as Number)
    }

    private companion object {
        const val SURUM = "1.0"
        const val PARA_BICIMI = "#,##0.00"
        const val MIKTAR_BICIMI = "#,##0.###"
        const val BASLIK_DOLGUSU = "E8F0FF"
        const val GRUP_DOLGUSU = "EDF1F6"
        const val BASLIK_PUNTO = 12
        const val EN_UZUN_SAYFA_ADI = 31
        const val ILK_BILGI_SATIRI = 2
        const val SUTUN_BIRIM_FIYAT = 3
        const val BASLANGIC_TAMPONU = 64 * 1024

        /** İmza bloğunun mantıksal sütun sayısı: sol üç unvan + sağ üç unvan. */
        const val IMZA_SUTUN_SAYISI = 6
        const val IMZA_SATIR_YUKSEKLIGI = 46.0

        /** Logonun kapladığı satır sayısı — bilgi bloğunun ilk satırlarının hizasında kalır. */
        const val LOGO_SATIR_YUKSEKLIGI = 5

        val TARIH_BICIMI: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.ROOT)
    }
}
