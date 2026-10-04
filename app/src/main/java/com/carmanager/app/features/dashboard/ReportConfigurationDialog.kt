package com.carmanager.app.features.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.carmanager.app.features.documents.reportDate
import com.carmanager.app.core.domain.model.ReportSection

@Composable
internal fun ReportConfigurationDialog(draft: ReportDraft, onToggle: (ReportSection) -> Unit,
    onCancel: () -> Unit, onGenerate: () -> Unit) {
    AlertDialog(onDismissRequest = onCancel, title = { Text("Créer un nouveau rapport") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (draft.previous.count > 0) {
                Text("Ce véhicule possède déjà ${draft.previous.count} rapport(s) enregistré(s).")
                draft.previous.latest?.let { Text("Dernier rapport : ${reportDate(it)}", style = MaterialTheme.typography.bodySmall) }
            }
            Text("Cette action créera un nouveau rapport sans remplacer les précédents.")
            ReportSection.entries.forEach { section ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(section in draft.selected,
                    role = Role.Checkbox, onValueChange = { onToggle(section) })) {
                    Checkbox(checked = section in draft.selected, onCheckedChange = null)
                    Text(section.label, Modifier.weight(1f).padding(vertical = 12.dp))
                }
            }
            if (!draft.canGenerate) Text("Sélectionnez au moins une section à inclure dans le rapport.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton(onClick = onGenerate, enabled = draft.canGenerate,
            modifier = Modifier.heightIn(min = 48.dp)) { Text("Générer le rapport") } },
        dismissButton = { TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Annuler") } })
}
