package com.carmanager.app.features.fuel

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.carmanager.app.consistency.GarageFixture
import com.carmanager.app.core.ads.ElapsedRealtimeClock
import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.repository.*
import com.carmanager.app.core.util.UiEvent
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class FuelSaveCompletionTest {
    private val dispatcher=StandardTestDispatcher()
    private val garage=GarageFixture()
    private val repository=mockk<FuelRepository>()
    private val vehicles=mockk<VehicleRepository>()
    private val context=mockk<Context>()
    private val clock=mockk<ElapsedRealtimeClock>()
    private val host=Any()
    private val store=ViewModelStore()
    private val events=mutableListOf<UiEvent>()
    private val record=FuelRecord(id=9,vehicleId=1,date=0,mileage=1500,liters=10.0,totalPrice=20.0)
    @BeforeEach fun setup() {
        Dispatchers.setMain(dispatcher); mockkStatic(Log::class)
        every { Log.e(any(),any(),any<Throwable>()) } returns 0
        every { vehicles.observeById(1) } returns flowOf(garage.vehicle().copy(tankCapacity=100.0,batteryCapacity=50.0))
        every { clock.now() } returns 60_000L
        coEvery { repository.saveFuelRecord(any()) } returns 9L
    }
    @AfterEach fun cleanup() { store.clear(); Dispatchers.resetMain(); unmockkStatic(Log::class) }
    private fun TestScope.vm(): AddFuelViewModel {
        val vm=AddFuelViewModel(SaveFuelRecordUseCase(repository),vehicles,context,garage.session,SavedStateHandle(mapOf("vehicleId" to 1L)),clock)
        store.put("fuel",vm); vm.bindCompletionHost(host)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiEvent.collect { events+=it } }
        runCurrent(); vm.onLitersChange("10"); vm.onTotalPriceChange("20"); vm.onMileageChange("1500"); vm.onDateChange(0)
        return vm
    }
    @Test fun durableWriteThenSuccessOffersOneReceiptWithReturnedId()=runTest(dispatcher) {
        val gate=CompletableDeferred<Unit>()
        coEvery { repository.saveFuelRecord(any()) } coAnswers { gate.await(); 9L }
        val vm=vm(); vm.save(); vm.save(); runCurrent()
        assertTrue(vm.isSaving); assertNull(vm.consumeCompletedSave(host)); assertTrue(events.isEmpty())
        gate.complete(Unit); runCurrent(); assertFalse(vm.isSaving); assertEquals(listOf(UiEvent.Success),events)
        val receipt=checkNotNull(vm.consumeCompletedSave(host)); assertEquals(record,receipt.record); assertEquals(60_000L,receipt.completedAtMs)
        assertNull(vm.consumeCompletedSave(host)); vm.save(); runCurrent(); coVerify(exactly=1) { repository.saveFuelRecord(any()) }
    }
    @Test fun failedWriteHasNoCompletion()=runTest(dispatcher) {
        coEvery { repository.saveFuelRecord(any()) } throws IllegalStateException("database failure")
        val vm=vm(); vm.save(); runCurrent(); assertNull(vm.consumeCompletedSave(host)); assertFalse(vm.hasSaved)
        assertTrue(events.single() is UiEvent.ShowSnackbar)
    }
    @Test fun validationFailureHasNoCompletionOrWrite()=runTest(dispatcher) {
        val vm=vm(); vm.onLitersChange("0"); vm.save(); runCurrent()
        assertNull(vm.consumeCompletedSave(host)); coVerify(exactly=0) { repository.saveFuelRecord(any()) }
    }
    @Test fun canceledWriteHasNoSuccessOrReceipt()=runTest(dispatcher) {
        coEvery { repository.saveFuelRecord(any()) } coAnswers { awaitCancellation() }
        val vm=vm(); vm.save(); runCurrent(); store.clear(); runCurrent()
        assertTrue(events.isEmpty()); assertNull(vm.consumeCompletedSave(host))
    }
    @Test fun rotationDuringWriteCannotCreateOpportunityInNewCollector()=runTest(dispatcher) {
        val gate=CompletableDeferred<Unit>(); coEvery { repository.saveFuelRecord(any()) } coAnswers { gate.await(); 9L }
        val vm=vm(); vm.save(); runCurrent(); vm.unbindCompletionHost(host); val rotated=Any(); vm.bindCompletionHost(rotated)
        gate.complete(Unit); runCurrent(); assertEquals(listOf(UiEvent.Success),events); assertNull(vm.consumeCompletedSave(rotated))
    }
    @Test fun stoppingCollectorDiscardsItsReceipt()=runTest(dispatcher) {
        val vm=vm(); vm.save(); runCurrent(); vm.unbindCompletionHost(host)
        assertNull(vm.consumeCompletedSave(host)); vm.bindCompletionHost(host); assertNull(vm.consumeCompletedSave(host))
    }
    private val completion get()=CompletedFuelSave(record,60_000)
    private val ready get()=LocalDataState.Ready("local:device",listOf(record))
    @Test fun committedRecordMustBeVisibleOnResumedNonScrollingHistory() {
        assertTrue(isFuelSaveResultVisible(completion,ready,setOf(9L),true,false,60_500))
        assertFalse(isFuelSaveResultVisible(completion,ready,emptySet(),true,false,60_500))
        assertFalse(isFuelSaveResultVisible(completion,ready,setOf(9L),false,false,60_500))
        assertFalse(isFuelSaveResultVisible(completion,ready,setOf(9L),true,true,60_500))
    }
    @Test fun loadingErrorOldSnapshotOrWrongOwnerCannotConfirmSuccess() {
        for(state in listOf(LocalDataState.Loading,LocalDataState.Error("local:device"),
            LocalDataState.Ready("other",listOf(record)),LocalDataState.Ready("local:device",listOf(record.copy(totalPrice=1.0))))) {
            assertFalse(isFuelSaveResultVisible(completion,state,setOf(9L),true,false,60_500))
        }
    }
    @Test fun resultBecomingAvailableAfterExpiryCannotPresent() {
        assertFalse(isFuelSaveResultVisible(completion,ready,setOf(9L),true,false,61_500))
        assertFalse(isFuelSaveResultVisible(completion,ready,setOf(9L),true,false,59_999))
    }
    @Test fun successfulRechargePreservesElectricFlagAndReceipt()=runTest(dispatcher) {
        every { vehicles.observeById(1) } returns flowOf(garage.vehicle().copy(fuelType=FuelType.ELECTRIC,batteryCapacity=50.0))
        val vm=vm(); vm.save(); runCurrent(); assertTrue(checkNotNull(vm.consumeCompletedSave(host)).record.isElectric)
    }
}
