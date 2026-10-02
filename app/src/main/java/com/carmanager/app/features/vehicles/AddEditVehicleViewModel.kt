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
import com.carmanager.app.core.domain.validation.NumericInput
import com.carmanager.app.core.domain.validation.GarageValidation
import com.carmanager.app.core.domain.validation.FormValidationException
import kotlinx.coroutines.CancellationException
import android.util.Log

@HiltViewModel
class AddEditVehicleViewModel @Inject constructor(
    private val saveVehicleUseCase: SaveVehicleUseCase,
    private val deleteVehicleUseCase: DeleteVehicleUseCase,
    private val vehicleRepository: VehicleRepository,
    private val referenceDao: VehicleReferenceDao,
    @ApplicationContext private val context: Context,
    private val session: com.carmanager.app.core.domain.session.WorkspaceSession,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val workspaceOwner = session.owner.value

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
    private var loadedVehicle: Vehicle? = null
    private var modelsJob: kotlinx.coroutines.Job? = null
    
    var isSaving by mutableStateOf(false)
        private set
    var hasSaved by mutableStateOf(false)
        private set
    private val _uiEvent = Channel<UiEvent>(Channel.BUFFERED)
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
                loadedVehicle = vehicle
                isCustomBrand = vehicle.brand !in referenceDao.getAllBrands().first()
                isCustomModel = isCustomBrand
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
        val modeChanged = isCustomBrand != isCustom
        brand = value
        isCustomBrand = isCustom
        if (isCustom) {
            modelsJob?.cancel()
            _modelsForBrand.value = emptyList()
            if (modeChanged) model = ""
            isCustomModel = true
        } else {
            model = ""
            isCustomModel = false
            loadModelsForBrand(value)
        }
    }

    private fun loadModelsForBrand(brandName: String) {
        modelsJob?.cancel()
        if (isCustomBrand) return
        modelsJob = viewModelScope.launch {
            referenceDao.getModelsForBrand(brandName).collect {
                if (brand == brandName && !isCustomBrand) {
                    _modelsForBrand.value = it
                    if (model.isNotBlank() && model !in it) isCustomModel = true
                }
            }
        }
    }

    fun onModelChange(value: String, isCustom: Boolean = false) {
        model = value
        isCustomModel = isCustom || isCustomBrand
    }

    fun onYearChange(value: String) { year = value }
    fun onMileageChange(value: String) { mileage = value }
    fun onFuelTypeChange(value: FuelType) { fuelType = value }
    fun onVehicleTypeChange(value: VehicleType) { vehicleType = value }
    fun onLicensePlateChange(value: String) { licensePlate = value }
    fun onPowerHpChange(value: String) { powerHp = value }
    fun onTankCapacityChange(value: String) { tankCapacity = value }
    fun onBatteryCapacityChange(value: String) { batteryCapacity = value }

    fun onEstimatedMileageSelect(increment: Int) {
        mileage = (currentVehicleMileage.toLong() + increment).toString()
    }

    fun deleteVehicle() {
        if (!isEditMode || isSaving || hasSaved) return
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
        if (isSaving || hasSaved) return
        val vehicle = try {
            session.requireWritable(workspaceOwner)
            if (isEditMode && loadedVehicle == null) throw FormValidationException("Attendez le chargement du véhicule.")
            isYearError = year.trim().toIntOrNull()?.let { it !in 1900..Calendar.getInstance().get(Calendar.YEAR) } ?: true
            isPowerError = powerHp.isNotBlank() && (powerHp.trim().toIntOrNull()?.let { it !in 1..3000 } ?: true)
            isCapacityError = listOf(tankCapacity, batteryCapacity).any { text ->
                val parsed = runCatching { NumericInput.decimal(text, "Capacité") }.getOrNull()
                text.isNotBlank() && (parsed == null || parsed <= 0 || parsed > 1000)
            }
            val now = System.currentTimeMillis()
            Vehicle(ownerKey = workspaceOwner, id = currentVehicleId, brand = brand.trim(), model = model.trim(),
                year = NumericInput.integer(year, "Année"),
                currentMileage = if (mileage.isBlank()) currentVehicleMileage else NumericInput.integer(mileage, "Kilométrage"),
                fuelType = fuelType, type = vehicleType, licensePlate = licensePlate.takeIf { it.isNotBlank() },
                powerHp = NumericInput.optionalInteger(powerHp, "Puissance"),
                tankCapacity = NumericInput.optionalDecimal(tankCapacity, "Réservoir"),
                batteryCapacity = NumericInput.optionalDecimal(batteryCapacity, "Batterie"),
                createdAt = loadedVehicle?.createdAt ?: now, updatedAt = now
            ).also {
                GarageValidation.vehicle(it)
                if (isEditMode && mileage.isNotBlank() && it.currentMileage < currentVehicleMileage)
                    throw FormValidationException("Le compteur ne peut pas diminuer. Utilisez un relevé historique de plein ou d'entretien.")
            }
        } catch (e: Exception) {
            showErrors = true
            viewModelScope.launch { _uiEvent.send(UiEvent.ShowSnackbar(
                if (e is FormValidationException) e.message!! else "Rouvrez ce formulaire dans l'espace actif."
            )) }
            return
        }
        isSaving = true
        viewModelScope.launch {
            try {
                saveVehicleUseCase(vehicle)
                hasSaved = true
                _uiEvent.send(UiEvent.Success)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("AddEditVehicle", "Échec sauvegarde locale", e)
                _uiEvent.send(UiEvent.ShowSnackbar(if (e is FormValidationException) e.message!! else
                    "Enregistrement impossible. Rouvrez le formulaire ou réessayez."))
            } finally { isSaving = false }
        }
    }
}
