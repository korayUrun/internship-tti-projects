package tr.com.tti.hakedis.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import tr.com.tti.hakedis.R

/**
 * Alt sekme çubuğu (Düzeltme Turu 4).
 *
 * Üç üst düzey hedef var; **görev ekranlarında gizlenir** (`NewWorkOrderRoute`, `DocumentRoute`):
 * onların kendi geri yolu var ve çubuğun açık kalması yarım doldurulmuş bir belgeyi terk etmeye
 * davetiye olurdu.
 *
 * İkonlar elle yazılmış vektör: `material-icons-core` sınıf yolunda yok ve yeni bağımlılık
 * eklenmiyor (CLAUDE.md §13).
 */
private data class Sekme(val rota: Any, @param:StringRes val etiket: Int, @param:DrawableRes val simge: Int)

private val SEKMELER = listOf(
    Sekme(WorkOrderListRoute, R.string.tab_projects, R.drawable.ic_projects),
    Sekme(DocumentArchiveRoute, R.string.tab_documents, R.drawable.ic_documents),
    Sekme(AccountRoute, R.string.tab_account, R.drawable.ic_account),
)

/** Çubuk yalnızca üç üst düzey rotada görünür. */
fun NavDestination?.altCubukGosterilirMi(): Boolean = this != null &&
    (
        hasRoute(WorkOrderListRoute::class) ||
            hasRoute(DocumentArchiveRoute::class) ||
            hasRoute(AccountRoute::class)
        )

@Composable
fun HakedisBottomBar(navController: NavController, hedef: NavDestination?) {
    NavigationBar {
        SEKMELER.forEach { sekme ->
            val secili = hedef?.hierarchy()?.any { it.hasRouteOf(sekme.rota) } == true
            NavigationBarItem(
                selected = secili,
                onClick = {
                    /*
                     * ⚠️ `popUpTo` olmadan geri yığını sekmeler arasında gezindikçe sınırsız büyür ve
                     * bu, sistem "geri" tuşuna on kez basılana kadar hiç görünmez. `saveState` /
                     * `restoreState` ile her sekme kendi kaydırma konumunu koruyor.
                     */
                    navController.navigate(sekme.rota) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(painter = painterResource(sekme.simge), contentDescription = null) },
                label = { Text(text = stringResource(sekme.etiket)) },
            )
        }
    }
}

private fun NavDestination.hierarchy(): Sequence<NavDestination> = generateSequence(this) { it.parent }

private fun NavDestination.hasRouteOf(rota: Any): Boolean = when (rota) {
    WorkOrderListRoute -> hasRoute(WorkOrderListRoute::class)
    DocumentArchiveRoute -> hasRoute(DocumentArchiveRoute::class)
    AccountRoute -> hasRoute(AccountRoute::class)
    else -> false
}
