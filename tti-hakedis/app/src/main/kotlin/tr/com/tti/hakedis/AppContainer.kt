package tr.com.tti.hakedis

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import tr.com.tti.hakedis.data.FileStorage
import tr.com.tti.hakedis.data.Session
import tr.com.tti.hakedis.data.Settings
import tr.com.tti.hakedis.data.api.HakedisApi
import tr.com.tti.hakedis.feature.account.AccountViewModel
import tr.com.tti.hakedis.feature.archive.DocumentArchiveViewModel
import tr.com.tti.hakedis.feature.document.DocumentViewModel
import tr.com.tti.hakedis.feature.login.LoginViewModel
import tr.com.tti.hakedis.feature.workorder.NewWorkOrderViewModel
import tr.com.tti.hakedis.feature.workorder.WorkOrderListViewModel

/**
 * Manuel DI: uygulama ömrü boyunca yaşayan bağımlılıkların tek kurulum yeri (CLAUDE.md §6).
 * Hilt kullanılmıyor — bu ölçekte annotation processing yükü gereksiz.
 *
 * Faz J'den itibaren uygulama ince istemci: veri kaynağı [api], oturum kaynağı [session].
 * Cihazda sözleşme deposu ya da taslak deposu yok.
 */
class AppContainer(applicationContext: Context) {

    val settings: Settings = Settings(applicationContext)

    val api: HakedisApi = HakedisApi(settings)

    val session: Session = Session(api, settings)

    val fileStorage: FileStorage = FileStorage(applicationContext)

    /**
     * ViewModel'ler için tek fabrika.
     *
     * Belge ekranı kimliği `SavedStateHandle` üzerinden alır: değer `HakedisNavHost`'taki type-safe
     * rotadan gelir, ayrıca bir "seçili kayıt" durumu taşınmaz.
     */
    val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { LoginViewModel(session, settings) }
        initializer { WorkOrderListViewModel(api, session, fileStorage) }
        initializer { NewWorkOrderViewModel(api, session) }
        initializer { DocumentViewModel(api, session, fileStorage, createSavedStateHandle()) }
        initializer { DocumentArchiveViewModel(api, session) }
        initializer { AccountViewModel(session, settings) }
    }
}
