package com.carmanager.app.features.maintenance

import com.carmanager.app.core.domain.model.LocalDataState
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.core.domain.session.observeLocalState
import kotlinx.coroutines.flow.MutableStateFlow
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
    savedStateHandle: SavedStateHandle,
    session: WorkspaceSession
) : ViewModel() {

    val vehicleId: Long = checkNotNull(savedStateHandle["vehicleId"])

    private val retry = MutableStateFlow(0)
    val uiState: StateFlow<LocalDataState<List<MaintenanceRecord>>> = observeLocalState(session, retry) { getMaintenanceRecordsUseCase(vehicleId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000, replayExpirationMillis = 0), LocalDataState.Loading)
    fun retryLoading() { retry.value++ }

    fun deleteRecord(record: MaintenanceRecord) {
        viewModelScope.launch {
            deleteMaintenanceRecordUseCase(record)
        }
    }
}
