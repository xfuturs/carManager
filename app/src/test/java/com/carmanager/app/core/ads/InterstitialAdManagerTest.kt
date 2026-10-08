package com.carmanager.app.core.ads

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import com.carmanager.app.core.domain.model.PremiumEntitlement
import com.carmanager.app.core.domain.model.PremiumState
import com.carmanager.app.core.domain.repository.PremiumRepository
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/** Adaptateur réel testé avec SDK et host simulés : aucune requête publicitaire réseau. */
class InterstitialAdManagerTest {
    private var now=0L
    private var resumed=true
    private var focused=true
    private var finishing=false
    private var destroyed=false
    private var ime=false
    private var missingInsets=false
    private var consentAllowed=true
    private val ready=MutableStateFlow(true)
    private val premiumState=MutableStateFlow(PremiumState(entitlement=PremiumEntitlement.FREE,isLoading=false,ownershipVerified=true))
    private val activity=mockk<ComponentActivity>()
    private val ad=mockk<InterstitialAd>()
    private val loads=mutableListOf<InterstitialAdLoadCallback>()
    private var callback:FullScreenContentCallback?=null
    private lateinit var manager:InterstitialAdManager
    private val safe=InterstitialPresentability("fuel/{vehicleId}",true,true,true,false,destinationId="history")

    @BeforeEach fun setup() {
        mockkStatic(SystemClock::class,Log::class,ViewCompat::class,UserMessagingPlatform::class,InterstitialAd::class)
        every { SystemClock.elapsedRealtime() } answers { now }
        every { Log.d(any(),any<String>()) } returns 0
        val context=mockk<Context>(); every { context.applicationContext } returns context
        val consent=mockk<ConsentInformation>(); every { consent.canRequestAds() } answers { consentAllowed }
        every { UserMessagingPlatform.getConsentInformation(context) } returns consent
        val initializer=mockk<MobileAdsInitializer>(); every { initializer.ready } returns ready
        val premium=mockk<PremiumRepository>(); every { premium.state } returns premiumState
        val window=mockk<Window>(); val view=mockk<View>(); val insets=mockk<WindowInsetsCompat>()
        every { activity.window } returns window; every { window.decorView } returns view
        every { ViewCompat.getRootWindowInsets(view) } answers { if(missingInsets) null else insets }
        every { insets.isVisible(any()) } answers { ime }
        val lifecycle=mockk<Lifecycle>()
        every { activity.lifecycle } returns lifecycle
        every { lifecycle.currentState } answers { if(resumed) Lifecycle.State.RESUMED else Lifecycle.State.STARTED }
        every { activity.hasWindowFocus() } answers { focused }
        every { activity.isFinishing } answers { finishing }; every { activity.isDestroyed } answers { destroyed }
        every { InterstitialAd.load(any(),any(),any(),any()) } answers { loads+=lastArg<InterstitialAdLoadCallback>(); Unit }
        every { ad.fullScreenContentCallback=any() } answers { callback=firstArg(); Unit }
        every { ad.show(activity) } just Runs
        manager=InterstitialAdManager(context,initializer,premium,AdsEntitlementGate())
    }
    @AfterEach fun cleanup() { unmockkStatic(SystemClock::class,Log::class,ViewCompat::class,UserMessagingPlatform::class,InterstitialAd::class) }
    private fun enable() { manager.updateEligibility(true); manager.updateForeground(safe) }
    private fun loaded() { loads.last().onAdLoaded(ad) }
    private fun due() { enable(); loaded(); now=60_000L }
    private fun show(state:InterstitialPresentability=safe) { manager.onNaturalBreak(activity,state,manager.createNaturalBreak(NaturalBreakWorkflow.FuelRecordSaved,now,"history")) }
    private fun assertNoShow() { verify(exactly=0) { ad.show(any()) } }

    @Test fun initialForegroundDeadlineRequiresANewNaturalBreak() {
        enable(); loaded(); assertEquals(60_000L,manager.nextWakeDelay())
        now=59_999L; show(); assertNoShow()
        now=60_000L; show(); verify(exactly=1) { ad.show(activity) }
    }
    @Test fun pauseCannotShowEvenIfComposeSnapshotSaysResumed() { due(); resumed=false; show(); assertNoShow(); assertNull(manager.nextWakeDelay()) }
    @Test fun finishingActivityCannotShow() { due(); finishing=true; show(); assertNoShow() }
    @Test fun destroyedActivityCannotShow() { due(); destroyed=true; show(); assertNoShow() }
    @Test fun actualWindowFocusIsRecheckedAtShowBoundary() { due(); focused=false; show(); assertNoShow() }
    @Test fun actualImeIsRecheckedEvenIfComposeSnapshotSaysHidden() { due(); ime=true; show(); assertNoShow(); assertNull(manager.nextWakeDelay()) }
    @Test fun unknownRootInsetsRemainFailClosed() { due(); missingInsets=true; show(); assertNoShow() }
    @Test fun everyUnsafeRootAndFormRouteBlocksActualSdkShow() {
        due(); for(route in listOf("calculators","vehicles","settings","login","vehicle/edit","fuel/add/1","maintenance/add/1","documents/1","stats")) show(safe.copy(route=route))
        assertNoShow(); show(); verify(exactly=1) { ad.show(activity) }
    }
    @Test fun activeModalDefersAndClosingItDoesNotResetDue() {
        due(); show(safe.copy(modalActive=true)); assertNoShow(); assertNull(manager.nextWakeDelay())
        show(); verify(exactly=1) { ad.show(activity) }
    }
    @Test fun unfinishedDestinationTransitionDefersUntilResumed() {
        due(); show(safe.copy(destinationResumed=false)); assertNoShow()
        show(); verify(exactly=1) { ad.show(activity) }
    }
    @Test fun premiumActiveBeforeShowRevokesCachedAd() {
        due(); premiumState.value=premiumState.value.copy(entitlement=PremiumEntitlement.ACTIVE)
        show(); assertNoShow(); assertNull(manager.nextWakeDelay())
    }
    @Test fun pendingAndUnverifiedOwnershipStayBlocked() {
        due(); premiumState.value=premiumState.value.copy(entitlement=PremiumEntitlement.PENDING)
        show(); assertNoShow()
        premiumState.value=PremiumState(); show(); assertNoShow()
    }
    @Test fun actualUmpValueIsRecheckedImmediatelyBeforeShow() { due(); consentAllowed=false; show(); assertNoShow(); assertNull(manager.nextWakeDelay()) }
    @Test fun sdkNotReadyCannotShowAnAlreadyCachedAd() { due(); ready.value=false; show(); assertNoShow() }
    @Test fun globalDeletionOrPrivacyGateBlocksEvenWhenRealEntitlementIsFree() { due(); manager.updateEligibility(false); show(); assertNoShow() }
    @Test fun realShownCallbackAdvancesTo80AndFullscreenTimeIsExcluded() {
        due(); show(); val actual=checkNotNull(callback); actual.onAdShowedFullScreenContent()
        now+=600_000L; actual.onAdDismissedFullScreenContent(); loaded(); manager.updateForeground(safe)
        assertEquals(80_000L,manager.nextWakeDelay())
        now+=79_999L; show(); verify(exactly=1) { ad.show(activity) }
        now++; show(); verify(exactly=2) { ad.show(activity) }
    }
    @Test fun failedShowKeepsDueAndUsesSixtySecondRetry() {
        due(); show(); val error=mockk<AdError>(); every { error.code } returns 1
        checkNotNull(callback).onAdFailedToShowFullScreenContent(error); manager.updateForeground(safe)
        assertEquals(60_000L,manager.nextWakeDelay())
        now+=59_999L; show(); assertEquals(1,loads.size)
        now++; show(); assertEquals(2,loads.size); loaded(); show(); verify(exactly=2) { ad.show(activity) }
    }
    @Test fun failedLoadCannotAdvanceCadenceAndLateSuccessRequiresFreshBreak() {
        enable(); val error=mockk<LoadAdError>(); every { error.code } returns 1
        loads.last().onAdFailedToLoad(error); now=60_000L; show(); assertEquals(2,loads.size)
        assertNoShow(); loaded(); show(); verify(exactly=1) { ad.show(activity) }
    }
    @Test fun hostDetachPausesProcessCadenceWithoutResettingIt() {
        enable(); loaded(); now=30_000L; manager.detachHost(); now+=600_000L; enable(); loaded()
        assertEquals(30_000L,manager.nextWakeDelay()); show(); assertNoShow()
        now+=30_000L; show(); verify(exactly=1) { ad.show(activity) }
    }
    @Test fun duplicateShownCallbackCannotSkipProgressiveInterval() {
        due(); show(); val actual=checkNotNull(callback)
        actual.onAdShowedFullScreenContent(); actual.onAdShowedFullScreenContent(); actual.onAdDismissedFullScreenContent(); loaded(); manager.updateForeground(safe)
        assertEquals(80_000L,manager.nextWakeDelay())
    }
    @Test fun timerExpiryAndAllPassiveHostUpdatesNeverPresent() {
        enable(); loaded(); now=60_000L
        val dashboard=safe.copy(route="dashboard")
        repeat(3) { manager.onClockOrHostChanged(dashboard) }
        manager.onClockOrHostChanged(dashboard.copy(hostResumed=false))
        now+=600_000L
        manager.onClockOrHostChanged(dashboard)
        manager.onClockOrHostChanged(dashboard.copy(windowFocused=false))
        manager.onClockOrHostChanged(dashboard.copy(modalActive=true))
        manager.onClockOrHostChanged(dashboard.copy(imeVisible=true))
        manager.onClockOrHostChanged(dashboard)
        manager.updateEligibility(false); manager.updateEligibility(true); loaded()
        manager.onClockOrHostChanged(dashboard)
        assertNoShow()
    }
    @Test fun loadedAfterMissedBreakCannotShowOrReuseTheOldEvent() {
        enable(); now=60_000L
        val event=manager.createNaturalBreak(NaturalBreakWorkflow.FuelRecordSaved,now,"history")
        manager.onNaturalBreak(activity,safe,event); assertNoShow()
        loaded(); manager.onClockOrHostChanged(safe); assertNoShow()
        manager.onNaturalBreak(activity,safe,event); assertNoShow()
        show(); verify(exactly=1) { ad.show(activity) }
    }
    @Test fun resumeAndRefocusCannotReviveAMissedBreak() {
        due(); val event=manager.createNaturalBreak(NaturalBreakWorkflow.FuelRecordSaved,now,"history")
        resumed=false; manager.onNaturalBreak(activity,safe,event)
        resumed=true; focused=false; manager.onClockOrHostChanged(safe.copy(windowFocused=false))
        focused=true; manager.onClockOrHostChanged(safe)
        manager.onNaturalBreak(activity,safe,event); assertNoShow()
    }
    @Test fun consentRestorationRequiresAFreshBreak() {
        due(); val event=manager.createNaturalBreak(NaturalBreakWorkflow.FuelRecordSaved,now,"history")
        consentAllowed=false; manager.onNaturalBreak(activity,safe,event)
        consentAllowed=true; manager.onClockOrHostChanged(safe); loaded()
        manager.onNaturalBreak(activity,safe,event); assertNoShow()
        show(); verify(exactly=1) { ad.show(activity) }
    }
    @Test fun premiumSettlementCannotReplayABlockedEvent() {
        due(); val event=manager.createNaturalBreak(NaturalBreakWorkflow.FuelRecordSaved,now,"history")
        premiumState.value=PremiumState(); manager.onNaturalBreak(activity,safe,event)
        premiumState.value=PremiumState(entitlement=PremiumEntitlement.FREE,isLoading=false,ownershipVerified=true)
        manager.onClockOrHostChanged(safe); loaded()
        manager.onNaturalBreak(activity,safe,event); assertNoShow()
    }
    @Test fun aNewUserGestureInvalidatesAnUnpresentedCompletion() {
        due(); val event=manager.createNaturalBreak(NaturalBreakWorkflow.FuelRecordSaved,now,"history")
        manager.userInteraction(); manager.onNaturalBreak(activity,safe,event); assertNoShow()
    }
    @Test fun aRestoredOrDifferentDestinationCannotUseThePermit() {
        due(); val event=manager.createNaturalBreak(NaturalBreakWorkflow.FuelRecordSaved,now,"history")
        manager.onNaturalBreak(activity,safe.copy(destinationId="restored"),event); assertNoShow()
        manager.onNaturalBreak(activity,safe,event); assertNoShow()
    }
    @Test fun expiredCompletionCannotRequestSdkShow() {
        due(); val event=manager.createNaturalBreak(NaturalBreakWorkflow.EvRechargeSaved,now,"history")
        now+=NaturalBreakOpportunity.VALIDITY_MS
        manager.onNaturalBreak(activity,safe,event); assertNoShow()
    }
    @Test fun dashboardCannotPresentEvenWithAFuelSavePermit() {
        due(); val event=manager.createNaturalBreak(NaturalBreakWorkflow.FuelRecordSaved,now,"history")
        manager.onNaturalBreak(activity,safe.copy(route="dashboard"),event); assertNoShow()
    }
}
