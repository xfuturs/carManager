package com.carmanager.app.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.carmanager.app.core.domain.model.LocalDataState
import com.carmanager.app.core.domain.model.forOwner

val LocalWorkspaceOwner = staticCompositionLocalOf<String?> { null }

@Composable
fun <T> LocalDataContent(
    state: LocalDataState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    val owner = LocalWorkspaceOwner.current
    val visibleState = if (owner == null) LocalDataState.Loading else state.forOwner(owner)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (visibleState) {
            LocalDataState.Loading -> CircularProgressIndicator()
            is LocalDataState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Chargement des données locales impossible.")
                TextButton(onClick = onRetry) { Text("Réessayer") }
            }
            is LocalDataState.Ready -> content(visibleState.data)
        }
    }
}
