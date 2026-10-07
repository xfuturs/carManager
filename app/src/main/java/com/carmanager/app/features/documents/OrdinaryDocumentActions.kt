package com.carmanager.app.features.documents

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.carmanager.app.core.domain.model.Document
import com.carmanager.app.core.domain.model.DocumentCategory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class OrdinaryDocumentActions(
    private val resolver: OwnedDocumentFileResolver,
    private val present: (File, String) -> Unit
) {
    constructor(resolver: OwnedDocumentFileResolver, context: Context) : this(resolver, { file, mime ->
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        // ACTION_VIEW fait remonter ActivityNotFoundException ; le chooser seul peut cacher l'absence de lecteur.
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri) ?: mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    })
    suspend fun open(document: Document, owner: String, vehicleId: Long) {
        val file = resolver.file(document, owner, vehicleId)
        withContext(Dispatchers.Main) {
            resolver.requireScope(document, owner, vehicleId)
            present(file, mime(document))
        }
    }
    companion object {
        fun mime(document: Document): String = when {
            document.category == DocumentCategory.REPORTS || document.filePath.endsWith(".pdf", true) -> "application/pdf"
            document.filePath.endsWith(".jpg", true) || document.filePath.endsWith(".jpeg", true) -> "image/jpeg"
            document.filePath.endsWith(".png", true) -> "image/png"
            else -> "*/*"
        }
    }
}
