package com.carmanager.app.core.util

import com.carmanager.app.core.domain.model.Document
import kotlinx.coroutines.*

/** Préparation IO puis commit SQL bref ; seul le nouveau fichier peut être compensé. */
internal object StagedDocumentFile {
    suspend fun store(prepare: suspend () -> String, insert: suspend (String) -> Document,
        remove: (String) -> Boolean): Document = withContext(Dispatchers.IO) {
        var created: String? = null
        var committed = false
        try {
            val path = prepare().also { created = it }
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                insert(path).also { committed = true }
            }
        } catch (error: Throwable) {
            if (!committed) created?.let { path ->
                try { if (!remove(path)) error.addSuppressed(IllegalStateException("Copie privée non supprimée.")) }
                catch (cleanup: Exception) { error.addSuppressed(cleanup) }
            }
            throw error
        }
    }
}
