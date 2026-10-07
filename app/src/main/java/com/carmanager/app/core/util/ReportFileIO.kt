package com.carmanager.app.core.util

import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Fichiers et copie en flux ; aucune décision Premium ni donnée conservée en mémoire UI. */
internal object ReportFileIO {
    const val MIME = "application/pdf"
    fun filename(vehicleName: String, date: Long, unique: String = UUID.randomUUID().toString()): String {
        val safe = vehicleName.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64).ifBlank { "vehicule" }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.FRANCE).format(Date(date))
        val token = unique.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64)
        require(token.isNotBlank())
        return "Rapport_${safe}_${stamp}_$token.pdf"
    }
    fun create(directory: File, filename: String, render: (OutputStream) -> Unit): String {
        return PrivateFileIO.create(directory, filename, render)
    }
    /** null = annulation normale. Une erreur de destination ne modifie jamais la source. */
    fun copy(source: File, destination: (() -> OutputStream?)?, requireCurrent: () -> Unit = {}): Boolean {
        if (destination == null) return false
        requireCurrent()
        check(source.isFile) { "Rapport introuvable sur cet appareil." }
        source.inputStream().use { input ->
            val output = destination() ?: error("Destination indisponible.")
            output.use {
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    requireCurrent()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                requireCurrent()
                output.flush()
            }
        }
        return true
    }
}
