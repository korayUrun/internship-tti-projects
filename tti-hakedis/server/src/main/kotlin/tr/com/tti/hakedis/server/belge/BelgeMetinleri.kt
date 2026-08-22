package tr.com.tti.hakedis.server.belge

import tr.com.tti.hakedis.domain.document.DocumentStatus
import tr.com.tti.hakedis.domain.document.DocumentType

/**
 * Belge türü ve durumunun kullanıcıya görünen Türkçe karşılıkları.
 *
 * Metinler **sunucuda** durur, `:core:domain`'de değil: domain'de kullanıcıya görünen metin bulunmaz
 * (CLAUDE.md §7, `DocumentStageRule` de aynı gerekçeyle etiket taşımıyor — `Region`/`Scope` gibi
 * sabitler de öyle). Buna karşılık metin **tek yerde** kalıyor: sunucu `durumMetni`'ni yanıtta
 * gönderir, web ve mobil kendi içinde durum metni yazmaz. Böylece üç arayüzde aynı ifade görünür.
 *
 * Durum metni `(tür, durum)` çiftine bağlıdır: aynı `TAMAMLANDI` durumu keşifte "1. Keşif Tamamlandı",
 * hakedişte "Hakediş İşlemi Gönderildi" diye okunur. Bu yüzden `DocumentStatus`'a ayrı bir
 * `GONDERILDI` değeri eklenmedi — davranış aynı, yalnızca ifade farklı.
 */
object BelgeMetinleri {
    /**
     * Durum metni **tür ön eki olmadan** (müdür isteği, madde 14): tür zaten ayrı sütunda gösteriliyor,
     * "1. Keşif Onay Bekliyor" gibi tekrar gereksiz. Üç aşama da aynı dört metni kullanır ve bu metinler
     * Projeler ekranındaki [IsMetinleri] ile birebir aynıdır.
     */
    fun durumMetni(durum: DocumentStatus): String = when (durum) {
        DocumentStatus.ONAY_BEKLIYOR -> "Onay Bekliyor"
        DocumentStatus.ISLENIYOR -> "İşleniyor"
        DocumentStatus.TAMAMLANDI -> "Tamamlandı"
        DocumentStatus.IPTAL -> "İptal Edildi"
    }

    fun turAdi(tur: DocumentType) = when (tur) {
        DocumentType.FIZIBILITE -> "ön fizibilite"
        DocumentType.KESIF -> "keşif"
        DocumentType.HAKEDIS -> "hakediş"
    }

    /** Hata mesajlarında kullanılan kısa durum sıfatı: "… belgesi tamamlanmış; artık değiştirilemez." */
    fun durumSifati(durum: DocumentStatus) = when (durum) {
        DocumentStatus.ONAY_BEKLIYOR -> "onay bekleyen"
        DocumentStatus.ISLENIYOR -> "işleme alınmış"
        DocumentStatus.TAMAMLANDI -> "tamamlanmış"
        DocumentStatus.IPTAL -> "iptal edilmiş"
    }
}
