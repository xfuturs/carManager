package com.carmanager.app.core.data.local

import android.content.Context
import androidx.room.withTransaction
import com.carmanager.app.core.domain.session.LocalAccountData
import com.carmanager.app.core.domain.session.WorkspaceOwner
import com.carmanager.app.core.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class LocalWorkspacePurger @Inject constructor(
    private val database: CarManagerDatabase,
    @ApplicationContext private val context: Context
) : LocalAccountData {
    override suspend fun purge(owner: String) = withContext(Dispatchers.IO) {
        check(WorkspaceOwner.uid(owner) != null) { "La purge de compte ne peut pas viser l'espace invité." }
        val documents = database.documentDao().getAll(owner)
        val reminders = database.maintenanceDao().getAll(owner)
        val directory = File(context.filesDir, "vehicle_documents").canonicalFile
        val protectedFiles = database.documentDao().filePathsOutsideOwner(owner).map { File(it).canonicalFile }.toSet()
        // Vérification de tous les chemins avant la première suppression physique.
        val files = documents.map { document ->
            val file = File(document.filePath).canonicalFile
            check(file.parentFile == directory) { "Chemin de document hors stockage privé : suppression refusée." }
            check(file !in protectedFiles) { "Fichier partagé avec un autre espace : suppression refusée." }
            file
        }.distinct()
        files.forEach { file -> check(!file.exists() || file.delete()) { "Impossible de supprimer le document ${file.name}." } }
        reminders.forEach { NotificationHelper.cancelOwnedReminder(context, owner, it.id) }
        // Les FK CASCADE suppriment les cinq catégories de données utilisateur, pas le catalogue.
        database.withTransaction { database.vehicleDao().deleteOwner(owner) }
    }
}
