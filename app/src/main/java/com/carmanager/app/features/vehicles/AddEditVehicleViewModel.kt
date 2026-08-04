package com.carmanager.app.features.vehicles

import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmanager.app.core.domain.model.FuelType
import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.domain.model.VehicleType
import com.carmanager.app.core.domain.repository.VehicleRepository
import com.carmanager.app.core.data.local.dao.VehicleReferenceDao
import com.carmanager.app.core.data.local.entity.VehicleReferenceEntity
import com.carmanager.app.core.util.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class AddEditVehicleViewModel @Inject constructor(
    private val saveVehicleUseCase: SaveVehicleUseCase,
    private val deleteVehicleUseCase: DeleteVehicleUseCase,
    private val vehicleRepository: VehicleRepository,
    private val referenceDao: VehicleReferenceDao,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    var brand by mutableStateOf("")
        private set
    var model by mutableStateOf("")
        private set
    var year by mutableStateOf("")
        private set
    var mileage by mutableStateOf("")
        private set
    var fuelType by mutableStateOf(FuelType.GASOLINE)
        private set
    var vehicleType by mutableStateOf(VehicleType.CAR)
        private set
    var licensePlate by mutableStateOf("")
        private set
    var powerHp by mutableStateOf("")
        private set
    var tankCapacity by mutableStateOf("")
        private set
    var batteryCapacity by mutableStateOf("")
        private set

    var isCustomBrand by mutableStateOf(false)
        private set
    var isCustomModel by mutableStateOf(false)
        private set

    var currentVehicleMileage by mutableStateOf(0)
        private set

    var isEditMode by mutableStateOf(false)
        private set
        
    var showErrors by mutableStateOf(false)
        private set
        
    var isYearError by mutableStateOf(false)
        private set

    var isPowerError by mutableStateOf(false)
        private set

    var isCapacityError by mutableStateOf(false)
        private set

    private var currentVehicleId: Long = 0L
    
    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    val allBrands: StateFlow<List<String>> = referenceDao.getAllBrands()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _modelsForBrand = MutableStateFlow<List<String>>(emptyList())
    val modelsForBrand: StateFlow<List<String>> = _modelsForBrand.asStateFlow()

    init {
        val vehicleId = savedStateHandle.get<Long>("vehicleId") ?: -1L
        if (vehicleId != -1L) {
            isEditMode = true
            currentVehicleId = vehicleId
            loadVehicle(vehicleId)
        }

        // Vérifier et pré-remplir la base de référence
        viewModelScope.launch {
            try {
                val count = referenceDao.getCount()
                if (count == 0) {
                    prepopulateDatabase()
                }
            } catch (e: Exception) {
                _uiEvent.send(UiEvent.ShowSnackbar("Erreur initialisation catalogue : ${e.message}"))
            }
        }
    }

    private suspend fun prepopulateDatabase() {
        try {
            val jsonString = context.assets.open("vehicle_references.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            val references = mutableListOf<VehicleReferenceEntity>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                references.add(
                    VehicleReferenceEntity(
                        brand = obj.getString("brand"),
                        model = obj.getString("model")
                    )
                )
            }
            referenceDao.insertAll(references)
        } catch (e: Exception) {
             _uiEvent.send(UiEvent.ShowSnackbar("Erreur catalogue : ${e.message}"))
        }
    }

    private fun loadVehicle(id: Long) {
        viewModelScope.launch {
            vehicleRepository.observeById(id).firstOrNull()?.let { vehicle ->
                brand = vehicle.brand
                model = vehicle.model
                year = vehicle.year.toString()
                mileage = ""
                currentVehicleMileage = vehicle.currentMileage
                fuelType = vehicle.fuelType
                vehicleType = vehicle.type
                licensePlate = vehicle.licensePlate ?: ""
                powerHp = vehicle.powerHp?.toString() ?: ""
                tankCapacity = vehicle.tankCapacity?.toString() ?: ""
                batteryCapacity = vehicle.batteryCapacity?.toString() ?: ""
                
                loadModelsForBrand(vehicle.brand)
            }
        }
    }

    fun onBrandChange(value: String, isCustom: Boolean = false) { 
        brand = value
        isCustomBrand = isCustom
        if (!isCustom) {
            loadModelsForBrand(value)
            model = "" // Reset model when brand changes
            isCustomModel = false
        }
    }

    private fun loadModelsForBrand(brandName: String) {
        viewModelScope.launch {
            referenceDao.getModelsForBrand(brandName).collect {
                _modelsForBrand.value = it
            }
        }
    }

    fun onModelChange(value: String, isCustom: Boolean = false) { 
        model = value 
        isCustomModel = isCustom
    }

    fun onYearChange(value: String) { if (value.all { it.isDigit() }) year = value }
    fun onMileageChange(value: String) { if (value.all { it.isDigit() }) mileage = value }
    fun onFuelTypeChange(value: FuelType) { fuelType = value }
    fun onVehicleTypeChange(value: VehicleType) { vehicleType = value }
    fun onLicensePlateChange(value: String) { licensePlate = value }
    fun onPowerHpChange(value: String) { if (value.all { it.isDigit() }) powerHp = value }
    fun onTankCapacityChange(value: String) { tankCapacity = value }
    fun onBatteryCapacityChange(value: String) { batteryCapacity = value }

    fun onEstimatedMileageSelect(increment: Int) {
        mileage = (currentVehicleMileage + increment).toString()
    }

    fun deleteVehicle() {
        if (!isEditMode) return
        viewModelScope.launch {
            try {
                vehicleRepository.observeById(currentVehicleId).firstOrNull()?.let { vehicle ->
                    deleteVehicleUseCase(vehicle)
                    _uiEvent.send(UiEvent.Success)
                }
            } catch (e: Exception) {
                _uiEvent.send(UiEvent.ShowSnackbar("Erreur lors de la suppression: ${e.localizedMessage}"))
            }
        }
    }

    fun save() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val enteredYear = year.toIntOrNull()
        
        isYearError = enteredYear != null && (enteredYear < 1900 || enteredYear > currentYear)
        
        val enteredPower = powerHp.toIntOrNull()
        isPowerError = enteredPower != null && (enteredPower <= 0 || enteredPower > 3000)
        
        val enteredTank = tankCapacity.toDoubleOrNull()
        val enteredBattery = batteryCapacity.toDoubleOrNull()
        isCapacityError = (enteredTank != null && (enteredTank <= 0 || enteredTank > 1000)) ||
                          (enteredBattery != null && (enteredBattery <= 0 || enteredBattery > 1000))

        if (brand.isBlank() || model.isBlank() || isYearError || isPowerError || isCapacityError) {
            showErrors = true
            val message = when {
                isYearError -> "L'année doit être comprise entre 1900 et $currentYear"
                isPowerError -> "La puissance doit être comprise entre 1 et 3000 ch"
                isCapacityError -> "La capacité doit être comprise entre 1 et 1000 L/kWh"
                else -> "Veuillez remplir les champs obligatoires (Marque, Modèle)"
            }
            viewModelScope.launch {
                _uiEvent.send(UiEvent.ShowSnackbar(message))
            }
            return
        }
        
        val enteredMileage = mileage.toIntOrNull() ?: currentVehicleMileage
        
        if (isEditMode && enteredMileage < currentVehicleMileage) {
             viewModelScope.launch {
                _uiEvent.send(UiEvent.ShowSnackbar("Le kilométrage ne peut pas être inférieur au précédent ($currentVehicleMileage km)"))
            }
            return
        }

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val vehicle = Vehicle(
                    id = currentVehicleId,
                    brand = brand,
                    model = model,
                    year = year.toIntOrNull() ?: 0,
                    currentMileage = enteredMileage,
                    fuelType = fuelType,
                    type = vehicleType,
                    powerHp = powerHp.toIntOrNull(),
                    licensePlate = licensePlate.takeIf { it.isNotBlank() },
                    tankCapacity = tankCapacity.toDoubleOrNull(),
                    batteryCapacity = batteryCapacity.toDoubleOrNull(),
                    createdAt = if (isEditMode) 0L else now,
                    updatedAt = now
                )
                saveVehicleUseCase(vehicle)
                _uiEvent.send(UiEvent.Success)
            } catch (e: Exception) {
                _uiEvent.send(UiEvent.ShowSnackbar("Erreur lors de l'enregistrement: ${e.localizedMessage}"))
            }
        }
    }
}
