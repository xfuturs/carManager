package com.carmanager.app.core.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.carmanager.app.R

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Vehicles : Screen("vehicles")
    data object Calculators : Screen("calculators")
    data object Settings : Screen("settings")

    data object VehicleEdit : Screen("vehicle/edit?vehicleId={vehicleId}") {
        fun createRoute(vehicleId: Long? = null): String =
            if (vehicleId != null) "vehicle/edit?vehicleId=$vehicleId" else "vehicle/edit"
    }

    data object VehicleDetail : Screen("vehicle/{vehicleId}") {
        fun createRoute(vehicleId: Long) = "vehicle/$vehicleId"
    }

    data object FuelList : Screen("fuel/{vehicleId}") {
        fun createRoute(vehicleId: Long) = "fuel/$vehicleId"
    }

    data object FuelEdit : Screen("fuel/add/{vehicleId}") {
        fun createRoute(vehicleId: Long) = "fuel/add/$vehicleId"
    }

    data object MaintenanceList : Screen("maintenance/{vehicleId}") {
        fun createRoute(vehicleId: Long) = "maintenance/$vehicleId"
    }

    data object MaintenanceEdit : Screen("maintenance/add/{vehicleId}?initialType={initialType}") {
        fun createRoute(vehicleId: Long, initialType: String? = null) = 
            "maintenance/add/$vehicleId" + (initialType?.let { "?initialType=$it" } ?: "")
    }

    data object Documents : Screen("documents/{vehicleId}") {
        fun createRoute(vehicleId: Long) = "documents/$vehicleId"
    }

    data object MaintenanceAdvice : Screen("maintenance/advice/{vehicleId}") {
        fun createRoute(vehicleId: Long) = "maintenance/advice/$vehicleId"
    }

    data object MileageHistory : Screen("mileage/history/{vehicleId}") {
        fun createRoute(vehicleId: Long) = "mileage/history/$vehicleId"
    }

    data object Login : Screen("login")
    data object PrivacyPolicy : Screen("privacy_policy")
    data object Stats : Screen("stats")
    data object Deadlines : Screen("deadlines")
}

enum class TopLevelDestination(
    val route: String,
    val titleRes: Int,
    val icon: ImageVector,
) {
    Dashboard(Screen.Dashboard.route, R.string.nav_dashboard, Icons.Default.Home),
    Vehicles(Screen.Vehicles.route, R.string.nav_vehicles, Icons.Default.DirectionsCar),
    Calculators(Screen.Calculators.route, R.string.nav_calculators, Icons.Default.Calculate),
    Settings(Screen.Settings.route, R.string.nav_settings, Icons.Default.Settings),
}
