package com.carmanager.app.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.carmanager.app.core.ui.theme.CarManagerShapes
import com.carmanager.app.core.ui.theme.CarManagerDimensions
import androidx.compose.ui.Modifier
import com.carmanager.app.core.util.DateFormatter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    selectedDate: Long?,
    onDateSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate ?: System.currentTimeMillis())

    val dateText = selectedDate?.let {
        DateFormatter.formatMedium(it)
    } ?: "Choisir une date"

    Box(modifier.fillMaxWidth().heightIn(min = CarManagerDimensions.touchTarget)
        .semantics { contentDescription = "$label, $dateText" }
        .clickable(role = Role.Button, onClickLabel = "Choisir une date") { showDialog = true }) {
        OutlinedTextField(
            shape = CarManagerShapes.control,
            textStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            value = dateText,
            onValueChange = {},
            label = { Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) },
            readOnly = true,
            trailingIcon = {
                Icon(Icons.Default.CalendarToday, contentDescription = null)
            },
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            enabled = false,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                disabledTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                disabledLabelColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }

    if (showDialog) {
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { onDateSelected(it) }
                    showDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Annuler")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
