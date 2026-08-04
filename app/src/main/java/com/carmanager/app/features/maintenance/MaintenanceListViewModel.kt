package com.carmanager.app.features.maintenance

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmanager.app.core.domain.model.MaintenanceRecord
import com.carmanager.app.features.maintenance.DeleteMaintenanceRecordUseCase
import com.carmanager.app.features.maintenance.GetMaintenanceRecordsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MaintenanceListViewModel @Inject constructor(
    getMaintenanceRecordsUseCase: GetMaintenanceRecordsUseCase,
    private val deleteMaintenanceRecordUseCase: DeleteMaintenanceRecordUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val vehicleId: Long = checkNotNull(savedStateHandle["vehicleId"])

    val maintenanceRecords: StateFlow<List<MaintenanceRecord>> = getMaintenanceRecordsUseCase(vehicleId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteRecord(record: MaintenanceRecord) {
        viewModelScope.launch {
            deleteMaintenanceRecordUseCase(record)
        }
    }
}
