package com.carmanager.app.startup

import androidx.lifecycle.ViewModelStore
import com.carmanager.app.core.domain.repository.AppTheme
import com.carmanager.app.core.domain.repository.SettingsRepository
import com.carmanager.app.core.ui.startup.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class StartupAppearanceTest {
    @Test fun `unresolved preference stays Loading instead of System`() = runTest {
        val preferences = MutableSharedFlow<AppTheme>()
        val states = mutableListOf<AppearanceBootstrapState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observeAppearanceBootstrap(preferences).toList(states)
        }
        runCurrent()
        assertEquals(listOf(AppearanceBootstrapState.Loading), states)
        advanceTimeBy(1999); runCurrent()
        assertEquals(listOf(AppearanceBootstrapState.Loading), states)
    }

    @Test fun `persisted Light with dark system never emits System or Dark first`() = runTest {
        val states = observeAppearanceBootstrap(flowOf(AppTheme.LIGHT)).toList()
        assertEquals(listOf(AppearanceBootstrapState.Loading, AppearanceBootstrapState.Ready(AppTheme.LIGHT)), states)
        assertFalse((states.last() as AppearanceBootstrapState.Ready).theme.usesDarkColors(true))
        assertEquals(0, testScheduler.currentTime)
    }

    @Test fun `persisted Dark with light system never emits System or Light first`() = runTest {
        val states = observeAppearanceBootstrap(flowOf(AppTheme.DARK)).toList()
        assertEquals(listOf(AppearanceBootstrapState.Loading, AppearanceBootstrapState.Ready(AppTheme.DARK)), states)
        assertTrue((states.last() as AppearanceBootstrapState.Ready).theme.usesDarkColors(false))
    }

    @Test fun `persisted System delegates to both current system appearances`() = runTest {
        val ready = observeAppearanceBootstrap(flowOf(AppTheme.SYSTEM)).last() as AppearanceBootstrapState.Ready
        assertFalse(ready.fallback)
        assertTrue(ready.theme.usesDarkColors(true))
        assertFalse(ready.theme.usesDarkColors(false))
    }

    @Test fun `read failure releases bootstrap immediately with safe fallback`() = runTest {
        var fallbacks = 0
        val states = observeAppearanceBootstrap(flow { throw IOException("unavailable") }, onFallback = { fallbacks++ }).toList()
        assertEquals(AppearanceBootstrapState.Ready(AppTheme.SYSTEM, fallback = true), states.last())
        assertEquals(1, fallbacks)
        assertEquals(0, testScheduler.currentTime)
    }

    @Test fun `empty preference source also releases bootstrap`() = runTest {
        val states = observeAppearanceBootstrap(emptyFlow()).toList()
        assertEquals(AppearanceBootstrapState.Ready(AppTheme.SYSTEM, fallback = true), states.last())
        assertEquals(0, testScheduler.currentTime)
    }

    @Test fun `timeout is bounded and a late authoritative preference still replaces fallback`() = runTest {
        val preferences = MutableSharedFlow<AppTheme>()
        val states = mutableListOf<AppearanceBootstrapState>()
        var subscriptions = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observeAppearanceBootstrap(preferences.onStart { subscriptions++ }).toList(states)
        }
        runCurrent(); advanceTimeBy(2000); runCurrent()
        assertEquals(AppearanceBootstrapState.Ready(AppTheme.SYSTEM, fallback = true), states.last())
        preferences.emit(AppTheme.LIGHT); runCurrent()
        assertEquals(AppearanceBootstrapState.Ready(AppTheme.LIGHT), states.last())
        assertEquals(1, subscriptions)
    }

    @Test fun `cancellation never becomes a fallback`() = runTest {
        var fallbacks = 0
        val states = mutableListOf<AppearanceBootstrapState>()
        val job = launch {
            observeAppearanceBootstrap(flow { awaitCancellation() }, onFallback = { fallbacks++ }).toList(states)
        }
        runCurrent(); job.cancelAndJoin(); advanceTimeBy(3000); runCurrent()
        assertEquals(0, fallbacks)
        assertEquals(listOf(AppearanceBootstrapState.Loading), states)
    }

    @Test fun `Activity bootstrap shares one eager reader and preserves live appearance across observers`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var subscriptions = 0
            val preferences = MutableSharedFlow<AppTheme>(replay = 1)
            val settings = mockk<SettingsRepository>()
            every { settings.themePreference } returns preferences.onStart { subscriptions++ }
            val vm = StartupViewModel(settings)
            store.put("startup", vm)
            runCurrent()
            assertEquals(1, subscriptions) // Works even before a Compose observer exists.
            assertEquals(AppearanceBootstrapState.Loading, vm.appearance.value)
            preferences.emit(AppTheme.LIGHT); runCurrent()
            assertEquals(AppearanceBootstrapState.Ready(AppTheme.LIGHT), vm.appearance.value)
            val firstObserver = backgroundScope.launch { vm.appearance.collect {} }
            runCurrent(); firstObserver.cancel(); runCurrent()
            val secondObserver = backgroundScope.launch { vm.appearance.collect {} }
            runCurrent()
            assertSame(vm, store["startup"])
            assertEquals(AppearanceBootstrapState.Ready(AppTheme.LIGHT), vm.appearance.value)
            preferences.emit(AppTheme.DARK); runCurrent()
            assertEquals(AppearanceBootstrapState.Ready(AppTheme.DARK), vm.appearance.value)
            assertEquals(1, subscriptions)
            secondObserver.cancel()
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun `theme before workspace only enables UI once local owner is resolved`() {
        val light = AppearanceBootstrapState.Ready(AppTheme.LIGHT)
        assertFalse(canComposeLocalApp(light, false))
        assertTrue(canComposeLocalApp(light, true))
    }

    @Test fun `workspace before theme remains unresolved until appearance or fallback is ready`() {
        assertFalse(canComposeLocalApp(AppearanceBootstrapState.Loading, true))
        assertTrue(canComposeLocalApp(AppearanceBootstrapState.Ready(AppTheme.DARK), true))
        assertTrue(canComposeLocalApp(AppearanceBootstrapState.Ready(AppTheme.SYSTEM, fallback = true), true))
        // The production gate accepts no Ads, Billing, Room or network state.
    }
}
