package com.carmanager.app.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmanager.app.core.domain.model.DashboardStats
import com.carmanager.app.core.domain.model.LocalDataState
import com.carmanager.app.core.domain.model.MileageSource
import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.domain.repository.PremiumRepository
import com.carmanager.app.core.domain.repository.SettingsRepository
import com.carmanager.app.features.mileage.UpdateMileageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.carmanager.app.core.util.UiEvent
import com.carmanager.app.core.domain.validation.FormValidationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import android.util.Log
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    getDashboardStatsUseCase: GetDashboardStatsUseCase,
    private val updateMileageUseCase: UpdateMileageUseCase,
    private val settingsRepository: SettingsRepository,
    private val premiumRepository: PremiumRepository,
    private val generateVehicleReportUseCase: GenerateVehicleReportUseCase
) : ViewModel() {

    private val retry = MutableStateFlow(0)
    val uiState: StateFlow<LocalDataState<DashboardStats>> = flow {
        emitAll(getDashboardStatsUseCase.observeState(retry))
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000, replayExpirationMillis = 0),
            initialValue = LocalDataState.Loading
        )
    fun retryLoading() { retry.value++ }

    val currency: StateFlow<String> = settingsRepository.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "€")

    val distanceUnit: StateFlow<String> = settingsRepository.distanceUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "km")

    val isPremium: StateFlow<Boolean> = premiumRepository.isPremium

    var isUpdatingMileage by mutableStateOf(false)
        private set
    private val _uiEvent = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    fun updateMileage(vehicle: Vehicle, newMileage: Int) {
        if (isUpdatingMileage) return
        isUpdatingMileage = true
        viewModelScope.launch {
            try {
                updateMileageUseCase(vehicle, newMileage, MileageSource.MANUAL)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("Dashboard", "Échec relevé kilométrique", e)
                _uiEvent.send(UiEvent.ShowSnackbar(if (e is FormValidationException) e.message!! else
                    "Relevé non enregistré. Rouvrez le formulaire ou réessayez."))
            } finally { isUpdatingMileage = false }
        }
    }

    fun generateReport(context: android.content.Context, vehicleId: Long) {
        viewModelScope.launch {
            val reportData = generateVehicleReportUseCase(vehicleId)
            if (reportData != null) {
                com.carmanager.app.core.util.PdfReportHelper.generateAndShare(
                    context = context,
                    vehicle = reportData.vehicle,
                    fuelRecords = reportData.fuelRecords,
                    maintenanceRecords = reportData.maintenanceRecords
                )
            }
        }
    }
}
