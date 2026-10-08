package com.carmanager.app.core.ui.components

import android.util.Log
import android.view.View
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
fun BannerAd(modifier: Modifier = Modifier, visible: Boolean = true) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val orientation = LocalConfiguration.current.orientation
    val adUnitId = AdsConfig.bannerAdUnitId
    val owner = LocalLifecycleOwner.current
    val currentVisible by rememberUpdatedState(visible)
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (!constraints.hasBoundedWidth || maxWidth.value.toInt() <= 0) return@BoxWithConstraints
        val width = maxWidth.value.toInt()
        key(context, adUnitId, width, orientation) {
            val size = remember(context, width, orientation) {
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, width)
            }
            val height = with(density) { size.getHeightInPixels(context).coerceAtLeast(0).toDp() }
            val lifecycle = remember { BannerRequestLifecycle() }
            val state by lifecycle.state.collectAsState()
            var ownedView by remember { mutableStateOf<AdView?>(null) }
            fun release(view: AdView) {
                lifecycle.dispose {
                    view.adListener = object : AdListener() {}
                    view.destroy()
                }
            }
            fun syncActivity(view: AdView) {
                if (lifecycle.state.value == BannerRequestState.DISPOSED) return
                val resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                // Recontrôle live : ne pas laisser le SDK actif si UMP révoque avant le parent.
                val consent = UserMessagingPlatform.getConsentInformation(view.context).canRequestAds()
                val show = currentVisible && consent
                view.visibility = if (show) View.VISIBLE else View.GONE
                lifecycle.updateActivity(show, resumed, view::pause, view::resume)
                if (show && resumed) {
                    // requestOnce empêche tout reload de navigation ou de recomposition.
                    try { lifecycle.requestOnce { view.loadAd(AdRequest.Builder().build()) } }
                    catch (_: Exception) { lifecycle.failed(); Log.w("Ads", "banner: failure") }
                } else if (!consent) {
                    lifecycle.failed()
                }
            }
            DisposableEffect(owner, ownedView) {
                val view = ownedView
                val observer = LifecycleEventObserver { _, event ->
                    if (view != null) {
                        if (event == Lifecycle.Event.ON_DESTROY) release(view) else syncActivity(view)
                    }
                }
                owner.lifecycle.addObserver(observer)
                if (view != null) syncActivity(view)
                onDispose { owner.lifecycle.removeObserver(observer) }
            }
            AndroidView(
                // Reserver la hauteur SDK pendant le chargement ; replier le slot en cas d'echec.
                modifier = Modifier.fillMaxWidth().height(if (!visible || state == BannerRequestState.FAILED || state == BannerRequestState.DISPOSED) 0.dp else height),
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
                        ownedView = this
                    }
                },
                onReset = null,
                onRelease = { adView ->
                    release(adView)
                    if (ownedView === adView) ownedView = null
                },
                update = { syncActivity(it) }
            )
        }
    }
}
