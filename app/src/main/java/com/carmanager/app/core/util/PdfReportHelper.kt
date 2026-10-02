package com.carmanager.app.core.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.carmanager.app.core.domain.model.FuelRecord
import com.carmanager.app.core.domain.model.MaintenanceRecord
import com.carmanager.app.core.domain.model.Vehicle
import java.io.File
import java.io.FileOutputStream
import java.util.Date

/**
 * Utilitaire pour générer un carnet d'entretien PDF professionnel.
 */
object PdfReportHelper {

    fun generateAndShare(
        context: Context,
        vehicle: Vehicle,
        fuelRecords: List<FuelRecord>,
        maintenanceRecords: List<MaintenanceRecord>
    ) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        // 1. En-tête
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 24f
        paint.color = Color.BLACK
        canvas.drawText("Carnet d'Entretien Numérique", 50f, 50f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 14f
        paint.color = Color.GRAY
        canvas.drawText("Généré le ${DateFormatter.formatMedium(System.currentTimeMillis())}", 50f, 75f, paint)

        // Ligne de séparation
        paint.color = Color.BLACK
        paint.strokeWidth = 2f
        canvas.drawLine(50f, 100f, 545f, 100f, paint)

        // 2. Infos Véhicule
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("${vehicle.brand} ${vehicle.model}", 50f, 140f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 12f
        canvas.drawText("Plaque : ${vehicle.licensePlate ?: "N/A"}", 50f, 165f, paint)
        canvas.drawText("Année : ${vehicle.year}", 50f, 185f, paint)
        canvas.drawText("Kilométrage actuel : ${vehicle.currentMileage} km", 50f, 205f, paint)

        // 3. Résumé Maintenance
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Historique des interventions", 50f, 250f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10f
        var yPos = 280f
        
        maintenanceRecords.take(15).forEach { record ->
            val dateStr = DateFormatter.formatShort(record.date)
            canvas.drawText("$dateStr | ${record.type} | ${record.mileage} km | ${record.cost} €", 50f, yPos, paint)
            yPos += 20f
            if (yPos > 800f) return@forEach // Simple protection débordement pour v1
        }

        // 4. Pied de page
        paint.color = Color.GRAY
        paint.textSize = 10f
        canvas.drawText("Document généré par Car Manager - Application XFuturs", 50f, 820f, paint)

        pdfDocument.finishPage(page)

        // Sauvegarde et Partage
        try {
            val fileName = "Entretien_${vehicle.brand}_${vehicle.model}.pdf"
            val file = File(context.cacheDir, fileName)
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Partager le carnet d'entretien"))
        } catch (e: Exception) {
            Log.e("PdfReportHelper", "Erreur lors de la génération ou du partage du PDF", e)
            Toast.makeText(context, "Erreur lors de la génération du PDF", Toast.LENGTH_SHORT).show()
        }
    }
}
