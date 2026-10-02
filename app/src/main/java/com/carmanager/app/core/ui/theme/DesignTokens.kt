package com.carmanager.app.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Socle partagé ; les contenus métier seront migrés dans leurs passes dédiées. */
object CarManagerSpacing {
    val extraSmall = 4.dp
    val chromeVertical = 6.dp
    val small = 8.dp
    val firstContentTop = 8.dp
    val medium = 12.dp
    val screenHorizontal = 16.dp
    val large = 24.dp
}

object CarManagerDimensions {
    val topLevelToolbar = 54.dp
    val secondaryToolbar = 56.dp
    val navigationMinHeight = 66.dp
    val touchTarget = 48.dp
    val icon = 24.dp
    val selectionWidth = 40.dp
    val selectionHeight = 28.dp
}

object CarManagerShapes {
    val navigationIndicator = RoundedCornerShape(14.dp)
    val card = RoundedCornerShape(12.dp)
    val control = RoundedCornerShape(8.dp)
}

object CarManagerElevation {
    val chrome = 0.dp
    val card = 1.dp
}
