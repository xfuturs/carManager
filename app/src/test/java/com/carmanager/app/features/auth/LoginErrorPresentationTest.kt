package com.carmanager.app.features.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LoginErrorPresentationTest {
    @Test fun `mapped Google failure and cancellation remain distinguishable`() {
        listOf("Connexion Google impossible. Réessayez.", "Connexion Google annulée ou impossible. Réessayez.")
            .forEach { assertEquals(it, loginErrorMessage(it)) }
    }

    @Test fun `provider errors never expose technical details or credentials in the snackbar`() {
        listOf("FirebaseAuthInvalidCredentialsException: token=secret", "NETWORK_ERROR code 7", "Erreur inconnue du fournisseur")
            .forEach { assertEquals("Connexion Google impossible. Réessayez.", loginErrorMessage(it)) }
    }

    @Test fun `empty and whitespace messages still provide a usable retry explanation`() {
        listOf("", "  ", "\n").forEach { assertEquals("Connexion Google impossible. Réessayez.", loginErrorMessage(it)) }
    }
}
