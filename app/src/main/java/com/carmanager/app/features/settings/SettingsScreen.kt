@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.carmanager.app.core.ui.theme.CarManagerShapes
import com.carmanager.app.core.ui.components.SecondarySectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onPrivacyOptionsClick: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    isPrivacyOptionsRequired: Boolean,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themePref = com.carmanager.app.core.ui.theme.LocalAppAppearance.current
    val reminders by viewModel.reminderPreferences.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.preferenceEvents.collect { snackbar.showSnackbar(it) } }
    val currency by viewModel.currency.collectAsState()
    val distanceUnit by viewModel.distanceUnit.collectAsState()
    val user by viewModel.currentUser.collectAsState()
    val premiumState by viewModel.premiumState.collectAsState()
    val deletionState by viewModel.deletionState.collectAsState()
    val deletionPending by viewModel.deletionPending.collectAsState()
    val accountError by viewModel.accountError.collectAsState()
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showPremiumTerms by remember { mutableStateOf(false) }
    var showRefundPolicy by remember { mutableStateOf(false) }
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

    if (showPremiumTerms) {
        AlertDialog(onDismissRequest = { showPremiumTerms = false },
            title = { Text(stringResource(R.string.premium_terms_title)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(R.string.premium_terms_1, R.string.premium_terms_2, R.string.premium_terms_3,
                    R.string.premium_terms_4, R.string.premium_terms_5, R.string.premium_terms_6,
                    R.string.premium_terms_7, R.string.premium_terms_8).forEach { Text(stringResource(it)) }
            } }, confirmButton = { TextButton(onClick = { showPremiumTerms = false }) { Text("Fermer") } })
    }
    if (showRefundPolicy) {
        AlertDialog(onDismissRequest = { showRefundPolicy = false }, title = { Text(stringResource(R.string.premium_refund_title)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(R.string.premium_refund_1, R.string.premium_refund_2, R.string.premium_refund_3,
                    R.string.premium_refund_4, R.string.premium_refund_5, R.string.premium_refund_6).forEach { Text(stringResource(it)) }
            } }, confirmButton = { TextButton(onClick = { showRefundPolicy = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Fermer") } })
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Supprimer ce compte ?") },
            text = { Text("Les données locales et les collections cloud connues de ce compte seront supprimées, puis le compte Firebase. L'espace invité et les autres comptes seront conservés. Une erreur peut laisser une suppression partielle ; elle sera signalée.") },
            confirmButton = { TextButton(onClick = { showDeleteConfirmation = false; viewModel.deleteAccount() }, enabled = !deletionState.running, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("Annuler") } }
        )
    }

    Scaffold(
        topBar = {
            CarManagerTopLevelAppBar(title = stringResource(R.string.settings_title))
        }, snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .clipToBounds()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = CarManagerSpacing.firstContentTop, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- SECTION COMPTE ---
            SettingsSection(title = stringResource(R.string.premium_account_section), icon = Icons.Default.AccountCircle) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (user != null) {
                        Text(stringResource(R.string.premium_status_connected), style = MaterialTheme.typography.labelSmall)
                        Text(user?.email ?: "Compte Google", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.signOut() },
                            enabled = !deletionState.running,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                            shape = CarManagerShapes.control
                        ) {
                            @Suppress("DEPRECATION")
                            Text(stringResource(R.string.premium_logout))
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        TextButton(
                            onClick = { showDeleteConfirmation = true },
                            enabled = !deletionState.running,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.premium_delete_account))
                        }
                        if (deletionState.running) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Suppression en cours — ${deletionState.stage?.label}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (deletionPending && !deletionState.running) {
                            Text("Suppression à reprendre : modifications suspendues pour ce compte. Réessayez la suppression.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        accountError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    } else {
                        Text(
                            text = stringResource(R.string.login_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onNavigateToLogin,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = CarManagerShapes.control
                        ) {
                            Text(stringResource(R.string.login_title))
                        }
                    }
                }
            }

            SettingsSection(title = stringResource(R.string.premium_title), icon = Icons.Default.WorkspacePremium) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (premiumState.entitlement == PremiumEntitlement.FREE) {
                        premiumDisplayPrice(premiumState.offer)?.let { Text(stringResource(R.string.premium_one_time_price, it),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                    }
                    Text(stringResource(R.string.premium_purchase_disclosure), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(when (premiumState.entitlement) {
                        PremiumEntitlement.ACTIVE -> R.string.premium_active
                        PremiumEntitlement.PENDING -> R.string.premium_pending
                        PremiumEntitlement.FREE -> R.string.premium_benefits
                    }), style = MaterialTheme.typography.bodyMedium)
                    listOf(R.string.premium_benefit_ads, R.string.premium_benefit_pdf, R.string.premium_benefit_sections,
                        R.string.premium_benefit_library, R.string.premium_benefit_save_share).forEach {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                    if (premiumState.isLoading || premiumState.isPurchasing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(stringResource(if (premiumState.isPurchasing) R.string.premium_purchase_loading else R.string.premium_loading), style = MaterialTheme.typography.labelSmall)
                    }
                    if (premiumState.acknowledgementPending && premiumState.issue == null) {
                        Text(stringResource(R.string.premium_ack_pending), style = MaterialTheme.typography.labelSmall)
                    }
                    premiumState.issue?.let { issue ->
                        Text(stringResource(when (issue) {
                            PremiumIssue.STORE_UNAVAILABLE -> R.string.premium_store_unavailable
                            PremiumIssue.OFFER_UNAVAILABLE -> R.string.premium_offer_unavailable
                            PremiumIssue.PURCHASE_FAILED -> R.string.premium_purchase_failed
                            PremiumIssue.ACKNOWLEDGEMENT_FAILED -> R.string.premium_ack_failed
                        }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (premiumState.entitlement == PremiumEntitlement.FREE) {
                        Button(
                            onClick = { context.findBillingActivity()?.let(viewModel::purchasePremium) },
                            enabled = premiumState.canPurchase && context.findBillingActivity() != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = CarManagerShapes.control
                        ) { Text(stringResource(R.string.premium_purchase)) }
                    }
                    HorizontalDivider()
                    Text(stringResource(R.string.premium_existing_reports_free), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.premium_future_note), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { showPremiumTerms = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.premium_terms_title))
                    }
                    TextButton(onClick = { showRefundPolicy = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.premium_refund_title))
                    }
                    TextButton(onClick = viewModel::refreshPremium,
                        enabled = !premiumState.isLoading && !premiumState.isPurchasing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.premium_refresh))
                    }
                }
            }

            // --- SECTION APPARENCE ---
            SettingsSection(title = stringResource(R.string.settings_appearance), icon = Icons.Default.Palette) {
                Column(modifier = Modifier.selectableGroup()) {
                    ThemeOption(
                        label = "Jour",
                        selected = themePref == AppTheme.LIGHT,
                        onClick = { viewModel.setTheme(AppTheme.LIGHT) },
                        icon = Icons.Default.LightMode
                    )
                    ThemeOption(
                        label = "Nuit",
                        selected = themePref == AppTheme.DARK,
                        onClick = { viewModel.setTheme(AppTheme.DARK) },
                        icon = Icons.Default.DarkMode
                    )
                }
            }

            SettingsSection(title = "Notifications et rappels", icon = Icons.Default.Notifications) {
                LocalReminderSettings(reminders, viewModel::setRemindersEnabled, viewModel::setReminderCategory, viewModel::setReminderLead)
            }

            // --- SECTION DEVISE ---
            SettingsSection(title = stringResource(R.string.settings_currency), icon = Icons.Default.Payments) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Symbole affiché pour les montants. Aucun taux de change ni conversion monétaire.",
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
                            shape = CarManagerShapes.control,
                            textStyle = MaterialTheme.typography.bodyMedium
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
                                            Text("${info.name} (${info.symbol})", style = MaterialTheme.typography.bodyMedium)
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
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("km", "mi").forEach { unit ->
                        FilterChip(
                            selected = distanceUnit == unit,
                            onClick = { viewModel.setDistanceUnit(unit) },
                            label = { Text(unit, style = MaterialTheme.typography.bodyMedium) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = CarManagerShapes.control,
                            leadingIcon = if (distanceUnit == unit) { { Icon(Icons.Default.Check, "Sélectionné", Modifier.size(20.dp)) } } else null,
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
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
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
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.settings_privacy_policy), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
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
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.report_problem), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                        Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(stringResource(R.string.settings_version), style = MaterialTheme.typography.bodyMedium)
                        Text("1.0.0", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }


        }
    }
}

@Composable
private fun SettingsSection(
    title: String, 
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            SecondarySectionTitle(title, Modifier.weight(1f))
        }
        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = CarManagerShapes.card) {
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
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
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
