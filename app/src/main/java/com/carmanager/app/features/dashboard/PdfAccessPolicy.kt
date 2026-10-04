package com.carmanager.app.features.dashboard

/** Accès à l'action PDF uniquement ; ne représente jamais un entitlement Premium. */
internal enum class PdfAccess { LOCKED, PREMIUM, DEBUG_TEST }

internal object PdfAccessPolicy {
    fun resolve(isDebugBuild: Boolean, isPremium: Boolean): PdfAccess = when {
        isPremium -> PdfAccess.PREMIUM
        isDebugBuild -> PdfAccess.DEBUG_TEST
        else -> PdfAccess.LOCKED
    }
}
