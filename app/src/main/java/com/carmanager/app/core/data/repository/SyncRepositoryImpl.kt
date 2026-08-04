package com.carmanager.app.core.data.repository

import com.carmanager.app.core.data.local.dao.*
import com.carmanager.app.core.domain.repository.AuthRepository
import com.carmanager.app.core.domain.repository.SyncRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val authRepository: AuthRepository,
    private val vehicleDao: VehicleDao,
    private val fuelDao: FuelRecordDao,
    private val maintenanceDao: MaintenanceDao
) : SyncRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _isSyncing = MutableStateFlow(false)
    override val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    override suspend fun syncAll() {
        val user = authRepository.currentUser.value ?: return
        _isSyncing.value = true
        
        try {
            coroutineScope {
                // 1. Sync Véhicules
                val localVehicles = vehicleDao.getAll()
                localVehicles.forEach { vehicle ->
                    firestore.collection("users")
                        .document(user.id)
                        .collection("vehicles")
                        .document(vehicle.id.toString())
                        .set(vehicle)
                }
                
                // 2. Sync Carburant
                val localFuel = fuelDao.getAll()
                localFuel.forEach { record ->
                    firestore.collection("users")
                        .document(user.id)
                        .collection("fuel_records")
                        .document(record.id.toString())
                        .set(record)
                }

                // 3. Sync Maintenance
                val localMaint = maintenanceDao.getAll()
                localMaint.forEach { record ->
                    firestore.collection("users")
                        .document(user.id)
                        .collection("maintenance_records")
                        .document(record.id.toString())
                        .set(record)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isSyncing.value = false
        }
    }

    override fun startAutoSync() {
        scope.launch {
            combine(
                vehicleDao.observeAll(),
                fuelDao.observeAll(),
                maintenanceDao.observeAll()
            ) { _, _, _ -> 
                syncAll() 
            }.collect()
        }
    }
}
