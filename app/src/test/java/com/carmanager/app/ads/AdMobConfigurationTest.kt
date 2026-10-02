package com.carmanager.app.ads

import com.carmanager.build.AdMobConfiguration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class AdMobConfigurationTest {
    // Fixtures syntaxiques exclusivement JVM ; jamais injectees dans une variante release.
    private val app = "ca-app-pub-1234567890123456~1234567890"
    private val banner = "ca-app-pub-1234567890123456/1234567890"

    @Test fun `debug uses only the official Google test configuration`() {
        assertTrue(AdMobConfiguration.isDebugTestConfiguration(AdMobConfiguration.DEBUG_APP_ID, AdMobConfiguration.DEBUG_BANNER_ID))
        assertFalse(AdMobConfiguration.isDebugTestConfiguration(app, banner))
    }
    @Test fun `release rejects missing and whitespace values with property names only`() {
        for (value in listOf(null, "", " ", "\t")) {
            val errors = AdMobConfiguration.releaseErrors(value, value)
            assertEquals(2, errors.size)
            assertTrue(errors[0].contains("ADMOB_APP_ID"))
            assertTrue(errors[1].contains("ADMOB_BANNER_AD_UNIT_ID"))
        }
    }
    @Test fun `release rejects every unit from the sample Google publisher`() {
        assertEquals(2, AdMobConfiguration.releaseErrors(AdMobConfiguration.DEBUG_APP_ID, AdMobConfiguration.DEBUG_BANNER_ID).size)
        assertEquals(1, AdMobConfiguration.releaseErrors(app, "ca-app-pub-3940256099942544/0000000000").size)
    }
    @Test fun `malformed or interchanged App ID is rejected without echoing value`() {
        for (value in listOf(banner, "invalid-private-value", " $app", "$app ", app.replace("1234567890", "abc"))) {
            val errors = AdMobConfiguration.releaseErrors(value, this.banner)
            assertEquals(1, errors.size)
            assertFalse(errors.single().contains(value))
        }
    }
    @Test fun `malformed or interchanged banner ID is rejected`() {
        for (value in listOf(app, "bad", "$banner ", "ca-app-pub-123/123", banner.replace("/", "~"))) {
            assertEquals(1, AdMobConfiguration.releaseErrors(app, value).size)
        }
    }
    @Test fun `production shaped pair passes syntax without claiming ownership`() {
        assertTrue(AdMobConfiguration.releaseErrors(app, banner).isEmpty())
    }
}
