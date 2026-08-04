package com.carmanager.app.core.util

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object OcrHelper {

    data class OcrResult(
        val totalPrice: Double? = null,
        val liters: Double? = null,
        val date: Long? = null
    )

    suspend fun analyzeImage(context: Context, uri: Uri): OcrResult {
        return try {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = recognizer.process(image).await()
            
            parseText(result.text)
        } catch (e: Exception) {
            e.printStackTrace()
            OcrResult()
        }
    }

    /**
     * Analyse le texte brut extrait d'une image pour en tirer les données utiles.
     */
    fun parseText(text: String): OcrResult {
        // Regex pour trouver les montants (ex: 75,50 ou 75.50)
        // On cherche souvent "TOTAL" ou "EUR" à proximité
        val priceRegex = Regex("""(\d+[.,]\d{2})\s*(?:€|EUR|TOTAL)""", RegexOption.IGNORE_CASE)
        val literRegex = Regex("""(\d+[.,]\d{2,3})\s*(?:L|LITRE)""", RegexOption.IGNORE_CASE)
        
        val prices = priceRegex.findAll(text).map { 
            it.groupValues[1].replace(",", ".").toDoubleOrNull() 
        }.filterNotNull().toList()
        
        val liters = literRegex.findAll(text).map { 
            it.groupValues[1].replace(",", ".").toDoubleOrNull() 
        }.filterNotNull().toList()

        // Souvent le prix le plus élevé est le total
        val totalPrice = prices.maxOrNull()
        // Le litrage est souvent unique ou bien identifié
        val totalLiters = liters.maxOrNull()

        return OcrResult(
            totalPrice = totalPrice,
            liters = totalLiters
        )
    }
}
