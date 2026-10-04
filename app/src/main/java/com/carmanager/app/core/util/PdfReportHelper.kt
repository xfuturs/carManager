package com.carmanager.app.core.util

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.util.Log
import androidx.core.content.FileProvider
import com.carmanager.app.core.domain.model.FuelRecord
import com.carmanager.app.core.domain.model.MaintenanceRecord
import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.domain.model.MileageRecord
import com.carmanager.app.core.domain.model.ReportSection
import java.io.File

/** Même rendu A13 ; génération privée et actions explicites séparées. */
object PdfReportHelper {
    fun generate(context: Context, vehicle: Vehicle, fuelRecords: List<FuelRecord>,
        maintenanceRecords: List<MaintenanceRecord>, date: Long,
        mileageRecords: List<MileageRecord> = emptyList(), sections: Set<ReportSection> = ReportSection.entries.toSet()): String {
        require(sections.isNotEmpty())
        val pdf = PdfDocument()
        try {
            PdfReportRenderer(context).render(pdf, vehicle, maintenanceRecords, fuelRecords, mileageRecords, sections, date)
            return FileStorageHelper.saveGeneratedPdf(context,
                ReportFileIO.filename("${vehicle.brand}_${vehicle.model}", date)) { pdf.writeTo(it) }
        } catch (error: Exception) {
            Log.e("PdfReportHelper", "Erreur lors de la génération du PDF", error)
            throw error
        } finally { pdf.close() }
    }
    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = ReportFileIO.MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Partager le carnet d'entretien").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun open(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, ReportFileIO.MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Ouvrir avec").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
