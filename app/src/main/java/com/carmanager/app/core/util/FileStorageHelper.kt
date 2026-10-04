package com.carmanager.app.core.util

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object FileStorageHelper {
    private const val DOCUMENTS_DIR = "vehicle_documents"

    internal fun saveGeneratedPdf(context: Context, filename: String, render: (java.io.OutputStream) -> Unit): String =
        ReportFileIO.create(File(context.filesDir, DOCUMENTS_DIR), filename, render)

    fun saveFileToInternalStorage(context: Context, uri: Uri): String? {
        return try {
            val contentResolver = context.contentResolver
            
            // Récupérer l'extension du fichier original
            val mimeTypeMap = MimeTypeMap.getSingleton()
            val extension = mimeTypeMap.getExtensionFromMimeType(contentResolver.getType(uri)) ?: "file"
            
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            
            val dir = File(context.filesDir, DOCUMENTS_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val fileName = "doc_${UUID.randomUUID()}.$extension"
            val file = File(dir, fileName)
            
            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun convertImageToPdf(context: Context, imagePath: String): String? {
        return try {
            val bitmap = BitmapFactory.decodeFile(imagePath) ?: return null
            
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            
            val canvas = page.canvas
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdfDocument.finishPage(page)

            val dir = File(context.filesDir, DOCUMENTS_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val fileName = "doc_${UUID.randomUUID()}.pdf"
            val file = File(dir, fileName)
            
            FileOutputStream(file).use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
            
            pdfDocument.close()
            bitmap.recycle()
            
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteFile(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
