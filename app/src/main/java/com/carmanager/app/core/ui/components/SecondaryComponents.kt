package com.carmanager.app.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carmanager.app.core.ui.theme.CarManagerShapes
import com.carmanager.app.core.ui.theme.CarManagerSpacing

/** Présentation commune aux écrans secondaires ; aucune donnée ou action imposée. */
@Composable
fun SecondarySectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier = modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface)
}

@Composable
fun SecondaryPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = CarManagerShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(CarManagerSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.small), content = content)
    }
}
