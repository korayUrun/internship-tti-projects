package tr.com.tti.hakedis.domain.document

import java.util.Locale

/**
 * Belge numarası ve çıktı dosyası adı.
 *
 * - Belge numarası: `IST-AVR_2026_KESIF_003` — bölgenin dosya kodu, yıl, belge türü ve o
 *   (bölge, yıl, tür) üçlüsündeki sıra numarası. Numarayı sunucu üretir, sayaç veritabanındadır.
 * - Dosya adı: `TTI_IST-AVR_2026_KESIF_003_FIYATLI` + uzantı (çağıran taraf ekler).
 *
 * Adda **zaman damgası yoktur**: aynı belgenin ikinci kez üretilmesi yeni bir dosya değil, aynı
 * dosyanın güncel hâlidir. Bu sayede arşiv klasörü çoğalmaz, üzerine yazılır.
 *
 * Türkçe karakterler ASCII'ye çevrilir ve dönüşümler **daima `Locale.ROOT`** ile yapılır:
 * `"i".uppercase()` Türkçe locale'de `"İ"` üretip dosya adını bozardı (CLAUDE.md §5).
 */
object DocumentFileNamer {
    private val TURKISH_TO_ASCII = mapOf(
        'ç' to 'c', 'Ç' to 'C',
        'ğ' to 'g', 'Ğ' to 'G',
        'ı' to 'i', 'İ' to 'I',
        'ö' to 'o', 'Ö' to 'O',
        'ş' to 's', 'Ş' to 'S',
        'ü' to 'u', 'Ü' to 'U',
        // Düzeltme işaretli harfler ("güzergâh", "kâğıt") de sık geçiyor.
        'â' to 'a', 'Â' to 'A',
        'î' to 'i', 'Î' to 'I',
        'û' to 'u', 'Û' to 'U',
    )

    /**
     * `IST-AVR_2026_KESIF_003`.
     *
     * Sıra numarası üç haneye sıfırla tamamlanır; yılda 999'u aşan bir bölge çıkarsa numara doğal
     * olarak dört haneye taşar ve sıralama bozulmaz.
     */
    fun documentNo(bolgeDosyaKodu: String, yil: Int, tur: DocumentType, siraNo: Int): String {
        require(siraNo > 0) { "Sıra numarası pozitif olmalı: $siraNo" }
        return "${sanitize(bolgeDosyaKodu)}_${yil}_${tur.name}_${"%03d".format(Locale.ROOT, siraNo)}"
    }

    /** `TTI_IST-AVR_2026_KESIF_003_FIYATLI` — uzantı çağıran tarafta eklenir. */
    fun baseName(documentNo: String, variant: DocumentVariant): String = "TTI_${sanitize(documentNo)}_${variant.name}"

    /** Türkçe karakter → ASCII, boşluk → `_`, kalan özel karakterler atılır. */
    fun sanitize(text: String): String {
        val ascii = buildString {
            for (char in text.trim()) {
                append(TURKISH_TO_ASCII[char] ?: char)
            }
        }
        return ascii
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^A-Za-z0-9._-]"), "")
            .replace(Regex("_+"), "_")
            .trim('_')
    }
}
