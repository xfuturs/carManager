package com.carmanager.app.features.documents

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmanager.app.core.domain.model.Document
import com.carmanager.app.core.domain.model.DocumentCategory
import com.carmanager.app.core.domain.repository.DocumentRepository
import com.carmanager.app.core.util.FileStorageHelper
import com.carmanager.app.core.util.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val vehicleId: Long = checkNotNull(savedStateHandle["vehicleId"])

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    val documents = documentRepository.observeByVehicle(vehicleId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addDocument(uri: Uri, title: String, category: DocumentCategory) {
        viewModelScope.launch {
            val internalPath = FileStorageHelper.saveFileToInternalStorage(context, uri)
            if (internalPath != null) {
                val document = Document(
                    vehicleId = vehicleId,
                    title = title,
                    category = category,
                    filePath = internalPath,
                    date = System.currentTimeMillis()
                )
                documentRepository.saveDocument(document)
                _uiEvent.send(UiEvent.ShowSnackbar("Document ajouté avec succès"))
            } else {
                _uiEvent.send(UiEvent.ShowSnackbar("Erreur lors de la sauvegarde du fichier"))
            }
        }
    }

    fun convertToPdf(document: Document) {
        viewModelScope.launch {
            val pdfPath = FileStorageHelper.convertImageToPdf(context, document.filePath)
            if (pdfPath != null) {
                val pdfDocument = Document(
                    vehicleId = vehicleId,
                    title = "${document.title} (PDF)",
                    category = document.category,
                    filePath = pdfPath,
                    date = System.currentTimeMillis()
                )
                documentRepository.saveDocument(pdfDocument)
                _uiEvent.send(UiEvent.ShowSnackbar("Conversion réussie"))
            } else {
                _uiEvent.send(UiEvent.ShowSnackbar("Erreur lors de la conversion"))
            }
        }
    }

    fun deleteDocument(document: Document) {
        viewModelScope.launch {
            FileStorageHelper.deleteFile(document.filePath)
            documentRepository.deleteDocument(document)
            _uiEvent.send(UiEvent.ShowSnackbar("Document supprimé"))
        }
    }
}
