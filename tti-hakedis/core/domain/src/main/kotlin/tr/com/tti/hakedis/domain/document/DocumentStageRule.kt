package tr.com.tti.hakedis.domain.document

import tr.com.tti.hakedis.domain.workorder.WorkOrderStatus

/**
 * Yeni belge açılırken bakılan durum.
 *
 * @param isDurumu işin kendi durumu; `null` = fizibilite bekleniyor (henüz belge açılmamış).
 * @param mevcutBelgeler bu **iş** için hâlihazırda açılmış belgelerin türü → durumu.
 */
data class DocumentStageState(val isDurumu: WorkOrderStatus?, val mevcutBelgeler: Map<DocumentType, DocumentStatus>)

sealed interface StageDecision {
    data object Izinli : StageDecision

    data class Reddedildi(val gerekce: StageRejection) : StageDecision
}

/**
 * Ret gerekçeleri. Kullanıcıya gösterilecek metin burada **yoktur**; çeviri sunucu katmanında yapılır
 * (domain'de kullanıcıya görünen metin bulunmaz — CLAUDE.md §7).
 */
enum class StageRejection {
    /** Bu iş için bu türde belge zaten var. */
    ZATEN_VAR,

    /** Önceki aşama hiç açılmamış (ör. fizibilite olmadan keşif). */
    ONCEKI_ASAMA_YOK,

    /** Bir önceki aşama henüz yönetici tarafından işleme alınmadı (`ISLENIYOR` değil). */
    ONCEKI_ASAMA_ISLEME_ALINMAMIS,

    /** Daha önceki bir aşama tamamlanmamış (beklenmedik ara durum). */
    ONCEKI_ASAMA_TAMAMLANMAMIS,

    /** Proje iptal edilmiş; yeni belge açılamaz. */
    IS_KAPANMIS,
}

/**
 * Aşama kuralı — **yeknesek durum makinesi** (Ağustos 2026, müdür isteği):
 *
 * `FIZIBILITE → KESIF → HAKEDIS` atlanamaz. Bir aşamanın açılabilmesi için **bir önceki aşamanın
 * yönetici tarafından işleme alınmış** (`ISLENIYOR`) olması gerekir; ondan öncekiler `TAMAMLANDI`
 * olmalıdır. Fizibilite ilk aşamadır, önkoşulu yoktur (proje iptal değilse açılır). Bir aşama
 * açıldığı an, bir önceki aşama `ISLENIYOR`'dan `TAMAMLANDI`'ya çekilir (çağıran serviste, tek
 * transaction).
 *
 * `IPTAL` edilmiş belge hiç yok sayılır: ne "zaten var" sayılır, ne de bir aşamayı kapatır.
 *
 * ~~İki kapı (aşama sırası + proje ISLENIYOR)~~ birleşti: sıra bilgisini artık aşamaların kendi
 * durumu taşıyor. Proje durumu türetilmiştir (bkz. [WorkOrderStatus]).
 */
object DocumentStageRule {
    fun acilabilirMi(tur: DocumentType, durum: DocumentStageState): StageDecision {
        val mevcut = durum.mevcutBelgeler.filterValues { it != DocumentStatus.IPTAL }

        mevcut[tur]?.let { return StageDecision.Reddedildi(StageRejection.ZATEN_VAR) }

        if (durum.isDurumu == WorkOrderStatus.IPTAL) {
            return StageDecision.Reddedildi(StageRejection.IS_KAPANMIS)
        }

        val oncekiler = tur.oncekiAsamalar
        oncekiler.forEachIndexed { i, onceki ->
            val oncekiDurum = mevcut[onceki] ?: return StageDecision.Reddedildi(StageRejection.ONCEKI_ASAMA_YOK)
            val hemenOncekiMi = i == oncekiler.lastIndex
            if (hemenOncekiMi) {
                // Bir önceki aşama yönetici tarafından işleme alınmış olmalı.
                if (oncekiDurum != DocumentStatus.ISLENIYOR) {
                    return StageDecision.Reddedildi(StageRejection.ONCEKI_ASAMA_ISLEME_ALINMAMIS)
                }
            } else if (oncekiDurum != DocumentStatus.TAMAMLANDI) {
                return StageDecision.Reddedildi(StageRejection.ONCEKI_ASAMA_TAMAMLANMAMIS)
            }
        }

        return StageDecision.Izinli
    }

    /**
     * Belgeye miktar/dosya girilebilir mi? Yalnızca aşama `ONAY_BEKLIYOR` iken: yönetici onayladıktan
     * (`ISLENIYOR`) sonra içerik dondurulur. Fizibiliteye ayrı bir kural gerekmez — o da onay
     * verildiğinde kilitlenir.
     */
    fun duzenlenebilirMi(tur: DocumentType, belgeDurumu: DocumentStatus): Boolean = belgeDurumu.duzenlenebilirMi

    /**
     * Yönetici bir aşamayı "işleme aldığında" (onayladığında) aşamanın geçtiği durum — tek karar
     * noktası. Hakediş son aşama olduğu için doğrudan `TAMAMLANDI`; diğerleri `ISLENIYOR` (bir sonraki
     * aşamanın kapısı açılır).
     */
    fun onaySonrasiDurum(tur: DocumentType): DocumentStatus = if (tur.sonAsamaMi) DocumentStatus.TAMAMLANDI else DocumentStatus.ISLENIYOR
}
