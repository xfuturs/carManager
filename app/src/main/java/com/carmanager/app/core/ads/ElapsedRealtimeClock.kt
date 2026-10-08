package com.carmanager.app.core.ads

import android.os.SystemClock
import javax.inject.Inject

class ElapsedRealtimeClock @Inject constructor() : MonotonicClock {
    override fun now(): Long = SystemClock.elapsedRealtime()
}
