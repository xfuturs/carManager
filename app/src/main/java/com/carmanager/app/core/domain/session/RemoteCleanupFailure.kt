package com.carmanager.app.core.domain.session

enum class RemoteCleanupFamily(val pathFamily: String) {
    PENDING_WRITES("<pending-writes>"),
    LEGACY_VEHICLES("users/{uid}/vehicles"),
    LEGACY_FUEL("users/{uid}/fuel_records"),
    LEGACY_MAINTENANCE("users/{uid}/maintenance_records"),
    USER_DOCUMENT("users/{uid}")
}

enum class RemoteCleanupOperation { DRAIN_WRITES, QUERY, BATCH_DELETE, DELETE_DOCUMENT }

/** Métadonnées bornées : aucun UID, chemin réel, contenu ou message serveur dans les logs/UI. */
data class RemoteCleanupDiagnostic(
    val family: RemoteCleanupFamily,
    val operation: RemoteCleanupOperation,
    val code: String
) {
    val safeDescription: String get() = "family=${family.name} operation=${operation.name} code=$code path=${family.pathFamily}"
}

class RemoteCleanupFailure(val diagnostic: RemoteCleanupDiagnostic, cause: Throwable) : IllegalStateException(
    if (diagnostic.code == "PERMISSION_DENIED")
        "Le nettoyage des anciennes données cloud a été refusé (PERMISSION_DENIED)."
    else "Le nettoyage des anciennes données cloud a échoué (${diagnostic.code}).",
    cause
)
