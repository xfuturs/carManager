package com.carmanager.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Immutable
data class CategoryColors(
    val fuel: Color,
    val onFuel: Color,
    val fuelContainer: Color,
    val maintenance: Color,
    val maintenanceContainer: Color,
    val admin: Color,
    val adminContainer: Color,
    val vehicle: Color,
    val vehicleContainer: Color,
    val success: Color,
    val successContainer: Color
)

@Immutable
data class AppUnits(
    val currency: String = "€",
    val distance: String = "km"
)

val LocalCategoryColors = staticCompositionLocalOf {
    CategoryColors(
        fuel = FuelColor,
        onFuel = OnFuelColor,
        fuelContainer = FuelContainer,
        maintenance = MaintenanceColor,
        maintenanceContainer = MaintenanceContainer,
        admin = AdminColor,
        adminContainer = AdminContainer,
        vehicle = VehicleColor,
        vehicleContainer = VehicleContainer,
        success = SuccessGreen,
        successContainer = SuccessContainer
    )
}

val LocalAppUnits = staticCompositionLocalOf { AppUnits() }

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    secondary = SecondaryTeal,
    background = BackgroundLight,
    surface = SurfaceLight,
    onSurface = Color.Black,
    onSurfaceVariant = Color(0xFF404040),
    surfaceVariant = Color(0xFFE9EEF2),
    outline = Color(0xFF808080),
    outlineVariant = Color(0xFFD0D0D0),
    error = ErrorRed,
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueNight,
    onPrimary = BackgroundDark,
    primaryContainer = PrimaryBlueDark,
    secondary = SecondaryTeal,
    background = BackgroundDark,
    surface = SurfaceDark,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFD1D1D1), 
    surfaceVariant = Color(0xFF25282B), 
    outline = Color(0xFF919191), // Gris beaucoup plus clair pour les icônes/traits secondaires
    outlineVariant = Color(0xFF45484B),
    error = ErrorRed,
)

@Composable
fun CarManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    units: AppUnits = AppUnits(),
    content: @Composable () -> Unit,
) {
    // On force nos couleurs Premium pour garantir le rendu "Pro"
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val categoryColors = if (darkTheme) {
        CategoryColors(
            fuel = FuelColorDark,
            onFuel = OnFuelColorDark,
            fuelContainer = FuelContainerDark,
            maintenance = MaintenanceColorDark,
            maintenanceContainer = MaintenanceContainerDark,
            admin = AdminColorDark,
            adminContainer = AdminContainerDark,
            vehicle = VehicleColorDark,
            vehicleContainer = VehicleContainerDark,
            success = SuccessGreenDark,
            successContainer = SuccessContainerDark
        )
    } else {
        CategoryColors(
            fuel = FuelColor,
            onFuel = OnFuelColor,
            fuelContainer = FuelContainer,
            maintenance = MaintenanceColor,
            maintenanceContainer = MaintenanceContainer,
            admin = AdminColor,
            adminContainer = AdminContainer,
            vehicle = VehicleColor,
            vehicleContainer = VehicleContainer,
            success = SuccessGreen,
            successContainer = SuccessContainer
        )
    }

    CompositionLocalProvider(
        LocalCategoryColors provides categoryColors,
        LocalAppUnits provides units
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
