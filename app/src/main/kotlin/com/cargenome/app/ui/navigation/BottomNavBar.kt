package com.cargenome.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.cargenome.app.R
import kotlin.reflect.KClass

data class NavTabItem(
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: Any,
    val routeClass: KClass<*>,
)

@Composable
fun CarGenomeBottomBar(
    navController: NavHostController,
    vehicleId: Long,
    modifier: Modifier = Modifier,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val tabs = remember(vehicleId) {
        listOf(
            NavTabItem(
                labelRes = R.string.nav_car_info,
                selectedIcon = Icons.Filled.DirectionsCar,
                unselectedIcon = Icons.Outlined.DirectionsCar,
                route = VehicleDetailRoute(vehicleId),
                routeClass = VehicleDetailRoute::class,
            ),
            NavTabItem(
                labelRes = R.string.fuel_title,
                selectedIcon = Icons.Filled.LocalGasStation,
                unselectedIcon = Icons.Outlined.LocalGasStation,
                route = FuelLogRoute(vehicleId),
                routeClass = FuelLogRoute::class,
            ),
            NavTabItem(
                labelRes = R.string.nav_service,
                selectedIcon = Icons.Filled.Build,
                unselectedIcon = Icons.Outlined.Build,
                route = ServiceLogRoute(vehicleId),
                routeClass = ServiceLogRoute::class,
            ),
            NavTabItem(
                labelRes = R.string.analytics_title,
                selectedIcon = Icons.Filled.BarChart,
                unselectedIcon = Icons.Outlined.BarChart,
                route = AnalyticsRoute(vehicleId),
                routeClass = AnalyticsRoute::class,
            ),
            NavTabItem(
                labelRes = R.string.settings_title,
                selectedIcon = Icons.Filled.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                route = SettingsRoute(),
                routeClass = SettingsRoute::class,
            ),
        )
    }

    NavigationBar(modifier = modifier) {
        tabs.forEach { tab ->
            val selected = currentDestination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(tab.route) {
                            popUpTo(GarageRoute) {
                                saveState = true
                                inclusive = false
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = stringResource(tab.labelRes),
                    )
                },
                label = {
                    Text(
                        text = stringResource(tab.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}
