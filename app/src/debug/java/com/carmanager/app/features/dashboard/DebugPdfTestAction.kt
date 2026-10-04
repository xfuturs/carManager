package com.carmanager.app.features.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.carmanager.app.core.ui.theme.CarManagerShapes

/** Compile uniquement le bouton de test local, sans état ni entitlement. */
@Composable
internal fun DebugPdfTestAction(onGenerateReport: () -> Unit) {
    TextButton(onClick = onGenerateReport, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = CarManagerShapes.control) {
        Text("DEBUG · Tester le PDF", Modifier.weight(1f))
    }
    Text("Test local du rendu PDF. N’accorde pas Premium.",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
