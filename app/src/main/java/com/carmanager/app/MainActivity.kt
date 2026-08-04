package com.carmanager.app

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import com.carmanager.app.core.domain.repository.AppTheme
import com.carmanager.app.core.domain.repository.PremiumRepository
import com.carmanager.app.core.domain.repository.SettingsRepository
import com.carmanager.app.core.domain.repository.SyncRepository
import com.carmanager.app.core.ui.CarManagerApp
import com.carmanager.app.core.ui.theme.AppUnits
import com.carmanager.app.core.ui.theme.CarManagerTheme
import com.carmanager.app.core.util.GoogleMobileAdsConsentManager
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var premiumRepository: PremiumRepository

    @Inject
    lateinit var syncRepository: SyncRepository

    private lateinit var googleMobileAdsConsentManager: GoogleMobileAdsConsentManager
    private val isMobileAdsInitializeCalled = AtomicBoolean(false)
    private var canShowAds by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val isTablet = resources.getBoolean(R.bool.isTablet)
        if (!isTablet) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        premiumRepository.initialize()
        syncRepository.startAutoSync()

        googleMobileAdsConsentManager = GoogleMobileAdsConsentManager(this)
        googleMobileAdsConsentManager.gatherConsent { error ->
            if (error != null) {
                // Log error or handle
            }
            if (googleMobileAdsConsentManager.canRequestAds()) {
                initializeMobileAdsSdk()
            }
        }

        // Si le consentement a déjà été donné précédemment
        if (googleMobileAdsConsentManager.canRequestAds()) {
            initializeMobileAdsSdk()
        }
        
        enableEdgeToEdge()
        setContent {
            val themePref by settingsRepository.themePreference.collectAsState(initial = AppTheme.SYSTEM)
            val currency by settingsRepository.currency.collectAsState(initial = "€")
            val distanceUnit by settingsRepository.distanceUnit.collectAsState(initial = "km")
            val isPremium by premiumRepository.isPremium.collectAsState()
            
            val useDarkTheme = when(themePref) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }

            CarManagerTheme(
                darkTheme = useDarkTheme,
                units = AppUnits(currency = currency, distance = distanceUnit)
            ) {
                CarManagerApp(
                    canShowAds = canShowAds && !isPremium,
                    isPrivacyOptionsRequired = googleMobileAdsConsentManager.isPrivacyOptionsRequired(),
                    onPrivacyOptionsClick = {
                        googleMobileAdsConsentManager.showPrivacyOptionsForm { error ->
                            if (error != null) {
                                // Log error
                            }
                        }
                    }
                )
            }
        }
    }

    private fun initializeMobileAdsSdk() {
        if (isMobileAdsInitializeCalled.getAndSet(true)) {
            return
        }
        MobileAds.initialize(this) {
            canShowAds = true
        }
    }
}
