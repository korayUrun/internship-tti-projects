package tr.com.tti.hakedis.server.isler

import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.Worksheet
import tr.com.tti.hakedis.domain.document.DocumentStatus
import tr.com.tti.hakedis.domain.document.DocumentType
import tr.com.tti.hakedis.server.belge.BelgeOzetYaniti
import tr.com.tti.hakedis.server.cikti.CiktiEtiketleri
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * İş listesinin `.xlsx` çıktısı — İşler ekranındaki "İşleri Excel olarak indir".
 *
 * Ekranın **aynası**: bir iş bir satır, aşama bilgileri o satırın sütunlarında. Ekranda aşamalar alt
 * alta üç kutu; Excel'de aynı bilgi yan yana üç blok olarak duruyor, çünkü listenin varlık nedeni
 * süzmek ve sıralamak — "hakedişi kapanmış işleri toplamıyla gör" ancak tek satırda mümkün.
 *
 * Üç kural çıktı tarafında aynen geçerli (CLAUDE.md §11):
 * 1. **Tutarlar değer olarak** yazılır, formül değil; sayı hücresi gerçek `number` tipindedir.
 * 2. **Tarihler gerçek tarih hücresidir**, metin değil. Metin yazılsaydı `dd.MM.yyyy` biçimi Excel'de
 *    alfabetik sıralanır ve listeyi tarihe göre sıralamak — indirmenin ana sebeplerinden biri —
 *    yanlış sonuç verirdi.
 * 3. **Kullanıcıya görünen metin `CiktiEtiketleri`'nden** gelir; sunucuda hardcoded string yasağı
 *    `application.yml` üzerinden işler (§13).
 *
 * Aşama blokları `DocumentType.entries` sırasını izler; enum sırası zaten akış sırasıdır.
 *
 * **Alt toplam satırı yoktur** *(müdür isteği, Ağustos 2026)*: `setAutoFilter` ile süzülen bir tabloda
 * sabit toplam satırı süzgeçle birlikte güncellenmez ve okuyan kişi onu süzülmüş toplam sanar. Excel'in
 * kendi toplamı (seçim + durum çubuğu) süzgece uyar. Yerine tablonun sonunda **aşama farkı** sütunları
 * var; toplamı gereken kişi o sütunu kendi süzgeciyle toplar.
 */
object IsListesiExceli {
    /**
     * @param kapsamAdlari `kapsam.kod → ad`. İşin yanıtında yalnızca kod var (`DEPLASE_IYILESTIRME`);
     *   çıktıya kod basmak dosyayı okuyan kişiye sözlük gerektirirdi.
     */
    fun uret(isler: List<IsYaniti>, kapsamAdlari: Map<String, String>, etiketler: CiktiEtiketleri): ByteArray {
        val cikti = ByteArrayOutputStream(BASLANGIC_TAMPONU)

        Workbook(cikti, etiketler.markaAdi, SURUM).use { kitap ->
            val sayfa = kitap.newWorksheet(etiketler.isListesiSayfaAdi)

            basliklariYaz(sayfa, etiketler)
            isler.forEachIndexed { indeks, kayit -> isYaz(sayfa, ILK_VERI_SATIRI + indeks, kayit, kapsamAdlari) }
            sutunGenislikleri(sayfa)

            // Başlık satırı ve SP No sütunu sabitlenir: 26 sütunluk bir tabloda sağa kaydırınca hangi
            // işe baktığını görememek listeyi okunmaz kılıyor.
            sayfa.freezePane(1, ILK_VERI_SATIRI)
            if (isler.isNotEmpty()) sayfa.setAutoFilter(BASLIK_SATIRI, 0, BASLIK_SATIRI + isler.size, SON_SUTUN)
        }

        return cikti.toByteArray()
    }

    private fun basliklariYaz(sayfa: Worksheet, etiketler: CiktiEtiketleri) {
        val basliklar = buildList {
            add(etiketler.spNo)
            add(etiketler.bolge)
            add(etiketler.kapsam)
            add(etiketler.kableId)
            add(etiketler.ekAraligi)
            add(etiketler.route)
            add(etiketler.sutunIsDurumu)
            add(etiketler.sutunIsAcilma)
            // Aşama sütunları tür adıyla öneklenir: önek olmadan altı ayrı "Durum" sütunu oluşur ve
            // hangisinin hangi aşamaya ait olduğu yalnızca sütun sırasından anlaşılırdı.
            DocumentType.entries.forEach { tur ->
                val ad = etiketler.turAdi(tur)
                add("$ad ${etiketler.sutunDurum}")
                add("$ad ${etiketler.sutunAcilma}")
                add("$ad ${etiketler.sutunDegistirilme}")
                add("$ad ${etiketler.sutunGuncelleyen}")
                add("$ad ${etiketler.toplam}")
            }
            add(etiketler.sutunIptalGerekce)
            add(etiketler.sutunFarkFizibiliteKesif)
            add(etiketler.sutunFarkKesifHakedis)
        }

        basliklar.forEachIndexed { sutun, metin -> sayfa.value(BASLIK_SATIRI, sutun, metin) }
        sayfa.range(BASLIK_SATIRI, 0, BASLIK_SATIRI, SON_SUTUN).style()
            .bold()
            .fillColor(BASLIK_DOLGUSU)
            .borderStyle("thin")
            .set()
    }

    private fun isYaz(sayfa: Worksheet, satir: Int, kayit: IsYaniti, kapsamAdlari: Map<String, String>) {
        sayfa.value(satir, SUTUN_SP_NO, kayit.spNo)
        sayfa.value(satir, SUTUN_BOLGE, kayit.bolgeAdi)
        sayfa.value(satir, SUTUN_KAPSAM, kapsamAdlari[kayit.kapsamKodu] ?: kayit.kapsamKodu)
        // Kablosuz projede hücre BOŞ bırakılır (Faz W); sütun düzeni kaymaz.
        kayit.kableNosid?.let { sayfa.value(satir, SUTUN_KABLE, it) }
        sayfa.value(satir, SUTUN_EK_ARALIGI, kayit.ekAraligi)
        sayfa.value(satir, SUTUN_ROUTE, kayit.route)
        sayfa.value(satir, SUTUN_IS_DURUMU, kayit.durumMetni)
        sayfa.tarih(satir, SUTUN_IS_ACILMA, kayit.olusturma)
        kayit.iptalGerekce?.let { sayfa.value(satir, SUTUN_IPTAL_GEREKCE, it) }

        // İptal edilmiş belge listede yok sayılır — ekranda da öyle: iptal edilen taslağın yerine
        // aynı türden yenisi açılabildiği için satırda görünmesi gereken canlı olandır.
        val asamalar = kayit.belgeler.filter { it.durum != DocumentStatus.IPTAL }.associateBy { it.tur }

        DocumentType.entries.forEachIndexed { sira, tur ->
            asamalar[tur]?.let { belge -> asamaYaz(sayfa, satir, asamaSutunu(sira), belge) }
        }

        farklariYaz(sayfa, satir, asamalar)
    }

    /**
     * Tablonun sonundaki iki aşama farkı — ikisi de **sonraki aşama − önceki aşama**, yani pozitif
     * değer sonraki aşamanın daha pahalı olduğunu söyler (kaldırılan ayrı "Siparişler" dosyasındaki
     * hesabın yönü korundu, okuyan kişi alışkanlığını değiştirmesin).
     *
     * Eksik aşama **sıfır sayılmaz**, hücre BOŞ kalır: keşfi henüz açılmamış bir projede
     * `keşif − fizibilite` yerine `0 − fizibilite` yazmak, yapılmamış bir karşılaştırmayı yapılmış
     * gibi gösterir ve süzgeçte gerçek bir sapmadan ayırt edilemezdi. Aynı gerekçe kablosuz projenin
     * boş `KableID` hücresinde de geçerli.
     */
    private fun farklariYaz(sayfa: Worksheet, satir: Int, asamalar: Map<DocumentType, BelgeOzetYaniti>) {
        fark(asamalar, DocumentType.FIZIBILITE, DocumentType.KESIF)
            ?.let { sayfa.para(satir, SUTUN_FARK_FIZ_KESIF, it) }
        fark(asamalar, DocumentType.KESIF, DocumentType.HAKEDIS)
            ?.let { sayfa.para(satir, SUTUN_FARK_KESIF_HAKEDIS, it) }
    }

    /** İki aşama da varsa `sonraki − onceki`; biri bile eksikse `null` (hücre yazılmaz). */
    private fun fark(asamalar: Map<DocumentType, BelgeOzetYaniti>, onceki: DocumentType, sonraki: DocumentType): BigDecimal? {
        val oncekiToplam = asamalar[onceki]?.toplam ?: return null
        val sonrakiToplam = asamalar[sonraki]?.toplam ?: return null
        return sonrakiToplam.subtract(oncekiToplam)
    }

    private fun asamaYaz(sayfa: Worksheet, satir: Int, ilkSutun: Int, belge: BelgeOzetYaniti) {
        sayfa.value(satir, ilkSutun + ASAMA_DURUM, belge.durumMetni)
        sayfa.tarih(satir, ilkSutun + ASAMA_ACILMA, belge.olusturma)
        sayfa.tarih(satir, ilkSutun + ASAMA_DEGISTIRILME, belge.sonGuncelleme)
        belge.sonGuncelleyenAdSoyad?.let { sayfa.value(satir, ilkSutun + ASAMA_GUNCELLEYEN, it) }
        sayfa.para(satir, ilkSutun + ASAMA_TOPLAM, belge.toplam)
    }

    private fun sutunGenislikleri(sayfa: Worksheet) {
        sayfa.width(SUTUN_SP_NO, 18.0)
        sayfa.width(SUTUN_BOLGE, 16.0)
        sayfa.width(SUTUN_KAPSAM, 26.0)
        sayfa.width(SUTUN_KABLE, 26.0)
        sayfa.width(SUTUN_EK_ARALIGI, 14.0)
        sayfa.width(SUTUN_ROUTE, 22.0)
        sayfa.width(SUTUN_IS_DURUMU, 18.0)
        sayfa.width(SUTUN_IS_ACILMA, 18.0)
        DocumentType.entries.forEachIndexed { sira, _ ->
            val ilk = asamaSutunu(sira)
            sayfa.width(ilk + ASAMA_DURUM, 22.0)
            sayfa.width(ilk + ASAMA_ACILMA, 18.0)
            sayfa.width(ilk + ASAMA_DEGISTIRILME, 18.0)
            sayfa.width(ilk + ASAMA_GUNCELLEYEN, 24.0)
            sayfa.width(ilk + ASAMA_TOPLAM, 16.0)
        }
        sayfa.width(SUTUN_IPTAL_GEREKCE, 40.0)
        sayfa.width(SUTUN_FARK_FIZ_KESIF, 24.0)
        sayfa.width(SUTUN_FARK_KESIF_HAKEDIS, 24.0)
    }

    private fun asamaSutunu(sira: Int) = ILK_ASAMA_SUTUNU + sira * ASAMA_SUTUN_SAYISI

    /**
     * Tarih hücresi. `Instant` **İstanbul saatine** çevrilerek yazılır: sunucu UTC çalışıyor ve ham
     * `Instant` yazılsaydı dosyada saatler üç saat geride görünürdü.
     */
    private fun Worksheet.tarih(satir: Int, sutun: Int, an: Instant?) {
        an?.let { value(satir, sutun, LocalDateTime.ofInstant(it, ISTANBUL)) }
        style(satir, sutun).format(TARIH_BICIMI).set()
    }

    private fun Worksheet.para(satir: Int, sutun: Int, tutar: BigDecimal) {
        value(satir, sutun, tutar as Number)
        style(satir, sutun).format(PARA_BICIMI).set()
    }

    private const val SURUM = "1.0"
    private const val PARA_BICIMI = "#,##0.00"
    private const val TARIH_BICIMI = "dd.MM.yyyy HH:mm"
    private const val BASLIK_DOLGUSU = "E8F0FF"
    private const val BASLANGIC_TAMPONU = 32 * 1024

    private val ISTANBUL: ZoneId = ZoneId.of("Europe/Istanbul")

    private const val BASLIK_SATIRI = 0
    private const val ILK_VERI_SATIRI = 1

    private const val SUTUN_SP_NO = 0
    private const val SUTUN_BOLGE = 1
    private const val SUTUN_KAPSAM = 2
    private const val SUTUN_KABLE = 3
    private const val SUTUN_EK_ARALIGI = 4
    private const val SUTUN_ROUTE = 5
    private const val SUTUN_IS_DURUMU = 6
    private const val SUTUN_IS_ACILMA = 7
    private const val ILK_ASAMA_SUTUNU = 8

    private const val ASAMA_DURUM = 0
    private const val ASAMA_ACILMA = 1
    private const val ASAMA_DEGISTIRILME = 2
    private const val ASAMA_GUNCELLEYEN = 3
    private const val ASAMA_TOPLAM = 4
    private const val ASAMA_SUTUN_SAYISI = 5

    private val SUTUN_IPTAL_GEREKCE = ILK_ASAMA_SUTUNU + DocumentType.entries.size * ASAMA_SUTUN_SAYISI

    // Tablonun sonundaki iki fark sütunu; `SON_SUTUN` başlık stili ve otomatik süzgeç aralığını
    // besliyor, o yüzden en sağdaki sütunu göstermek ZORUNDA — aksi hâlde yeni sütunlar süzgecin
    // dışında kalır ve başlıkları biçimsiz görünür.
    private val SUTUN_FARK_FIZ_KESIF = SUTUN_IPTAL_GEREKCE + 1
    private val SUTUN_FARK_KESIF_HAKEDIS = SUTUN_FARK_FIZ_KESIF + 1
    private val SON_SUTUN = SUTUN_FARK_KESIF_HAKEDIS
}
