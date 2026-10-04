package com.carmanager.app.core.ads

import com.carmanager.build.AdMobConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InterstitialConfigurationTest {
    private val app = "ca-app-pub-9876543210987654~9876543210"
    private val banner = "ca-app-pub-9876543210987654/9876543211"
    private val interstitial = "ca-app-pub-9876543210987654/9876543212"
    private fun errors(value: String?) = AdMobConfiguration.releaseErrors(app, banner, value)
    @Test fun debugTripleUsesOfficialFormats() {
        assertEquals("ca-app-pub-3940256099942544/1033173712", AdMobConfiguration.DEBUG_INTERSTITIAL_ID)
        assertTrue(AdMobConfiguration.isDebugTestConfiguration(AdMobConfiguration.DEBUG_APP_ID,
            AdMobConfiguration.DEBUG_BANNER_ID, AdMobConfiguration.DEBUG_INTERSTITIAL_ID))
        assertFalse(AdMobConfiguration.isDebugTestConfiguration(AdMobConfiguration.DEBUG_APP_ID,
            AdMobConfiguration.DEBUG_BANNER_ID, interstitial))
    }
    @Test fun syntacticallyValidNonFixtureReleaseTriplePasses() { assertTrue(errors(interstitial).isEmpty()) }
    @Test fun missingBlankAndNullThirdPropertyFailClosed() {
        for (value in listOf(null, "", " ")) {
            assertEquals(1, errors(value).size); assertTrue(errors(value).single().startsWith("ADMOB_INTERSTITIAL_AD_UNIT_ID"))
        }
    }
    @Test fun malformedOrAppFormatCannotBeAdUnit() {
        for (value in listOf(app, "placeholder", "ca-app-pub-12/34", "$interstitial ")) assertFalse(errors(value).isEmpty())
    }
    @Test fun googleSamplePublisherAndOtherOfficialSampleUnitsRejected() {
        for (value in listOf(AdMobConfiguration.DEBUG_INTERSTITIAL_ID, AdMobConfiguration.DEBUG_BANNER_ID,
            "ca-app-pub-3940256099942544/9999999999")) assertFalse(errors(value).isEmpty())
    }
    @Test fun knownPlaceholderPublishersAndUnitsRejected() {
        for (value in listOf("ca-app-pub-1234567890123456/9876543212", "ca-app-pub-0000000000000000/9876543212",
            "ca-app-pub-1111111111111111/9876543212", "ca-app-pub-9876543210987654/0000000000",
            "ca-app-pub-9876543210987654/1234567890")) assertFalse(errors(value).isEmpty())
    }
    @Test fun legacyAppAndBannerGuardsAreStillRequired() {
        assertEquals(2, AdMobConfiguration.releaseErrors(null, null, interstitial).size)
        assertEquals(2, AdMobConfiguration.releaseErrors(AdMobConfiguration.DEBUG_APP_ID,
            AdMobConfiguration.DEBUG_BANNER_ID, interstitial).size)
    }
    @Test fun validationErrorsDoNotEchoIdentifiers() {
        val invalid = "$interstitial-invalid"
        assertTrue(errors(invalid).isNotEmpty()); assertFalse(errors(invalid).joinToString().contains(invalid))
    }
}
