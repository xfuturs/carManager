package com.carmanager.app.core.ui

import androidx.compose.runtime.Composable
import androidx.activity.ComponentActivity
import com.carmanager.app.core.ui.navigation.CarManagerNavHost
import com.carmanager.app.core.ads.InterstitialAdManager

@Composable
fun CarManagerApp(
    activity: ComponentActivity,
    interstitials: InterstitialAdManager,
    canShowAds: Boolean,
    isPrivacyOptionsRequired: Boolean,
    onPrivacyOptionsClick: () -> Unit
) {
    CarManagerNavHost(
        activity = activity,
        interstitials = interstitials,
        canShowAds = canShowAds,
        isPrivacyOptionsRequired = isPrivacyOptionsRequired,
        onPrivacyOptionsClick = onPrivacyOptionsClick
    )
}
