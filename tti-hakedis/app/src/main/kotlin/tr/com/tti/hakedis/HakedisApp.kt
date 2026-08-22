package tr.com.tti.hakedis

import android.app.Application
import android.content.Intent
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class HakedisApp : Application() {

    lateinit var container: AppContainer
        private set

    /** Uygulama ömrü boyunca yaşayan iş parçacığı: eski ek dosyalarının temizliği. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)

        installCrashHandler()
        cleanupOldFiles()
    }

    /**
     * Yakalanmamış hata: teknik detay `Log.e` ile loglanır, kullanıcıya anlaşılır bir ekran gösterilir.
     */
    private fun installCrashHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Yakalanmamış hata (${thread.name})", throwable)
            runCatching {
                startActivity(
                    Intent(this, CrashActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                )
            }.onFailure {
                previousHandler?.uncaughtException(thread, throwable)
                return@setDefaultUncaughtExceptionHandler
            }
            Process.killProcess(Process.myPid())
            exitProcess(1)
        }
    }

    /**
     * Eski geçici dosyalar silinir. Uygulama ince istemci: ekler ve çıktılar sunucuda yaşıyor,
     * cihazdakiler yalnızca yükleme/indirme sırasında kullanılan kopyalar — korunması gereken veri yok.
     */
    private fun cleanupOldFiles() {
        appScope.launch {
            container.fileStorage.cleanupOldFiles()
        }
    }

    private companion object {
        const val TAG = "HakedisApp"
    }
}
