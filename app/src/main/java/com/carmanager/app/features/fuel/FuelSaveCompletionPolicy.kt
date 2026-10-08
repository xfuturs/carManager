package com.carmanager.app.features.fuel

import com.carmanager.app.core.ads.NaturalBreakOpportunity
import com.carmanager.app.core.domain.model.FuelRecord
import com.carmanager.app.core.domain.model.LocalDataState

internal fun isFuelSaveResultVisible(completion: CompletedFuelSave,
    state: LocalDataState<List<FuelRecord>>, visibleIds: Set<Any>, resumed: Boolean,
    scrolling: Boolean, now: Long): Boolean {
    val ready = state as? LocalDataState.Ready<List<FuelRecord>> ?: return false
    return resumed && !scrolling && now >= completion.completedAtMs &&
        now - completion.completedAtMs < NaturalBreakOpportunity.VALIDITY_MS &&
        ready.owner == completion.record.ownerKey && completion.record.id > 0 &&
        completion.record.id in visibleIds && ready.data.any { it == completion.record }
}
