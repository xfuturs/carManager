package com.carmanager.app.core.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** AlarmManager efface ses alarmes au redémarrage : reconstruire uniquement les échéances locales. */
@AndroidEntryPoint
class ReminderBootReceiver : BroadcastReceiver() {
    @Inject lateinit var reminders: LocalReminderCoordinator
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        reminders.afterBoot { pending.finish() }
    }
}
