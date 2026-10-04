package com.carmanager.app.core.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.carmanager.app.core.ui.theme.CarManagerDimensions
import com.carmanager.app.core.ui.theme.CarManagerSpacing
import com.carmanager.app.core.ui.theme.CarManagerTypography

/** Une ligne, avec assez de hauteur pour sa métrique même en grande police. */
@Composable
private fun toolbarHeight(style: TextStyle, minimumHeight: Dp, verticalPadding: Dp = CarManagerSpacing.small * 2): Dp = maxOf(
    minimumHeight,
    CarManagerDimensions.touchTarget,
    with(LocalDensity.current) { style.lineHeight.toDp() } + verticalPadding
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarManagerTopLevelAppBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = {
            Text(title, style = CarManagerTypography.pageTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        actions = actions,
        expandedHeight = toolbarHeight(CarManagerTypography.pageTitle, CarManagerDimensions.topLevelToolbar),
        windowInsets = TopAppBarDefaults.windowInsets,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarManagerBackAppBar(
    title: String,
    onNavigateBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    backDescription: String = "Retour",
    titleContent: (@Composable () -> Unit)? = null
) {
    TopAppBar(
        title = titleContent ?: {
            Text(title, style = CarManagerTypography.toolbarTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        modifier = Modifier.padding(horizontal = CarManagerSpacing.screenHorizontal - CarManagerSpacing.extraSmall),
        navigationIcon = {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.sizeIn(minWidth = CarManagerDimensions.touchTarget, minHeight = CarManagerDimensions.touchTarget)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDescription)
            }
        },
        actions = actions,
        expandedHeight = if (titleContent == null) toolbarHeight(CarManagerTypography.toolbarTitle, CarManagerDimensions.secondaryToolbar) else
            toolbarHeight(MaterialTheme.typography.bodyLarge, CarManagerDimensions.secondaryToolbar, CarManagerSpacing.screenHorizontal * 2),
        windowInsets = TopAppBarDefaults.windowInsets,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            actionIconContentColor = MaterialTheme.colorScheme.primary
        )
    )
}
