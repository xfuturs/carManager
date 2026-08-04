package com.carmanager.app.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmanager.app.core.domain.model.DashboardStats
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
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    getDashboardStatsUseCase: GetDashboardStatsUseCase,
    private val updateMileageUseCase: UpdateMileageUseCase,
    private val settingsRepository: SettingsRepository,
    private val premiumRepository: PremiumRepository,
    private val generateVehicleReportUseCase: GenerateVehicleReportUseCase
) : ViewModel() {

    val uiState: StateFlow<DashboardStats> = getDashboardStatsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardStats()
        )

    val currency: StateFlow<String> = settingsRepository.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "€")

    val distanceUnit: StateFlow<String> = settingsRepository.distanceUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "km")

    val isPremium: StateFlow<Boolean> = premiumRepository.isPremium

    fun updateMileage(vehicle: Vehicle, newMileage: Int) {
        viewModelScope.launch {
            try {
                updateMileageUseCase(vehicle, newMileage, MileageSource.MANUAL)
            } catch (e: Exception) {
                // Handle error
            }
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
