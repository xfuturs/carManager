package com.carmanager.app.core.ads

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MobileAdsInitializer @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val gate = AdsInitializationGate()
    val ready = gate.ready

    fun initializeIfEligible(canRequestAds: Boolean, isPremium: Boolean) {
        try {
            gate.initializeIfEligible(canRequestAds, isPremium) { complete ->
                MobileAds.initialize(context.applicationContext) { complete() }
            }
        } catch (_: Exception) {
            // Une initialisation impossible reste sans publicite, sans boucle de retry.
            Log.w("Ads", "initialization: failure")
        }
    }
}
