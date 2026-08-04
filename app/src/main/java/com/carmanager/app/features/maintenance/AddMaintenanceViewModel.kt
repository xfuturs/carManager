package com.carmanager.app.features.maintenance

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmanager.app.core.domain.model.MaintenanceRecord
import com.carmanager.app.core.domain.model.MaintenanceType
import com.carmanager.app.core.domain.repository.VehicleRepository
import com.carmanager.app.core.util.OcrHelper
import com.carmanager.app.core.util.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class AddMaintenanceViewModel @Inject constructor(
    private val saveMaintenanceUseCase: SaveMaintenanceUseCase,
    private val vehicleRepository: VehicleRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val vehicleId: Long = checkNotNull(savedStateHandle["vehicleId"])

    var type by mutableStateOf(MaintenanceType.OIL_CHANGE)
        private set
    var mileage by mutableStateOf("")
        private set
    var cost by mutableStateOf("")
        private set
    var note by mutableStateOf("")
        private set
    var nextDueDate by mutableStateOf<Long?>(null)
        private set
    var nextDueMileage by mutableStateOf("")
        private set
    var date by mutableStateOf(System.currentTimeMillis())
        private set
        
    var showErrors by mutableStateOf(false)
        private set
        
    var currentVehicleMileage by mutableStateOf(0)
        private set

    var isTypeLocked by mutableStateOf(false)
        private set
        
    var isScanning by mutableStateOf(false)
        private set

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    init {
        val initialTypeStr = savedStateHandle.get<String>("initialType")
        if (initialTypeStr != null) {
            try {
                type = MaintenanceType.valueOf(initialTypeStr)
                isTypeLocked = true
            } catch (e: Exception) {
                // Ignore invalid type
            }
        }
        
        loadCurrentMileage()
    }

    private fun loadCurrentMileage() {
        viewModelScope.launch {
            vehicleRepository.observeById(vehicleId).firstOrNull()?.let { vehicle ->
                currentVehicleMileage = vehicle.currentMileage
                mileage = "" // Optionnel par défaut
            }
        }
    }

    fun onTypeChange(value: MaintenanceType) { 
        type = value 
    }
    fun onMileageChange(value: String) { if (value.all { it.isDigit() }) mileage = value }
    fun onCostChange(value: String) { cost = value }
    fun onNoteChange(value: String) { note = value }
    fun onNextDueMileageChange(value: String) { if (value.all { it.isDigit() }) nextDueMileage = value }
    fun onNextDueDateChange(value: Long?) { nextDueDate = value }
    fun onDateChange(value: Long) { date = value }

    fun onEstimatedMileageSelect(increment: Int) {
        mileage = (currentVehicleMileage + increment).toString()
    }

    fun applyMileageIncrement(increment: Int) {
        val base = mileage.toIntOrNull() ?: currentVehicleMileage
        nextDueMileage = (base + increment).toString()
    }

    fun applyDateIncrement(years: Int = 0, months: Int = 0) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = date
        if (years > 0) calendar.add(Calendar.YEAR, years)
        if (months > 0) calendar.add(Calendar.MONTH, months)
        nextDueDate = calendar.timeInMillis
    }

    fun onScanReceipt(uri: Uri) {
        viewModelScope.launch {
            isScanning = true
            val result = OcrHelper.analyzeImage(context, uri)
            
            // Pour l'entretien on cherche surtout le prix
            result.totalPrice?.let { cost = "%.2f".format(it).replace(",", ".") }
            
            isScanning = false
            _uiEvent.send(UiEvent.ShowSnackbar("Analyse terminée"))
        }
    }

    fun save() {
        val enteredMileage = mileage.toIntOrNull() ?: currentVehicleMileage
        
        if (enteredMileage < currentVehicleMileage) {
            viewModelScope.launch {
                _uiEvent.send(UiEvent.ShowSnackbar("Le kilométrage ne peut pas être inférieur au précédent ($currentVehicleMileage km)"))
            }
            return
        }

        viewModelScope.launch {
            try {
                val record = MaintenanceRecord(
                    vehicleId = vehicleId,
                    type = type,
                    date = date,
                    mileage = enteredMileage,
                    cost = cost.toDoubleOrNull() ?: 0.0,
                    note = note.takeIf { it.isNotBlank() },
                    nextDueDate = nextDueDate,
                    nextDueMileage = nextDueMileage.toIntOrNull()
                )
                saveMaintenanceUseCase(record)
                _uiEvent.send(UiEvent.Success)
            } catch (e: Exception) {
                _uiEvent.send(UiEvent.ShowSnackbar("Erreur lors de la sauvegarde: ${e.localizedMessage}"))
            }
        }
    }
}
