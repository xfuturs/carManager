package com.carmanager.app.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carmanager.app.R
import com.carmanager.app.core.domain.repository.AppTheme
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Black) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // --- SECTION COMPTE ---
            SettingsSection(title = "Compte Premium", icon = Icons.Default.AccountCircle) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (user != null) {
                        Text("Connecté en tant que :", style = MaterialTheme.typography.labelSmall)
                        Text(user?.email ?: "Utilisateur Premium", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.signOut() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                        ) {
                            @Suppress("DEPRECATION")
                            Text("SE DÉCONNECTER")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        TextButton(
                            onClick = { viewModel.deleteAccount() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Supprimer mon compte et mes données")
                        }
                    } else {
                        Text(
                            "Connectez-vous pour synchroniser vos données sur le Cloud.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToLogin,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = VehicleColor)
                        ) {
                            Text("CONNEXION / INSCRIPTION")
                        }
                    }
                }
            }

            // --- SECTION APPARENCE ---
            SettingsSection(title = "Apparence", icon = Icons.Default.Palette) {
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
            SettingsSection(title = "Devise Monétaire", icon = Icons.Default.Payments) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("€", "$", "£", "CHF").forEach { symbol ->
                        FilterChip(
                            selected = currency == symbol,
                            onClick = { viewModel.setCurrency(symbol) },
                            label = { Text(symbol, style = MaterialTheme.typography.titleLarge) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            // --- SECTION UNITÉS ---
            SettingsSection(title = "Unités de Distance", icon = Icons.Default.Speed) {
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
                SettingsSection(title = "Confidentialité publicitaire", icon = Icons.Default.PrivacyTip) {
                    TextButton(
                        onClick = onPrivacyOptionsClick,
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("Modifier mes choix publicitaires")
                    }
                }
            }

            // --- SECTION INFORMATIONS ---
            SettingsSection(title = "Informations", icon = Icons.Default.Info) {
                Column {
                    TextButton(
                        onClick = onNavigateToPrivacy,
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("Politique de Confidentialité", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Version", style = MaterialTheme.typography.bodyMedium)
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
