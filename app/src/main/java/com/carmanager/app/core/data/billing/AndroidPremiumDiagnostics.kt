package com.carmanager.app.core.data.billing

import android.util.Log
import com.carmanager.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPremiumDiagnostics @Inject constructor() : PremiumDiagnostics {
    override fun record(event: PremiumDiagnostic, outcome: BillingOutcome?) {
        if (BuildConfig.DEBUG) {
            Log.d("Premium", "premium: event=${event.name}" + (outcome?.let { " outcome=${it.name}" } ?: ""))
        }
    }
}
