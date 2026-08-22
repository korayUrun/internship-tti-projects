package tr.com.tti.hakedis.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import tr.com.tti.hakedis.AppContainer
import tr.com.tti.hakedis.feature.account.AccountScreen
import tr.com.tti.hakedis.feature.account.AccountViewModel
import tr.com.tti.hakedis.feature.archive.DocumentArchiveScreen
import tr.com.tti.hakedis.feature.archive.DocumentArchiveViewModel
import tr.com.tti.hakedis.feature.document.DocumentScreen
import tr.com.tti.hakedis.feature.document.DocumentViewModel
import tr.com.tti.hakedis.feature.login.LoginScreen
import tr.com.tti.hakedis.feature.login.LoginViewModel
import tr.com.tti.hakedis.feature.workorder.NewWorkOrderScreen
import tr.com.tti.hakedis.feature.workorder.NewWorkOrderViewModel
import tr.com.tti.hakedis.feature.workorder.WorkOrderListScreen
import tr.com.tti.hakedis.feature.workorder.WorkOrderListViewModel

/**
 * Uygulama gezintisi.
 *
 * Oturum yoksa **hiçbir rota çizilmez**: giriş ekranı `NavHost`'un dışında, tek bir dallanmayla
 * gösterilir. Böylece oturum düştüğünde (401) geri yığınında kalmış bir ekrana dönmek mümkün olmaz —
 * `Session` kullanıcıyı `null` yaptığı anda ekran giriş ekranına iner. Alt sekme çubuğu ve
 * `navController` de bu dalın **içinde**, dolayısıyla oturumla birlikte topluca atılır.
 */
@Composable
fun HakedisNavHost(container: AppContainer) {
    val user by container.session.user.collectAsStateWithLifecycle()

    if (user == null) {
        val viewModel: LoginViewModel = viewModel(factory = container.viewModelFactory)
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        LoginScreen(uiState = uiState, onEvent = viewModel::onEvent)
        return
    }

    val navController = rememberNavController()
    val geriYigini by navController.currentBackStackEntryAsState()

    Scaffold(
        bottomBar = {
            if (geriYigini?.destination.altCubukGosterilirMi()) {
                HakedisBottomBar(navController = navController, hedef = geriYigini?.destination)
            }
        },
    ) { padding ->
        /*
         * ⚠️ Dış `Scaffold` YALNIZCA `bottomBar` veriyor. Ekranların çoğu kendi `Scaffold`'unu (ve
         * `TopAppBar`'ını) taşıyor; dışa bir `topBar` eklemek iç `Scaffold`'un üst boşluğunu ikiye
         * katlardı. Dış boşluk `NavHost`'un modifier'ına uygulanıyor.
         */
        NavHost(
            navController = navController,
            startDestination = WorkOrderListRoute,
            modifier = Modifier.padding(padding),
        ) {
            hedefler(container, navController)
        }
    }
}

private fun NavGraphBuilder.hedefler(container: AppContainer, navController: NavHostController) {
    composable<WorkOrderListRoute> {
        val viewModel: WorkOrderListViewModel = viewModel(factory = container.viewModelFactory)
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        WorkOrderListScreen(
            uiState = uiState,
            onEvent = viewModel::onEvent,
            onOpenDocument = { id, salt -> navController.navigate(DocumentRoute(id, salt)) },
            onNew = { navController.navigate(NewWorkOrderRoute) },
        )
    }

    composable<DocumentArchiveRoute> {
        val viewModel: DocumentArchiveViewModel = viewModel(factory = container.viewModelFactory)
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        DocumentArchiveScreen(
            uiState = uiState,
            onEvent = viewModel::onEvent,
            // Arşivden açılan belge **salt okunur**: burası düzenleme değil, okuma ve indirme yeri.
            onOpenDocument = { id -> navController.navigate(DocumentRoute(id, saltOkunur = true)) },
        )
    }

    composable<AccountRoute> {
        val viewModel: AccountViewModel = viewModel(factory = container.viewModelFactory)
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        AccountScreen(uiState = uiState, onEvent = viewModel::onEvent)
    }

    composable<NewWorkOrderRoute> {
        val viewModel: NewWorkOrderViewModel = viewModel(factory = container.viewModelFactory)
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        // Proje oluşunca ekran "Bu proje açıldı" penceresini gösterir; listeye dönüş o pencerenin
        // düğmesiyle olur. Ekran geri yığınından düşer: oluşturulmuş bir projenin formuna "geri" ile
        // dönüp aynı formu ikinci kez göndermenin anlamı yok.
        NewWorkOrderScreen(
            uiState = uiState,
            onEvent = viewModel::onEvent,
            onCancel = { navController.popBackStack() },
            onOpenList = { navController.popBackStack() },
        )
    }

    composable<DocumentRoute> { entry ->
        val viewModel: DocumentViewModel = viewModel(factory = container.viewModelFactory)
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val cameraUri by viewModel.cameraUri.collectAsStateWithLifecycle()

        DocumentScreen(
            durum = uiState,
            readOnly = entry.toRoute<DocumentRoute>().saltOkunur,
            cameraUri = cameraUri,
            onEvent = viewModel::onEvent,
            onBack = { navController.popBackStack() },
            // Gönderim bitti: iş listesine dönülür ki işin yeni durumu ve sıradaki aşama görünsün.
            onFinished = { navController.popBackStack(WorkOrderListRoute, inclusive = false) },
        )
    }
}
