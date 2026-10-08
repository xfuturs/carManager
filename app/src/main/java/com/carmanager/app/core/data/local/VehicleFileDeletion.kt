package com.carmanager.app.core.data.local

import android.content.Context
import com.carmanager.app.core.data.mapper.toEntity
import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.core.util.PrivateFileIO
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Le véhicule est déjà supprimé : l'UI doit afficher un avertissement, pas proposer un nouveau DELETE. */
class VehicleCleanupPendingException : IllegalStateException("Véhicule supprimé. Nettoyage de fichiers en attente ; il sera réessayé au prochain redémarrage de l'application, à l'ouverture du garage.")

internal data class IndexedVehicleFile(val id: Long, val path: String)
internal data class VehicleCleanupEntry(val token: String, val owner: String, val vehicleId: Long, val paths: List<String>)
internal interface VehicleCleanupJournal {
    fun entries(): List<VehicleCleanupEntry>
    fun save(entries: List<VehicleCleanupEntry>)
}

@Singleton
class VehicleFileDeletion internal constructor(
    private val session: WorkspaceSession,
    private val directory: File,
    private val journal: VehicleCleanupJournal,
    private val capture: suspend (Vehicle) -> List<IndexedVehicleFile>,
    private val references: suspend (Long?) -> List<String>,
    private val exists: suspend (String, Long) -> Boolean,
    private val deleteRow: suspend (Vehicle, List<IndexedVehicleFile>) -> Unit,
    private val remove: (File) -> Boolean = { !it.exists() || it.delete() },
    startReplay: Boolean = false
) {
    @Inject constructor(database: CarManagerDatabase, session: WorkspaceSession, access: OwnedDatabaseAccess,
        @ApplicationContext context: Context) : this(session, File(context.filesDir, "vehicle_documents"),
        PreferencesVehicleCleanupJournal(context),
        { vehicle -> access.read(vehicle.ownerKey, vehicle.id) {
            database.documentDao().getByVehicle(vehicle.id, vehicle.ownerKey).map { IndexedVehicleFile(it.id, it.filePath) }
        } },
        { vehicle -> if (vehicle == null) database.documentDao().allFilePaths() else database.documentDao().filePathsOutsideVehicle(vehicle) },
        // Les IDs sont globaux : un owner legacy journalisé ne doit jamais rendre un véhicule vivant absent.
        { _, id -> database.vehicleDao().existsById(id) },
        { vehicle, captured -> access.write(vehicle.ownerKey, vehicle.id) {
            val current = database.documentDao().getByVehicle(vehicle.id, vehicle.ownerKey).map { IndexedVehicleFile(it.id, it.filePath) }
            check(current.toSet() == captured.toSet()) { "Les documents ont changé. Réessayez la suppression." }
            database.vehicleDao().delete(vehicle.toEntity())
        } }, startReplay = true)

    private val mutex = Mutex()
    init {
        if (startReplay) CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { retryPending() }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                android.util.Log.w("VehicleCleanup", "Nettoyage différé indisponible.")
            }
        }
    }

    suspend fun delete(vehicle: Vehicle) = withContext(Dispatchers.IO) {
        mutex.withLock {
            session.requireWritable(vehicle.ownerKey)
            val captured = capture(vehicle)
            val protected = references(vehicle.id).map { File(it).canonicalFile }.toSet()
            val paths = captured.map { PrivateFileIO.canonical(directory, it.path).also { file ->
                check(file !in protected) { "Fichier partagé avec un autre véhicule : suppression refusée." }
            }.path }.distinct()
            val entry = VehicleCleanupEntry(UUID.randomUUID().toString(), vehicle.ownerKey, vehicle.id, paths)
            journal.save(journal.entries() + entry) // Intention durable avant la cascade, sans supprimer de bytes.
            try {
                currentCoroutineContext().ensureActive()
                session.requireWritable(vehicle.ownerKey)
                deleteRow(vehicle, captured)
            } catch (error: Exception) {
                withContext(NonCancellable) { try { replay(entry) } catch (cleanup: Exception) { error.addSuppressed(cleanup) } }
                throw error
            }
            val cleaned = withContext(NonCancellable) { replay(entry) }
            if (!cleaned) throw VehicleCleanupPendingException()
        }
    }

    suspend fun retryPending() = withContext(Dispatchers.IO) {
        mutex.withLock { journal.entries().forEach { replay(it) } }
    }

    private suspend fun replay(entry: VehicleCleanupEntry): Boolean {
        // Avant commit/rollback/process death : un véhicule existant interdit toute suppression physique.
        return try {
            if (exists(entry.owner, entry.vehicleId)) {
                journal.save(journal.entries().filterNot { it.token == entry.token })
                return true
            }
            val protected = references(null).map { File(it).canonicalFile }.toSet()
            val files = entry.paths.map { PrivateFileIO.canonical(directory, it) }
            var complete = true
            files.forEach { file -> if (file in protected || !remove(file)) complete = false }
            if (complete) journal.save(journal.entries().filterNot { it.token == entry.token })
            complete
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            false // L'intention reste persistée ; aucun DELETE métier n'est relancé.
        }
    }
}

internal object VehicleCleanupCodec {
    private fun encode(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun decode(value: String) = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    fun decodeEntry(encoded: String): VehicleCleanupEntry {
        val parts = encoded.split(':')
        check(parts.size >= 3)
        return VehicleCleanupEntry(parts[0], decode(parts[1]), parts[2].toLong(), parts.drop(3).map(::decode)).also {
            check(it.token.isNotBlank() && it.owner.isNotBlank() && it.vehicleId > 0)
        }
    }
    fun encodeEntry(entry: VehicleCleanupEntry) =
        (listOf(entry.token, encode(entry.owner), entry.vehicleId.toString()) + entry.paths.map(::encode)).joinToString(":")
}

private class PreferencesVehicleCleanupJournal(context: Context) : VehicleCleanupJournal {
    private val preferences = context.getSharedPreferences("vehicle_file_cleanup", Context.MODE_PRIVATE)
    override fun entries() = preferences.getStringSet("pending", emptySet()).orEmpty().map(VehicleCleanupCodec::decodeEntry)
    override fun save(entries: List<VehicleCleanupEntry>) {
        val values = entries.map(VehicleCleanupCodec::encodeEntry).toSet()
        check(preferences.edit().putStringSet("pending", values).commit()) { "Nettoyage différé non enregistré." }
    }
}
