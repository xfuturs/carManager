package com.carmanager.app.features.settings

import com.carmanager.app.core.ui.components.CarManagerTopLevelAppBar
import com.carmanager.app.core.ui.theme.CarManagerSpacing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.Intent
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import com.carmanager.app.R
import com.carmanager.app.core.domain.repository.AppTheme
import com.carmanager.app.core.domain.model.PremiumEntitlement
import com.carmanager.app.core.domain.model.PremiumIssue
import com.carmanager.app.core.ui.theme.VehicleColor
import com.carmanager.app.core.util.GoogleMobileAdsConsentManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onPrivacyOptionsClick: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    isPrivacyOptionsRequired: Boolean,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themePref by viewModel.themePreference.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val distanceUnit by viewModel.distanceUnit.collectAsState()
    val user by viewModel.currentUser.collectAsState()
    val premiumState by viewModel.premiumState.collectAsState()
    val deletionState by viewModel.deletionState.collectAsState()
    val deletionPending by viewModel.deletionPending.collectAsState()
    val accountError by viewModel.accountError.collectAsState()
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    var isCurrencyDropdownExpanded by remember { mutableStateOf(false) }
    
    val currencies = remember {
        listOf(
            CurrencyInfo("€", "Euro", "🇪🇺"),
            CurrencyInfo("£", "Livre", "🇬🇧"),
            CurrencyInfo("CHF", "Franc", "🇨🇭"),
            CurrencyInfo("$", "Dollar", "🇺🇸"),
            CurrencyInfo("zł", "Złoty", "🇵🇱"),
            CurrencyInfo("Kč", "Couronne", "🇨🇿"),
            CurrencyInfo("Ft", "Forint", "🇭🇺"),
            CurrencyInfo("lei", "Leu", "🇷🇴"),
            CurrencyInfo("kr", "Couronne", "🇸🇪"),
            CurrencyInfo("лв", "Lev", "🇧🇬"),
            CurrencyInfo("₴", "Hryvnia", "🇺🇦"),
            CurrencyInfo("₺", "Lira", "🇹🇷"),
            CurrencyInfo("din.", "Dinar", "🇷🇸"),
            CurrencyInfo("KM", "Mark", "🇧🇦"),
            CurrencyInfo("L", "Lek", "🇦🇱"),
            CurrencyInfo("den.", "Denar", "🇲🇰")
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Supprimer ce compte ?") },
            text = { Text("Les données locales et les collections cloud connues de ce compte seront supprimées, puis le compte Firebase. L'espace invité et les autres comptes seront conservés. Une erreur peut laisser une suppression partielle ; elle sera signalée.") },
            confirmButton = { TextButton(onClick = { showDeleteConfirmation = false; viewModel.deleteAccount() }, enabled = !deletionState.running) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("Annuler") } }
        )
    }

    Scaffold(
        topBar = {
            CarManagerTopLevelAppBar(title = stringResource(R.string.settings_title))
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = CarManagerSpacing.firstContentTop, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // --- SECTION COMPTE ---
            SettingsSection(title = stringResource(R.string.premium_account_section), icon = Icons.Default.AccountCircle) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (user != null) {
                        Text(stringResource(R.string.premium_status_connected), style = MaterialTheme.typography.labelSmall)
                        Text(user?.email ?: "Compte Google", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.signOut() },
                            enabled = !deletionState.running,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                        ) {
                            @Suppress("DEPRECATION")
                            Text(stringResource(R.string.premium_logout))
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        TextButton(
                            onClick = { showDeleteConfirmation = true },
                            enabled = !deletionState.running,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.premium_delete_account))
                        }
                        if (deletionState.running) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Suppression en cours — ${deletionState.stage?.label}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (deletionPending && !deletionState.running) {
                            Text("Suppression à reprendre : modifications suspendues pour ce compte. Réessayez la suppression.", color = MaterialTheme.colorScheme.error)
                        }
                        accountError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    } else {
                        Text(
                            text = stringResource(R.string.login_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToLogin,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = VehicleColor)
                        ) {
                            Text(stringResource(R.string.login_title).uppercase())
                        }
                    }
                }
            }

            SettingsSection(title = stringResource(R.string.premium_title), icon = Icons.Default.WorkspacePremium) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(when (premiumState.entitlement) {
                        PremiumEntitlement.ACTIVE -> R.string.premium_active
                        PremiumEntitlement.PENDING -> R.string.premium_pending
                        PremiumEntitlement.FREE -> R.string.premium_benefits
                    }), style = MaterialTheme.typography.bodyMedium)
                    if (premiumState.isLoading || premiumState.isPurchasing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(stringResource(if (premiumState.isPurchasing) R.string.premium_purchase_loading else R.string.premium_loading))
                    }
                    if (premiumState.acknowledgementPending && premiumState.issue == null) {
                        Text(stringResource(R.string.premium_ack_pending))
                    }
                    premiumState.issue?.let { issue ->
                        Text(stringResource(when (issue) {
                            PremiumIssue.STORE_UNAVAILABLE -> R.string.premium_store_unavailable
                            PremiumIssue.OFFER_UNAVAILABLE -> R.string.premium_offer_unavailable
                            PremiumIssue.PURCHASE_FAILED -> R.string.premium_purchase_failed
                            PremiumIssue.ACKNOWLEDGEMENT_FAILED -> R.string.premium_ack_failed
                        }), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (premiumState.entitlement == PremiumEntitlement.FREE) {
                        premiumState.offer?.let { Text(stringResource(R.string.premium_one_time_price, it.formattedPrice)) }
                        Button(
                            onClick = { context.findBillingActivity()?.let(viewModel::purchasePremium) },
                            enabled = premiumState.canPurchase && context.findBillingActivity() != null,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.premium_purchase)) }
                    }
                    TextButton(onClick = viewModel::refreshPremium,
                        enabled = !premiumState.isLoading && !premiumState.isPurchasing,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.premium_refresh))
                    }
                }
            }

            // --- SECTION APPARENCE ---
            SettingsSection(title = stringResource(R.string.settings_appearance), icon = Icons.Default.Palette) {
                Column(modifier = Modifier.selectableGroup()) {
                    ThemeOption(
                        label = "Système (par défaut)",
                        selected = themePref == AppTheme.SYSTEM,
                        onClick = { viewModel.setTheme(AppTheme.SYSTEM) },
                        icon = Icons.Default.Brightness6
                    )
                    ThemeOption(
                        label = "Clair",
                        selected = themePref == AppTheme.LIGHT,
                        onClick = { viewModel.setTheme(AppTheme.LIGHT) },
                        icon = Icons.Default.LightMode
                    )
                    ThemeOption(
                        label = "Sombre",
                        selected = themePref == AppTheme.DARK,
                        onClick = { viewModel.setTheme(AppTheme.DARK) },
                        icon = Icons.Default.DarkMode
                    )
                }
            }

            // --- SECTION DEVISE ---
            SettingsSection(title = stringResource(R.string.settings_currency), icon = Icons.Default.Payments) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Utilisée pour vos pleins, entretiens et statistiques de budget.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    ExposedDropdownMenuBox(
                        expanded = isCurrencyDropdownExpanded,
                        onExpandedChange = { isCurrencyDropdownExpanded = !isCurrencyDropdownExpanded }
                    ) {
                        val currentCurrencyInfo = currencies.find { it.symbol == currency } ?: currencies[0]
                        OutlinedTextField(
                            value = "${currentCurrencyInfo.flag} ${currentCurrencyInfo.name} (${currentCurrencyInfo.symbol})",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCurrencyDropdownExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        )

                        ExposedDropdownMenu(
                            expanded = isCurrencyDropdownExpanded,
                            onDismissRequest = { isCurrencyDropdownExpanded = false }
                        ) {
                            currencies.forEach { info ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(info.flag, modifier = Modifier.padding(end = 12.dp))
                                            Text("${info.name} (${info.symbol})", style = MaterialTheme.typography.bodyLarge)
                                        }
                                    },
                                    onClick = {
                                        viewModel.setCurrency(info.symbol)
                                        isCurrencyDropdownExpanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                }
            }

            // --- SECTION UNITÉS ---
            SettingsSection(title = stringResource(R.string.settings_units), icon = Icons.Default.Speed) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("km", "mi").forEach { unit ->
                        FilterChip(
                            selected = distanceUnit == unit,
                            onClick = { viewModel.setDistanceUnit(unit) },
                            label = { Text(unit, style = MaterialTheme.typography.titleMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
            
            if (isPrivacyOptionsRequired) {
                SettingsSection(title = stringResource(R.string.settings_privacy_ads), icon = Icons.Default.PrivacyTip) {
                    TextButton(
                        onClick = onPrivacyOptionsClick,
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("Modifier mes choix publicitaires")
                    }
                }
            }

            // --- SECTION INFORMATIONS ---
            SettingsSection(title = stringResource(R.string.settings_info_section), icon = Icons.Default.Info) {
                Column {
                    TextButton(
                        onClick = onNavigateToPrivacy,
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(stringResource(R.string.settings_privacy_policy), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:xfuturs.app@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "Signalement de problème - Car Manager")
                            }
                            try {
                                context.startActivity(Intent.createChooser(intent, "Envoyer un e-mail"))
                            } catch (e: Exception) {
                                // Ignore
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(stringResource(R.string.report_problem), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                        Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.settings_version), style = MaterialTheme.typography.bodyMedium)
                        Text("1.0.0", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String, 
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Icon(
                imageVector = icon, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            content()
        }
    }
}

private tailrec fun Context.findBillingActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.findBillingActivity() else null
    else -> null
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon, 
                    contentDescription = null, 
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        RadioButton(
            selected = selected,
            onClick = null
        )
    }
}

private data class CurrencyInfo(val symbol: String, val name: String, val flag: String)
