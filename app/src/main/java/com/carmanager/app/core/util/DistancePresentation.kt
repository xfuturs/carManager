package com.carmanager.app.core.util

import java.text.NumberFormat
import java.util.Locale

/** Les anciens relevés ne portent aucune unité : formatage seul, jamais de réinterprétation. */
object DistancePresentation {
    fun recorded(value: Int, unit: String): String =
        NumberFormat.getIntegerInstance(Locale.FRANCE).format(value) + " " + unit
}
