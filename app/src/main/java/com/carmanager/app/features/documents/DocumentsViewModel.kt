package com.carmanager.app.features.documents

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmanager.app.core.domain.model.Document
import com.carmanager.app.core.domain.model.DocumentCategory
import com.carmanager.app.core.domain.model.LocalDataState
import com.carmanager.app.core.domain.session.observeLocalState
import com.carmanager.app.core.domain.repository.DocumentRepository
import com.carmanager.app.core.util.FileStorageHelper
import com.carmanager.app.core.util.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import java.io.File
import kotlinx.coroutines.CancellationException
import com.carmanager.app.core.data.local.OwnedDatabaseAccess

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val session: com.carmanager.app.core.domain.session.WorkspaceSession,
    private val access: OwnedDatabaseAccess,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val workspaceOwner = session.owner.value

    val vehicleId: Long = checkNotNull(savedStateHandle["vehicleId"])

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val retry = MutableStateFlow(0)
    val uiState: StateFlow<LocalDataState<List<Document>>> = observeLocalState(session, retry) {
        combine(documentRepository.observeByVehicle(vehicleId), _searchQuery) { docs, query ->
            if (query.isBlank()) docs else docs.filter { it.title.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000, replayExpirationMillis = 0), LocalDataState.Loading)

    fun retryLoading() { retry.value++ }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun canOpen(document: Document): Boolean = runCatching {
        session.requireCurrent(workspaceOwner)
        check(document.ownerKey == workspaceOwner && document.vehicleId == vehicleId)
    }.isSuccess

    var pendingDeletion by androidx.compose.runtime.mutableStateOf<Document?>(null)
        private set
    fun requestDeletion(document: Document) {
        if (canOpen(document)) pendingDeletion = document
    }
    fun cancelDeletion() { pendingDeletion = null }
    fun confirmDeletion() {
        val document = pendingDeletion ?: return
        pendingDeletion = null
        deleteDocument(document)
    }

    private val fileResolver = OwnedDocumentFileResolver(session, access, context)
    private val ordinaryActions = OrdinaryDocumentActions(fileResolver, context)

    fun openDocument(document: Document) {
        viewModelScope.launch {
            try { ordinaryActions.open(document, workspaceOwner, vehicleId) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiEvent.send(UiEvent.ShowSnackbar("Ouverture impossible : document indisponible ou aucun lecteur compatible."))
            }
        }
    }

    fun addDocument(uri: Uri, title: String, category: DocumentCategory) {
        viewModelScope.launch {
            try {
                require(category != DocumentCategory.REPORTS) { "Les rapports Car Manager sont créés depuis Statistiques." }
                access.read(workspaceOwner, vehicleId) { Unit }
                com.carmanager.app.core.util.StagedDocumentFile.store(
                    prepare = {
                        val coroutine = kotlinx.coroutines.currentCoroutineContext()
                        FileStorageHelper.saveFileToInternalStorage(context, uri) {
                            coroutine.ensureActive(); session.requireWritable(workspaceOwner)
                        }
                    },
                    insert = { path -> access.write(workspaceOwner, vehicleId) {
                        val document = Document(ownerKey = workspaceOwner, vehicleId = vehicleId,
                            title = title, category = category, filePath = path, date = System.currentTimeMillis())
                        val id = documentRepository.saveDocument(document)
                        check(id > 0) { "Document non enregistré." }
                        document.copy(id = id)
                    } },
                    remove = FileStorageHelper::deleteFile
                )
                _uiEvent.send(UiEvent.ShowSnackbar("Document ajouté avec succès"))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiEvent.send(UiEvent.ShowSnackbar(if (category == DocumentCategory.REPORTS)
                    "Les rapports Car Manager sont créés depuis Statistiques."
                    else "Import impossible. Vérifiez le fichier et l'espace disponible, puis réessayez."))
            }
        }
    }

    fun convertToPdf(document: Document) {
        viewModelScope.launch {
            try {
                check(document.category != DocumentCategory.REPORTS) { "Un rapport est déjà au format PDF." }
                val source = fileResolver.file(document, workspaceOwner, vehicleId)
                com.carmanager.app.core.util.StagedDocumentFile.store(
                    prepare = {
                        val coroutine = kotlinx.coroutines.currentCoroutineContext()
                        FileStorageHelper.convertImageToPdf(context, source.path) {
                            coroutine.ensureActive(); session.requireWritable(workspaceOwner)
                        }
                    },
                    insert = { path -> access.write(workspaceOwner, vehicleId) {
                        val result = Document(ownerKey = workspaceOwner, vehicleId = vehicleId,
                            title = "${document.title} (PDF)", category = document.category,
                            filePath = path, date = System.currentTimeMillis())
                        val id = documentRepository.saveDocument(result)
                        check(id > 0) { "Document non enregistré." }
                        result.copy(id = id)
                    } },
                    remove = FileStorageHelper::deleteFile
                )
                _uiEvent.send(UiEvent.ShowSnackbar("Conversion réussie"))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiEvent.send(UiEvent.ShowSnackbar("Conversion impossible. Vérifiez l'image et l'espace disponible, puis réessayez."))
            }
        }
    }

    fun deleteDocument(document: Document) {
        viewModelScope.launch {
            try {
                access.write(workspaceOwner, vehicleId) {
                    check(document.ownerKey == workspaceOwner && document.vehicleId == vehicleId)
                    val file = access.documentFile(workspaceOwner, document.id, document.filePath, File(context.filesDir, "vehicle_documents"))
                    check(!file.exists() || file.delete()) { "Impossible de supprimer le fichier." }
                    documentRepository.deleteDocument(document)
                }
                _uiEvent.send(UiEvent.ShowSnackbar("Document supprimé"))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiEvent.send(UiEvent.ShowSnackbar(e.message ?: "Suppression impossible"))
            }
        }
    }
}
