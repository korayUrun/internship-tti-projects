package tr.com.tti.hakedis.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe Navigation Compose rotaları (CLAUDE.md §6).
 *
 * Giriş ekranı burada yok: oturum olup olmadığı `HakedisNavHost` içinde tek bir dallanmayla
 * çözülür. Giriş bir rota olsaydı, oturum düşünce geri yığınında kalan ekranlara geri dönülebilirdi.
 *
 * ~~`WorkOrderDetailRoute`~~ Faz S'de kalktı: iş detayı ekranı silindi, iş listesi satırı aynı
 * bilgiyi taşıyor ve aşama düğmeleri doğrudan belgeye gidiyor.
 */
@Serializable
data object WorkOrderListRoute

/** Belge arşivi — alt sekme çubuğunun ikinci sekmesi (Düzeltme Turu 4). */
@Serializable
data object DocumentArchiveRoute

/** Hesap: kullanıcı bilgisi, sunucu adresi, çıkış. Üst şeritte sıkışık duruyorlardı. */
@Serializable
data object AccountRoute

@Serializable
data object NewWorkOrderRoute

/**
 * Belge ekranı. Kimlik ViewModel'e `SavedStateHandle["id"]` olarak ulaşır.
 *
 * [saltOkunur] `true` → "Gözlemle" ile açıldı: hiçbir giriş açık değil, yalnızca çıktı bölümü
 * kullanılabilir.
 */
@Serializable
data class DocumentRoute(val id: Long, val saltOkunur: Boolean = false)
