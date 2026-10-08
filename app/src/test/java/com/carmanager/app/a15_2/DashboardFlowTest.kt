package com.carmanager.app.a15_2

import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.repository.*
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.features.dashboard.*
import com.carmanager.app.ownership.TestDeletionRegistry
import io.mockk.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardFlowTest {
    private class Fixture(dispatcher: CoroutineDispatcher, time: DashboardTimeSource) {
        val session=WorkspaceSession(TestDeletionRegistry()).apply { completeBootstrap() }
        val vehicles=MutableStateFlow(listOf(vehicle()))
        val fuel=MutableStateFlow(listOf(fill(cost=10.0)))
        val maintenance=MutableStateFlow(listOf(intervention(cost=20.0)))
        val documents=MutableStateFlow(emptyList<Document>())
        val f=mockk<FuelRepository>(); val m=mockk<MaintenanceRepository>()
        val useCase=GetDashboardStatsUseCase(mockk<VehicleRepository>().also { every { it.observeAll() } returns vehicles },
            f.also { every { it.observeAll() } returns fuel }, m.also { every { it.observeAll() } returns maintenance },
            mockk<DocumentRepository>().also { every { it.observeAll() } returns documents },session,time,dispatcher)
    }
    @Test fun `calculation runs only when the injected computation dispatcher executes without Main`() = runTest {
        val tasks=ArrayDeque<Runnable>()
        val cpu=object: CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { tasks.addLast(block) }
        }
        val fixture=Fixture(cpu,DashboardTimeSource({october.instant},{october.zone}))
        val values=mutableListOf<DashboardStats>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.useCase().toList(values) }
        runCurrent(); assertTrue(values.isEmpty()); assertFalse(tasks.isEmpty())
        while (tasks.isNotEmpty()) { tasks.removeFirst().run(); runCurrent() }
        assertEquals(30.0,values.single().vehicles.single().totalExpenses)
        verify(exactly=0) { fixture.f.observeMonthlyTotal(any()) }; verify(exactly=0) { fixture.m.observeNextUpcoming() }
    }
    @Test fun `month changes update both totals without Room invalidation`() = runTest {
        val initial=Instant.parse("2026-10-31T22:59:00Z")
        val fixture=Fixture(StandardTestDispatcher(testScheduler),DashboardTimeSource({initial.plusMillis(testScheduler.currentTime)},{october.zone}))
        val values=mutableListOf<DashboardStats>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.useCase().toList(values) }
        runCurrent(); assertEquals(10.0,values.last().monthlyFuelCost); assertEquals(20.0,values.last().monthlyMaintenanceCost)
        advanceTimeBy(60000); runCurrent()
        assertEquals(0.0,values.last().monthlyFuelCost); assertEquals(0.0,values.last().monthlyMaintenanceCost)
        assertEquals(30.0,values.last().vehicles.single().totalExpenses)
    }
    @Test fun `active resume refresh changes zone periods without database writes`() = runTest {
        var zone=ZoneId.of("UTC")
        val fixture=Fixture(StandardTestDispatcher(testScheduler),DashboardTimeSource({Instant.parse("2026-11-01T00:30:00Z")},{zone}))
        fixture.fuel.value=listOf(fill(date=Instant.parse("2026-10-31T23:30:00Z").toEpochMilli(),cost=10.0))
        val values=mutableListOf<DashboardStats>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.useCase().toList(values) }
        runCurrent(); assertEquals(0.0,values.last().monthlyFuelCost)
        zone=ZoneId.of("America/Los_Angeles"); fixture.useCase.refreshTime(); runCurrent()
        assertEquals(10.0,values.last().monthlyFuelCost); assertEquals(zone,values.last().temporalContext!!.zone)
    }
    @Test fun `midnight updates overdue alerts and next maintenance without record invalidation`() = runTest {
        val initial=Instant.parse("2026-10-07T21:59:00Z")
        val fixture=Fixture(StandardTestDispatcher(testScheduler),DashboardTimeSource({initial.plusMillis(testScheduler.currentTime)},{october.zone}))
        fixture.maintenance.value=listOf(intervention(due=initial.plusSeconds(30).toEpochMilli()))
        val values=mutableListOf<DashboardStats>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.useCase().toList(values) }
        runCurrent(); assertNotNull(values.last().nextMaintenance); assertTrue(values.last().vehicles.single().alerts.isEmpty())
        advanceTimeBy(60000); runCurrent()
        assertNull(values.last().nextMaintenance); assertEquals(1,values.last().vehicles.single().alerts.size)
    }
    @Test fun `year rollover recomputes yearly maintenance without changing all time totals`() = runTest {
        val initial=Instant.parse("2026-12-31T22:59:00Z")
        val fixture=Fixture(StandardTestDispatcher(testScheduler),DashboardTimeSource({initial.plusMillis(testScheduler.currentTime)},{october.zone}))
        val values=mutableListOf<DashboardStats>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.useCase().toList(values) }
        runCurrent(); assertEquals(20.0,values.last().vehicles.single().yearlyMaintenanceCost)
        advanceTimeBy(60000); runCurrent()
        assertEquals(0.0,values.last().vehicles.single().yearlyMaintenanceCost)
        assertEquals(30.0,values.last().vehicles.single().totalExpenses)
    }
    @Test fun `ViewModel restart after stopped sharing immediately rehydrates current month`() = runTest {
        sharingResume(5001)
    }
    @Test fun `ViewModel resume during sharing grace window refreshes retained upstream`() = runTest {
        sharingResume(1000)
    }
    private suspend fun TestScope.sharingResume(stoppedMillis: Long) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store=androidx.lifecycle.ViewModelStore()
        try {
            var now=Instant.parse("2026-10-31T22:59:00Z")
            val fixture=Fixture(StandardTestDispatcher(testScheduler),DashboardTimeSource({now},{october.zone}))
            val settings=mockk<SettingsRepository>().also {
                every { it.currency } returns flowOf("€"); every { it.distanceUnit } returns flowOf("km")
            }
            val premium=mockk<PremiumRepository>().also { every { it.isPremium } returns MutableStateFlow(false) }
            val vm=DashboardViewModel(fixture.useCase,mockk(),settings,premium,mockk())
            store.put("dashboard",vm)
            val collector=backgroundScope.launch { vm.uiState.collect {} }
            runCurrent(); assertEquals(10.0,(vm.uiState.value as LocalDataState.Ready).data.monthlyFuelCost)
            collector.cancel(); runCurrent(); advanceTimeBy(stoppedMillis); runCurrent()
            if (stoppedMillis>5000) assertEquals(LocalDataState.Loading,vm.uiState.value)
            now=Instant.parse("2026-11-01T10:00:00Z")
            backgroundScope.launch { vm.uiState.collect {} }; vm.refreshTime(); runCurrent()
            val ready=vm.uiState.value as LocalDataState.Ready
            assertEquals(0.0,ready.data.monthlyFuelCost); assertEquals(YearMonth.of(2026,11),ready.data.temporalContext!!.yearMonth)
            assertEquals("local:device",ready.owner)
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }
}
