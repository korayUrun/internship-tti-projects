package tr.com.tti.hakedis.designsystem

import androidx.compose.ui.graphics.Color

val Primary = Color(0xFF0B5FFF)
val PrimaryDark = Color(0xFF0842B8)
val PrimarySoft = Color(0xFFE8F0FF)
val Surface = Color(0xFFFFFFFF)
val Background = Color(0xFFF5F8FC)
val Outline = Color(0xFFD6E0EE)
val OnSurface = Color(0xFF0F1B2D)
val OnSurfaceMut = Color(0xFF5B6B80)
val Success = Color(0xFF1B8A5A)
val ErrorRed = Color(0xFFC62828)
val DisabledBg = Color(0xFFEDF1F6)

// Şerit (banner) zeminleri — web'deki `stil.css` ile birebir aynı değerler: aynı uyarıyı iki arayüzde
// iki farklı renkte görmek, kullanıcıya iki farklı şey söylüyormuş izlenimi verir.
val ErrorSoft = Color(0xFFFDECEC)
val WarningBorder = Color(0xFFB47500)
val WarningSoft = Color(0xFFFFF7E6)
val SuccessSoft = Color(0xFFE8F6EF)

// Şerit metin renkleri. Webde `--uyari-metin` / `--basari-metin` olarak zaten vardı; burada eksikti
// ve durum aileleri (StatusTone) bunlara dayanıyor.
val WarningText = Color(0xFF7A4F00)
val SuccessText = Color(0xFF10603E)

// Koyu gezinme yüzeyi — web `--kenar-*` ile birebir aynı değerler (Düzeltme Turu 4).
//
// Web paletindeki `--kenar-cizgi` / `--kenar-vurgu` / `--kenar-etkin` / `--kenar-metin-soluk`
// karşılıkları burada BİLİNÇLİ olarak yok: üçü kenar çubuğuna özgü kavramlar (ayraç, üzerine
// gelme, etkin öğe hapı) ve dokunmatik bir alt sekme çubuğunda karşılıkları yok — M3
// `NavigationBar` kendi göstergesini çiziyor. Tüketicisi olmayan renk tanımlamıyoruz (§13).
val NavSurface = Color(0xFF0F1B2D) // OnSurface ile aynı: lacivert zaten metin laciverdi
val NavOnSurface = Color(0xFFE6EDF7)
