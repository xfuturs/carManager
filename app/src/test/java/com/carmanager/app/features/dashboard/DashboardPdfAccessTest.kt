package com.carmanager.app.features.dashboard

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.repository.PremiumRepository
import com.carmanager.app.core.domain.repository.SettingsRepository
import com.carmanager.app.features.documents.ReportSummary
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardPdfAccessTest {
    private val dispatcher = StandardTestDispatcher()
    private val premium = MutableStateFlow(false)
    private val reports = mockk<StoreVehicleReportUseCase>()
    private val store = ViewModelStore()
    private lateinit var vm: DashboardViewModel
    private lateinit var observationJobs: Set<Job>
    private val saved = Document(12, 7, "Rapport existant", DocumentCategory.REPORTS, "/private/report.pdf", 123)

    @BeforeEach fun setup() {
        Dispatchers.setMain(dispatcher)
        val settings = mockk<SettingsRepository>()
        every { settings.currency } returns flowOf("€")
        every { settings.distanceUnit } returns flowOf("km")
        val entitlement = mockk<PremiumRepository>()
        every { entitlement.isPremium } returns premium
        coEvery { reports.previousReports(7) } returns ReportSummary(1, 123)
        coEvery { reports(7, any()) } returns saved
        vm = DashboardViewModel(mockk(), mockk(), settings, entitlement, reports)
        store.put("pdf", vm)
        observationJobs = vm.viewModelScope.coroutineContext[Job]!!.children.toSet()
    }
    @AfterEach fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private suspend fun finish() {
        vm.viewModelScope.coroutineContext[Job]!!.children.filter { it !in observationJobs }.toList().joinAll()
    }

    @Test fun `non Premium cannot open configuration through a direct or stale callback`() = runTest(dispatcher) {
        vm.prepareReport(7); runCurrent()
        assertNull(vm.reportDraft.value); assertFalse(vm.isPreparingReport)
        coVerify(exactly = 0) { reports.previousReports(any()) }
    }
    @Test fun `non Premium direct generation never reaches renderer or storage`() = runTest(dispatcher) {
        vm.generateReport(7); vm.confirmReportDraft(); runCurrent()
        assertNull(vm.generatedReport.value); assertFalse(vm.isGeneratingReport)
        coVerify(exactly = 0) { reports(any(), any()) }
    }
    @Test fun `Premium prepares previous report summary and generates selected sections`() = runTest(dispatcher) {
        premium.value = true; vm.prepareReport(7)
        val draft = vm.reportDraft.first { it != null }!!
        assertEquals(ReportSummary(1, 123), draft.previous)
        vm.toggleReportSection(ReportSection.MILEAGE_HISTORY)
        val selected = vm.reportDraft.value!!.selected
        vm.confirmReportDraft()
        assertEquals(saved, vm.generatedReport.first { it != null })
        coVerify(exactly = 1) { reports(7, selected) }
    }
    @Test fun `entitlement lost while configuration is open denies confirmation`() = runTest(dispatcher) {
        premium.value = true; vm.prepareReport(7); vm.reportDraft.first { it != null }
        premium.value = false; vm.confirmReportDraft(); runCurrent()
        assertNull(vm.reportDraft.value); assertNull(vm.generatedReport.value)
        coVerify(exactly = 0) { reports(any(), any()) }
    }
    @Test fun `entitlement lost before preparation coroutine runs prevents its IO`() = runTest(dispatcher) {
        premium.value = true; vm.prepareReport(7); premium.value = false
        runCurrent(); finish()
        assertNull(vm.reportDraft.value); assertFalse(vm.isPreparingReport)
        coVerify(exactly = 0) { reports.previousReports(any()) }
    }
    @Test fun `entitlement lost during previous report lookup cannot publish a generation draft`() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        coEvery { reports.previousReports(7) } coAnswers { started.complete(Unit); release.await(); ReportSummary(1, 123) }
        premium.value = true; vm.prepareReport(7); started.await()
        premium.value = false; release.complete(Unit); finish()
        assertNull(vm.reportDraft.value); assertFalse(vm.isPreparingReport)
        coVerify(exactly = 0) { reports(any(), any()) }
    }
    @Test fun `entitlement lost before generation coroutine runs prevents renderer IO`() = runTest(dispatcher) {
        premium.value = true; vm.generateReport(7); premium.value = false
        runCurrent(); finish()
        assertNull(vm.generatedReport.value); assertFalse(vm.isGeneratingReport)
        coVerify(exactly = 0) { reports(any(), any()) }
    }
    @Test fun `Premium direct generation remains available and closing result performs no deletion`() = runTest(dispatcher) {
        premium.value = true; vm.generateReport(7)
        assertEquals(saved, vm.generatedReport.first { it != null })
        finish(); vm.closeReportResult(); assertNull(vm.generatedReport.value)
        coVerify(exactly = 1) { reports(7, ReportSection.entries.toSet()) }
        assertEquals("/private/report.pdf", saved.filePath)
    }
}
