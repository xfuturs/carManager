package com.carmanager.app.features.dashboard

import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.repository.*
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.core.domain.session.observeLocalState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetDashboardStatsUseCase internal constructor(
    private val vehicleRepository: VehicleRepository,
    private val fuelRepository: FuelRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val documentRepository: DocumentRepository,
    private val session: WorkspaceSession,
    private val time: DashboardTimeSource,
    private val computation: CoroutineDispatcher
) {
    @Inject constructor(vehicles: VehicleRepository, fuel: FuelRepository, maintenance: MaintenanceRepository,
                        documents: DocumentRepository, session: WorkspaceSession) :
        this(vehicles, fuel, maintenance, documents, session, DashboardTimeSource(), Dispatchers.Default)

    private val refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    fun refreshTime() { refresh.tryEmit(Unit) }
    operator fun invoke(): Flow<DashboardStats> = session.observe { calculateWorkspace() }
    fun observeState(retry: Flow<Int>): Flow<LocalDataState<DashboardStats>> =
        observeLocalState(session, retry) { calculateWorkspace() }

    private fun calculateWorkspace(): Flow<DashboardStats> = combine(
        vehicleRepository.observeAll(), fuelRepository.observeAll(), maintenanceRepository.observeAll(),
        documentRepository.observeAll(), time.observe(refresh)
    ) { vehicles, fuel, maintenance, documents, temporal ->
        withContext(computation) { DashboardCalculator.calculate(vehicles, fuel, maintenance, documents, temporal) }
    }
}
