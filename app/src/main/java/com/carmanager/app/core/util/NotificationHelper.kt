package com.carmanager.app.core.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri

object NotificationHelper {
    private const val CHANNEL_ID = "maintenance_reminders"
    private const val CHANNEL_NAME = "Rappels d'Entretien"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications pour les échéances d'entretien et d'assurance"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun reminderUri(owner: String, id: Long): Uri = Uri.Builder().scheme("carmanager")
        .authority("maintenance").appendPath(id.toString()).appendQueryParameter("owner", owner).build()

    private fun reminderIntent(context: Context, owner: String, id: Long) = Intent(context, ReminderReceiver::class.java).apply {
        data = reminderUri(owner, id)
        putExtra("ownerKey", owner)
        putExtra("recordId", id)
    }

    fun cancelOwnedReminder(context: Context, owner: String, id: Long) {
        val pending = PendingIntent.getBroadcast(context, 0, reminderIntent(context, owner, id),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
            pending.cancel()
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(reminderUri(owner, id).toString(), id.hashCode())
    }

    fun clearInactiveNotifications(context: Context, owner: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.activeNotifications.filter { it.notification.channelId == CHANNEL_ID }.forEach { notification ->
            val notificationOwner = notification.tag?.let { Uri.parse(it).getQueryParameter("owner") }
                ?: com.carmanager.app.core.domain.session.WorkspaceOwner.GUEST
            if (notificationOwner != owner) manager.cancel(notification.tag, notification.id)
        }
    }

    fun scheduleReminder(context: Context, timeInMillis: Long, title: String, message: String, owner: String, recordId: Long) {
        val intent = reminderIntent(context, owner, recordId).apply {
            putExtra("title", title)
            putExtra("message", message)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0, // L'URI encode propriétaire + identifiant, même si deux rappels partagent la date.
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    timeInMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                pendingIntent
            )
        }
    }
}
