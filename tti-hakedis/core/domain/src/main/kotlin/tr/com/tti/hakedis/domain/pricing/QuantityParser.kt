package tr.com.tti.hakedis.domain.pricing

import java.math.BigDecimal

/** Miktar girişinin sonucu. "Girilmemiş" ([Empty]) ile "0 girilmiş" ([Valid]) **ayrı** durumlardır. */
sealed interface QuantityInput {
    data object Empty : QuantityInput

    data class Valid(val value: BigDecimal) : QuantityInput

    data class Invalid(val error: QuantityError) : QuantityInput
}

enum class QuantityError {
    NOT_A_NUMBER,
    NEGATIVE,
}

/**
 * Kullanıcı hem `,` hem `.` yazabilir; ikisi de ondalık ayırıcı kabul edilir
 * (`"12,5"` ve `"12.5"` aynı sonucu verir). Her ikisi birden varsa **sondaki** ondalık,
 * baştaki binlik ayırıcı sayılır (`"1.234,5"` → `1234.5`).
 */
object QuantityParser {
    fun parse(raw: String): QuantityInput {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return QuantityInput.Empty

        val normalized = normalize(trimmed) ?: return QuantityInput.Invalid(QuantityError.NOT_A_NUMBER)

        val value = try {
            BigDecimal(normalized)
        } catch (e: NumberFormatException) {
            return QuantityInput.Invalid(QuantityError.NOT_A_NUMBER)
        }

        if (value.signum() < 0) return QuantityInput.Invalid(QuantityError.NEGATIVE)
        return QuantityInput.Valid(value)
    }

    private val GROUPED_INTEGER = Regex("""\d{1,3}([.,]\d{3})+""")

    /**
     * Ayırıcıları tek bir `.`'ya indirger; anlamlandırılamayan giriş için `null` döner.
     *
     * Kurallar:
     * - tek ayırıcı → ondalık (`"12,5"` = `"12.5"` = 12,5)
     * - birden fazla ayırıcı → sondaki ondalık, öncekiler **binlik** sayılır ve
     *   üçerli gruplama kuralına uymak zorundadır (`"1.234,5"` geçerli, `"1,2,3"` değil)
     */
    private fun normalize(text: String): String? {
        val decimalIndex = maxOf(text.lastIndexOf(','), text.lastIndexOf('.'))
        if (decimalIndex < 0) return text

        val fractionPart = text.substring(decimalIndex + 1)
        if (fractionPart.any { !it.isDigit() }) return null

        val integerPart = text.substring(0, decimalIndex)
        val sign = if (integerPart.startsWith('-')) "-" else ""
        val digits = integerPart.removePrefix("-").removePrefix("+")

        val normalizedInteger = when {
            digits.none { it == ',' || it == '.' } -> digits
            GROUPED_INTEGER.matches(digits) -> digits.replace(".", "").replace(",", "")
            else -> return null
        }

        return if (fractionPart.isEmpty()) "$sign$normalizedInteger" else "$sign$normalizedInteger.$fractionPart"
    }
}
