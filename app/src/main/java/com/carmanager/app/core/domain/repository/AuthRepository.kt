package com.carmanager.app.core.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface pour la gestion de l'authentification Firebase.
 */
interface AuthRepository {
    /**
     * Flux indiquant si l'utilisateur est actuellement connecté.
     */
    val currentUser: StateFlow<User?>

    /**
     * Connecte l'utilisateur avec Email/Mot de passe.
     */
    suspend fun signIn(email: String, password: String): Result<Unit>

    /**
     * Crée un nouveau compte utilisateur.
     */
    suspend fun signUp(email: String, password: String): Result<Unit>

    /**
     * Déconnecte l'utilisateur actuel.
     */
    suspend fun signOut()

    /**
     * Connecte l'utilisateur avec un identifiant Google.
     */
    suspend fun signInWithGoogle(idToken: String): Result<Unit>

    /**
     * Supprime définitivement le compte de l'utilisateur et ses données.
     */
    suspend fun deleteAccount(): Result<Unit>
}

data class User(
    val id: String,
    val email: String?
)
