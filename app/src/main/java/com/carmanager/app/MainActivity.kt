package com.carmanager.app

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.core.domain.session.AccountDeletion
import com.carmanager.app.core.domain.session.DeletionStage
import androidx.lifecycle.ViewModelProvider
import com.carmanager.app.core.ui.WorkspaceViewModelStores
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.carmanager.app.core.ui.startup.AppearanceBootstrapState
import com.carmanager.app.core.ui.startup.StartupViewModel
import com.carmanager.app.core.ui.startup.usesDarkColors
import com.carmanager.app.core.ui.startup.canComposeLocalApp
import com.carmanager.app.core.ui.components.LocalWorkspaceOwner
import com.carmanager.app.core.domain.repository.PremiumRepository
import com.carmanager.app.core.domain.repository.SettingsRepository
import com.carmanager.app.core.ui.CarManagerApp
import com.carmanager.app.core.ui.theme.AppUnits
import com.carmanager.app.core.ui.theme.CarManagerTheme
import com.carmanager.app.core.util.GoogleMobileAdsConsentManager
import com.carmanager.app.core.ads.MobileAdsInitializer
import com.carmanager.app.core.ads.adsEligible
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var premiumRepository: PremiumRepository


    @Inject lateinit var session: WorkspaceSession
    @Inject lateinit var deletion: AccountDeletion
    @Inject lateinit var adsInitializer: MobileAdsInitializer

    private lateinit var googleMobileAdsConsentManager: GoogleMobileAdsConsentManager

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_CarManager)
        super.onCreate(savedInstanceState)
        val startup = ViewModelProvider(this)[StartupViewModel::class.java]
        var appearanceComposed = false
        val content: View = findViewById(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!appearanceComposed) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
        
        val isTablet = resources.getBoolean(R.bool.isTablet)
        if (!isTablet) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        premiumRepository.initialize()

        googleMobileAdsConsentManager = GoogleMobileAdsConsentManager(this)
        googleMobileAdsConsentManager.gatherConsent { /* L'eligibilite vient toujours de l'etat UMP. */ }
        
        enableEdgeToEdge()
        val workspaceStores = ViewModelProvider(this)[WorkspaceViewModelStores::class.java]
        setContent {
            val appearance by startup.appearance.collectAsState()
            val workspaceResolved by session.isResolved.collectAsState()
            SideEffect { appearanceComposed = appearance is AppearanceBootstrapState.Ready }
            val currency by settingsRepository.currency.collectAsState(initial = "€")
            val distanceUnit by settingsRepository.distanceUnit.collectAsState(initial = "km")
            val isPremium by premiumRepository.isPremium.collectAsState()
            val premiumState by premiumRepository.state.collectAsState()
            val consentState by googleMobileAdsConsentManager.state.collectAsState()
            val adsReady by adsInitializer.ready.collectAsState()
            // Attendre la premiere verification Play ; ne pas recréer une pub a chaque refresh ulterieur.
            var premiumChecked by remember { mutableStateOf(!premiumState.isLoading) }
            LaunchedEffect(premiumState.isLoading, consentState.canRequestAds, isPremium) {
                if (!premiumState.isLoading) premiumChecked = true
                if (premiumChecked) adsInitializer.initializeIfEligible(consentState.canRequestAds, isPremium)
            }
            val canShowAds = adsReady && premiumChecked && adsEligible(consentState.canRequestAds, isPremium)
            val owner by session.owner.collectAsState()
            val deletionState by deletion.state.collectAsState()
            val readyAppearance = appearance as? AppearanceBootstrapState.Ready
            if (!canComposeLocalApp(appearance, workspaceResolved)) {
                Box(Modifier.fillMaxSize().background(colorResource(R.color.startup_surface)))
                return@setContent
            }
            val workspaceStore = remember(owner) { workspaceStores.forOwner(owner) }
            val useDarkTheme = checkNotNull(readyAppearance).theme.usesDarkColors(isSystemInDarkTheme())

            CarManagerTheme(
                darkTheme = useDarkTheme,
                units = AppUnits(currency = currency, distance = distanceUnit)
            ) {
                // Recrée le contrôleur de navigation et les ViewModels liés à ses entrées.
                key(owner) { CompositionLocalProvider(
                    LocalViewModelStoreOwner provides workspaceStore,
                    LocalWorkspaceOwner provides owner
                ) { CarManagerApp(
                    canShowAds = canShowAds && !isPremium,
                    isPrivacyOptionsRequired = consentState.showPrivacyOptions,
                    onPrivacyOptionsClick = {
                        googleMobileAdsConsentManager.showPrivacyOptionsForm { /* Etat UMP republie au retour. */ }
                    }
                ) } }
                if (!deletionState.running && deletionState.stage != null) {
                    AlertDialog(
                        onDismissRequest = { deletion.acknowledgeResult() },
                        title = { Text(if (deletionState.stage == DeletionStage.COMPLETE) "Compte supprimé" else "Suppression incomplète") },
                        text = { Text(deletionState.error ?: "Les données de ce compte sur cette installation et ses collections cloud connues ont été supprimées. L'espace invité est actif.") },
                        confirmButton = { TextButton(onClick = { deletion.acknowledgeResult() }) { Text("Fermer") } }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        premiumRepository.checkPremiumStatus()
        if (::googleMobileAdsConsentManager.isInitialized) googleMobileAdsConsentManager.refreshState()
    }
}
