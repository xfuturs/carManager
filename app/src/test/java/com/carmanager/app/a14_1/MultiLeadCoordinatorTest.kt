package com.carmanager.app.a14_1

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.carmanager.app.consistency.GarageFixture
import com.carmanager.app.core.data.repository.ReminderSettingsStore
import com.carmanager.app.core.domain.repository.*
import com.carmanager.app.core.util.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MultiLeadCoordinatorTest {
    @TempDir lateinit var dir: File
    private fun scenario(body: suspend (LocalReminderCoordinator,ReminderSettingsStore,GarageFixture) -> Unit) = runBlocking {
        val job=SupervisorJob()
        val data=PreferenceDataStoreFactory.create(scope=CoroutineScope(job+Dispatchers.IO),produceFile={File(dir,"settings.preferences_pb")})
        val settings=ReminderSettingsStore(data); val garage=GarageFixture()
        val maintenance=mockk<MaintenanceRepository>(); val vehicles=mockk<VehicleRepository>()
        every { maintenance.observeAll() } returns flowOf(listOf(garage.maintenance().copy(id=9,nextDueDate=System.currentTimeMillis()+40*86400_000L)))
        every { vehicles.observeAll() } returns flowOf(listOf(garage.vehicle()))
        mockkObject(NotificationHelper)
        every { NotificationHelper.scheduleReminder(any(),any(),any(),any(),any(),any(),any(),any()) } just Runs
        every { NotificationHelper.cancelOwnedReminder(any(),any(),any(),any()) } just Runs
        try { body(LocalReminderCoordinator(settings,garage.session,garage.registry,maintenance,vehicles,mockk<Context>()),settings,garage) }
        finally { job.cancelAndJoin(); unmockkObject(NotificationHelper) }
    }
    @Test fun `actual boot entry restores all four leads and finishes bounded async work`() = scenario { coordinator,settings,_ ->
        settings.update { it.copy(leadDaysSet=setOf(30,7,1,0)) }
        val finished=CompletableDeferred<Unit>(); coordinator.afterBoot { finished.complete(Unit) }
        withTimeout(5000) { finished.await() }
        verify(exactly=4) { NotificationHelper.scheduleReminder(any(),any(),any(),any(),any(),any(),any(),any()) }
        assertEquals(setOf(0,1,7,30),settings.knownKeys().map { it.leadDays }.toSet())
        verify { NotificationHelper.cancelOwnedReminder(any(),"firebase:A",any(),null) }
    }
    @Test fun `actual boot with global off cancels legacy and every durable multi key`() = scenario { coordinator,settings,garage ->
        val id=9L
        settings.update { it.copy(enabled=false,leadDaysSet=setOf(30,7,1,0)) }
        settings.saveKeys(setOf(0,1,7,30).map { ReminderKey("firebase:A",id,it) }.toSet()+ReminderKey("firebase:A",id,null))
        val finished=CompletableDeferred<Unit>(); coordinator.afterBoot { finished.complete(Unit) }
        withTimeout(5000) { finished.await() }
        verify(exactly=0) { NotificationHelper.scheduleReminder(any(),any(),any(),any(),any(),any(),any(),any()) }
        verify(exactly=5) { NotificationHelper.cancelOwnedReminder(any(),any(),any(),any()) }
        assertTrue(settings.knownKeys().isEmpty())
    }
    @Test fun `permission eligibility requires an applicable future selected trigger`() = scenario { coordinator,settings,garage ->
        val soon=garage.maintenance().copy(nextDueDate=System.currentTimeMillis()+3600_000)
        settings.update { it.copy(leadDaysSet=setOf(30)) }; assertFalse(coordinator.enabledFor(soon))
        settings.update { it.toggleLead(0) }; assertTrue(coordinator.enabledFor(soon))
        settings.update { it.copy(enabled=false) }; assertFalse(coordinator.enabledFor(soon))
    }
    @Test fun `same coordinator reconciliation twice schedules no duplicate multi alarms`() = scenario { coordinator,settings,_ ->
        settings.update { it.copy(leadDaysSet=setOf(30,7,1,0)) }
        coordinator.reconcileNow(); coordinator.reconcileNow()
        verify(exactly=4) { NotificationHelper.scheduleReminder(any(),any(),any(),any(),any(),any(),any(),any()) }
    }
}
