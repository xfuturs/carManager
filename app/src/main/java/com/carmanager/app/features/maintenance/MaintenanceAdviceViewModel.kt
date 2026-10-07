package com.carmanager.app.features.maintenance

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmanager.app.core.domain.model.LocalDataState
import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.domain.repository.VehicleRepository
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.core.domain.session.observeLocalState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class MaintenanceAdviceViewModel @Inject constructor(
    vehicleRepository: VehicleRepository,
    savedStateHandle: SavedStateHandle,
    session: WorkspaceSession
) : ViewModel() {
    private val vehicleId: Long = checkNotNull(savedStateHandle["vehicleId"])
    private val retry = MutableStateFlow(0)
    val uiState: StateFlow<LocalDataState<Vehicle?>> = observeLocalState(session, retry) {
        vehicleRepository.observeById(vehicleId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000, replayExpirationMillis = 0), LocalDataState.Loading)
    fun retryLoading() { retry.value++ }
}
