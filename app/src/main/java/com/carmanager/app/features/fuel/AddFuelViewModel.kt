package com.carmanager.app.features.fuel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmanager.app.core.domain.model.FuelRecord
import com.carmanager.app.core.domain.model.FuelType
import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.domain.model.VehicleType
import com.carmanager.app.core.domain.repository.VehicleRepository
import com.carmanager.app.core.util.OcrHelper
import com.carmanager.app.core.util.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddFuelViewModel @Inject constructor(
    private val saveFuelRecordUseCase: SaveFuelRecordUseCase,
    private val vehicleRepository: VehicleRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val vehicleId: Long = checkNotNull(savedStateHandle["vehicleId"])

    var mileage by mutableStateOf("")
        private set
    var liters by mutableStateOf("")
        private set
    var totalPrice by mutableStateOf("")
        private set
    var note by mutableStateOf("")
        private set
    var date by mutableStateOf(System.currentTimeMillis())
        private set
        
    var tankCapacity by mutableStateOf<Double?>(null)
        private set
    var batteryCapacity by mutableStateOf<Double?>(null)
        private set
    var fuelType by mutableStateOf(FuelType.GASOLINE)
        private set
        
    var isElectricEntry by mutableStateOf(false)
        private set
        
    var currentVehicleMileage by mutableStateOf(0)
        private set
        
    var showErrors by mutableStateOf(false)
        private set
        
    var isScanning by mutableStateOf(false)
        private set

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    init {
        viewModelScope.launch {
            vehicleRepository.observeById(vehicleId).firstOrNull()?.let { vehicle ->
                tankCapacity = vehicle.tankCapacity
                batteryCapacity = vehicle.batteryCapacity
                fuelType = vehicle.fuelType
                currentVehicleMileage = vehicle.currentMileage
                mileage = ""
                
                isElectricEntry = (vehicle.fuelType == FuelType.ELECTRIC)
            }
        }
    }

    fun onMileageChange(value: String) { if (value.all { it.isDigit() }) mileage = value }
    fun onLitersChange(value: String) { liters = value }
    fun onTotalPriceChange(value: String) { totalPrice = value }
    fun onNoteChange(value: String) { note = value }
    fun onDateChange(value: Long) { date = value }
    fun onElectricEntryToggle(value: Boolean) { isElectricEntry = value }
    
    fun onEstimatedMileageSelect(increment: Int) {
        mileage = (currentVehicleMileage + increment).toString()
    }

    fun onScanReceipt(uri: Uri) {
        viewModelScope.launch {
            isScanning = true
            val result = OcrHelper.analyzeImage(context, uri)
            
            result.totalPrice?.let { totalPrice = "%.2f".format(it).replace(",", ".") }
            result.liters?.let { liters = "%.2f".format(it).replace(",", ".") }
            
            isScanning = false
            _uiEvent.send(UiEvent.ShowSnackbar("Analyse terminée"))
        }
    }

    fun save() {
        if (liters.isBlank() || totalPrice.isBlank()) {
            showErrors = true
            val errorMsg = if (isElectricEntry) "Veuillez remplir kWh et Prix" else "Veuillez remplir Litres et Prix"
            viewModelScope.launch {
                _uiEvent.send(UiEvent.ShowSnackbar(errorMsg))
            }
            return
        }
        
        val enteredLiters = liters.toDoubleOrNull() ?: 0.0
        val capacity = if (isElectricEntry) batteryCapacity else tankCapacity
        
        if (capacity != null && enteredLiters > (capacity * 1.05)) {
            val unit = if (isElectricEntry) "kWh" else "L"
            viewModelScope.launch {
                _uiEvent.send(UiEvent.ShowSnackbar("Le volume saisi ($enteredLiters $unit) dépasse la capacité du véhicule (${capacity} $unit)"))
            }
            return
        }
        
        val enteredMileage = mileage.toIntOrNull() ?: currentVehicleMileage
        
        if (enteredMileage < currentVehicleMileage) {
            viewModelScope.launch {
                _uiEvent.send(UiEvent.ShowSnackbar("Le kilométrage ne peut pas être inférieur au précédent ($currentVehicleMileage km)"))
            }
            return
        }

        viewModelScope.launch {
            try {
                val record = FuelRecord(
                    vehicleId = vehicleId,
                    date = date,
                    mileage = enteredMileage,
                    liters = liters.toDoubleOrNull() ?: 0.0,
                    totalPrice = totalPrice.toDoubleOrNull() ?: 0.0,
                    note = note.takeIf { it.isNotBlank() },
                    isElectric = isElectricEntry
                )
                saveFuelRecordUseCase(record)
                _uiEvent.send(UiEvent.Success)
            } catch (e: Exception) {
                _uiEvent.send(UiEvent.ShowSnackbar("Erreur lors de la sauvegarde: ${e.localizedMessage}"))
            }
        }
    }
}
