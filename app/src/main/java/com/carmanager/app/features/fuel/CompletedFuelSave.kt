package com.carmanager.app.features.fuel

import com.carmanager.app.core.domain.model.FuelRecord

/** Reçu en mémoire après retour du commit ; aucune persistance ni droit publicitaire. */
data class CompletedFuelSave(val record: FuelRecord, val completedAtMs: Long)
