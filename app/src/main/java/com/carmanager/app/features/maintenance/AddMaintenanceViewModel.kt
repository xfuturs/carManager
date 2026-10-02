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
import com.carmanager.app.core.domain.validation.NumericInput
import com.carmanager.app.core.domain.validation.GarageValidation
import com.carmanager.app.core.domain.validation.FormValidationException
import kotlinx.coroutines.CancellationException
import android.util.Log
import com.carmanager.app.R

@HiltViewModel
class AddMaintenanceViewModel @Inject constructor(
    private val saveMaintenanceUseCase: SaveMaintenanceUseCase,
    private val vehicleRepository: VehicleRepository,
    @ApplicationContext private val context: Context,
    private val session: com.carmanager.app.core.domain.session.WorkspaceSession,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val workspaceOwner = session.owner.value

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

    var isSaving by mutableStateOf(false)
        private set
    var hasSaved by mutableStateOf(false)
        private set
    private val _uiEvent = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()
    private val _notificationPermissionRequests = Channel<Unit>(Channel.BUFFERED)
    val notificationPermissionRequests = _notificationPermissionRequests.receiveAsFlow()
    private var awaitingNotificationPermission = false
    private var postSaveWarning: String? = null

    var isVehicleLoaded by mutableStateOf(false)
        private set

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
                isVehicleLoaded = true
                mileage = "" // Optionnel par défaut
            }
        }
    }

    fun onTypeChange(value: MaintenanceType) { 
        if (!isTypeLocked) type = value
    }
    fun onMileageChange(value: String) { mileage = value }
    fun onCostChange(value: String) { cost = value }
    fun onNoteChange(value: String) { note = value }
    fun onNextDueMileageChange(value: String) { nextDueMileage = value }
    fun onNextDueDateChange(value: Long?) { nextDueDate = value }
    fun onDateChange(value: Long) { date = value }

    fun onEstimatedMileageSelect(increment: Int) {
        mileage = (currentVehicleMileage.toLong() + increment).toString()
    }

    fun applyMileageIncrement(increment: Int) {
        val base = mileage.toIntOrNull() ?: currentVehicleMileage
        nextDueMileage = (base.toLong() + increment).toString()
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

    fun save(notificationPermission: NotificationPermissionStatus = NotificationPermissionStatus.NOT_REQUIRED) {
        if (isSaving || hasSaved || isScanning) return
        val record = try {
            session.requireWritable(workspaceOwner)
            if (!isVehicleLoaded) throw FormValidationException("Attendez le chargement du véhicule.")
            MaintenanceRecord(ownerKey = workspaceOwner, vehicleId = vehicleId, type = type, date = date,
                mileage = if (mileage.isBlank()) currentVehicleMileage else NumericInput.integer(mileage, "Kilométrage"),
                cost = if (cost.isBlank()) 0.0 else NumericInput.decimal(cost, "Coût"),
                note = note.takeIf { it.isNotBlank() }, nextDueDate = nextDueDate,
                nextDueMileage = NumericInput.optionalInteger(nextDueMileage, "Prochaine échéance kilométrique")
            ).also { GarageValidation.maintenance(it) }
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
                val result = saveMaintenanceUseCase(record)
                hasSaved = true
                postSaveWarning = result.warning
                if (notificationPermission == NotificationPermissionStatus.MISSING &&
                    record.nextDueDate?.let { it > System.currentTimeMillis() } == true) {
                    // Le commit et la programmation sont déjà terminés. Le callback ne sauvegarde jamais.
                    awaitingNotificationPermission = true
                    _notificationPermissionRequests.send(Unit)
                } else {
                    completeSave(notificationsGranted = true)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("AddMaintenance", "Échec sauvegarde locale", e)
                _uiEvent.send(UiEvent.ShowSnackbar(if (e is FormValidationException) e.message!! else
                    "Sauvegarde impossible. Rouvrez le formulaire ou réessayez."))
            } finally { isSaving = false }
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        if (!hasSaved || !awaitingNotificationPermission) return
        // Consommer le résultat avant de lancer la coroutine : un second callback est ignoré.
        awaitingNotificationPermission = false
        viewModelScope.launch { completeSave(notificationsGranted = granted) }
    }

    private suspend fun completeSave(notificationsGranted: Boolean) {
        val warning = if (notificationsGranted) postSaveWarning else context.getString(
            if (postSaveWarning == null) R.string.maintenance_saved_notifications_disabled
            else R.string.maintenance_saved_reminder_failed_notifications_disabled
        )
        warning?.let { _uiEvent.send(UiEvent.ShowSnackbar(it)) }
        _uiEvent.send(UiEvent.Success)
    }
}
