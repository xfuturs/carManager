package com.carmanager.app.core.ui

import androidx.compose.runtime.Composable
import com.carmanager.app.core.ui.navigation.CarManagerNavHost

@Composable
fun CarManagerApp(
    canShowAds: Boolean,
    isPrivacyOptionsRequired: Boolean,
    onPrivacyOptionsClick: () -> Unit
) {
    CarManagerNavHost(
        canShowAds = canShowAds,
        isPrivacyOptionsRequired = isPrivacyOptionsRequired,
        onPrivacyOptionsClick = onPrivacyOptionsClick
    )
}
