package tr.com.tti.hakedis.domain.document

import tr.com.tti.hakedis.domain.contract.Contract
import tr.com.tti.hakedis.domain.contract.PozItem
import tr.com.tti.hakedis.domain.pricing.PricedRow
import tr.com.tti.hakedis.domain.pricing.PricingCalculator
import tr.com.tti.hakedis.domain.workorder.Attachment
import tr.com.tti.hakedis.domain.workorder.WorkOrder
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Saf fonksiyon: kayıt durumundan çıktı modelini üretir.
 *
 * Kurallar:
 * - sadece **miktarı girilmiş** satırlar listelenir,
 * - grup başlığı ancak altında dolu satır varsa korunur,
 * - tutarlar `PricingCalculator` ile hesaplanır (ekranla birebir aynı sayı),
 * - `TEKLIF` varyantında para alanları `null` bırakılır.
 */
object DocumentModelBuilder {
    fun build(
        documentNo: String,
        type: DocumentType,
        variant: DocumentVariant,
        contract: Contract,
        workOrder: WorkOrder,
        quantities: Map<String, BigDecimal>,
        createdAt: LocalDateTime,
        /**
         * Aynı işin **keşif** satırları (poz kimliği → keşif fiyatı + miktarı) — yalnızca hakediş
         * çıktısında kullanılır. Varsayılanı boş: mobil önizleme ve fizibilite/keşif çıktıları bu
         * parametreyi hiç vermez.
         */
        kesifRows: Map<String, PricedRow> = emptyMap(),
        /** Keşif belgesinin kendi toplamı; listelenen satırlardan türetilmez. */
        kesifTotal: BigDecimal? = null,
        routeFile: Attachment? = null,
        images: List<Attachment> = emptyList(),
        /**
         * EK3 "TTI Kullanılan Malzemeler" — yalnızca hakediş çıktısında dolu. Varsayılanı boş:
         * mobil önizleme ve diğer iki aşama bu parametreyi hiç vermez.
         */
        materials: List<DocumentMaterial> = emptyList(),
    ): DocumentModel = DocumentModel(
        documentNo = documentNo,
        type = type,
        variant = variant,
        title = contract.meta.title,
        header = DocumentHeader(
            region = workOrder.region,
            scope = workOrder.scope,
            contractName = contract.meta.name,
            spNo = workOrder.spNo.trim(),
            route = workOrder.route.trim(),
            ekAraligi = workOrder.ekAraligi.trim(),
            // Üçü de aynı kalıptan geçiyor: boş metin `null`'a iner, böylece çıktı "boş satır" ile
            // "satır yok" arasında ayrım yapabiliyor (Faz W).
            kableId = workOrder.kableId.bosDegilse(),
            adresA = workOrder.adresA.bosDegilse(),
            adresB = workOrder.adresB.bosDegilse(),
        ),
        sections = buildSections(contract.items, quantities, kesifRows, variant),
        total = PricingCalculator.total(contract.items, quantities).takeIf { variant.fiyatGoster },
        kesifTotal = kesifTotal?.takeIf { variant.fiyatGoster },
        attachments = DocumentAttachments(
            routeFileName = routeFile?.fileName,
            imagePaths = images.map { it.storedPath },
            // Tamamen boş satır modele girmez: çıktıda anlamsız boş satır üretirdi.
            materials = materials.filterNot { it.bosMu },
        ),
        createdAt = createdAt,
    )

    private fun buildSections(
        items: List<PozItem>,
        quantities: Map<String, BigDecimal>,
        kesifRows: Map<String, PricedRow>,
        variant: DocumentVariant,
    ): List<DocumentSection> {
        val sections = mutableListOf<DocumentSection>()
        var currentGroup: PozItem? = null
        var currentRows = mutableListOf<DocumentRow>()

        fun flush() {
            if (currentRows.isNotEmpty()) {
                sections += DocumentSection(
                    groupPozNo = currentGroup?.pozNo.orEmpty(),
                    groupName = currentGroup?.name.orEmpty(),
                    rows = currentRows.toList(),
                )
            }
            currentRows = mutableListOf()
        }

        for (item in items) {
            if (item.isGroup) {
                flush()
                currentGroup = item
                continue
            }
            // Satır listeye yalnızca BU belgenin miktarı varsa girer. Keşifte olup hakedişte
            // olmayan kalem (yapılmamış iş) listelenmez; sapma sütunu geldiğinde (Faz K) bu kural
            // yeniden ele alınacak.
            val miktar = quantities[item.id] ?: continue
            currentRows += item.toDocumentRow(miktar, kesifRows[item.id], variant)
        }
        flush()

        return sections
    }

    private fun PozItem.toDocumentRow(quantity: BigDecimal, kesif: PricedRow?, variant: DocumentVariant): DocumentRow = DocumentRow(
        pozNo = pozNo,
        name = name,
        unit = unit,
        quantity = quantity,
        unitPrice = unitPrice.takeIf { variant.fiyatGoster },
        amount = PricingCalculator.rowAmount(unitPrice, quantity).takeIf { variant.fiyatGoster },
        // Keşif miktarı para değil: `TEKLIF` varyantında da görünür. Tutar ise para, gizlenir.
        kesifQuantity = kesif?.quantity,
        kesifAmount = kesif?.let { PricingCalculator.rowAmount(it.unitPrice, it.quantity) }?.takeIf { variant.fiyatGoster },
    )

    /** Boş ya da yalnızca boşluktan ibaret metin `null`'a iner — "değer yok" ile "boş değer" aynı şey. */
    private fun String?.bosDegilse(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
