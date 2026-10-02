package com.carmanager.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.carmanager.app.core.ui.navigation.TopLevelDestination
import com.carmanager.app.core.ui.theme.*

/** Le corps peut grandir avec la police ; l'inset système est ajouté séparément. */
@Composable
fun CarManagerBottomNavigation(
    selectedDestination: TopLevelDestination?,
    onDestinationClick: (TopLevelDestination) -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = CarManagerElevation.chrome) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .heightIn(min = CarManagerDimensions.navigationMinHeight)
                .selectableGroup()
        ) {
            TopLevelDestination.entries.forEach { destination ->
                val selected = destination == selectedDestination
                val label = stringResource(destination.titleRes)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = CarManagerDimensions.touchTarget)
                        .heightIn(min = CarManagerDimensions.navigationMinHeight)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onDestinationClick(destination) })
                        .padding(horizontal = CarManagerSpacing.extraSmall, vertical = CarManagerSpacing.chromeVertical),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.extraSmall, Alignment.CenterVertically)
                ) {
                    Box(
                        modifier = Modifier
                            .size(CarManagerDimensions.selectionWidth, CarManagerDimensions.selectionHeight)
                            .clip(CarManagerShapes.navigationIndicator)
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null, // Le libellé décrit le même onglet sélectionnable.
                            modifier = Modifier.size(CarManagerDimensions.icon),
                            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = label,
                        style = CarManagerTypography.navigationLabel,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
