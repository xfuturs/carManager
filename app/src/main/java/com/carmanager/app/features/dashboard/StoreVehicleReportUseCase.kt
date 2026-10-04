package com.carmanager.app.features.dashboard

import android.content.Context
import com.carmanager.app.core.data.local.OwnedDatabaseAccess
import com.carmanager.app.core.domain.model.Document
import com.carmanager.app.core.domain.model.DocumentCategory
import com.carmanager.app.core.domain.model.ReportSection
import com.carmanager.app.core.domain.repository.DocumentRepository
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.core.util.FileStorageHelper
import com.carmanager.app.core.util.PdfReportHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import com.carmanager.app.features.documents.ReportSummary

internal interface ReportStorage {
    fun create(data: GenerateVehicleReportUseCase.ReportData, date: Long): String
    fun delete(path: String): Boolean
}

/** Index Documents et fichier privé liés à la même écriture contrôlée par le workspace. */
class StoreVehicleReportUseCase internal constructor(
    private val readReport: suspend (Long) -> GenerateVehicleReportUseCase.ReportData?,
    private val documents: DocumentRepository,
    private val session: WorkspaceSession,
    private val storage: ReportStorage,
    private val transaction: suspend (String, Long, suspend () -> Document) -> Document,
    private val now: () -> Long
) {
    @Inject constructor(read: GenerateVehicleReportUseCase, documents: DocumentRepository,
        session: WorkspaceSession, access: OwnedDatabaseAccess, @ApplicationContext context: Context) :
        this({ read(it) }, documents, session, object : ReportStorage {
            override fun create(data: GenerateVehicleReportUseCase.ReportData, date: Long) = PdfReportHelper.generate(
                context, data.vehicle, data.fuelRecords, data.maintenanceRecords, date, data.mileageRecords, data.sections)
            override fun delete(path: String) = FileStorageHelper.deleteFile(path)
        }, { owner, vehicle, action -> access.write(owner, vehicle, action) }, System::currentTimeMillis)

    suspend fun previousReports(vehicleId: Long): ReportSummary {
        val owner = session.owner.value
        session.requireCurrent(owner)
        val current = documents.observeByVehicle(vehicleId).first()
        session.requireCurrent(owner)
        check(current.all { it.ownerKey == owner && it.vehicleId == vehicleId })
        return ReportSummary.from(current)
    }

    suspend operator fun invoke(vehicleId: Long, sections: Set<ReportSection> = ReportSection.entries.toSet()): Document {
        val selected = sections.toSet()
        require(selected.isNotEmpty()) { "Sélectionnez au moins une section." }
        val owner = session.owner.value
        session.requireWritable(owner)
        val data = (readReport(vehicleId) ?: error("Véhicule indisponible.")).copy(sections = selected)
        session.requireWritable(owner)
        check(data.vehicle.id == vehicleId && data.vehicle.ownerKey == owner &&
            data.fuelRecords.all { it.ownerKey == owner && it.vehicleId == vehicleId } &&
            data.maintenanceRecords.all { it.ownerKey == owner && it.vehicleId == vehicleId } &&
            data.mileageRecords.all { it.ownerKey == owner && it.vehicleId == vehicleId })
        val date = now()
        var created: String? = null
        try {
            return transaction(owner, vehicleId) {
                val path = storage.create(data, date).also { created = it }
                currentCoroutineContext().ensureActive()
                session.requireWritable(owner)
                val title = "Rapport du " + SimpleDateFormat("dd/MM/yyyy 'à' HH:mm:ss", Locale.FRANCE).format(Date(date))
                val document = Document(vehicleId = vehicleId, title = title, category = DocumentCategory.REPORTS,
                    filePath = path, date = date, ownerKey = owner)
                val id = documents.saveDocument(document)
                check(id > 0) { "Rapport non enregistré." }
                document.copy(id = id)
            }
        } catch (error: Throwable) {
            created?.let { path ->
                try { if (!storage.delete(path)) error.addSuppressed(IllegalStateException("Copie privée non supprimée.")) }
                catch (cleanup: Exception) { error.addSuppressed(cleanup) }
            }
            throw error
        }
    }
}
