package tr.com.tti.hakedis.server.ek

import tr.com.tti.hakedis.domain.document.DocumentFileNamer

/**
 * Yüklenen dosyaların `YUKLEME_KOK` altındaki **göreli** yolları.
 *
 * Ağaç `bölge/yıl/belgeNo/` — arşivin (`bölge/yıl/tür/`) benzeri ama belge kırılımlı: bir belgenin
 * ekleri tek klasörde toplanır, uygulamaya erişilemediğinde "şu hakedişin güzergâh dosyası" tek
 * bakışta bulunur.
 *
 * Dosya adı `EK1_{ekId}_{özgün ad}` biçimindedir:
 * - **`ekId`** çakışmayı bitirir — aynı belgeye aynı adla ikinci bir görsel yüklenmesi olağandır
 *   (`IMG_0001.jpg`), ve ikincisi birincinin üzerine yazarsa veri sessizce kaybolur.
 * - **Özgün ad korunur** (sadeleştirilmiş hâliyle): dosya sisteminden bakan kişi hangi dosyanın ne
 *   olduğunu görebilmeli.
 *
 * Adlar [DocumentFileNamer.sanitize] ile Türkçe karakterlerden ve özel karakterlerden arındırılır;
 * `..` ve `/` de bu süzgeçte düşer, dolayısıyla kullanıcı adıyla dizin dışına çıkılamaz.
 */
object EkYolu {
    /** `IST-AVR/2026/IST-AVR_2026_HAKEDIS_001` */
    fun klasor(bolgeKodu: String, yil: Int, belgeNo: String): String =
        "${DocumentFileNamer.sanitize(bolgeKodu)}/$yil/${DocumentFileNamer.sanitize(belgeNo)}"

    /** `IST-AVR/2026/IST-AVR_2026_HAKEDIS_001/EK1_42_guzergah.kmz` */
    fun ek(bolgeKodu: String, yil: Int, belgeNo: String, tur: EkTuru, ekId: Long, dosyaAdi: String): String =
        "${klasor(bolgeKodu, yil, belgeNo)}/${tur.etiket}_${ekId}_${guvenliAd(dosyaAdi)}"

    /**
     * Sadeleştirme, adın **gövdesini** tamamen yiyebilir: yalnızca Latin dışı karakterlerden oluşan bir
     * ad (`图片.png`) ya da `../../../etc/passwd` gibi bir deneme geriye sadece nokta bırakır. Böyle bir
     * durumda `.png` gibi **gizli dosya** adı yazmak yerine gövde `dosya` ile doldurulur.
     *
     * Uzantı sadeleştirilmiş addan okunur, özgün addan değil: özgün adda uzantı sanılan parça `/`
     * içerebilir (`../../../etc/passwd`) ve yolu dizin dışına taşırdı.
     */
    private fun guvenliAd(dosyaAdi: String): String {
        val temiz = DocumentFileNamer.sanitize(dosyaAdi)
        if (!temiz.contains('.')) return temiz.ifEmpty { "dosya" }

        val uzanti = temiz.substringAfterLast('.')
        val govde = temiz.substringBeforeLast('.').trim('.')
        return when {
            govde.isEmpty() && uzanti.isEmpty() -> "dosya"
            govde.isEmpty() -> "dosya.$uzanti"
            uzanti.isEmpty() -> govde
            else -> "$govde.$uzanti"
        }
    }
}
