package com.carmanager.app.core.data.local

import androidx.room.withTransaction
import com.carmanager.app.core.domain.session.WorkspaceSession
import javax.inject.Inject
import java.io.File

/** Tous les changements utilisateur sont contrôlés avant ET après l'opération transactionnelle. */
class OwnedDatabaseAccess @Inject constructor(
    private val database: CarManagerDatabase,
    private val session: WorkspaceSession
) {
    suspend fun documentFile(owner: String, id: Long, expectedPath: String, directory: File): File {
        session.requireWritable(owner)
        val document = database.documentDao().getById(id, owner)
        check(document != null && document.filePath == expectedPath) { "Document absent de cet espace." }
        val file = File(expectedPath).canonicalFile
        check(file.parentFile == directory.canonicalFile) { "Chemin de document hors stockage privé." }
        check(database.documentDao().filePathsOutsideOwner(owner).none { File(it).canonicalFile == file }) {
            "Fichier partagé avec un autre espace : opération refusée."
        }
        return file
    }
    suspend fun <T> write(owner: String, vehicleId: Long? = null, action: suspend () -> T): T = database.withTransaction {
        session.requireWritable(owner)
        if (vehicleId != null) check(database.vehicleDao().getById(vehicleId, owner) != null) { "Véhicule absent de cet espace." }
        val result = action()
        session.requireWritable(owner)
        result
    }
}
