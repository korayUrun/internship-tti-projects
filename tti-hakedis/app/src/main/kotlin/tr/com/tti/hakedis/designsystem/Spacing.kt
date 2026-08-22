package tr.com.tti.hakedis.designsystem

import androidx.compose.ui.unit.dp

/** Tek yerden yönetilen boşluk ve dokunma hedefi ölçüleri (bkz. CLAUDE.md §8). */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp

    /** Minimum dokunma alanı — eldivenli parmak. */
    val minTouchTarget = 48.dp

    /** Metin girişi minimum yüksekliği. */
    val fieldHeight = 56.dp

    /**
     * Form içeriğinin azami genişliği. Tablet yatayda içerik ~1000dp+'a yayılıp okunmaz olmasın diye
     * `FormContainer` içeriği bu genişlikte ortalar. İki sütunda ~440dp/sütun kalır (bkz. CLAUDE.md §8).
     */
    val formMaxWidth = 920.dp
}
