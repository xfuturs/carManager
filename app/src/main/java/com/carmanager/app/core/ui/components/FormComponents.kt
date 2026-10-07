package com.carmanager.app.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carmanager.app.core.ui.theme.CarManagerDimensions
import com.carmanager.app.core.ui.theme.CarManagerShapes
import com.carmanager.app.core.ui.theme.CarManagerSpacing
import com.carmanager.app.core.ui.theme.CarManagerTypography

/** Le padding Scaffold et l'IME restent hors du contenu défilant. */
@Composable
fun FormScreenContent(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = CarManagerSpacing.screenHorizontal, vertical = CarManagerSpacing.small),
        verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.screenHorizontal),
        content = content
    )
}

@Composable
fun FormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.small)) {
        Text(title, style = CarManagerTypography.supporting, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
fun FormFieldPair(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 320.dp * fontScale && fontScale <= 1.3f) {
            Row(horizontalArrangement = Arrangement.spacedBy(CarManagerSpacing.small)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.small)) { first() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.small)) { second() }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(CarManagerSpacing.small)) { first(); second() }
        }
    }
}

@Composable
fun FormSaveAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = CarManagerDimensions.touchTarget),
        shape = CarManagerShapes.control,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary)) {
        Text(label, style = CarManagerTypography.buttonLabel)
    }
}

/** Échec terminal de chargement : les champs ne sont pas présentés comme des données chargées. */
@Composable
fun FormLoadFailure(message: String?, onRetry: () -> Unit) {
    if (message != null) {
        Column(Modifier.fillMaxWidth()) {
            Text(message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text("Réessayer") }
        }
    }
}
