package com.carmanager.app.features.auth

/** Le snackbar garde les messages UI connus sans exposer une exception du fournisseur. */
internal fun loginErrorMessage(message: String): String = when (message) {
    "Connexion Google impossible. Réessayez.",
    "Connexion Google annulée ou impossible. Réessayez." -> message
    else -> "Connexion Google impossible. Réessayez."
}
