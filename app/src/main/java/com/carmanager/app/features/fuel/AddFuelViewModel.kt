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
import com.carmanager.app.core.domain.validation.NumericInput
import com.carmanager.app.core.domain.validation.GarageValidation
import com.carmanager.app.core.domain.validation.FormValidationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import android.util.Log

@HiltViewModel
class AddFuelViewModel @Inject constructor(
    private val saveFuelRecordUseCase: SaveFuelRecordUseCase,
    private val vehicleRepository: VehicleRepository,
    @ApplicationContext private val context: Context,
    private val session: com.carmanager.app.core.domain.session.WorkspaceSession,
    savedStateHandle: SavedStateHandle,
    private val completionClock: com.carmanager.app.core.ads.ElapsedRealtimeClock = com.carmanager.app.core.ads.ElapsedRealtimeClock()
) : ViewModel() {
    private val workspaceOwner = session.owner.value

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

    var isSaving by mutableStateOf(false)
        private set
    var hasSaved by mutableStateOf(false)
        private set
    private val _uiEvent = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()
    private var completionHost: Any? = null
    private var savedHost: Any? = null
    private var completedSave: CompletedFuelSave? = null
    internal fun bindCompletionHost(token: Any) { completionHost = token }
    internal fun unbindCompletionHost(token: Any) { if (completionHost === token) completionHost = null }
    internal fun consumeCompletedSave(token: Any): CompletedFuelSave? {
        val result = completedSave.takeIf { !isSaving && hasSaved && savedHost === token && completionHost === token }
        completedSave = null
        savedHost = null
        return result
    }

    var isVehicleLoading by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    fun retryLoading() { loadCurrentVehicle() }

    var isVehicleLoaded by mutableStateOf(false)
        private set

    init { loadCurrentVehicle() }

    private fun loadCurrentVehicle() {
        if (isVehicleLoading) return
        isVehicleLoading = true
        isVehicleLoaded = false
        loadError = null
        viewModelScope.launch {
            try {
                session.requireWritable(workspaceOwner)
                val vehicle = vehicleRepository.observeById(vehicleId).firstOrNull()
                    ?: error("Véhicule absent.")
                session.requireWritable(workspaceOwner)
                check(vehicle.ownerKey == workspaceOwner && vehicle.id == vehicleId)
                tankCapacity = vehicle.tankCapacity
                batteryCapacity = vehicle.batteryCapacity
                fuelType = vehicle.fuelType
                currentVehicleMileage = vehicle.currentMileage
                isVehicleLoaded = true
                isElectricEntry = vehicle.fuelType == FuelType.ELECTRIC
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                loadError = "Véhicule indisponible. Réessayez ou rouvrez le formulaire."
                _uiEvent.send(UiEvent.ShowSnackbar(loadError!!))
            } finally { isVehicleLoading = false }
        }
    }

    fun onMileageChange(value: String) { mileage = value }
    fun onLitersChange(value: String) { liters = value }
    fun onTotalPriceChange(value: String) { totalPrice = value }
    fun onNoteChange(value: String) { note = value }
    fun onDateChange(value: Long) { date = value }
    fun onElectricEntryToggle(value: Boolean) { isElectricEntry = value }
    
    fun onEstimatedMileageSelect(increment: Int) {
        mileage = (currentVehicleMileage.toLong() + increment).toString()
    }

    fun onScanReceipt(uri: Uri) {
        if (isScanning) return
        viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            isScanning = true
            try {
                session.requireWritable(workspaceOwner)
                val result = OcrHelper.analyzeImage(context, uri)
                session.requireWritable(workspaceOwner)
                when (result) {
                    is OcrHelper.Analysis.Values -> {
                        result.result.totalPrice?.let { totalPrice = "%.2f".format(it).replace(",", ".") }
                        result.result.liters?.let { liters = "%.2f".format(it).replace(",", ".") }
                        _uiEvent.send(UiEvent.ShowSnackbar("Analyse terminée"))
                    }
                    OcrHelper.Analysis.NoValues -> _uiEvent.send(UiEvent.ShowSnackbar("Aucune valeur reconnue. Vous pouvez saisir les informations manuellement."))
                    OcrHelper.Analysis.Failed -> _uiEvent.send(UiEvent.ShowSnackbar("Analyse impossible. Réessayez ou saisissez les informations manuellement."))
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiEvent.send(UiEvent.ShowSnackbar("Analyse impossible. Réessayez ou saisissez les informations manuellement."))
            } finally { isScanning = false }
        }
    }

    fun save() {
        if (isSaving || hasSaved || isScanning) return
        val record = try {
            session.requireWritable(workspaceOwner)
            if (!isVehicleLoaded) throw FormValidationException("Attendez le chargement du véhicule.")
            FuelRecord(ownerKey = workspaceOwner, vehicleId = vehicleId, date = date,
                mileage = if (mileage.isBlank()) currentVehicleMileage else NumericInput.integer(mileage, "Kilométrage"),
                liters = NumericInput.decimal(liters, if (isElectricEntry) "kWh" else "Litres"),
                totalPrice = NumericInput.decimal(totalPrice, "Prix total"),
                note = note.takeIf { it.isNotBlank() }, isElectric = isElectricEntry
            ).also { GarageValidation.fuel(it, if (isElectricEntry) batteryCapacity else tankCapacity) }
        } catch (e: Exception) {
            showErrors = true
            viewModelScope.launch { _uiEvent.send(UiEvent.ShowSnackbar(
                if (e is FormValidationException) e.message!! else "Rouvrez ce formulaire dans l'espace actif."
            )) }
            return
        }
        isSaving = true
        val savingHost = completionHost
        viewModelScope.launch {
            try {
                val id = saveFuelRecordUseCase(record)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                hasSaved = true
                isSaving = false
                completedSave = CompletedFuelSave(record.copy(id = id), completionClock.now())
                savedHost = savingHost
                _uiEvent.send(UiEvent.Success)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("AddFuel", "Échec sauvegarde locale", e)
                _uiEvent.send(UiEvent.ShowSnackbar(if (e is FormValidationException) e.message!! else
                    "Sauvegarde impossible. Rouvrez le formulaire ou réessayez."))
            } finally { isSaving = false }
        }
    }
}
