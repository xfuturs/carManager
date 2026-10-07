package com.carmanager.app.a15_1

import android.content.Context
import android.net.Uri
import androidx.lifecycle.*
import com.carmanager.app.consistency.GarageFixture
import com.carmanager.app.core.domain.repository.*
import com.carmanager.app.core.util.*
import com.carmanager.app.features.fuel.*
import com.carmanager.app.features.maintenance.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@OptIn(ExperimentalCoroutinesApi::class)
class OcrViewModelTest {
    enum class Form { FUEL, MAINTENANCE }
    private val dispatcher=StandardTestDispatcher()
    private val garage=GarageFixture()
    private val vehicles=mockk<VehicleRepository>()
    private val fuel=mockk<FuelRepository>()
    private val maintenance=mockk<MaintenanceRepository>()
    private val context=mockk<Context>()
    private val uri=mockk<Uri>()
    private val store=ViewModelStore()
    private data class Screen(val vm: ViewModel,val scan: () -> Unit,val scanning: () -> Boolean,val edit: (String) -> Unit,val price: () -> String,val events: Flow<UiEvent>)
    @BeforeEach fun setup() {
        Dispatchers.setMain(dispatcher); mockkObject(OcrHelper)
        every { vehicles.observeById(1) } returns flowOf(garage.vehicle())
    }
    @AfterEach fun cleanup() { store.clear(); unmockkObject(OcrHelper); Dispatchers.resetMain() }
    private fun screen(form: Form): Screen {
        val handle=SavedStateHandle(mapOf("vehicleId" to 1L))
        val screen=when(form) {
            Form.FUEL -> AddFuelViewModel(SaveFuelRecordUseCase(fuel),vehicles,context,garage.session,handle).let {
                Screen(it,{ it.onScanReceipt(uri) },{ it.isScanning },it::onTotalPriceChange,{ it.totalPrice },it.uiEvent)
            }
            Form.MAINTENANCE -> AddMaintenanceViewModel(SaveMaintenanceUseCase(maintenance,vehicles,garage.session,context),vehicles,context,garage.session,handle).let {
                Screen(it,{ it.onScanReceipt(uri) },{ it.isScanning },it::onCostChange,{ it.cost },it.uiEvent)
            }
        }; store.put(form.name,screen.vm); return screen
    }
    @ParameterizedTest @EnumSource(Form::class)
    fun `active scan rejects second action and proposals remain manually editable without saving`(form: Form)=runTest(dispatcher) {
        val gate=CompletableDeferred<OcrHelper.Analysis>(); coEvery { OcrHelper.analyzeImage(context,uri) } coAnswers { gate.await() }
        val screen=screen(form); runCurrent(); screen.scan(); screen.scan(); assertTrue(screen.scanning())
        coVerify(exactly=1) { OcrHelper.analyzeImage(context,uri) }
        gate.complete(OcrHelper.Analysis.Values(OcrHelper.OcrResult(totalPrice=42.5,liters=20.0))); runCurrent()
        assertFalse(screen.scanning()); assertEquals("42.50",screen.price()); screen.edit("39,90"); assertEquals("39,90",screen.price())
        assertEquals("Analyse terminée",(screen.events.first() as UiEvent.ShowSnackbar).message)
        coVerify(exactly=0) { fuel.saveFuelRecord(any()) }; coVerify(exactly=0) { maintenance.saveMaintenanceRecord(any()) }
    }
    @ParameterizedTest @EnumSource(Form::class)
    fun `failed analysis differs from successful no value and never publishes analysed success`(form: Form)=runTest(dispatcher) {
        val screen=screen(form); runCurrent(); val messages=mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { screen.events.collect { if(it is UiEvent.ShowSnackbar) messages += it.message } }
        for(result in listOf(OcrHelper.Analysis.Failed,OcrHelper.Analysis.NoValues)) {
            coEvery { OcrHelper.analyzeImage(context,uri) } returns result; screen.scan(); runCurrent(); assertFalse(screen.scanning())
        }
        assertEquals(2,messages.size); assertTrue(messages[0].contains("impossible")); assertTrue(messages[1].contains("Aucune valeur")); assertFalse(messages.any { it=="Analyse terminée" })
    }
    @ParameterizedTest @EnumSource(Form::class)
    fun `scan cancellation propagates and finally clears spinner without publishing failure`(form: Form)=runTest(dispatcher) {
        val gate=CompletableDeferred<Unit>(); coEvery { OcrHelper.analyzeImage(context,uri) } coAnswers { gate.await(); throw CancellationException() }
        val screen=screen(form); runCurrent(); val events=mutableListOf<UiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { screen.events.collect { events += it } }
        screen.scan(); val job=screen.vm.viewModelScope.coroutineContext[Job]!!.children.last(); assertTrue(screen.scanning())
        gate.complete(Unit); runCurrent(); assertTrue(job.isCancelled); assertFalse(screen.scanning()); assertTrue(events.isEmpty())
    }
    @ParameterizedTest @EnumSource(Form::class)
    fun `owner change during recognition cannot publish values into stale form`(form: Form)=runTest(dispatcher) {
        val gate=CompletableDeferred<OcrHelper.Analysis>(); coEvery { OcrHelper.analyzeImage(context,uri) } coAnswers { gate.await() }
        val screen=screen(form); runCurrent(); screen.edit("10"); screen.scan(); garage.session.setAuthenticatedUid("B")
        gate.complete(OcrHelper.Analysis.Values(OcrHelper.OcrResult(totalPrice=42.5))); runCurrent()
        assertEquals("10",screen.price()); assertFalse(screen.scanning()); assertTrue((screen.events.first() as UiEvent.ShowSnackbar).message.contains("impossible"))
    }
}
