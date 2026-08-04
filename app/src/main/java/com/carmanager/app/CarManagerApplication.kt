package com.carmanager.app

import android.app.Application
import com.carmanager.app.core.util.NotificationHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CarManagerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
    }
}
