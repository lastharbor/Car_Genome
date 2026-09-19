package com.cargenome.app.ui.navigation

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.cargenome.app.data.settings.AppSettings
import com.cargenome.app.data.settings.AppSettingsRepository
import com.cargenome.app.domain.premium.LocalIsPremium
import com.cargenome.app.ui.analytics.AnalyticsScreen
import com.cargenome.app.ui.expense.ExpenseEditorScreen
import com.cargenome.app.ui.expense.ExpenseLogScreen
import com.cargenome.app.ui.fuel.FuelEditorScreen
import com.cargenome.app.ui.fuel.FuelLogScreen
import com.cargenome.app.ui.garage.GarageScreen
import com.cargenome.app.ui.odometer.OdometerLogScreen
import com.cargenome.app.ui.service.ScheduleEditorScreen
import com.cargenome.app.ui.service.ServiceEditorScreen
import com.cargenome.app.ui.service.ServiceLogScreen
import com.cargenome.app.ui.settings.SettingsScreen
import com.cargenome.app.ui.vehicle.VehicleDetailScreen
import com.cargenome.app.ui.vehicle.VehicleEditorScreen
import com.cargenome.app.ui.vehicle.VehicleEditorViewModel
import com.cargenome.app.ui.vin.VinDecoderScreen
import com.cargenome.app.ui.vin.VinDecoderViewModel
import com.cargenome.app.ui.vin.VinScanScreen
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

@Composable
fun CarGenomeNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    settingsRepo: AppSettingsRepository? = null,
) {
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val coroutineScope = rememberCoroutineScope()

    val isAtRoot = currentDestination?.hasRoute<GarageRoute>() == true
    BackHandler(enabled = isAtRoot) {
        (context as? Activity)?.moveTaskToBack(true)
    }

    val appSettings by (settingsRepo?.settings ?: emptyFlow()).collectAsStateWithLifecycle(
        initialValue = AppSettings(),
    )

    // Current vehicle ID is taken from route arguments if present, falling back to selectedVehicleId
    val currentVehicleId: Long? = navBackStackEntry?.arguments?.let { args ->
        if (args.containsKey("vehicleId")) {
            args.getLong("vehicleId").takeIf { it > 0 }
        } else {
            null
        }
    } ?: appSettings.selectedVehicleId

    val isCarTab = currentDestination?.let { dest ->
        dest.hasRoute<VehicleDetailRoute>() ||
        dest.hasRoute<FuelLogRoute>() ||
        dest.hasRoute<ServiceLogRoute>() ||
        dest.hasRoute<AnalyticsRoute>() ||
        (dest.hasRoute<SettingsRoute>() && navController.previousBackStackEntry?.destination?.hasRoute<GarageRoute>() != true)
    } == true

    val showBottomBar = isCarTab && currentVehicleId != null

    CompositionLocalProvider(LocalIsPremium provides appSettings.isPremiumActive) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            bottomBar = {
                if (showBottomBar) {
                    checkNotNull(currentVehicleId)
                    CarGenomeBottomBar(
                        navController = navController,
                        vehicleId = currentVehicleId,
                    )
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = GarageRoute,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            ) {
                composable<GarageRoute> {
                    GarageScreen(
                        onAddVehicle = { navController.navigate(VehicleEditorRoute()) },
                        onOpenVehicle = { vehicleId ->
                            coroutineScope.launch {
                                settingsRepo?.setSelectedVehicleId(vehicleId)
                            }
                            navController.navigate(VehicleDetailRoute(vehicleId)) {
                                popUpTo(GarageRoute) { inclusive = false }
                            }
                        },
                        onOpenSettings = {
                            navController.navigate(SettingsRoute(openPremium = true)) {
                                launchSingleTop = true
                            }
                        },
                    )
                }

            composable<VehicleDetailRoute> { entry ->
                val route: VehicleDetailRoute = entry.toRoute()
                VehicleDetailScreen(
                    onBack = { navController.popBackStack(GarageRoute, inclusive = false) },
                    onEdit = { vehicleId -> navController.navigate(VehicleEditorRoute(vehicleId = vehicleId)) },
                    onDeleted = { navController.popBackStack(GarageRoute, inclusive = false) },
                    onOpenOdometerLog = { vehicleId -> navController.navigate(OdometerLogRoute(vehicleId)) },
                    onOpenExpenses = { vehicleId -> navController.navigate(ExpenseLogRoute(vehicleId)) },
                )
            }

            composable<FuelLogRoute> { entry ->
                FuelLogScreen(
                    onAddRecord = { vehicleId -> navController.navigate(FuelEditorRoute(vehicleId)) },
                    onOpenRecord = { vehicleId, id ->
                        navController.navigate(FuelEditorRoute(vehicleId, recordId = id))
                    },
                    onBack = { navController.popBackStack(GarageRoute, inclusive = false) },
                    onAddVehicle = { navController.navigate(VehicleEditorRoute()) },
                )
            }

            composable<ServiceLogRoute> { entry ->
                ServiceLogScreen(
                    onAddRecord = { vehicleId -> navController.navigate(ServiceEditorRoute(vehicleId = vehicleId)) },
                    onOpenRecord = { vehicleId, id ->
                        navController.navigate(ServiceEditorRoute(vehicleId = vehicleId, recordId = id))
                    },
                    onAddSchedule = { vehicleId -> navController.navigate(ScheduleEditorRoute(vehicleId = vehicleId)) },
                    onEditSchedule = { vehicleId, id ->
                        navController.navigate(ScheduleEditorRoute(vehicleId = vehicleId, scheduleId = id))
                    },
                    onMarkScheduleDone = { vehicleId, id ->
                        navController.navigate(ServiceEditorRoute(vehicleId = vehicleId, scheduleId = id))
                    },
                    onBack = { navController.popBackStack(GarageRoute, inclusive = false) },
                    onAddVehicle = { navController.navigate(VehicleEditorRoute()) },
                )
            }

            composable<AnalyticsRoute> { entry ->
                AnalyticsScreen(
                    onBack = { navController.popBackStack(GarageRoute, inclusive = false) },
                    onAddVehicle = { navController.navigate(VehicleEditorRoute()) },
                )
            }

            composable<ExpenseLogRoute> { entry ->
                val route: ExpenseLogRoute = entry.toRoute()
                ExpenseLogScreen(
                    onBack = navController::popBackStack,
                    onAddExpense = { navController.navigate(ExpenseEditorRoute(route.vehicleId)) },
                    onOpenExpense = { id ->
                        navController.navigate(ExpenseEditorRoute(route.vehicleId, expenseId = id))
                    },
                )
            }

            composable<ExpenseEditorRoute> {
                ExpenseEditorScreen(
                    onBack = navController::popBackStack,
                    onSaved = { navController.popBackStack() },
                )
            }

            composable<ServiceEditorRoute> {
                ServiceEditorScreen(
                    onBack = navController::popBackStack,
                    onSaved = { navController.popBackStack() },
                )
            }

            composable<ScheduleEditorRoute> {
                ScheduleEditorScreen(
                    onBack = navController::popBackStack,
                    onSaved = { navController.popBackStack() },
                )
            }

            composable<OdometerLogRoute> {
                OdometerLogScreen(
                    onBack = navController::popBackStack,
                )
            }

            composable<FuelEditorRoute> {
                FuelEditorScreen(
                    onBack = navController::popBackStack,
                    onSaved = { navController.popBackStack() },
                )
            }

            composable<VehicleEditorRoute> { entry ->
                val scannedVin = entry.savedStateHandle.get<String>("scanned_vin")
                val editorViewModel: VehicleEditorViewModel = hiltViewModel()
                LaunchedEffect(scannedVin) {
                    if (!scannedVin.isNullOrBlank()) {
                        editorViewModel.onVinChanged(scannedVin)
                        entry.savedStateHandle.remove<String>("scanned_vin")
                    }
                }
                VehicleEditorScreen(
                    onBack = navController::popBackStack,
                    onSaved = { vehicleId ->
                        coroutineScope.launch {
                            settingsRepo?.setSelectedVehicleId(vehicleId)
                        }
                        // Replace the editor so Back from the detail screen lands in
                        // the garage rather than in a half-filled form.
                        navController.navigate(VehicleDetailRoute(vehicleId)) {
                            popUpTo(GarageRoute)
                        }
                    },
                    onScanVin = { navController.navigate(VinScanRoute) },
                    viewModel = editorViewModel,
                )
            }

            composable<VinDecoderRoute> { entry ->
                val scannedVin = entry.savedStateHandle.get<String>("scanned_vin")
                val decoderViewModel: VinDecoderViewModel = hiltViewModel()
                LaunchedEffect(scannedVin) {
                    if (!scannedVin.isNullOrBlank()) {
                        decoderViewModel.onInputChanged(scannedVin)
                        entry.savedStateHandle.remove<String>("scanned_vin")
                    }
                }
                VinDecoderScreen(
                    onAddToGarage = { vin -> navController.navigate(VehicleEditorRoute(vin = vin)) },
                    onBack = navController::popBackStack,
                    onScanVin = { navController.navigate(VinScanRoute) },
                    viewModel = decoderViewModel,
                )
            }

            composable<SettingsRoute> { entry ->
                val route: SettingsRoute = entry.toRoute()
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    openPremiumOnLaunch = route.openPremium,
                )
            }

            composable<VinScanRoute> {
                VinScanScreen(
                    onBack = navController::popBackStack,
                    onVinScanned = { vin ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("scanned_vin", vin)
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}
}
