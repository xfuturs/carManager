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
    suspend fun documentFile(owner: String, id: Long, expectedPath: String, directory: File, vehicleId: Long? = null): File = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        session.requireWritable(owner)
        val document = database.documentDao().getById(id, owner)
        check(document != null && document.filePath == expectedPath) { "Document absent de cet espace." }
        check(vehicleId == null || document.vehicleId == vehicleId) { "Document absent de ce véhicule." }
        val file = File(expectedPath).canonicalFile
        check(file.parentFile == directory.canonicalFile) { "Chemin de document hors stockage privé." }
        check(database.documentDao().filePathsOutsideOwner(owner).none { File(it).canonicalFile == file }) {
            "Fichier partagé avec un autre espace : opération refusée."
        }
        session.requireWritable(owner)
        file
    }
    /** Une seule frontière SQL pour les données possédées, sans copie/rendu dans action. */
    suspend fun <T> read(owner: String, vehicleId: Long, action: suspend () -> T): T = write(owner, vehicleId, action)
    suspend fun <T> write(owner: String, vehicleId: Long? = null, action: suspend () -> T): T = database.withTransaction {
        session.requireWritable(owner)
        if (vehicleId != null) check(database.vehicleDao().getById(vehicleId, owner) != null) { "Véhicule absent de cet espace." }
        val result = action()
        session.requireWritable(owner)
        result
    }
}
