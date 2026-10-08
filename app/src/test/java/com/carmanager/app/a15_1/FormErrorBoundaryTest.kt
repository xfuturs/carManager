package com.carmanager.app.a15_1

import android.content.Context
import androidx.lifecycle.*
import com.carmanager.app.consistency.GarageFixture
import com.carmanager.app.core.data.local.dao.VehicleReferenceDao
import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.repository.*
import com.carmanager.app.core.domain.session.*
import com.carmanager.app.core.util.UiEvent
import com.carmanager.app.features.fuel.*
import com.carmanager.app.features.maintenance.*
import com.carmanager.app.features.settings.SettingsViewModel
import com.carmanager.app.features.vehicles.*
import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@OptIn(ExperimentalCoroutinesApi::class)
class FormErrorBoundaryTest {
    enum class Form { VEHICLE, FUEL, MAINTENANCE }
    enum class Preference { CURRENCY, DISTANCE }
    private val dispatcher=StandardTestDispatcher()
    private val garage=GarageFixture()
    private val context=mockk<Context>()
    private val vehicles=mockk<VehicleRepository>()
    private val references=mockk<VehicleReferenceDao>()
    private val fuel=mockk<FuelRepository>()
    private val maintenance=mockk<MaintenanceRepository>()
    private val store=ViewModelStore()
    private var source: Flow<Vehicle?> = flowOf(garage.vehicle())
    private val handle get()=SavedStateHandle(mapOf("vehicleId" to 1L))
    @BeforeEach fun setup() {
        Dispatchers.setMain(dispatcher)
        every { vehicles.observeById(1) } answers { source }
        every { references.getAllBrands() } returns flowOf(listOf("Renault"))
        every { references.getModelsForBrand(any()) } returns flowOf(listOf("Clio"))
        coEvery { references.getCount() } returns 1
    }
    @AfterEach fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun vehicle()=AddEditVehicleViewModel(SaveVehicleUseCase(vehicles),DeleteVehicleUseCase(vehicles),vehicles,references,context,garage.session,handle).also { store.put("vehicle",it) }
    private fun fuel()=AddFuelViewModel(SaveFuelRecordUseCase(fuel),vehicles,context,garage.session,handle).also { store.put("fuel",it) }
    private fun maintenance()=AddMaintenanceViewModel(SaveMaintenanceUseCase(maintenance,vehicles,garage.session,context),vehicles,context,garage.session,handle).also { store.put("maintenance",it) }
    @Test fun `vehicle database load exception is terminal sanitized and retryable`()=runTest(dispatcher) {
        source=flow { throw IOException("raw path SQLite") }; val vm=vehicle(); runCurrent()
        assertFalse(vm.isVehicleLoading); assertNotNull(vm.loadError)
        assertFalse((vm.uiEvent.first() as UiEvent.ShowSnackbar).message.contains("SQLite"))
        source=flowOf(garage.vehicle()); vm.retryLoading(); runCurrent(); assertNull(vm.loadError); assertEquals(garage.vehicle().brand,vm.brand)
    }
    @Test fun `missing edited vehicle has terminal failure instead of spinner`()=runTest(dispatcher) {
        source=flowOf(null); val vm=vehicle(); runCurrent(); assertFalse(vm.isVehicleLoading); assertNotNull(vm.loadError)
        vm.save(); runCurrent(); coVerify(exactly=0) { vehicles.saveVehicle(any()) }
    }
    @Test fun `committed vehicle cleanup warning cannot trigger a second business deletion`()=runTest(dispatcher) {
        coEvery { vehicles.deleteVehicle(any()) } throws com.carmanager.app.core.data.local.VehicleCleanupPendingException()
        val vm=vehicle(); runCurrent(); val events=mutableListOf<UiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiEvent.collect { events += it } }
        vm.deleteVehicle(); vm.deleteVehicle(); runCurrent(); vm.deleteVehicle(); runCurrent()
        assertTrue(vm.hasSaved); assertFalse(vm.isSaving); assertEquals(1,events.count { it==UiEvent.Success })
        assertTrue(events.filterIsInstance<UiEvent.ShowSnackbar>().single().message.startsWith("Véhicule supprimé"))
        coVerify(exactly=1) { vehicles.deleteVehicle(any()) }
    }
    @Test fun `stale vehicle load never overwrites typed input after owner transition`()=runTest(dispatcher) {
        val gate=CompletableDeferred<Unit>(); source=flow { gate.await(); emit(garage.vehicle()) }
        val vm=vehicle(); runCurrent(); vm.onBrandChange("Saisie manuelle",true)
        garage.session.beginBootstrap(); gate.complete(Unit); runCurrent()
        assertEquals("Saisie manuelle",vm.brand); assertNotNull(vm.loadError); assertFalse(vm.isVehicleLoading)
    }
    @Test fun `fuel load failure keeps manual input and retry restores real mileage`()=runTest(dispatcher) {
        source=flow { throw IOException("database") }; val vm=fuel(); vm.onMileageChange("1234"); runCurrent()
        assertFalse(vm.isVehicleLoaded); assertNotNull(vm.loadError); assertFalse(vm.isVehicleLoading)
        source=flowOf(garage.vehicle()); vm.retryLoading(); runCurrent()
        assertTrue(vm.isVehicleLoaded); assertEquals(1000,vm.currentVehicleMileage); assertEquals("1234",vm.mileage); assertNull(vm.loadError)
    }
    @Test fun `maintenance load failure cannot be saved as a fake zero mileage`()=runTest(dispatcher) {
        source=flow { throw IOException("database") }; val vm=maintenance(); runCurrent()
        assertNotNull(vm.loadError); assertFalse(vm.isVehicleLoading); assertFalse(vm.isVehicleLoaded)
        vm.save(); runCurrent(); coVerify(exactly=0) { maintenance.saveMaintenanceRecord(any()) }
        source=flowOf(garage.vehicle()); vm.retryLoading(); runCurrent(); assertEquals(1000,vm.currentVehicleMileage); assertTrue(vm.isVehicleLoaded)
    }
    @ParameterizedTest @EnumSource(Form::class)
    fun `cancelled form load is rethrown without fake error or success`(form: Form)=runTest(dispatcher) {
        val gate=CompletableDeferred<Unit>(); source=flow { gate.await(); throw CancellationException("cancelled load") }
        val vm: ViewModel=when(form) { Form.VEHICLE -> vehicle(); Form.FUEL -> fuel(); Form.MAINTENANCE -> maintenance() }
        runCurrent(); val jobs=vm.viewModelScope.coroutineContext[Job]!!.children.toList()
        gate.complete(Unit); runCurrent(); assertTrue(jobs.any { it.isCancelled })
        when(vm) {
            is AddEditVehicleViewModel -> { assertFalse(vm.isVehicleLoading); assertNull(vm.loadError); assertFalse(vm.hasSaved) }
            is AddFuelViewModel -> { assertFalse(vm.isVehicleLoading); assertNull(vm.loadError); assertFalse(vm.isVehicleLoaded) }
            is AddMaintenanceViewModel -> { assertFalse(vm.isVehicleLoading); assertNull(vm.loadError); assertFalse(vm.isVehicleLoaded) }
        }
    }
    private fun settings(repository: SettingsRepository): SettingsViewModel {
        every { repository.themePreference } returns flowOf(AppTheme.LIGHT)
        every { repository.currency } returns flowOf("€")
        every { repository.distanceUnit } returns flowOf("km")
        val auth=mockk<AuthRepository>(); every { auth.currentUser } returns MutableStateFlow(null)
        val premium=mockk<PremiumRepository>(); every { premium.isPremium } returns MutableStateFlow(false); every { premium.state } returns MutableStateFlow(PremiumState())
        val deletion=mockk<AccountDeletion>(); every { deletion.state } returns MutableStateFlow(DeletionState())
        return SettingsViewModel(repository,auth,premium,deletion,garage.session,garage.registry).also { store.put("settings",it) }
    }
    @ParameterizedTest @EnumSource(Preference::class)
    fun `preference write failure publishes error while persisted value remains displayed`(preference: Preference)=runTest(dispatcher) {
        val repository=mockk<SettingsRepository>(); coEvery { repository.setCurrency(any()) } throws IOException("DataStore")
        coEvery { repository.setDistanceUnit(any()) } throws IOException("DataStore")
        val vm=settings(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.currency.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.distanceUnit.collect {} }; runCurrent()
        when(preference) { Preference.CURRENCY -> vm.setCurrency("$"); Preference.DISTANCE -> vm.setDistanceUnit("mi") }
        runCurrent(); assertTrue(vm.preferenceEvents.first().contains("non enregistrée")); assertEquals("€",vm.currency.value); assertEquals("km",vm.distanceUnit.value)
    }
    @ParameterizedTest @EnumSource(Preference::class)
    fun `preference cancellation remains cancellation without failure feedback`(preference: Preference)=runTest(dispatcher) {
        val repository=mockk<SettingsRepository>(); val gate=CompletableDeferred<Unit>()
        coEvery { repository.setCurrency(any()) } coAnswers { gate.await(); throw CancellationException() }
        coEvery { repository.setDistanceUnit(any()) } coAnswers { gate.await(); throw CancellationException() }
        val vm=settings(repository); val events=mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.preferenceEvents.collect { events += it } }
        when(preference) { Preference.CURRENCY -> vm.setCurrency("$"); Preference.DISTANCE -> vm.setDistanceUnit("mi") }
        runCurrent(); val jobs=vm.viewModelScope.coroutineContext[Job]!!.children.toList(); gate.complete(Unit); runCurrent()
        assertTrue(jobs.any { it.isCancelled }); assertTrue(events.isEmpty())
    }
    @Test fun `Coach missing vehicle reaches ready unavailable rather than eternal loading`()=runTest(dispatcher) {
        source=flowOf(null); val vm=MaintenanceAdviceViewModel(vehicles,handle,garage.session); store.put("coach",vm)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }; runCurrent()
        assertEquals(LocalDataState.Ready<Vehicle?>("local:device",null),vm.uiState.value)
    }
    @Test fun `Coach source exception reaches Error and explicit retry reaches Ready`()=runTest(dispatcher) {
        source=flow { throw IOException("database") }; val vm=MaintenanceAdviceViewModel(vehicles,handle,garage.session); store.put("coach",vm)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }; runCurrent()
        assertEquals(LocalDataState.Error("local:device"),vm.uiState.value)
        source=flowOf(garage.vehicle()); vm.retryLoading(); runCurrent(); assertEquals(LocalDataState.Ready("local:device",garage.vehicle()),vm.uiState.value)
    }
}
