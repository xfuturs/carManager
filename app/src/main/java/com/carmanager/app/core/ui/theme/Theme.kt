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
    primary = CarManagerColors.primaryLight,
    onPrimary = Color.White,
    primaryContainer = CarManagerColors.primaryContainerLight,
    onPrimaryContainer = CarManagerColors.onPrimaryContainerLight,
    secondary = CarManagerColors.secondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEAF6),
    onSecondaryContainer = Color(0xFF133B56),
    tertiary = Color(0xFF516576),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDEE7EF),
    onTertiaryContainer = Color(0xFF263B4A),
    background = BackgroundLight,
    surface = SurfaceLight,
    onBackground = Color(0xFF182126),
    onSurface = Color(0xFF182126),
    onSurfaceVariant = Color(0xFF47565E),
    surfaceVariant = Color(0xFFE3EAEE),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7FAFB),
    surfaceContainer = Color(0xFFE8EFF1),
    surfaceContainerHigh = Color(0xFFE3EBEE),
    surfaceContainerHighest = Color(0xFFDFE7EA),
    surfaceDim = Color(0xFFDFE7EA),
    surfaceBright = Color.White,
    inverseSurface = Color(0xFF273238),
    inverseOnSurface = Color(0xFFEEF2F5),
    inversePrimary = CarManagerColors.primaryDark,
    surfaceTint = CarManagerColors.primaryLight,
    outline = Color(0xFF6F7D84),
    outlineVariant = Color(0xFFC5D0D6),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColorScheme = darkColorScheme(
    primary = CarManagerColors.primaryDark,
    onPrimary = CarManagerColors.onPrimaryDark,
    primaryContainer = CarManagerColors.primaryContainerDark,
    onPrimaryContainer = CarManagerColors.onPrimaryContainerDark,
    secondary = CarManagerColors.secondaryDark,
    onSecondary = Color(0xFF00344D),
    secondaryContainer = Color(0xFF124B6B),
    onSecondaryContainer = Color(0xFFCBE8FF),
    tertiary = Color(0xFFBACADA),
    onTertiary = Color(0xFF253B49),
    tertiaryContainer = Color(0xFF3B5261),
    onTertiaryContainer = Color(0xFFD6E6F6),
    background = BackgroundDark,
    surface = SurfaceDark,
    onBackground = Color(0xFFE1E9EE),
    onSurface = Color(0xFFE1E9EE),
    onSurfaceVariant = Color(0xFFBDC9D0),
    surfaceVariant = Color(0xFF263339),
    surfaceContainerLowest = Color(0xFF0C1317),
    surfaceContainerLow = Color(0xFF182126),
    surfaceContainer = Color(0xFF1D272C),
    surfaceContainerHigh = Color(0xFF273238),
    surfaceContainerHighest = Color(0xFF323D43),
    surfaceDim = BackgroundDark,
    surfaceBright = Color(0xFF323D43),
    inverseSurface = Color(0xFFE1E9EE),
    inverseOnSurface = Color(0xFF182126),
    inversePrimary = CarManagerColors.primaryLight,
    surfaceTint = CarManagerColors.primaryDark,
    outline = Color(0xFF87959C),
    outlineVariant = Color(0xFF3D4B52),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
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
