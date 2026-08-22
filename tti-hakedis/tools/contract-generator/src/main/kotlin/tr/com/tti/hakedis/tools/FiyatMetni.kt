package tr.com.tti.hakedis.tools

import java.math.BigDecimal

/**
 * Metin hâlindeki fiyatı `BigDecimal`'e çevirir; çözülemezse `null` döner (çağıran taraf satır
 * numarasıyla hata üretir).
 *
 * **Neden ayrı bir fonksiyon:** eski hâli tüm noktaları binlik ayırıcı sayıp siliyordu —
 * `"1234.56"` → `123456`, fiyat 100 kat büyüyor ve **hiçbir hata çıkmıyordu**. Tek bir bilinen dosyaya
 * göre yazılmış olması bir CLI'da fark edilmez; yöneticinin keyfi Excel yükleyebildiği bir uçta doğrudan
 * para hatasıdır. Kural artık burada, testlerle kilitli.
 *
 * **Ayrım kuralı** (binlik grubu daima 3 hanedir, tek ayırt edici işaret budur):
 * 1. Hem `.` hem `,` varsa **sonda gelen ondalık** ayırıcıdır, diğeri binlik (`1.234,56`, `1,234.56`).
 * 2. Yalnızca bir tür varsa ve **birden fazla** geçiyorsa binliktir (`1.234.567`).
 * 3. Tek geçiyorsa: sağında **tam 3 hane** varsa belirsizdir → Türkçe geleneğe göre çözülür
 *    (`.` binlik, `,` ondalık). Sağında 1, 2 veya 4+ hane varsa **ondalık** ayırıcıdır.
 *
 * (3)'ün son kısmı kritik: kaynak fiyatlar 14 ondalık taşıyor (`648.35852723050107`). "3'ten fazla hane
 * → binlik" varsayan bir kural bu değeri `64835852723050107` yapardı.
 *
 * Binlik olduğuna karar verilen ayırıcının **grupları da doğrulanır**: `12,34,56.78` gibi bozuk
 * gruplama sessizce kabul edilmez, `null` döner. Gevşek davranmak, düzeltilen 100× hatasının aynı
 * sınıfından yeni bir hataya kapı açardı.
 */
fun fiyatMetniniCoz(ham: String): BigDecimal? {
    // Kırılmayan boşluk (U+00A0) Excel'den kopyalanan hücrelerde sık görülür.
    val temiz = ham.trim().replace(" ", "").replace(" ", "").replace("₺", "").trim()
    if (temiz.isEmpty()) return BigDecimal.ZERO

    val noktaVar = '.' in temiz
    val virgulVar = ',' in temiz

    val normal = when {
        noktaVar && virgulVar -> {
            val ondalik = if (temiz.lastIndexOf(',') > temiz.lastIndexOf('.')) ',' else '.'
            val binlik = if (ondalik == ',') '.' else ','
            binliksiz(temiz, binlik, ondalik) ?: return null
        }

        virgulVar ->
            if (binlikMi(temiz, ',')) binliksiz(temiz, ',', null) ?: return null else temiz.replace(',', '.')

        noktaVar ->
            if (binlikMi(temiz, '.')) binliksiz(temiz, '.', null) ?: return null else temiz

        else -> temiz
    }

    return normal.toBigDecimalOrNull()
}

/**
 * Binlik ayırıcıları siler; gruplama geçersizse `null` döner.
 * Tam sayı kısmı `\d{1,3}(<binlik>\d{3})+` kalıbına uymak zorunda.
 */
private fun binliksiz(metin: String, binlik: Char, ondalik: Char?): String? {
    val ondalikIndeks = ondalik?.let { metin.lastIndexOf(it) } ?: -1
    val tamKisim = if (ondalikIndeks >= 0) metin.substring(0, ondalikIndeks) else metin
    val kesirKisim = if (ondalikIndeks >= 0) metin.substring(ondalikIndeks + 1) else ""

    val kalip = Regex("^-?\\d{1,3}(\\Q$binlik\\E\\d{3})+$")
    if (!kalip.matches(tamKisim)) return null

    val duzTam = tamKisim.replace(binlik.toString(), "")
    return if (kesirKisim.isEmpty()) duzTam else "$duzTam.$kesirKisim"
}

/**
 * Tek türde ayırıcı taşıyan metinde bu ayırıcı binlik mi?
 *
 * Birden fazla geçiyorsa binliktir. Tek geçiyorsa yalnızca **tam 3 hane** kaldığında belirsizdir; o
 * belirsizlik Türkçe geleneğe göre çözülür: `.` binlik, `,` ondalık.
 */
private fun binlikMi(metin: String, ayirici: Char): Boolean {
    val adet = metin.count { it == ayirici }
    if (adet == 0) return false
    if (adet > 1) return true

    val sagdakiHane = metin.length - metin.indexOf(ayirici) - 1
    return sagdakiHane == 3 && ayirici == '.'
}
