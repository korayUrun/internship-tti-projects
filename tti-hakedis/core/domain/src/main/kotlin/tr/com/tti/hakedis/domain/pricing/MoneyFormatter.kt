package tr.com.tti.hakedis.domain.pricing

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Tüm parasal/sayısal gösterim tek yerden yapılır (CLAUDE.md §5).
 * Binlik `.`, ondalık `,`, para birimi `₺` → `1.234,56 ₺`.
 *
 * Ayırıcılar JDK'nın locale verisine bırakılmaz, açıkça set edilir; böylece çıktı
 * çalıştığı ortamdan bağımsız olarak aynıdır.
 *
 * ~~`moneyExact`~~ Faz P'de kaldırıldı (kullanıcı kararı, 3 Ağustos 2026). Birim fiyat artık
 * **belgeye çivilenirken** 2 haneye yuvarlanıyor (`BelgeSorgulari.satirYaz`), yani ekranda gösterilen
 * fiyat hesapta kullanılan fiyatla zaten aynı. İki farklı hassasiyette biçim tutmak, aynı sayının iki
 * görünümü olduğu izlenimini veriyordu. Sözleşmedeki ham fiyat `poz_fiyat`'ta dokunulmadan duruyor.
 */
object MoneyFormatter {
    private val turkish: Locale = Locale.forLanguageTag("tr-TR")

    private val symbols = DecimalFormatSymbols(turkish).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

    private val moneyFormat = ThreadLocal.withInitial { DecimalFormat("#,##0.00", symbols) }
    private val quantityFormat = ThreadLocal.withInitial { DecimalFormat("#,##0.###", symbols) }

    /** `1234.5` → `1.234,50 ₺` */
    fun money(value: BigDecimal): String = "${moneyValue(value)} ₺"

    /** Para değerinin sembolsüz hâli — Excel/CSV gibi sembol istemeyen yerler için. */
    fun moneyValue(value: BigDecimal): String = moneyFormat.get().format(value.setScale(2, RoundingMode.HALF_UP))

    /** `12.50` → `12,5` · `1234` → `1.234` */
    fun quantity(value: BigDecimal): String = quantityFormat.get().format(value)
}
