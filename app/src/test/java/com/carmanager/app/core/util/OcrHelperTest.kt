package com.carmanager.app.core.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OcrHelperTest {

    @Test
    fun `parseText should extract price and liters correctly from receipt text`() {
        // GIVEN
        val receiptText = """
            STATION TOTAL ENERGIES
            RECU DE VENTE
            GASOIL: 55,20 L
            PRIX L: 1,450 EUR
            TOTAL TTC: 80.04 EUR
            MERCI DE VOTRE VISITE
        """.trimIndent()

        // WHEN
        val result = OcrHelper.parseText(receiptText)

        // THEN
        assertEquals(80.04, result.totalPrice)
        assertEquals(55.20, result.liters)
    }

    @Test
    fun `parseText should return nulls if no data found`() {
        // GIVEN
        val randomText = "Ceci n'est pas un ticket de caisse"

        // WHEN
        val result = OcrHelper.parseText(randomText)

        // THEN
        assertEquals(null, result.totalPrice)
        assertEquals(null, result.liters)
    }
}
