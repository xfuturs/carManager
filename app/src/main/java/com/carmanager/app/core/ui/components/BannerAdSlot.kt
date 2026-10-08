package com.carmanager.app.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Slot mesure du bottomBar : jamais superpose au contenu ni au FAB du Scaffold. */
@Composable
fun BannerAdSlot(visible: Boolean = true) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = if (visible) 8.dp else 0.dp).fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) { BannerAd(visible = visible) }
}
