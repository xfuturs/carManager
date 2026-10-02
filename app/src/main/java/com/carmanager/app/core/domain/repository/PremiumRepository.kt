package com.carmanager.app.core.domain.repository

import android.app.Activity
import com.carmanager.app.core.domain.model.PremiumState
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface pour la gestion du statut Premium et des achats In-App.
 */
interface PremiumRepository {
    val state: StateFlow<PremiumState>
    /**
     * Flux indiquant si l'utilisateur possède actuellement le statut Premium.
     */
    val isPremium: StateFlow<Boolean>

    /**
     * Vérifie l'état actuel des achats auprès de Google Play.
     */
    fun checkPremiumStatus()

    /**
     * Initialise la connexion au service de facturation Google Play.
     */
    fun initialize()

    /** Activity utilisée pendant cet appel uniquement ; aucun achat sans clic explicite. */
    fun launchPurchase(activity: Activity)
}
