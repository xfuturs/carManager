package com.carmanager.app.core.util

/** Capturé une fois hors transaction Room ; aucun Flow observé par le renderer. */
data class ReportPresentationSettings(val distanceUnit: String = "km", val currencySymbol: String = "€") {
    // Le chemin rapide a historiquement écrit des valeurs brutes km/mi sans métadonnée.
    // Même si la préférence est mi, aucune conversion devinée dans un nouveau PDF.
    fun mileage(value: Int): String = DistancePresentation.recorded(value, "km")
}
