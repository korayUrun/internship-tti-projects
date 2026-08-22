package tr.com.tti.hakedis.data

import android.content.Context
import tr.com.tti.hakedis.R

/**
 * Cihazda kalıcı olan tek şey: **son kullanılan kullanıcı adı**.
 *
 * **Parola saklanmaz** (karar J2). Oturum çerezi de diske yazılmaz — uygulama kapanınca oturum düşer
 * ve parola yeniden istenir. Tablet başkasının eline geçtiğinde bölgenin verisine doğrudan erişim
 * olmaması bu kararın karşılığıdır.
 *
 * **Sunucu adresi artık ayar değil** (kullanıcı isteği, 19 Ağustos 2026): derleme zamanında sabit ve
 * `res/values/config.xml`'den okunuyor. ~~Karar J1: giriş ekranından ayarlanır~~ geri alındı;
 * gerekçesi ve bedeli o dosyada yazılı.
 *
 * ⚠️ Eski sürümlerin yazdığı `sunucuAdresi` anahtarı `SharedPreferences`'ta kalmış olabilir ve
 * **bilerek okunmuyor**: okunsaydı güncellenen bir tablet, kayıtlı eski adrese (ör. geliştirme
 * makinesinin `10.0.2.2`'si) bağlanmaya devam eder ve yeni adresi hiç görmezdi — üstelik sessizce.
 * Değer kullanılmadığı için zararsız; ilk yeniden kurulumda kayboluyor.
 */
class Settings(context: Context) {

    private val store = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * `Context` **saklanmıyor**: değer kurulumda okunup tutuluyor (uygulama ömrü boyunca yaşayan bir
     * nesnede Activity context'i tutmak sızıntıdır).
     */
    private val address = context.getString(R.string.server_address)

    fun serverAddress(): String = address

    fun lastUserName(): String = store.getString(LAST_USER, null).orEmpty()

    fun writeLastUserName(userName: String) {
        store.edit().putString(LAST_USER, userName.trim()).apply()
    }

    private companion object {
        const val FILE = "hakedis-ayarlar"
        const val LAST_USER = "sonKullaniciAdi"
    }
}
