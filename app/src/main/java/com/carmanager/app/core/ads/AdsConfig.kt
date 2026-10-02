package com.carmanager.app.core.ads

import com.carmanager.app.BuildConfig

/** Identifiant genere par variante, sans fallback dans le code de l'application. */
object AdsConfig {
    val bannerAdUnitId: String get() = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
}
