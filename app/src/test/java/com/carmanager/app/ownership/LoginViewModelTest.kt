package com.carmanager.app.ownership

import com.carmanager.app.core.domain.repository.AuthRepository
import com.carmanager.app.core.util.UiEvent
import com.carmanager.app.features.auth.LoginViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @Test fun `Google failure is visible and loading ends`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val auth = mockk<AuthRepository>()
            coEvery { auth.signInWithGoogle("token") } returns Result.failure(IllegalStateException("Connexion refusée"))
            val viewModel = LoginViewModel(auth)
            viewModel.onGoogleSignIn("token")
            runCurrent()
            assertFalse(viewModel.isLoading)
            assertEquals("Connexion refusée", (viewModel.uiEvent.first() as UiEvent.ShowSnackbar).message)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `duplicate Google submission is ignored and success emitted once`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val auth = mockk<AuthRepository>()
            coEvery { auth.signInWithGoogle("token") } returns Result.success(Unit)
            val viewModel = LoginViewModel(auth)
            viewModel.onGoogleSignIn("token")
            viewModel.onGoogleSignIn("token")
            runCurrent()
            coVerify(exactly = 1) { auth.signInWithGoogle("token") }
            assertEquals(UiEvent.Success, viewModel.uiEvent.first())
            assertFalse(viewModel.isLoading)
        } finally { Dispatchers.resetMain() }
    }
}
