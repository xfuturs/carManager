package com.carmanager.app.core.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Une seule lecture d'horloge et de fuseau pour tout un calcul. */
data class TemporalContext(val instant: Instant, val zone: ZoneId) {
    val localDate: LocalDate = instant.atZone(zone).toLocalDate()
    val yearMonth: YearMonth = YearMonth.from(localDate)
    val monthStart: Long = yearMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val nextMonthStart: Long = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val yearStart: Long = localDate.withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val nextYearStart: Long = localDate.withDayOfYear(1).plusYears(1).atStartOfDay(zone).toInstant().toEpochMilli()
    fun daysUntil(timestamp: Long): Long = java.time.temporal.ChronoUnit.DAYS.between(
        localDate, Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate())
}
