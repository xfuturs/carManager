package com.carmanager.app.core.ui

import androidx.compose.runtime.Composable
import androidx.activity.ComponentActivity
import com.carmanager.app.core.ui.navigation.CarManagerNavHost

@Composable
fun CarManagerApp(
    activity: ComponentActivity,
    onInterstitialOpportunity: () -> Unit,
    canShowAds: Boolean,
    isPrivacyOptionsRequired: Boolean,
    onPrivacyOptionsClick: () -> Unit
) {
    CarManagerNavHost(
        activity = activity,
        onInterstitialOpportunity = onInterstitialOpportunity,
        canShowAds = canShowAds,
        isPrivacyOptionsRequired = isPrivacyOptionsRequired,
        onPrivacyOptionsClick = onPrivacyOptionsClick
    )
}
