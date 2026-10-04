@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.carmanager.app.features.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.core.app.ActivityCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.carmanager.app.core.domain.model.ReminderCategory
import com.carmanager.app.core.domain.model.ReminderPreferences

@Composable
internal fun LocalReminderSettings(preferences: ReminderPreferences?, onEnabled: (Boolean) -> Unit,
    onCategory: (ReminderCategory, Boolean) -> Unit, onLead: (Int) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun permissionGranted() = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
        Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var permission by remember { mutableStateOf(permissionGranted()) }
    var systemEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var error by remember { mutableStateOf<String?>(null) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission = it; denied = !it; systemEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            permission = permissionGranted(); systemEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (preferences == null) {
            Text("Chargement des réglages locaux…", style = MaterialTheme.typography.bodySmall)
        } else {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Activer les rappels", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = preferences.enabled, onCheckedChange = onEnabled)
            }
            Text("Sur cet appareil, sans e-mail. Désactiver conserve vos échéances.",
                style = MaterialTheme.typography.bodySmall)
            ReminderCategory.entries.forEach { category ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(category.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(preferences.categoryEnabled(category), { onCategory(category, it) }, enabled = preferences.enabled)
                }
            }
            Text("Quand vous rappeler ?", style = MaterialTheme.typography.titleSmall)
            Text("Plusieurs choix possibles", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReminderPreferences.LEAD_DAYS.forEach { days ->
                    FilterChip(days in preferences.leadDaysSet, { onLead(days) },
                        enabled = preferences.enabled && !(days in preferences.leadDaysSet && preferences.leadDaysSet.size == 1),
                        modifier = Modifier.heightIn(min = 48.dp), label = { Text(when (days) {
                            0 -> "Le jour même"; 1 -> "1 jour"; else -> "$days jours"
                        }) })
                }
            }
            Text("Avant l’échéance. Les délais déjà passés sont ignorés. Android peut retarder les rappels.",
                style = MaterialTheme.typography.bodySmall)
        }
        Text(if (permission && systemEnabled) "Notifications Android autorisées" else "Notifications Android désactivées",
            style = MaterialTheme.typography.bodySmall)
        if (!permission || !systemEnabled) {
            val canRequest = Build.VERSION.SDK_INT >= 33 && !permission && (!denied ||
                context.reminderActivity()?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS) } == true)
            if (canRequest) {
                OutlinedButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Autoriser les notifications")
                }
            } else TextButton(onClick = {
                try { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
                catch (_: Exception) { error = "Paramètres Android indisponibles. Ouvrez les réglages de l’appareil." }
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ouvrir les paramètres Android") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

private tailrec fun Context.reminderActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.reminderActivity() else null
    else -> null
}
