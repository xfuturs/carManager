package com.carmanager.app.core.util

import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Uniquement les fichiers créés par l'opération ; aucun balayage du stockage existant. */
internal object PrivateFileIO {
    fun canonical(directory: File, path: String): File = File(path).canonicalFile.also {
        check(it.parentFile == directory.canonicalFile) { "Chemin hors stockage privé." }
    }
    fun create(directory: File, filename: String, write: (OutputStream) -> Unit): String {
        check(directory.isDirectory || directory.mkdirs()) { "Stockage privé indisponible." }
        val file = canonical(directory, File(directory, filename).path)
        check(file.createNewFile()) { "Un fichier porte déjà ce nom." }
        try {
            file.outputStream().use(write)
            check(file.length() > 0) { "Le fichier reçu est vide." }
            return file.absolutePath
        } catch (error: Throwable) {
            try {
                if (file.exists() && !file.delete()) error.addSuppressed(IllegalStateException("Fichier incomplet non supprimé."))
            } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
            throw error
        }
    }
    fun copyNew(directory: File, filename: String, open: () -> InputStream?, requireActive: () -> Unit = {}): String =
        create(directory, filename) { output ->
            requireActive()
            (open() ?: error("Le fournisseur ne permet pas de lire ce fichier.")).use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    requireActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                requireActive()
            }
        }
}
