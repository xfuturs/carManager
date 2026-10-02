package com.carmanager.app.core.ui.components

import android.util.Log
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.carmanager.app.core.ads.AdsConfig
import com.carmanager.app.core.ads.BannerRequestLifecycle
import com.carmanager.app.core.ads.BannerRequestState
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.ump.UserMessagingPlatform

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val orientation = LocalConfiguration.current.orientation
    val adUnitId = AdsConfig.bannerAdUnitId
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (!constraints.hasBoundedWidth || maxWidth.value.toInt() <= 0) return@BoxWithConstraints
        val width = maxWidth.value.toInt()
        key(adUnitId, width, orientation) {
            val size = remember(context, width, orientation) {
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, width)
            }
            val height = with(density) { size.getHeightInPixels(context).coerceAtLeast(0).toDp() }
            val lifecycle = remember { BannerRequestLifecycle() }
            val state by lifecycle.state.collectAsState()
            AndroidView(
                // Reserver la hauteur SDK pendant le chargement ; replier le slot en cas d'echec.
                modifier = Modifier.fillMaxWidth().height(if (state == BannerRequestState.FAILED || state == BannerRequestState.DISPOSED) 0.dp else height),
                factory = { hostContext ->
                    AdView(hostContext).apply {
                        setAdSize(size)
                        setAdUnitId(adUnitId)
                        adListener = object : AdListener() {
                            override fun onAdLoaded() { lifecycle.loaded() }
                            override fun onAdFailedToLoad(error: LoadAdError) {
                                lifecycle.failed()
                                Log.w("Ads", "banner: code=${error.code}")
                            }
                        }
                        // Derniere lecture UMP avant la requete, sans ciblage des donnees du garage.
                        if (UserMessagingPlatform.getConsentInformation(hostContext).canRequestAds()) {
                            try { lifecycle.requestOnce { loadAd(AdRequest.Builder().build()) } }
                            catch (_: Exception) {
                                lifecycle.failed()
                                Log.w("Ads", "banner: failure")
                            }
                        } else lifecycle.failed()
                    }
                },
                onReset = null,
                onRelease = { adView ->
                    lifecycle.dispose {
                        adView.adListener = object : AdListener() {}
                        adView.destroy()
                    }
                },
                update = { /* Aucun load a la recomposition. */ }
            )
        }
    }
}
