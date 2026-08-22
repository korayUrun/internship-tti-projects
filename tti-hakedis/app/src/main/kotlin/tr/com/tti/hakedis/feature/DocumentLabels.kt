package tr.com.tti.hakedis.feature

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tr.com.tti.hakedis.R
import tr.com.tti.hakedis.domain.document.DocumentType

/**
 * Belge türünün kullanıcıya görünen adı. Metin `strings.xml`'de; domain katmanı görünen metin
 * taşımaz (CLAUDE.md §7).
 *
 * ⚠️ Belge **durumu** burada yoktur: durum metni `(tür, durum)` fonksiyonudur ve **sunucudan**
 * gelir (`durumMetni`, NEWPHASES.md E2). Mobil durum metni yazmaz.
 */
@StringRes
fun DocumentType.labelRes(): Int = when (this) {
    DocumentType.FIZIBILITE -> R.string.document_type_fizibilite
    DocumentType.KESIF -> R.string.document_type_kesif
    DocumentType.HAKEDIS -> R.string.document_type_hakedis
}

@Composable
fun DocumentType.label(): String = stringResource(labelRes())
