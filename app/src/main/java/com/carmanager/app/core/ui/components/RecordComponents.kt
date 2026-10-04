package com.carmanager.app.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.carmanager.app.core.ui.theme.CarManagerDimensions
import com.carmanager.app.core.ui.theme.CarManagerShapes
import com.carmanager.app.core.ui.theme.CarManagerSpacing
import com.carmanager.app.core.ui.theme.CarManagerTypography

/** Une seule surface plate ; les valeurs restent fournies par chaque feature. */
@Composable
fun RecordRow(icon: ImageVector, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = CarManagerShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(CarManagerSpacing.medium), horizontalArrangement = Arrangement.spacedBy(CarManagerSpacing.medium)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(CarManagerDimensions.icon))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.extraSmall), content = content)
        }
    }
}

@Composable
fun CompactEmptyState(icon: ImageVector, title: String, supporting: String) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = CarManagerShapes.card) {
        Column(Modifier.padding(CarManagerSpacing.screenHorizontal), verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.small)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(CarManagerDimensions.icon),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = CarManagerTypography.supporting, modifier = Modifier.semantics { heading() })
            Text(supporting, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
