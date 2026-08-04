package com.carmanager.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Confidentialité", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Politique de Confidentialité - Car Manager",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Dernière mise à jour : 4 Août 2026",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PrivacySection(
                title = "1. Collecte des données",
                content = "Nous collectons votre adresse e-mail lors de la création de votre compte afin de sécuriser l'accès à vos données. Les informations relatives à vos véhicules (kilométrage, entretiens, photos) sont également collectées pour assurer le service de suivi et de sauvegarde."
            )

            PrivacySection(
                title = "2. Utilisation et Stockage",
                content = "Vos données sont stockées de manière sécurisée via les services Google Firebase. Elles sont utilisées exclusivement pour vous fournir les fonctionnalités de l'application (synchronisation cloud, statistiques, rappels)."
            )

            PrivacySection(
                title = "3. Partage des données",
                content = "Car Manager ne vend, ne loue, ni ne partage vos données personnelles avec des tiers à des fins marketing. Vos données sont privées et accessibles uniquement par vous via votre compte."
            )

            PrivacySection(
                title = "4. Publicité",
                content = "L'application utilise Google AdMob pour afficher des publicités. Des identifiants publicitaires anonymes peuvent être utilisés pour personnaliser les annonces, sous réserve de votre consentement via le formulaire RGPD dédié."
            )

            PrivacySection(
                title = "5. Vos droits (Droit à l'oubli)",
                content = "Conformément au RGPD, vous disposez d'un droit d'accès et de suppression de vos données. Vous pouvez supprimer l'intégralité de vos informations et votre compte directement depuis les paramètres de l'application."
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PrivacySection(title: String, content: String) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
    }
}
