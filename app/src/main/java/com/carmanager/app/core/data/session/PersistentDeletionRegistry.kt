package com.carmanager.app.core.data.session

import android.content.Context
import com.carmanager.app.core.domain.session.DeletionRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Journal durable : une suppression interrompue ne doit jamais relancer les uploads. */
@Singleton
class PersistentDeletionRegistry @Inject constructor(@ApplicationContext context: Context) : DeletionRegistry {
    private val preferences = context.getSharedPreferences("account_deletions", Context.MODE_PRIVATE)
    private val blocked = MutableStateFlow(preferences.getStringSet("blocked", emptySet())!!.toSet())
    override val blockedOwners = blocked.asStateFlow()
    override suspend fun block(owner: String) = withContext(Dispatchers.IO) {
        synchronized(this@PersistentDeletionRegistry) {
            val next = blocked.value + owner
            check(preferences.edit().putStringSet("blocked", next).commit()) { "Impossible de conserver l'état de suppression." }
            blocked.value = next
        }
    }
}
