package tr.com.tti.hakedis.server.ek

import tr.com.tti.hakedis.domain.workorder.AttachmentKind

/**
 * Ek dosya türü — API ve veritabanı sözcüğü (`ek.tur` check kısıtı: `EK1_ROTA`, `EK2_GORSEL`).
 *
 * Domain'deki [AttachmentKind] ile **aynı kavramın** iki adı değildir: domain adı dosyanın ne olduğunu
 * söyler (`ROUTE_FILE`, `IMAGE`), buradaki ad kullanıcının onu hangi kutuda gördüğünü (`EK1`, `EK2`).
 * Doğrulama kuralı (izinli uzantılar) tek yerde, domain'de yaşar; burası yalnızca eşlemedir.
 */
enum class EkTuru(val kind: AttachmentKind, val etiket: String) {
    EK1_ROTA(AttachmentKind.ROUTE_FILE, "EK1"),
    EK2_GORSEL(AttachmentKind.IMAGE, "EK2"),
    ;

    val izinliUzantilar: Set<String> get() = kind.allowedExtensions
}
