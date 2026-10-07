package com.carmanager.app.core.util

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.util.UUID

object FileStorageHelper {
    private const val DOCUMENTS_DIR = "vehicle_documents"
    internal fun saveGeneratedPdf(context: Context, filename: String, render: (java.io.OutputStream) -> Unit): String =
        ReportFileIO.create(File(context.filesDir, DOCUMENTS_DIR), filename, render)

    /** Frontière IO du ViewModel, jamais une transaction Room. */
    fun saveFileToInternalStorage(context: Context, uri: Uri, requireActive: () -> Unit = {}): String {
        requireActive()
        val resolver = context.contentResolver
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(resolver.getType(uri)) ?: "file"
        return PrivateFileIO.copyNew(File(context.filesDir, DOCUMENTS_DIR), "doc_${UUID.randomUUID()}.$extension",
            { resolver.openInputStream(uri) }, requireActive)
    }

    fun convertImageToPdf(context: Context, imagePath: String, requireActive: () -> Unit = {}): String {
        requireActive()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imagePath, bounds)
        val plan = ImageDecodePlan.from(bounds.outWidth, bounds.outHeight)
        requireActive()
        val options = BitmapFactory.Options().apply { inSampleSize = plan.sampleSize }
        val bitmap = BitmapFactory.decodeFile(imagePath, options) ?: error("Image illisible.")
        try {
            check(maxOf(bitmap.width, bitmap.height) <= ImageDecodePlan.MAX_LONG_EDGE) { "Image trop volumineuse." }
            requireActive()
            val pdf = PdfDocument()
            try {
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create())
                try { page.canvas.drawBitmap(bitmap, 0f, 0f, null) } finally { pdf.finishPage(page) }
                requireActive()
                return PrivateFileIO.create(File(context.filesDir, DOCUMENTS_DIR), "doc_${UUID.randomUUID()}.pdf") {
                    requireActive()
                    pdf.writeTo(it)
                    requireActive()
                }
            } finally { pdf.close() }
        } finally { bitmap.recycle() }
    }

    fun deleteFile(path: String): Boolean = try {
        val file = File(path)
        !file.exists() || file.delete()
    } catch (_: Exception) { false }
}
