package com.carmanager.app.features.documents

import android.content.Context
import com.carmanager.app.core.data.local.OwnedDatabaseAccess
import com.carmanager.app.core.domain.model.Document
import com.carmanager.app.core.domain.model.DocumentCategory
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.core.util.PdfReportHelper
import com.carmanager.app.core.util.ReportFileIO
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Les documents acquis n'ont aucun lien d'accès avec l'entitlement Premium. */
class ReportDocumentActions internal constructor(
    private val session: WorkspaceSession,
    private val resolveFile: suspend (Document) -> File,
    private val present: (File, Boolean) -> Unit
) {
    @Inject constructor(session: WorkspaceSession, access: OwnedDatabaseAccess, @ApplicationContext context: Context) :
        this(session, { document -> OwnedDocumentFileResolver(session, access, context).file(document) },
            { file, share -> if (share) PdfReportHelper.share(context, file) else PdfReportHelper.open(context, file) })
    private suspend fun file(document: Document): File {
        session.requireCurrent(document.ownerKey)
        check(document.category == DocumentCategory.REPORTS)
        val file = resolveFile(document)
        session.requireCurrent(document.ownerKey)
        check(file.isFile) { "Rapport introuvable sur cet appareil." }
        return file
    }
    suspend fun open(document: Document) { val file = file(document); present(file, false) }
    suspend fun share(document: Document) { val file = file(document); present(file, true) }
    suspend fun saveCopy(document: Document, destination: (() -> OutputStream?)?): Boolean {
        if (destination == null) return false
        val coroutine = currentCoroutineContext()
        return ReportFileIO.copy(file(document), destination) {
            coroutine.ensureActive()
            session.requireCurrent(document.ownerKey)
        }
    }
}
