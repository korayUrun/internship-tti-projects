package tr.com.tti.hakedis.feature

/** Assets'ten okuma gibi kısa süren yüklemelerin durumu. İki ekranda kullanıldığı için ortak. */
enum class LoadState {
    Loading,
    Ready,
    Failed,
}
