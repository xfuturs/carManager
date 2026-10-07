package com.carmanager.app.features.dashboard

import com.carmanager.app.core.domain.model.TemporalContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

/** Réveil au prochain jour local ; chaque reprise relit aussi le fuseau système. */
class DashboardTimeSource(
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) {
    fun current() = TemporalContext(now(), zone())

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(refresh: Flow<Unit> = emptyFlow()): Flow<TemporalContext> =
        merge(flowOf(Unit), refresh).transformLatest {
            while (true) {
                val context = current()
                emit(context)
                val nextDay = context.localDate.plusDays(1).atStartOfDay(context.zone).toInstant()
                delay(Duration.between(context.instant, nextDay).toMillis().coerceAtLeast(1))
            }
        }
}
