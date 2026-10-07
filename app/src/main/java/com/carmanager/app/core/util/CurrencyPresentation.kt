package com.carmanager.app.core.util

import java.text.NumberFormat
import java.util.Locale

/** Préférence de symbole uniquement : aucune conversion des montants historiques. */
object CurrencyPresentation {
    fun format(amount: Double, symbol: String, decimals: Int = 2): String =
        NumberFormat.getNumberInstance(Locale.FRANCE).apply {
            minimumFractionDigits = decimals; maximumFractionDigits = decimals
            if (decimals == 0) roundingMode = java.math.RoundingMode.HALF_UP
        }.format(amount) + " " + symbol
}
