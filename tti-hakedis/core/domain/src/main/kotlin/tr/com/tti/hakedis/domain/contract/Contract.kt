package tr.com.tti.hakedis.domain.contract

import kotlinx.serialization.Serializable
import tr.com.tti.hakedis.domain.serialization.BigDecimalSerializer
import java.math.BigDecimal

/**
 * Bir sözleşmenin kimlik bilgileri.
 *
 * @param id assets içindeki dosya adı ile aynı olan teknik kimlik (ör. `2026-guvenay-rota-1`)
 * @param name kullanıcıya listede gösterilen kısa ad (ör. `2026 Güvenay Rota-1`)
 * @param title PDF/önizleme başlığı — kaynak Excel'in 1. satırındaki metin
 * @param sheetName kaynak Excel sheet adı (izlenebilirlik için)
 */
@Serializable
data class ContractMeta(val id: String, val name: String, val title: String, val sheetName: String)

/**
 * Sözleşmedeki tek bir satır.
 *
 * @param id satır sırasından türetilen stabil kimlik — `pozNo` tekrar edebildiği için kimlik olarak kullanılamaz
 * @param pozNo kaynaktaki poz numarası; **string kalır**, sayıya çevrilmez (`1,5`, `4.4.`, `4.10`)
 * @param sortKey sıralama anahtarı (`"4.10"` → `[4, 10]`) — düz string sıralaması yanlış sonuç verir
 * @param unitPrice ham hassasiyetiyle birim fiyat; yuvarlama sadece satır tutarında yapılır
 * @param isGroup fiyatsız/birimsiz başlık satırı — input almaz, toplama katılmaz
 * @param isPriced bu sözleşmede fiyatlandırılmış mı (fiyat > 0)
 */
@Serializable
data class PozItem(
    val id: String,
    val pozNo: String,
    val sortKey: List<Int>,
    val name: String,
    val unit: String,
    @Serializable(with = BigDecimalSerializer::class)
    val unitPrice: BigDecimal,
    val isGroup: Boolean,
    val isPriced: Boolean,
)

@Serializable
data class Contract(val meta: ContractMeta, val items: List<PozItem>)
