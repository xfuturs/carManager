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

    sealed interface Analysis {
        data class Values(val result: OcrResult) : Analysis
        data object NoValues : Analysis
        data object Failed : Analysis
    }
    internal interface Recognizer {
        suspend fun text(): String
        fun close()
    }
    suspend fun analyzeImage(context: Context, uri: Uri): Analysis = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        analyze {
            val image = InputImage.fromFilePath(context.applicationContext, uri)
            val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            object : Recognizer {
                override suspend fun text() = client.process(image).await().text
                override fun close() = client.close()
            }
        }
    }
    internal suspend fun analyze(create: suspend () -> Recognizer): Analysis {
        return try {
            val recognizer = create()
            var failure: Throwable? = null
            val result = try { parseText(recognizer.text()) }
            catch (error: Throwable) { failure = error; throw error }
            finally {
                try { recognizer.close() }
                catch (cleanup: Exception) { if (failure != null) failure.addSuppressed(cleanup) else throw cleanup }
            }
            if (result.totalPrice != null || result.liters != null || result.date != null) Analysis.Values(result) else Analysis.NoValues
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Analysis.Failed
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
