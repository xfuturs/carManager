package com.carmanager.app.features.maintenance

/** État lu par l'UI au clic Enregistrer, sans API Android dans le contrat du formulaire. */
enum class NotificationPermissionStatus {
    NOT_REQUIRED, GRANTED, MISSING;

    companion object {
        fun from(sdkInt: Int, granted: Boolean): NotificationPermissionStatus = when {
            sdkInt < 33 -> NOT_REQUIRED
            granted -> GRANTED
            else -> MISSING
        }
    }
}
