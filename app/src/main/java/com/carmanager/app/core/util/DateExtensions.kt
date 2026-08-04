package com.carmanager.app.core.util

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utilitaires pour garantir un formatage des dates homogène et en français
 * à travers toute l'application.
 */
object DateFormatter {
    private val locale = Locale.FRANCE

    /**
     * Formate une date en style moyen : "28 juil. 2026"
     */
    fun formatMedium(timestamp: Long): String {
        return DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(timestamp))
    }

    /**
     * Formate une date en style court : "28/07/26"
     */
    fun formatShort(timestamp: Long): String {
        return DateFormat.getDateInstance(DateFormat.SHORT, locale).format(Date(timestamp))
    }

    /**
     * Formate le mois et l'année pour les en-têtes de listes : "Juillet 2026"
     */
    fun formatMonthYear(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMMM yyyy", locale)
        return sdf.format(Date(timestamp)).replaceFirstChar { it.uppercase() }
    }
}
