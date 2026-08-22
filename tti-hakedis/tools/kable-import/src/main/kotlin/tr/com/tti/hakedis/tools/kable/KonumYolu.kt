package tr.com.tti.hakedis.tools.kable

/**
 * Bir ucun **konum yolu** — envanterdeki "Location A / Location B" gösterimi
 * (kullanıcı isteği, 4 Ağustos 2026):
 *
 * ```
 * Turkiye >> Istanbul >> AND Plaza - Umut Sokak 10-12 (Atalian World ) >> AND Plaza > Ground Floor
 *         >> System Room >> (TTI Cabinet) >> HU2 - TTI ODF#01 Direction Kocnet (24p patchpanel)
 * ```
 *
 * Sıra envanterin sütun sırasıyla aynı ve hiyerarşiktir. **Boş segment atlanır**: kayıtların
 * çoğunda yalnızca ülke/şehir/adres dolu (943 satırın 144'ünde bina, 59'unda raf var), dolayısıyla
 * kısa yol normaldir — eksiklik değil.
 *
 * `addressnum` ve `siteprovider` **bilinçli olarak dışarıda**: adres alanı ikisini de kendi içinde
 * parantezli olarak taşıyor.
 *
 * Kural **tek yerde** duruyor çünkü iki çağıranı var (Faz V): Excel'den okuyan parser ve yöneticinin
 * elle girdiği form. İkisi ayrışsaydı elle eklenen kablo dosyadan gelenle aynı biçimde olmaz, fark
 * ekranı da onu her yüklemede "değişmiş" sayardı.
 */
object KonumYolu {
    /** Segment adları, envanterin sütun sırasında. Sütun adları bunlara `A`/`B` eklenerek kurulur. */
    val ALANLAR = listOf("country", "city", "address", "building", "floor", "room", "cage", "footprint", "rackspace")

    const val AYRAC = " >> "

    /** Bina ve kat **tek segmentte** birleşir: `AND Plaza > Ground Floor`. */
    const val BINA_KAT_AYRACI = " > "

    /**
     * @param deger segment adını (`"country"`, `"building"` …) alıp değerini döner; boş/eksik için `null`.
     * @return hiçbir segment yoksa `null` (B ucu boş olabilir).
     */
    fun kur(deger: (String) -> String?): String? {
        val binaKat = listOfNotNull(deger("building"), deger("floor"))
            .takeIf { it.isNotEmpty() }
            ?.joinToString(BINA_KAT_AYRACI)

        val segmentler = listOfNotNull(
            deger("country"),
            deger("city"),
            deger("address"),
            binaKat,
            deger("room"),
            deger("cage"),
            deger("footprint"),
            deger("rackspace"),
        )
        return segmentler.takeIf { it.isNotEmpty() }?.joinToString(AYRAC)
    }

    /**
     * Hücre/alan değerini sadeleştirir: envanterde boş yerine "-" kullanıldığı için o da boş sayılır,
     * satır içi fazla boşluklar tek boşluğa iner.
     *
     * Büyük/küçük harf dönüşümü **yapılmaz** — `"İzmir".lowercase()` birleşik noktalı bir karakter
     * üretip eşleşmeyi bozar (CLAUDE.md §5).
     */
    fun sadelestir(ham: String?): String? {
        val sade = ham?.trim()?.replace(Regex("\\s+"), " ") ?: return null
        return sade.takeIf { it.isNotEmpty() && it != "-" }
    }
}
