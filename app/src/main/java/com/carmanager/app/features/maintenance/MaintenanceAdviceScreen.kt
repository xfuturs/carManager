package com.carmanager.app.features.maintenance

import com.carmanager.app.core.ui.components.CarManagerBackAppBar
import com.carmanager.app.core.ui.components.SecondaryPanel
import com.carmanager.app.core.ui.components.SecondarySectionTitle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carmanager.app.core.domain.model.FuelType
import com.carmanager.app.core.domain.model.Vehicle
import com.carmanager.app.core.domain.model.VehicleType
import com.carmanager.app.core.ui.theme.LocalCategoryColors
import com.carmanager.app.core.ui.theme.CarManagerShapes
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceAdviceScreen(
    onNavigateBack: () -> Unit,
    viewModel: MaintenanceAdviceViewModel = hiltViewModel()
) {
    val vehicle by viewModel.vehicle.collectAsState()

    Scaffold(
        topBar = {
            CarManagerBackAppBar(
                title = "Coach Entretien",
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            vehicle?.let { v ->
                item {
                    AdviceHeader(v)
                }

                // --- SECTION 1 : RAPPELS KILOMÉTRIQUES ---
                item { SectionTitle("Points de contrôle (Km)") }
                item {
                    MileageAdvice(v.currentMileage)
                }

                // --- SECTION 2 : CONSEILS DE SAISON ---
                item { SectionTitle("Conseils de saison") }
                item {
                    SeasonalAdvice()
                }

                // --- SECTION 3 : SELON LE TYPE ---
                item { SectionTitle("Spécificités ${getVehicleTypeLabel(v.type)}") }
                item {
                    TypeSpecificAdvice(v)
                }

                // --- SECTION 4 : ÉCO-CONDUITE ---
                item { SectionTitle("Éco-conduite & Économies") }
                item {
                    AdviceCard(
                        title = "Poids inutile",
                        description = "Retirez les barres de toit ou objets lourds inutiles. 100kg de trop = +0.5L/100km.",
                        icon = Icons.Default.Scale,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } ?: item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            

        }
    }
}

@Composable
private fun AdviceHeader(vehicle: Vehicle) {
    SecondaryPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(imageVector = when(vehicle.type) {
                VehicleType.CAR -> Icons.Default.DirectionsCar
                VehicleType.MOTORCYCLE -> Icons.Default.TwoWheeler
                VehicleType.UTILITY -> Icons.Default.LocalShipping
            }, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SecondarySectionTitle("${vehicle.brand} ${vehicle.model}")
                Text("Compteur : ${vehicle.currentMileage} km", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    SecondarySectionTitle(title)
}

@Composable
private fun MileageAdvice(mileage: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (mileage > 80000) {
            AdviceCard(
                title = "Courroie de Distribution",
                description = "Entre 80k et 120k km, la courroie est un point critique. Une rupture peut détruire le moteur.",
                icon = Icons.Default.Warning,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        AdviceCard(
            title = "Check-up des 10k km",
            description = "Tous les 10 000 km, vérifiez l'état des plaquettes de frein et l'usure symétrique des pneus.",
            icon = Icons.Default.Build,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SeasonalAdvice() {
    val month = Calendar.getInstance().get(Calendar.MONTH)
    
    when (month) {
        in 10..11, in 0..1 -> { // Hiver
            AdviceCard(
                title = "Grand Froid",
                description = "La batterie perd jusqu'à 30% de sa capacité. Vérifiez sa tension et l'antigel.",
                icon = Icons.Default.AcUnit,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        in 5..7 -> { // Été
            AdviceCard(
                title = "Fortes Chaleurs",
                description = "Vérifiez le niveau du liquide de refroidissement. La chaleur augmente la pression des pneus.",
                icon = Icons.Default.WbSunny,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        else -> {
            AdviceCard(
                title = "Visibilité Pluie",
                description = "En automne/printemps, changez vos essuie-glaces s'ils font du bruit ou laissent des traces.",
                icon = Icons.Default.Cloud,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TypeSpecificAdvice(vehicle: Vehicle) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (vehicle.type) {
            VehicleType.MOTORCYCLE -> {
                AdviceCard("Kit Chaîne", "Nettoyage et graissage tous les 500km. Une chaîne détendue s'use 3x plus vite.", Icons.Default.Settings, MaterialTheme.colorScheme.onSurfaceVariant)
                AdviceCard("Pneus Moto", "La gomme chauffe vite mais s'use de façon inégale si vous faites beaucoup d'autoroute.", Icons.Default.Speed, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            VehicleType.UTILITY -> {
                AdviceCard("Amortisseurs", "Avec les charges lourdes, ils s'usent plus vite. Vérifiez toute trace de fuite d'huile.", Icons.Default.KeyboardDoubleArrowDown, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                AdviceCard("Climatisation", "Faites tourner la clim 10 min par mois, même en hiver, pour entretenir les joints.", Icons.Default.Air, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (vehicle.fuelType == FuelType.ELECTRIC) {
            AdviceCard("Santé Batterie", "Privilégiez les recharges lentes à domicile. Gardez le niveau entre 20% et 80%.", Icons.Default.ElectricCar, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdviceCard(
    title: String,
    description: String,
    icon: ImageVector,
    color: Color
) {
    SecondaryPanel {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun getVehicleTypeLabel(type: VehicleType) = when(type) {
    VehicleType.CAR -> "Voiture"
    VehicleType.MOTORCYCLE -> "Moto"
    VehicleType.UTILITY -> "Utilitaire"
}
