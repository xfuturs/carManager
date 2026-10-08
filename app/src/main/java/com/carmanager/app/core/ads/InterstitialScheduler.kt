package com.carmanager.app.core.ads

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Un propriétaire de coroutine au shell : échéance directe, jamais de polling périodique. */
internal class InterstitialScheduler {
    suspend fun run(evaluate: suspend () -> Unit, nextDelay: () -> Long?) {
        while (currentCoroutineContext().isActive) {
            evaluate()
            val wait = nextDelay()
            // Une échéance différée attend un événement de sécurité/SDK, sans boucle à 0 ms.
            if (wait == null || wait <= 0L) awaitCancellation()
            delay(wait)
        }
    }
}
