package com.carmanager.app.a14_1

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.carmanager.app.consistency.GarageFixture
import com.carmanager.app.core.data.mapper.toEntity
import com.carmanager.app.core.data.repository.ReminderSettingsStore
import com.carmanager.app.core.util.ReminderReceiver
import io.mockk.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

class MultiLeadReceiverTest {
    @TempDir lateinit var dir: File
    @ParameterizedTest @ValueSource(strings=["legacy","removed","deleted","disabled","owner","valid"])
    fun `receiver revalidates URI record owner enabled state and selected lead before Android delivery`(mode:String) = runBlocking {
        val job=SupervisorJob(); val store=PreferenceDataStoreFactory.create(scope=CoroutineScope(job+Dispatchers.IO),
            produceFile={File(dir,"settings.preferences_pb")})
        try {
            val garage=GarageFixture(); val settings=ReminderSettingsStore(store)
            settings.update { it.copy(enabled=mode!="disabled",leadDaysSet=if(mode=="removed") setOf(0) else setOf(0,7)) }
            val due=System.currentTimeMillis()+30*86400_000L
            coEvery { garage.maintenanceDao.getById(9,"firebase:A") } returns
                if(mode=="deleted") null else garage.maintenance().copy(id=9,nextDueDate=due).toEntity()
            if(mode=="owner") garage.session.setAuthenticatedUid("B")
            val intent=mockk<Intent>(); val uri=mockk<Uri>(); val context=mockk<Context>(); val manager=mockk<NotificationManager>()
            every { intent.getStringExtra("ownerKey") } returns "firebase:A"
            every { intent.getLongExtra("recordId",0) } returns 9
            every { intent.getIntExtra("leadDays",any()) } returns 7
            every { intent.hasExtra("dueAt") } returns true
            every { intent.hasExtra("leadDays") } returns true
            every { intent.getLongExtra("dueAt",0) } returns due
            every { intent.data } returns uri; every { uri.scheme } returns "carmanager"
            every { uri.authority } returns "maintenance"; every { uri.lastPathSegment } returns "9"
            every { uri.getQueryParameter("owner") } returns "firebase:A"
            every { uri.getQueryParameter("leadDays") } returns if(mode=="legacy") null else "7"
            every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns manager
            every { manager.areNotificationsEnabled() } returns false
            val receiver=ReminderReceiver().apply {
                session=garage.session; deletionRegistry=garage.registry; this.settings=settings; maintenance=garage.maintenanceDao
            }
            receiver.deliver(context,intent)
            verify(exactly=if(mode=="valid") 1 else 0) { manager.areNotificationsEnabled() }
            if(mode=="legacy") coVerify(exactly=0) { garage.maintenanceDao.getById(any(),any()) }
        } finally { job.cancelAndJoin() }
    }
}
