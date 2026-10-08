package com.carmanager.app.ads

import com.carmanager.app.core.ads.*
import com.carmanager.app.core.ui.navigation.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PersistentBannerTest {
    private class Host {
        val retention = BannerHostRetention()
        var view: BannerRequestLifecycle? = null
        var loads = 0; var destroys = 0; var pauses = 0; var resumes = 0
        fun route(route: String, consent: Boolean = true, premium: Boolean = false, resumed: Boolean = true) {
            val allowed = adsEligible(consent, premium)
            val visible = canShowBannerOnRoute(route, allowed)
            retention.update(allowed, visible)
            if (retention.retained.value && view == null) view = BannerRequestLifecycle()
            if (!retention.retained.value) { view?.dispose { destroys++ }; view = null }
            view?.updateActivity(visible, resumed, { pauses++ }, { resumes++ })
            if (visible && resumed) view?.requestOnce { loads++ }
        }
    }
    @Test fun `first eligible surface activates one host and one request`() {
        val h=Host(); h.route("dashboard")
        assertNotNull(h.view); assertEquals(1,h.loads); assertEquals(1,h.resumes)
    }
    @Test fun `Accueil Calculs round trips retain identity without load or resume churn`() {
        val h=Host(); h.route("dashboard"); val view=h.view
        repeat(10) { h.route("calculators"); assertSame(view,h.view); h.route("dashboard"); assertSame(view,h.view) }
        assertEquals(1,h.loads); assertEquals(1,h.resumes); assertEquals(0,h.destroys)
    }
    @Test fun `recomposition does not request again`() {
        val h=Host(); repeat(20) { h.route("dashboard") }; assertEquals(1,h.loads)
    }
    @Test fun `Premium removes owner and stops its requests`() {
        val h=Host(); h.route("dashboard"); val old=h.view!!
        repeat(3) { h.route("calculators",premium=true) }
        old.requestOnce { fail("Disposed request") }
        assertNull(h.view); assertEquals(1,h.destroys); assertEquals(1,h.loads)
        h.route("dashboard"); assertNotSame(old,h.view); assertEquals(2,h.loads)
    }
    @Test fun `consent revocation removes owner and initial denial never creates one`() {
        val h=Host(); h.route("dashboard",consent=false); assertNull(h.view); assertEquals(0,h.loads)
        h.route("dashboard"); h.route("dashboard",consent=false)
        assertNull(h.view); assertEquals(1,h.destroys)
    }
    @Test fun `Settings hides pauses and returning resumes same owner`() {
        val h=Host(); h.route("dashboard"); val view=h.view
        h.route("settings"); assertSame(view,h.view); assertFalse(canShowBannerOnRoute("settings",true))
        assertEquals(1,h.pauses); h.route("calculators"); assertSame(view,h.view)
        assertEquals(2,h.resumes); assertEquals(1,h.loads)
    }
    @Test fun `restored ad free surface never requests until eligible route`() {
        val h=Host(); h.route("settings"); h.route("login"); assertNull(h.view); assertEquals(0,h.loads)
        h.route("calculators"); assertEquals(1,h.loads)
    }
    @Test fun `Activity pause resume does not reload and destroy is terminal exactly once`() {
        val h=Host(); h.route("dashboard"); val view=h.view!!
        h.route("dashboard",resumed=false); h.route("dashboard",resumed=true)
        assertEquals(1,h.pauses); assertEquals(2,h.resumes); assertEquals(1,h.loads)
        repeat(2) { view.dispose { h.destroys++ } }
        view.updateActivity(true,true,{fail("pause")},{fail("resume")}); view.requestOnce { fail("load") }
        assertEquals(1,h.destroys)
    }
    @Test fun `Vehicles shares owner while forms never expose banner`() {
        val h=Host(); h.route("dashboard"); val view=h.view; h.route("vehicles"); assertSame(view,h.view)
        assertTrue(canShowBannerOnRoute("vehicles",true)); h.route(Screen.VehicleEdit.route)
        assertSame(view,h.view); assertFalse(canShowBannerOnRoute(Screen.VehicleEdit.route,true)); assertEquals(1,h.loads)
    }
    @Test fun `repeated switching never creates two simultaneous owners`() {
        val h=Host(); h.route("dashboard"); val view=h.view
        repeat(20) { for(route in listOf("dashboard","calculators","vehicles","settings")) { h.route(route); assertSame(view,h.view) } }
        assertEquals(0,h.destroys); assertEquals(1,h.loads)
    }
}
