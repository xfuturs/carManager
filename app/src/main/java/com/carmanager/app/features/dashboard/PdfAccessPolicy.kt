package com.carmanager.app.features.dashboard

/** Accès à la génération PDF selon la propriété Premium réelle. */
internal enum class PdfAccess { LOCKED, PREMIUM }

internal object PdfAccessPolicy {
    fun resolve(isPremium: Boolean): PdfAccess = if (isPremium) PdfAccess.PREMIUM else PdfAccess.LOCKED
}
