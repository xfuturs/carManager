package com.carmanager.app.core.data.repository

import com.carmanager.app.core.domain.repository.AuthRepository
import com.carmanager.app.core.domain.repository.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val session: com.carmanager.app.core.domain.session.AuthSession,
    private val sync: com.carmanager.app.core.domain.repository.SyncRepository,
    private val deletion: com.carmanager.app.core.domain.session.AccountDeletion,
    @ApplicationContext private val context: Context
) : AuthRepository {
    private val transitionMutex = kotlinx.coroutines.sync.Mutex()

    private val auth = FirebaseAuth.getInstance()
    private val _currentUser = MutableStateFlow(auth.currentUser?.let { User(id = it.uid, email = it.email) })
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        session.setUid(auth.currentUser?.uid)
        auth.addAuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            session.setUid(firebaseUser?.uid)
            _currentUser.value = firebaseUser?.let { 
                User(id = it.uid, email = it.email) 
            }
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> {
        return transitionMutex.withLock { try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        } }
    }

    override suspend fun signOut() = transitionMutex.withLock {
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            sync.stopSync()
            auth.signOut()
            session.setUid(null)
        }
    }

    override suspend fun deleteAccount(): Result<Unit> = transitionMutex.withLock {
        val uid = auth.currentUser?.uid ?: return@withLock Result.failure(IllegalStateException("Non connecté"))
        deletion.delete(com.carmanager.app.core.domain.session.WorkspaceOwner.fromUid(uid))
    }
}
