package com.carmanager.app.features.mileage

import com.carmanager.app.core.domain.model.LocalDataState
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.core.domain.session.observeLocalState
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmanager.app.core.domain.model.MileageRecord
import com.carmanager.app.core.domain.repository.MileageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class MileageHistoryViewModel @Inject constructor(
    mileageRepository: MileageRepository,
    savedStateHandle: SavedStateHandle,
    session: WorkspaceSession
) : ViewModel() {

    private val vehicleId: Long = checkNotNull(savedStateHandle["vehicleId"])

    private val retry = MutableStateFlow(0)
    val uiState: StateFlow<LocalDataState<List<MileageRecord>>> = observeLocalState(session, retry) { mileageRepository.observeByVehicle(vehicleId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000, replayExpirationMillis = 0), LocalDataState.Loading)
    fun retryLoading() { retry.value++ }

}
