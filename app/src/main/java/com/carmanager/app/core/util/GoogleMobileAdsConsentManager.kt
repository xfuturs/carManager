package com.carmanager.app.core.util

import android.app.Activity
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.carmanager.app.core.ads.AdsConsentState
import com.carmanager.app.core.ads.AdsPrivacyOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestionnaire du consentement pour Google Mobile Ads (RGPD).
 */
class GoogleMobileAdsConsentManager(private val activity: Activity) {
    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(activity)
    private val _state = MutableStateFlow(AdsConsentState())
    val state = _state.asStateFlow()

    fun refreshState() {
        _state.value = AdsConsentState(consentInformation.canRequestAds(), when (consentInformation.privacyOptionsRequirementStatus) {
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED -> AdsPrivacyOptions.REQUIRED
            ConsentInformation.PrivacyOptionsRequirementStatus.NOT_REQUIRED -> AdsPrivacyOptions.NOT_REQUIRED
            else -> AdsPrivacyOptions.UNKNOWN
        })
    }

    /**
     * Interface pour notifier quand le processus de consentement est terminé.
     */
    fun interface OnConsentCheckCompleteListener {
        fun onConsentCheckComplete(error: Exception?)
    }

    /**
     * Vérifie si l'on peut demander des publicités.
     */
    fun canRequestAds(): Boolean = consentInformation.canRequestAds()

    /**
     * Vérifie si le formulaire de confidentialité est requis.
     */
    fun isPrivacyOptionsRequired(): Boolean =
        consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /**
     * Lance la demande de consentement.
     */
    fun gatherConsent(
        onConsentCheckCompleteListener: OnConsentCheckCompleteListener
    ) {
        val params = ConsentRequestParameters.Builder().build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                refreshState()
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    refreshState()
                    onConsentCheckCompleteListener.onConsentCheckComplete(formError?.let { Exception(it.message) })
                }
            },
            { requestError ->
                refreshState()
                onConsentCheckCompleteListener.onConsentCheckComplete(Exception(requestError.message))
            }
        )
        // Lecture SDK apres requestConsentInfoUpdate, y compris un etat valide de session precedente.
        refreshState()
    }

    /**
     * Affiche le formulaire de changement de préférences de confidentialité.
     */
    fun showPrivacyOptionsForm(
        onConsentFormDismissedListener: ConsentForm.OnConsentFormDismissedListener
    ) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            refreshState()
            onConsentFormDismissedListener.onConsentFormDismissed(error)
        }
    }
}
