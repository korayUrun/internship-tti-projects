package tr.com.tti.hakedis.server.kable

import tr.com.tti.hakedis.tools.kable.KonumYolu

/**
 * Bir ucun adresi — **ya segmentler ya hazır yol**.
 *
 * Segment formu (Ülke, Şehir, Adres … Raf) yeni kayıtlarda kullanılır; düzenleme formu var olan
 * [konumYolu]'nu olduğu gibi geri gönderir. İkisi de verilirse **segmentler kazanır**.
 *
 * Birleştirme **sunucuda** yapılıyor ([KonumYolu.kur]): istemci ayraçları (`>>`, bina `>` kat) kendi
 * kursaydı kural iki yerde yaşar ve elle girilen adres envanterden gelenden sessizce ayrışırdı
 * (CLAUDE.md §7/1).
 *
 * İki çağıranı var: elle kablo ekleme (Faz U/V) ve "Yeni Tesis" projesinde kablosuz uç girişi
 * (Faz W). Bu yüzden `KableElleServisi`'den ayrı bir dosyaya çıkarıldı — `isler` paketinin kablo
 * servisine bağımlı olması yanlış bir bağımlılık yönü olurdu.
 */
data class UcIstegi(val konumYolu: String? = null, val segmentler: Map<String, String>? = null)

/** [UcIstegi]'nin çözümlenmesi — iki çağıran da aynı kuralı kullansın diye tek yerde. */
object KonumGirdisi {
    /** Konum yolu; hiçbir segment ve hazır yol verilmemişse `null`. */
    fun yol(uc: UcIstegi?): String? {
        if (uc == null) return null
        val segmentler = uc.segmentler
        if (!segmentler.isNullOrEmpty()) return KonumYolu.kur { ad -> KonumYolu.sadelestir(segmentler[ad]) }
        return KonumYolu.sadelestir(uc.konumYolu)
    }

    /**
     * Şehir, segment formunda **`city` segmentinden** gelir; ayrı bir kutu sorulmaz.
     *
     * Parser'da da böyle: `cityA` sütunu hem `kable.sehir_a`'yı hem konum yolunun şehir segmentini
     * besliyor. İkisi ayrışsaydı envanter dışa aktarımı yolu doğru bölemez ve gidiş-dönüş bozulurdu
     * (bkz. `KableCiktisi.ucYaz`).
     *
     * Hazır yol gönderilen (düzenleme) hâlde istekteki şehir alanı kullanılır.
     */
    fun sehir(uc: UcIstegi?, istektekiSehir: String?): String? {
        val segmentler = uc?.segmentler
        if (!segmentler.isNullOrEmpty()) return KonumYolu.sadelestir(segmentler["city"])
        return KonumYolu.sadelestir(istektekiSehir)
    }
}
