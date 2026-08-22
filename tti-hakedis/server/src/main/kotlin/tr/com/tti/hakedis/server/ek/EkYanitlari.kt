package tr.com.tti.hakedis.server.ek

import java.time.Instant

/**
 * Ek listesi satırı. [depoYolu] **dışarı verilmez**: istemcinin işine yaramaz ve sunucunun dosya
 * sistemi düzenini sızdırır; indirme kimlik üzerinden yapılır (`GET /api/ekler/{id}`).
 */
data class EkYaniti(
    val id: Long,
    val belgeId: Long,
    val tur: EkTuru,
    /** Kullanıcının yüklediği özgün ad — listede bu gösterilir. */
    val dosyaAdi: String,
    val boyutBayt: Long,
    val yukleme: Instant,
    val yukleyenAdSoyad: String?,
)

fun EkKaydi.yanit() = EkYaniti(
    id = id,
    belgeId = belgeId,
    tur = tur,
    dosyaAdi = dosyaAdi,
    boyutBayt = boyutBayt,
    yukleme = yukleme,
    yukleyenAdSoyad = yukleyenAdSoyad,
)
