package com.carmanager.app

import android.app.Application
import com.carmanager.app.core.util.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import com.carmanager.app.core.domain.repository.AuthRepository
import javax.inject.Inject

@HiltAndroidApp
class CarManagerApplication : Application() {
    @Inject lateinit var authRepository: AuthRepository
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
    }
}
