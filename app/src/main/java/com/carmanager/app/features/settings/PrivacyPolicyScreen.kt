package com.carmanager.app.features.settings

import com.carmanager.app.core.ui.components.CarManagerBackAppBar
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
            CarManagerBackAppBar(
                title = "Confidentialité",
                onNavigateBack = onNavigateBack
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
                text = "Dernière mise à jour : 30 septembre 2026",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PrivacySection(
                title = "1. Collecte des données",
                content = "La connexion utilise Google et Firebase pour votre identité et votre adresse e-mail. Vos véhicules et leurs historiques sont conservés localement dans un espace invité ou dans un espace distinct pour chaque compte."
            )

            PrivacySection(
                title = "2. Utilisation et Stockage",
                content = "En mode invité comme avec un compte Google, le garage, les historiques et les documents sont gérés localement sur cet appareil. Car Manager ne fournit ni synchronisation cloud du garage ni sauvegarde Google Drive. La sauvegarde ou le transfert du système Android peut toutefois inclure les données locales et documents, selon les réglages et capacités de l'appareil ; leur restauration complète n'est pas garantie. Aucun transfert automatique entre espaces ni restauration cloud applicative ne sont disponibles. Google/Firebase, Google Play Billing et les services publicitaires utilisent des connexions réseau."
            )

            PrivacySection(
                title = "3. Partage des données",
                content = "Le garage et les documents sont conservés dans le stockage privé de l'application, avec un espace invité et un espace distinct par compte sur cette installation. Les services Google/Firebase, Google Play Billing et Google AdMob/UMP traitent les données nécessaires à leurs fonctions. Un document ou rapport peut être transmis au destinataire que vous choisissez lors d'un partage."
            )

            PrivacySection(
                title = "4. Publicité",
                content = "L'application utilise Google AdMob et UMP pour les publicités et la gestion des choix de confidentialité. Selon ces choix et la configuration des services, des identifiants publicitaires ou d'appareil, des données techniques et des interactions peuvent être traités par Google. Ces données ne sont pas présentées comme anonymes. Les options publicitaires requises sont accessibles dans les paramètres."
            )

            PrivacySection(
                title = "5. Vos droits (Droit à l'oubli)",
                content = "La suppression depuis les paramètres vise les anciennes données envoyées dans les collections Firestore connues du compte, ses données et documents sur cette installation, ses rappels identifiables, puis son compte Firebase. Une erreur est signalée et peut laisser une suppression partielle. Les données invitées et celles des autres comptes restent conservées. Les exports partagés et les données conservées sur d'autres appareils ne sont pas effacés par cette action."
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
