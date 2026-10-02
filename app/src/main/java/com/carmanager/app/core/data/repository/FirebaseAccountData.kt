package com.carmanager.app.core.data.repository

import com.carmanager.app.core.domain.session.AccountRemoteData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAccountData @Inject constructor() : AccountRemoteData {
    private val auth get() = FirebaseAuth.getInstance()
    private val firestore get() = FirebaseFirestore.getInstance()
    override fun requireUid(uid: String) {
        check(auth.currentUser?.uid == uid) { "Le compte actif a changé." }
    }
    override suspend fun drainWrites(uid: String) = withTimeout(30_000) {
        requireUid(uid)
        firestore.waitForPendingWrites().await()
        requireUid(uid)
    }
    override suspend fun deleteKnownData(uid: String) = withTimeout(120_000) {
        val root = firestore.collection("users").document(uid)
        for (name in listOf("vehicles", "fuel_records", "maintenance_records")) {
            // Pages de 400 : sous la limite de 500 opérations/batch, sans plafond de collection.
            while (true) {
                requireUid(uid)
                val page = root.collection(name).limit(400).get(com.google.firebase.firestore.Source.SERVER).await()
                if (page.isEmpty) break
                requireUid(uid)
                val batch = firestore.batch()
                page.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }
        requireUid(uid)
        root.delete().await()
        requireUid(uid)
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
