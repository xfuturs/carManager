package com.carmanager.app.core.util

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.carmanager.app.MainActivity
import com.carmanager.app.R
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.core.domain.session.WorkspaceOwner
import com.carmanager.app.core.domain.session.DeletionRegistry
import dagger.hilt.android.AndroidEntryPoint
import com.carmanager.app.core.data.repository.ReminderSettingsStore
import com.carmanager.app.core.data.local.dao.MaintenanceDao
import com.carmanager.app.core.data.mapper.toDomain
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var session: WorkspaceSession
    @Inject lateinit var deletionRegistry: DeletionRegistry
    @Inject lateinit var settings: ReminderSettingsStore
    @Inject lateinit var maintenance: MaintenanceDao
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { withTimeout(8_000) { deliver(context.applicationContext, intent) } }
            catch (error: Exception) { android.util.Log.w("Reminders", "Rappel local ignoré ou indisponible.", error) }
            finally { pending.finish() }
        }
    }
    internal suspend fun deliver(context: Context, intent: Intent) {
        // Les anciennes alarmes sans propriétaire correspondent aux données migrées invitées.
        val owner = intent.getStringExtra("ownerKey") ?: WorkspaceOwner.GUEST
        session.isResolved.first { it }
        if (owner != com.carmanager.app.core.domain.session.LocalGarageOwner.KEY) return
        val id = intent.getLongExtra("recordId", 0)
        // Les anciennes URI sont annulées par réconciliation et ne peuvent plus livrer.
        val lead = intent.getIntExtra("leadDays", -1)
        val uri = intent.data ?: return
        if (!intent.hasExtra("dueAt") || uri.scheme != "carmanager" || uri.authority != "maintenance" ||
            uri.lastPathSegment != id.toString() || uri.getQueryParameter("owner") != owner ||
            uri.getQueryParameter("leadDays") != lead.toString()) return
        val record = maintenance.getById(id, owner)?.toDomain(owner)
        val preferences = settings.preferences.first()
        if (!ReminderPlanning.canDeliver(owner, session.owner.value, owner in deletionRegistry.blockedOwners.value,
            preferences, record, if (intent.hasExtra("dueAt")) intent.getLongExtra("dueAt", 0) else null,
            if (intent.hasExtra("leadDays")) intent.getIntExtra("leadDays", 0) else null)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled()) return
        val title = intent.getStringExtra("title") ?: "Rappel Car Manager"
        val message = intent.getStringExtra("message") ?: "Une échéance arrive à terme."

        val activityIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "maintenance_reminders")
            .setSmallIcon(R.mipmap.ic_launcher) // Utiliser l'icône de l'app
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (!manager.areNotificationsEnabled() || session.owner.value != owner || owner in deletionRegistry.blockedOwners.value) return
        manager.notify(intent.data?.toString() ?: "legacy_guest", id.hashCode(), notification)
    }
}
