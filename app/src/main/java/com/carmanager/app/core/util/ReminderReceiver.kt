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
import javax.inject.Inject

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var session: WorkspaceSession
    @Inject lateinit var deletionRegistry: DeletionRegistry
    override fun onReceive(context: Context, intent: Intent) {
        // Les anciennes alarmes sans propriétaire correspondent aux données migrées invitées.
        val owner = intent.getStringExtra("ownerKey") ?: WorkspaceOwner.GUEST
        if (session.owner.value != owner || owner in deletionRegistry.blockedOwners.value) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
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

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(intent.data?.toString() ?: "legacy_guest", intent.getLongExtra("recordId", 0).hashCode(), notification)
    }
}
