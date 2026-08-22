package tr.com.tti.hakedis.domain.document

/**
 * Bir işin ürettiği belge türleri. Sıra anlamlıdır: bir iş bu aşamaları atlayamaz
 * (bkz. [DocumentStageRule]).
 *
 * - [FIZIBILITE] — sahaya çıkmadan, farazi miktarlarla.
 * - [KESIF] — sahadaki keşif miktarları.
 * - [HAKEDIS] — fiilen uygulanan miktarlar.
 *
 * Üç belge de kendi sözleşmesini seçer ve o sözleşmenin **yayındaki referans** fiyat sürümüne
 * çivilenir. İşe özel pazarlık fiyat listesi Faz N'de kaldırıldı.
 */
enum class DocumentType {
    FIZIBILITE,
    KESIF,
    HAKEDIS,
    ;

    /** Bu türden önceki aşamalar (sıra ile). Bir önceki `ISLENIYOR`, ondan öncekiler `TAMAMLANDI` olmalı. */
    val oncekiAsamalar: List<DocumentType> get() = entries.take(ordinal)

    /**
     * Son aşama mı? *(Yeknesek durum makinesi, Ağustos 2026 — müdür isteği)*
     *
     * Hakediş son aşama olduğu için yönetici onayladığında bir sonraki aşamaya "İşleniyor" diye
     * geçmez; doğrudan **`TAMAMLANDI`** olur ve proje biter. Fizibilite ve keşif onaylanınca
     * `ISLENIYOR` olur (bir sonraki aşamanın kapısı açılır). Karar tek yerde:
     * [DocumentStageRule.onaySonrasiDurum].
     */
    val sonAsamaMi: Boolean get() = this == HAKEDIS
}

/**
 * Belgenin (aşamanın) yaşam döngüsü — **yeknesek durum makinesi** (Ağustos 2026, müdür isteği).
 *
 * ```
 * (açılınca) ──▶ ONAY_BEKLIYOR ──yönetici "İşleme Al"──▶ ISLENIYOR ──sonraki aşama açılınca──▶ TAMAMLANDI
 *                     │                                      │
 *                     │                       (hakediş: "İşleme Al" doğrudan ──▶ TAMAMLANDI)
 *                     └──yönetici iptal──▶ IPTAL
 * ```
 *
 * Üç aşama da (ön fizibilite, keşif, hakediş) **aynı** döngüyü yürütür; ayrı bir `TASLAK` yoktur:
 * açılan aşama doğrudan `ONAY_BEKLIYOR` doğar ve kullanıcı onay verilene kadar içeriğini düzeltebilir.
 * `ISLENIYOR` = yönetici bu aşamayı onayladı, bir sonraki aşamanın kapısı açık; aşama bir sonraki
 * aşama başlayınca `TAMAMLANDI` olur. ~~TASLAK~~ ve ~~İade~~ Ağustos 2026'da kaldırıldı: yönetici
 * yalnızca **beklet / işleme al / iptal** yapar (müdür kararı).
 *
 * Durum metni **sunucudaki** `BelgeMetinleri`'nde; tür ön eki yoktur (tür ayrı sütunda gösterilir).
 */
enum class DocumentStatus {
    /** Kullanıcı açtı/dolduruyor; yönetici onayında. İçerik **hâlâ düzenlenebilir**. */
    ONAY_BEKLIYOR,

    /** Yönetici onayladı (işleme aldı); bir sonraki aşama açılabilir. İçerik kilitli. */
    ISLENIYOR,

    /** Bir sonraki aşama başladı (ya da hakediş onaylandı); nihai. */
    TAMAMLANDI,

    IPTAL,
    ;

    /**
     * Miktar/dosya girilebilir mi? Yalnızca `ONAY_BEKLIYOR` iken: yönetici onayladıktan (`ISLENIYOR`)
     * sonra içerik dondurulur.
     */
    val duzenlenebilirMi: Boolean get() = this == ONAY_BEKLIYOR

    /** Aşama hâlâ "açık" mı — bitmemiş ve iptal olmamış (güncel aşama). */
    val acikMi: Boolean get() = this == ONAY_BEKLIYOR || this == ISLENIYOR
}

/**
 * Aynı belgeden üretilen iki çıktı.
 *
 * - [FIYATLI] — birim fiyat, tutar ve toplam dolu; iç kullanım ve bütçe içindir.
 * - [TEKLIF] — birim fiyat, tutar ve toplam **boş**; miktarlar görünür. Firmadan teklif istemek için
 *   kullanılır: fiyatı karşı taraf dolduracaktır.
 *
 * Bu bir belge türü değil, aynı belgenin ikinci bir görünümüdür — numarası ve içeriği aynıdır.
 */
enum class DocumentVariant {
    FIYATLI,
    TEKLIF,
    ;

    val fiyatGoster: Boolean get() = this == FIYATLI
}
