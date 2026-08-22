package tr.com.tti.hakedis.data.api

import android.util.Log

/**
 * Başarısız bir isteğin kullanıcıya gösterilecek mesajı — ve **tek yerde loglanması**.
 *
 * Sunucudan gelen mesajlar Türkçe ve eyleme dönüktür, olduğu gibi gösterilir. Ama her istisnanın
 * mesajı olmak zorunda değil: mesajsız bir hata ekranda genel metne düşer ve o noktadan sonra neyin
 * bozulduğunu **hiçbir yerden** öğrenmenin yolu kalmaz. Bu yüzden istisnanın sınıfı ve yığın izi
 * her durumda `Log.e` ile kaydedilir (CLAUDE.md: fail loud).
 *
 * Dönen değer boş olabilir; `ErrorBanner` boş mesajı genel metne çevirir.
 */
fun Throwable.userMessage(tag: String): String {
    Log.e(tag, "İstek başarısız: ${javaClass.name}", this)
    return message.orEmpty()
}
