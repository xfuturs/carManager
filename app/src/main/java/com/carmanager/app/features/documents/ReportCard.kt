@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.carmanager.app.features.documents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carmanager.app.core.domain.model.Document
import com.carmanager.app.core.ui.theme.CarManagerShapes
import com.carmanager.app.core.util.DateFormatter

/** La même carte en pleine largeur dans le dossier Rapports et la recherche globale. */
@Composable
internal fun ReportCard(document: Document, onDelete: () -> Unit, onOpen: () -> Unit,
    onSave: () -> Unit, onShare: () -> Unit) {
    var menu by remember(document.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), shape = CarManagerShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.PictureAsPdf, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(document.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    val date = DateFormatter.formatShort(document.date)
                    if (!document.title.contains(date)) Text(date, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                        Icon(Icons.Default.MoreVert, "Actions du rapport")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onDelete() }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ouvrir") }
                IconButton(onClick = onSave, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Icon(Icons.Default.SaveAlt, "Enregistrer une copie")
                }
                IconButton(onClick = onShare, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Icon(Icons.Default.Share, "Partager")
                }
            }
        }
    }
}
