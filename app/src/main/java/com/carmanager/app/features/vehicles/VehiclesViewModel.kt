package com.carmanager.app.features.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.domain.model.LocalDataState
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.core.domain.session.observeLocalState
import kotlinx.coroutines.flow.MutableStateFlow
import com.carmanager.app.features.vehicles.DeleteVehicleUseCase
import com.carmanager.app.features.vehicles.GetVehiclesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VehiclesViewModel @Inject constructor(
    getVehiclesUseCase: GetVehiclesUseCase,
    private val deleteVehicleUseCase: DeleteVehicleUseCase,
    session: WorkspaceSession
) : ViewModel() {

    private val retry = MutableStateFlow(0)
    val uiState: StateFlow<LocalDataState<List<Vehicle>>> = observeLocalState(session, retry) { getVehiclesUseCase() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000, replayExpirationMillis = 0),
            initialValue = LocalDataState.Loading
        )
    fun retryLoading() { retry.value++ }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            deleteVehicleUseCase(vehicle)
        }
    }
}
