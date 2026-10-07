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

/** Snapshot, rendu IO, puis index Documents dans un commit bref contrôlé par le workspace. */
class StoreVehicleReportUseCase internal constructor(
    private val readReport: suspend (Long) -> GenerateVehicleReportUseCase.ReportData?,
    private val documents: DocumentRepository,
    private val session: WorkspaceSession,
    private val storage: ReportStorage,
    private val transaction: suspend (String, Long, suspend () -> Document) -> Document,
    private val now: () -> Long,
    private val presentation: suspend () -> com.carmanager.app.core.util.ReportPresentationSettings = { com.carmanager.app.core.util.ReportPresentationSettings() }
) {
    @Inject constructor(read: GenerateVehicleReportUseCase, documents: DocumentRepository,
        session: WorkspaceSession, access: OwnedDatabaseAccess, @ApplicationContext context: Context,
        settings: com.carmanager.app.core.domain.repository.SettingsRepository) :
        this({ read(it) }, documents, session, object : ReportStorage {
            override fun create(data: GenerateVehicleReportUseCase.ReportData, date: Long) = PdfReportHelper.generate(
                context, data.vehicle, data.fuelRecords, data.maintenanceRecords, date, data.mileageRecords, data.sections, data.presentation)
            override fun delete(path: String) = FileStorageHelper.deleteFile(path)
        }, { owner, vehicle, action -> access.write(owner, vehicle, action) }, System::currentTimeMillis, {
            kotlinx.coroutines.flow.combine(settings.distanceUnit, settings.currency) { distance, currency ->
                com.carmanager.app.core.util.ReportPresentationSettings(distance, currency)
            }.first()
        })

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
        val snapshot = readReport(vehicleId) ?: error("Véhicule indisponible.")
        val settings = presentation()
        val data = snapshot.copy(sections = selected, presentation = settings)
        session.requireWritable(owner)
        check(data.vehicle.id == vehicleId && data.vehicle.ownerKey == owner &&
            data.fuelRecords.all { it.ownerKey == owner && it.vehicleId == vehicleId } &&
            data.maintenanceRecords.all { it.ownerKey == owner && it.vehicleId == vehicleId } &&
            data.mileageRecords.all { it.ownerKey == owner && it.vehicleId == vehicleId })
        val date = now()
        return com.carmanager.app.core.util.StagedDocumentFile.store(
            prepare = { storage.create(data, date) },
            insert = { path -> transaction(owner, vehicleId) {
                session.requireWritable(owner)
                val title = "Rapport du " + SimpleDateFormat("dd/MM/yyyy 'à' HH:mm:ss", Locale.FRANCE).format(Date(date))
                val document = Document(vehicleId = vehicleId, title = title, category = DocumentCategory.REPORTS,
                    filePath = path, date = date, ownerKey = owner)
                val id = documents.saveDocument(document)
                check(id > 0) { "Rapport non enregistré." }
                document.copy(id = id)
            } },
            remove = storage::delete
        )
    }
}
