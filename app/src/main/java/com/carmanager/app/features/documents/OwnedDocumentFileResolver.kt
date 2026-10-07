package com.carmanager.app.features.documents

import android.content.Context
import com.carmanager.app.core.data.local.OwnedDatabaseAccess
import com.carmanager.app.core.domain.model.Document
import com.carmanager.app.core.domain.session.WorkspaceSession
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Résolution commune, sans entitlement : les documents déjà acquis restent accessibles. */
class OwnedDocumentFileResolver internal constructor(
    private val session: WorkspaceSession,
    private val resolve: suspend (Document) -> File
) {
    internal fun requireScope(document: Document, owner: String, vehicleId: Long) {
        session.requireWritable(owner)
        check(document.ownerKey == owner && document.vehicleId == vehicleId) { "Document absent de cet espace." }
    }
    constructor(session: WorkspaceSession, access: OwnedDatabaseAccess, context: Context) : this(session, { document ->
        access.documentFile(document.ownerKey, document.id, document.filePath, File(context.filesDir, "vehicle_documents"), document.vehicleId)
    })
    suspend fun file(document: Document, owner: String = document.ownerKey, vehicleId: Long = document.vehicleId): File = withContext(Dispatchers.IO) {
        requireScope(document, owner, vehicleId)
        val file = resolve(document)
        session.requireWritable(owner)
        check(file.isFile && file.canRead()) { "Document introuvable ou illisible sur cet appareil." }
        file
    }
}
