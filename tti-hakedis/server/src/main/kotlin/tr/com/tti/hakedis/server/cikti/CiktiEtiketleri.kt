package tr.com.tti.hakedis.server.cikti

import org.springframework.boot.context.properties.ConfigurationProperties
import tr.com.tti.hakedis.domain.document.DocumentType

/**
 * PDF ve Excel çıktısındaki **kullanıcıya görünen** tüm metinler.
 *
 * Hardcoded string yasağı sunucuda da geçerli (CLAUDE.md §13): mobilde `strings.xml` neyse, sunucuda
 * `application.yml` odur. Metin değişikliği için kod derlemek gerekmez.
 *
 * Varsayılanlar burada duruyor ki `application.yml`'de bir anahtar unutulursa çıktı boş etiketle değil,
 * doğru metinle üretilsin — eksik etiket sessiz bir bozulma olurdu.
 */
@ConfigurationProperties(prefix = "cikti.etiket")
data class CiktiEtiketleri(
    val markaAdi: String = "Türk Telekom International",
    val bolge: String = "Bölge",
    val kapsam: String = "Kapsam",
    val sozlesme: String = "Sözleşme",
    val spNo: String = "SP No",
    val route: String = "Route",
    val ekAraligi: String = "Ek Aralığı",
    val kableId: String = "KableID",
    val adresA: String = "Uç A",
    val adresB: String = "Uç B",
    val belgeNo: String = "Belge No",
    val olusturulma: String = "Oluşturulma",
    val sutunPozNo: String = "Poz No",
    val sutunAd: String = "İş Kalemi",
    val sutunBirim: String = "Birim",
    val sutunBirimFiyat: String = "Br Fiyat",
    val sutunMiktar: String = "Miktar",
    val sutunTutar: String = "Tutar",
    /** Keşif ve hakedişin paylaştığı kaynak şablon başlıkları (8 sütun düzeni). */
    val sutunKesifMiktar: String = "1. Keşif Talep",
    val sutunSahaMiktar: String = "Saha Uygulama",
    val sutunKesifTutar: String = "1.Keşif Tutar",
    val sutunHakedisTutar: String = "Kesin Hakediş Tutar",
    /** Fizibilitenin kendi miktar/tutar başlıkları — kaynakta karşılığı yok (Faz G kararı). */
    val sutunFizibiliteMiktar: String = "Fizibilite Miktar",
    val sutunFizibiliteTutar: String = "Fizibilite Tutar",
    val toplam: String = "Toplam",
    val ek1: String = "EK1 (güzergâh)",
    val ek1Yok: String = "yok",
    val ek2: String = "EK2 (görsel)",
    val ek2Adet: String = "%d adet",
    /**
     * EK3 — yalnızca hakediş belgelerinde dolar (kullanıcı isteği, 18 Ağustos 2026).
     * Excel'de EK2 satırının hemen altında, her malzeme kendi satırında.
     */
    val ek3: String = "EK3 (TTI Kullanılan Malzemeler)",
    val ek3MalzemeAdi: String = "Malzeme Adı",
    val ek3Miktar: String = "Malzeme Miktarı",
    val ek3SeriNo: String = "Seri No",
    /**
     * Toplam satırının altındaki imza bloğu (kullanıcı isteği, 18 Ağustos 2026).
     *
     * [imzaSirket] **boş bırakılır**: yüklenici firma projeden projeye değişiyor ve sistemde tutulan
     * bir alanı yok; ilgili kişi dosyada elle yazıyor. Sistemden gelen tek isim bölge sorumlusudur.
     */
    val imzaSirket: String = "ŞİRKET ADI: ",
    /**
     * Kurum adı bu blokta **büyük harfle** yazılır ve metin olarak burada duruyor.
     *
     * ⚠️ `markaAdi.uppercase()` ile TÜRETİLMEZ: Türkçe locale `International` → `INTERNATİONAL`
     * üretiyor (i → İ), `Locale.ROOT` ise `Türk` → `TÜRK`'ü doğru yapıyor ama kural iki dilli bir
     * metinde tek seçimle çözülmüyor. Sabit metin bu tuzağı tamamen ortadan kaldırıyor (CLAUDE.md §5).
     */
    val imzaKurumAdi: String = "TURK TELEKOM INTERNATIONAL",
    val imzaSirketBolumu: String = "KEŞİF VE PROJELENDİRME",
    val imzaTtiBolumu: String = "ONAY",
    val imzaSurvey: String = "Survey-Keşif",
    val imzaProjeHazirlama: String = "Proje Hazırlama",
    val imzaProjeMuduru: String = "Proje Müdürü",
    val imzaBolgeSorumlusu: String = "Bölge Sorumlusu",
    val imzaKontrolEden: String = "Kontrol Eden",
    /** İki kontrol eden yan yana; ikisi de sabit (kullanıcı isteği). */
    val imzaKontrolEden1: String = "Sinan Pamuk",
    val imzaKontrolEden2: String = "Serhat Çorbacı",
    val turFizibilite: String = "Ön Fizibilite",
    val turKesif: String = "1. Keşif",
    val turHakedis: String = "Hakediş",
    /** İş listesi çıktısının başlıkları (`IsListesiExceli`) — aşama sütunları tür adıyla öneklenir. */
    val isListesiSayfaAdi: String = "Projeler",
    val sutunIsDurumu: String = "Proje Durumu",
    val sutunIsAcilma: String = "Proje Başlatma",
    val sutunDurum: String = "Durum",
    val sutunAcilma: String = "Açılma",
    val sutunDegistirilme: String = "Değiştirilme",
    val sutunGuncelleyen: String = "Güncelleyen",
    val sutunIptalGerekce: String = "İptal Gerekçesi",
    /**
     * Tablonun sonundaki iki aşama farkı sütunu (müdür isteği, Ağustos 2026). İkisi de **sonraki
     * aşama − önceki aşama**; ayrı bir "Siparişler" dosyası yerine asıl listeye taşındı.
     */
    val sutunFarkFizibiliteKesif: String = "Fizibilite / 1. Keşif Farkı",
    val sutunFarkKesifHakedis: String = "1. Keşif / Hakediş Farkı",
) {
    fun turAdi(tur: DocumentType): String = when (tur) {
        DocumentType.FIZIBILITE -> turFizibilite
        DocumentType.KESIF -> turKesif
        DocumentType.HAKEDIS -> turHakedis
    }

    /** EK2 görselleri PDF'e **basılmaz**, yalnızca adedi yazılır (NEWPHASES.md E9). */
    fun ek2Metni(adet: Int): String = ek2Adet.format(adet)
}
