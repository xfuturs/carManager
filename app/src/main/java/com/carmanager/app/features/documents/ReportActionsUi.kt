package com.carmanager.app.features.documents

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.carmanager.app.core.domain.model.Document
import com.carmanager.app.core.util.ReportFileIO

internal class ReportUiActions(val open: (Document) -> Unit, val share: (Document) -> Unit, val save: (Document) -> Unit)

@Composable
internal fun reportUiActions(snackbar: SnackbarHostState, viewModel: ReportActionsViewModel = hiltViewModel()): ReportUiActions {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ReportFileIO.MIME), viewModel::finishExport)
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbar.showSnackbar(it) } }
    return ReportUiActions(viewModel::open, viewModel::share) { document ->
        viewModel.prepareExport(document)?.let { name ->
            try { launcher.launch(name) } catch (_: Exception) { viewModel.exportLaunchFailed() }
        }
    }
}
