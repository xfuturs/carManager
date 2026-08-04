package com.carmanager.app.core.util

import android.app.Activity
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Gestionnaire du consentement pour Google Mobile Ads (RGPD).
 */
class GoogleMobileAdsConsentManager(private val activity: Activity) {
    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(activity)

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
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    onConsentCheckCompleteListener.onConsentCheckComplete(formError?.let { Exception(it.message) })
                }
            },
            { requestError ->
                onConsentCheckCompleteListener.onConsentCheckComplete(Exception(requestError.message))
            }
        )
    }

    /**
     * Affiche le formulaire de changement de préférences de confidentialité.
     */
    fun showPrivacyOptionsForm(
        onConsentFormDismissedListener: ConsentForm.OnConsentFormDismissedListener
    ) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, onConsentFormDismissedListener)
    }
}
