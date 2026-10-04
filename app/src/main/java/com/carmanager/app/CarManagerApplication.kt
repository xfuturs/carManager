package com.carmanager.app

import android.app.Application
import com.carmanager.app.core.util.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import com.carmanager.app.core.domain.repository.AuthRepository
import com.carmanager.app.core.ads.InterstitialAdManager
import javax.inject.Inject

@HiltAndroidApp
class CarManagerApplication : Application() {
    @Inject lateinit var authRepository: AuthRepository
    // Creation process-scoped du compteur, sans requete publicitaire au constructeur.
    @Inject lateinit var interstitials: InterstitialAdManager
    @Inject lateinit var reminders: com.carmanager.app.core.util.LocalReminderCoordinator
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
        reminders.start()
    }
}
