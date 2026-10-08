package com.carmanager.app.core.data.repository

import com.carmanager.app.core.domain.session.AccountRemoteData
import com.carmanager.app.core.domain.session.RemoteCleanupDiagnostic
import com.carmanager.app.core.domain.session.RemoteCleanupFailure
import com.carmanager.app.core.domain.session.RemoteCleanupFamily
import com.carmanager.app.core.domain.session.RemoteCleanupOperation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAccountData internal constructor(private val reportFailure: (RemoteCleanupDiagnostic) -> Unit) : AccountRemoteData {
    @Inject constructor() : this({ android.util.Log.w("AccountCleanup", it.safeDescription) })
    private val auth get() = FirebaseAuth.getInstance()
    private val firestore get() = FirebaseFirestore.getInstance()
    override fun requireUid(uid: String) {
        check(auth.currentUser?.uid == uid) { "Le compte actif a changé." }
    }
    override suspend fun drainWrites(uid: String) = withTimeout(30_000) {
        requireUid(uid)
        cleanup(RemoteCleanupFamily.PENDING_WRITES, RemoteCleanupOperation.DRAIN_WRITES) {
            firestore.waitForPendingWrites().await()
        }
        requireUid(uid)
    }
    override suspend fun deleteKnownData(uid: String) = withTimeout(120_000) {
        val root = firestore.collection("users").document(uid)
        for ((name, family) in listOf("vehicles" to RemoteCleanupFamily.LEGACY_VEHICLES,
            "fuel_records" to RemoteCleanupFamily.LEGACY_FUEL,
            "maintenance_records" to RemoteCleanupFamily.LEGACY_MAINTENANCE)) {
            // Pages de 400 : sous la limite de 500 opérations/batch, sans plafond de collection.
            while (true) {
                requireUid(uid)
                val page = cleanup(family, RemoteCleanupOperation.QUERY) {
                    root.collection(name).limit(400).get(com.google.firebase.firestore.Source.SERVER).await()
                }
                if (page.isEmpty) break
                requireUid(uid)
                val batch = firestore.batch()
                page.documents.forEach { batch.delete(it.reference) }
                cleanup(family, RemoteCleanupOperation.BATCH_DELETE) { batch.commit().await() }
            }
        }
        requireUid(uid)
        cleanup(RemoteCleanupFamily.USER_DOCUMENT, RemoteCleanupOperation.DELETE_DOCUMENT) { root.delete().await() }
        requireUid(uid)
    }
    private suspend fun <T> cleanup(family: RemoteCleanupFamily, operation: RemoteCleanupOperation,
        action: suspend () -> T): T = try { action() } catch (error: Exception) {
        if (error is CancellationException) throw error
        val code = (error as? FirebaseFirestoreException)?.code?.name ?: "NON_FIRESTORE"
        val diagnostic = RemoteCleanupDiagnostic(family, operation, code)
        // Un logger indisponible ne doit jamais masquer le refus d'origine.
        runCatching { reportFailure(diagnostic) }
        throw RemoteCleanupFailure(diagnostic, error)
    }
    override suspend fun deleteAuth(uid: String) = withTimeout(30_000) {
        requireUid(uid)
        try {
            checkNotNull(auth.currentUser).delete().await()
            Unit
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            throw IllegalStateException("Reconnectez-vous au même compte, puis réessayez la suppression. Les données déjà supprimées ne seront pas restaurées.", e)
        }
    }
}
