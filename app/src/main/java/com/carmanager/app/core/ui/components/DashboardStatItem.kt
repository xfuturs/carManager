package com.carmanager.app.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.carmanager.app.core.ui.theme.CarManagerDimensions
import com.carmanager.app.core.ui.theme.CarManagerShapes
import com.carmanager.app.core.ui.theme.CarManagerSpacing
import com.carmanager.app.core.ui.theme.CarManagerTypography
import androidx.compose.ui.unit.dp

@Composable
fun DashboardStatItem(
    label: String, value: String, modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary, onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.heightIn(min = CarManagerDimensions.touchTarget)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button,
                onClickLabel = "Ouvrir $label", onClick = onClick) else Modifier),
        color = MaterialTheme.colorScheme.surfaceContainerLow, shape = CarManagerShapes.control,
        border = if (onClick != null) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    ) {
        Column(Modifier.padding(CarManagerSpacing.small),
            verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.extraSmall)) {
            Text(value, style = CarManagerTypography.cardTitle, color = color,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
