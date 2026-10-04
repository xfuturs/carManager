package com.carmanager.app.features.documents

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmanager.app.core.domain.model.Document
import com.carmanager.app.core.domain.session.WorkspaceSession
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ReportActionsViewModel @Inject constructor(private val actions: ReportDocumentActions,
    private val session: WorkspaceSession, @param:ApplicationContext private val context: Context) : ViewModel() {
    private val workspaceOwner = session.owner.value
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private var pendingExport: Document? = null

    private fun checkOwner(document: Document) {
        session.requireCurrent(workspaceOwner)
        check(document.ownerKey == workspaceOwner)
    }
    private fun runAction(errorMessage: String, action: suspend () -> Unit) { viewModelScope.launch {
        try { action() } catch (error: Exception) {
            if (error is CancellationException) throw error
            messages.send(errorMessage)
        }
    } }
    fun open(document: Document) = runAction("Ouverture du rapport impossible.") { checkOwner(document); actions.open(document) }
    fun share(document: Document) = runAction("Partage du rapport impossible.") { checkOwner(document); actions.share(document) }
    fun prepareExport(document: Document): String? = try {
        checkOwner(document)
        if (pendingExport != null) null else {
            pendingExport = document
            File(document.filePath).name
        }
    } catch (_: Exception) { messages.trySend("Rapport indisponible dans cet espace."); null }
    fun exportLaunchFailed() { pendingExport = null; messages.trySend("Enregistrement de la copie impossible.") }
    fun finishExport(uri: Uri?) {
        val document = pendingExport ?: return
        pendingExport = null
        if (uri == null) return
        runAction("Copie non enregistrée. Le rapport reste dans Car Manager.") {
            checkOwner(document)
            withContext(Dispatchers.IO) { actions.saveCopy(document) { context.contentResolver.openOutputStream(uri, "w") } }
            messages.send("Copie enregistrée.")
        }
    }
}
