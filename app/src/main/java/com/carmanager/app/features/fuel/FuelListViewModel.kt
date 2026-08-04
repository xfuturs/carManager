package com.carmanager.app.features.fuel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmanager.app.core.domain.model.FuelRecord
import com.carmanager.app.features.fuel.DeleteFuelRecordUseCase
import com.carmanager.app.features.fuel.GetFuelRecordsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FuelListViewModel @Inject constructor(
    getFuelRecordsUseCase: GetFuelRecordsUseCase,
    private val deleteFuelRecordUseCase: DeleteFuelRecordUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val vehicleId: Long = checkNotNull(savedStateHandle["vehicleId"])

    val fuelRecords: StateFlow<List<FuelRecord>> = getFuelRecordsUseCase(vehicleId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteRecord(record: FuelRecord) {
        viewModelScope.launch {
            deleteFuelRecordUseCase(record)
        }
    }
}
