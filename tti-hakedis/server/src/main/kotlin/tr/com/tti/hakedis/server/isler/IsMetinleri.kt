package tr.com.tti.hakedis.server.isler

/**
 * İş durumunun kullanıcıya görünen Türkçe karşılığı.
 *
 * `BelgeMetinleri` ile aynı gerekçe: metin **sunucuda** durur, `:core:domain`'de değil (CLAUDE.md
 * §7). Sunucu `durumMetni`'ni yanıtta gönderir; web ve mobil kendi durum metnini yazmaz, böylece üç
 * arayüzde aynı ifade görünür.
 */
object IsMetinleri {
    fun durumMetni(durum: IsDurumu?): String = when (durum) {
        // Henüz hiç aşama açılmamış proje de "talep" sayılır ve onay bekler (müdür kararı).
        null, IsDurumu.ONAY_BEKLIYOR -> "Onay Bekliyor"
        IsDurumu.ISLENIYOR -> "İşleniyor"
        IsDurumu.TAMAMLANDI -> "Tamamlandı"
        IsDurumu.IPTAL -> "İptal Edildi"
    }
}
