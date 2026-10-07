package com.carmanager.app.a15_2

import com.carmanager.app.core.domain.model.TemporalContext
import com.carmanager.app.features.dashboard.DashboardTimeSource
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardTimeSourceTest {
    @Test fun `same day does not poll or emit every minute`() = runTest {
        var reads = 0
        val base = Instant.parse("2026-10-07T08:00:00Z")
        val source = DashboardTimeSource({ reads++; base.plusMillis(testScheduler.currentTime) }, { ZoneId.of("Europe/Paris") })
        val values = mutableListOf<TemporalContext>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.observe().toList(values) }
        runCurrent(); advanceTimeBy(3600000); runCurrent()
        assertEquals(1, reads); assertEquals(1, values.size)
    }
    @Test fun `2359 to midnight emits the next local day`() = runTest { rollover("2026-10-06T21:59:00Z", "2026-10-07", "2026-10") }
    @Test fun `month end changes year month immediately`() = runTest { rollover("2026-10-31T22:59:00Z", "2026-11-01", "2026-11") }
    @Test fun `December to January changes year boundaries`() = runTest { rollover("2026-12-31T22:59:00Z", "2027-01-01", "2027-01") }
    @Test fun `leap February has a real 29th day`() = runTest { rollover("2028-02-28T22:59:00Z", "2028-02-29", "2028-02") }
    @Test fun `leap February ends after the 29th`() = runTest { rollover("2028-02-29T22:59:00Z", "2028-03-01", "2028-03") }
    private suspend fun TestScope.rollover(initial: String, date: String, month: String) {
        val base=Instant.parse(initial)
        val source=DashboardTimeSource({base.plusMillis(testScheduler.currentTime)}, { ZoneId.of("Europe/Paris") })
        val values=mutableListOf<TemporalContext>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.observe().toList(values) }
        runCurrent(); assertEquals(1,values.size); advanceTimeBy(60000); runCurrent()
        assertEquals(2,values.size); assertEquals(LocalDate.parse(date),values.last().localDate)
        assertEquals(YearMonth.parse(month),values.last().yearMonth)
    }
    @Test fun `resume with timezone changing date immediately refreshes context`() = runTest {
        var zone=ZoneId.of("UTC"); val refresh=MutableSharedFlow<Unit>(extraBufferCapacity=1)
        val source=DashboardTimeSource({ Instant.parse("2026-10-07T01:00:00Z") }, {zone})
        val values=mutableListOf<TemporalContext>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.observe(refresh).toList(values) }
        runCurrent(); zone=ZoneId.of("America/Los_Angeles"); refresh.emit(Unit); runCurrent()
        assertEquals(LocalDate.parse("2026-10-06"),values.last().localDate)
        assertEquals(zone,values.last().zone); assertEquals(2,values.size)
    }
    @Test fun `same date timezone change is not suppressed and reschedules next midnight`() = runTest {
        val base=Instant.parse("2026-10-07T10:00:00Z"); var zone=ZoneId.of("UTC")
        val refresh=MutableSharedFlow<Unit>(extraBufferCapacity=1)
        val source=DashboardTimeSource({base.plusMillis(testScheduler.currentTime)}, {zone})
        val values=mutableListOf<TemporalContext>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.observe(refresh).toList(values) }
        runCurrent(); zone=ZoneId.of("Europe/Paris"); refresh.emit(Unit); runCurrent()
        assertEquals(2,values.size); assertEquals(values.first().localDate,values.last().localDate)
        assertEquals(zone,values.last().zone)
        advanceTimeBy(12*3600000L); runCurrent()
        assertEquals(LocalDate.parse("2026-10-08"),values.last().localDate)
    }
    @Test fun `resubscription after missed midnight immediately reads new date`() = runTest {
        var now=Instant.parse("2026-10-07T21:59:00Z")
        val source=DashboardTimeSource({now},{ZoneId.of("Europe/Paris")})
        assertEquals(LocalDate.parse("2026-10-07"),source.observe().first().localDate)
        now=Instant.parse("2026-11-01T10:00:00Z")
        assertEquals(YearMonth.of(2026,11),source.observe().first().yearMonth)
    }
    @Test fun `DST day schedules next local midnight rather than 24 hours`() = runTest {
        val base=Instant.parse("2026-10-24T22:00:00Z")
        val source=DashboardTimeSource({base.plusMillis(testScheduler.currentTime)},{ZoneId.of("Europe/Paris")})
        val values=mutableListOf<TemporalContext>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.observe().toList(values) }
        runCurrent(); advanceTimeBy(24*3600000L); runCurrent(); assertEquals(1,values.size)
        advanceTimeBy(3600000); runCurrent(); assertEquals(2,values.size)
    }
    @Test fun `deadline days use local dates and show yesterday overdue even under 24 hours`() {
        val context=TemporalContext(Instant.parse("2026-10-07T22:05:00Z"),ZoneId.of("Europe/Paris"))
        assertEquals(-1,context.daysUntil(Instant.parse("2026-10-07T21:59:00Z").toEpochMilli()))
        assertEquals(0,context.daysUntil(Instant.parse("2026-10-08T12:00:00Z").toEpochMilli()))
    }
}
