package com.carmanager.app.core.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.carmanager.app.core.ui.theme.LocalAppUnits

/**
 * Composant de suggestions rapides pour la saisie du kilométrage.
 */
@Composable
fun MileageSuggestions(
    onIncrementSelect: (Int) -> Unit
) {
    val units = LocalAppUnits.current
    val suggestions = listOf(50, 100, 200, 500, 1000)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Estimer selon dernier relevé",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            suggestions.forEach { amount ->
                SuggestionChip(
                    onClick = { onIncrementSelect(amount) },
                    label = { Text("+$amount ${units.distance}") }
                )
            }
        }
    }
}
