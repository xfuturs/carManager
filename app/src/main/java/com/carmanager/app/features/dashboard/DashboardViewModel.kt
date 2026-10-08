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
import com.carmanager.app.core.domain.model.ReportSection

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getDashboardStatsUseCase: GetDashboardStatsUseCase,
    private val updateMileageUseCase: UpdateMileageUseCase,
    private val settingsRepository: SettingsRepository,
    private val premiumRepository: PremiumRepository,
    private val storeVehicleReportUseCase: StoreVehicleReportUseCase
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
    fun refreshTime() { getDashboardStatsUseCase.refreshTime() }
    fun toggleAppearance() {
        viewModelScope.launch {
            try { settingsRepository.toggleTheme() }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiEvent.send(UiEvent.ShowSnackbar("Apparence non enregistrée. Réessayez."))
            }
        }
    }

    val currency: StateFlow<String> = settingsRepository.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "€")

    val distanceUnit: StateFlow<String> = settingsRepository.distanceUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "km")

    val isPremium: StateFlow<Boolean> = premiumRepository.isPremium

    var isUpdatingMileage by mutableStateOf(false)
        private set
    private val _uiEvent = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    private val _generatedReport = MutableStateFlow<com.carmanager.app.core.domain.model.Document?>(null)
    val generatedReport: StateFlow<com.carmanager.app.core.domain.model.Document?> = _generatedReport
    var isGeneratingReport by mutableStateOf(false)
        private set
    fun closeReportResult() { _generatedReport.value = null }
    private val _reportDraft = MutableStateFlow<ReportDraft?>(null)
    val reportDraft: StateFlow<ReportDraft?> = _reportDraft
    var isPreparingReport by mutableStateOf(false)
        private set
    fun prepareReport(vehicleId: Long) {
        if (PdfAccessPolicy.resolve(isPremium.value) != PdfAccess.PREMIUM) return
        if (isPreparingReport || isGeneratingReport || _reportDraft.value != null) return
        isPreparingReport = true
        viewModelScope.launch {
            try {
                if (PdfAccessPolicy.resolve(isPremium.value) != PdfAccess.PREMIUM) return@launch
                val previous = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { storeVehicleReportUseCase.previousReports(vehicleId) }
                if (PdfAccessPolicy.resolve(isPremium.value) != PdfAccess.PREMIUM) return@launch
                _reportDraft.value = ReportDraft(vehicleId, previous)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiEvent.send(UiEvent.ShowSnackbar("Configuration du rapport indisponible. Réessayez."))
            } finally { isPreparingReport = false }
        }
    }
    fun cancelReportDraft() { _reportDraft.value = null }
    fun toggleReportSection(section: ReportSection) { _reportDraft.value = _reportDraft.value?.toggle(section) }
    fun confirmReportDraft() {
        if (PdfAccessPolicy.resolve(isPremium.value) != PdfAccess.PREMIUM) { cancelReportDraft(); return }
        val draft = _reportDraft.value?.takeIf { it.canGenerate } ?: return
        _reportDraft.value = null
        generateReport(draft.vehicleId, draft.selected)
    }

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

    fun generateReport(vehicleId: Long, sections: Set<ReportSection> = ReportSection.entries.toSet()) {
        if (PdfAccessPolicy.resolve(isPremium.value) != PdfAccess.PREMIUM) return
        if (sections.isEmpty()) return
        if (isGeneratingReport) return
        isGeneratingReport = true
        viewModelScope.launch {
            try {
                _generatedReport.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (PdfAccessPolicy.resolve(isPremium.value) != PdfAccess.PREMIUM) return@withContext null
                    storeVehicleReportUseCase(vehicleId, sections)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.e("PdfReportHelper", "Erreur lors de la génération du PDF", error)
                _uiEvent.send(UiEvent.ShowSnackbar("Rapport non enregistré. Réessayez depuis le véhicule."))
            } finally { isGeneratingReport = false }
        }
    }
}
