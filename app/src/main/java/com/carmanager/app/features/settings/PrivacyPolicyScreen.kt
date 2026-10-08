package com.carmanager.app.features.settings

import com.carmanager.app.core.ui.components.CarManagerBackAppBar
import com.carmanager.app.core.ui.components.SecondarySectionTitle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SecondarySectionTitle("Politique de Confidentialité - Car Manager")

            Text(
                text = "Dernière mise à jour : 7 octobre 2026",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PrivacySection(
                title = "1. Collecte des données",
                content = "La connexion utilise Google et Firebase pour votre identité et votre adresse e-mail. L’authentification est indépendante du garage local : se connecter, se déconnecter ou changer de compte Google ne change pas les véhicules et leurs historiques sur cette installation."
            )

            PrivacySection(
                title = "2. Utilisation et Stockage",
                content = "En mode invité comme avec un compte Google, le garage, les historiques et les documents sont gérés localement sur cet appareil. Car Manager ne fournit ni synchronisation cloud du garage ni sauvegarde Google Drive. La sauvegarde ou le transfert du système Android peut toutefois inclure les données locales et documents, selon les réglages et capacités de l'appareil ; leur restauration complète n'est pas garantie. Aucune restauration cloud applicative n’est disponible. Google/Firebase, Google Play Billing et les services publicitaires utilisent des connexions réseau."
            )

            PrivacySection(
                title = "3. Partage des données",
                content = "Le garage et les documents sont conservés dans le stockage privé de l'application, dans un seul garage local sur cette installation. Les différentes identités Google utilisées sur cette même installation accèdent au même garage. Les services Google/Firebase, Google Play Billing et Google AdMob/UMP traitent les données nécessaires à leurs fonctions. Un document ou rapport peut être transmis au destinataire que vous choisissez lors d'un partage."
            )

            PrivacySection(
                title = "4. Publicité",
                content = "L'application utilise Google AdMob et UMP pour les publicités et la gestion des choix de confidentialité. Selon ces choix et la configuration des services, des identifiants publicitaires ou d'appareil, des données techniques et des interactions peuvent être traités par Google. Ces données ne sont pas présentées comme anonymes. Les options publicitaires requises sont accessibles dans les paramètres."
            )

            PrivacySection(
                title = "5. Vos droits (Droit à l'oubli)",
                content = "La suppression du compte depuis les paramètres vise les anciennes collections Firestore connues et le compte Firebase. Elle ne supprime pas automatiquement les véhicules, historiques, documents, rapports ou rappels du garage local. Vous pouvez toujours supprimer individuellement vos véhicules et documents dans l’application. Une erreur est signalée et peut laisser une suppression partielle du compte. Les exports partagés et les données conservées sur d'autres appareils ne sont pas effacés par cette action."
            )

        }
    }
}

@Composable
private fun PrivacySection(title: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondarySectionTitle(title)
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )
    }
}
