package com.carmanager.app.ads

import com.carmanager.app.core.ads.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class BannerRequestLifecycleTest {
    @Test fun `ordinary state refresh and recomposition cannot request twice`() {
        val lifecycle = BannerRequestLifecycle()
        var loads = 0
        repeat(10) { lifecycle.requestOnce { loads++ } }
        assertEquals(1, loads)
        assertEquals(BannerRequestState.REQUESTED, lifecycle.state.value)
        lifecycle.loaded()
        lifecycle.requestOnce { loads++ }
        assertEquals(1, loads)
    }
    @Test fun `failure collapses state without enabling an immediate retry loop`() {
        val lifecycle = BannerRequestLifecycle()
        var loads = 0
        lifecycle.requestOnce { loads++ }
        lifecycle.failed()
        repeat(10) { lifecycle.requestOnce { loads++ } }
        assertEquals(BannerRequestState.FAILED, lifecycle.state.value)
        assertEquals(1, loads)
    }
    @Test fun `Premium removal disposes view once and ignores late callbacks`() {
        val lifecycle = BannerRequestLifecycle()
        var loads = 0
        var destroys = 0
        lifecycle.requestOnce { loads++ }
        lifecycle.loaded()
        lifecycle.dispose { destroys++ }; lifecycle.dispose { destroys++ }
        lifecycle.loaded(); lifecycle.failed(); lifecycle.requestOnce { loads++ }
        assertEquals(BannerRequestState.DISPOSED, lifecycle.state.value)
        assertEquals(1, loads)
        assertEquals(1, destroys)
    }
    @Test fun `new configuration or screen lifecycle permits one deliberate new load`() {
        var loads = 0
        val first = BannerRequestLifecycle()
        first.requestOnce { loads++ }; first.dispose {}
        val next = BannerRequestLifecycle()
        next.requestOnce { loads++ }; next.requestOnce { loads++ }
        assertEquals(2, loads)
    }
}
