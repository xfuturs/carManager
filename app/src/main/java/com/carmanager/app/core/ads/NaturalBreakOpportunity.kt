package com.carmanager.app.core.ads

internal enum class NaturalBreakWorkflow { FuelRecordSaved, EvRechargeSaved }

/** Autorisation éphémère, liée au résultat d'une écriture et à une entrée précise. */
internal class NaturalBreakOpportunity(
    val workflow: NaturalBreakWorkflow,
    val completedAtMs: Long,
    val destinationId: String,
    val hostGeneration: Long
) {
    companion object { const val VALIDITY_MS = 1_500L }
    private var consumed = false

    fun consume(now: Long): InterstitialDecision {
        if (consumed) return InterstitialDecision.NaturalBreakRejected
        consumed = true
        return if (now < completedAtMs || now - completedAtMs >= VALIDITY_MS)
            InterstitialDecision.NaturalBreakExpired else InterstitialDecision.NaturalBreakAccepted
    }

    fun discard() { consumed = true }
}
