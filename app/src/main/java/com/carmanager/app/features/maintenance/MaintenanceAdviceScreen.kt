package com.carmanager.app.features.maintenance

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
import com.carmanager.app.core.ui.theme.VehicleColor
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
            CenterAlignedTopAppBar(
                title = { Text("Coach Entretien", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
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
                        color = Color(0xFF4CAF50)
                    )
                }
            } ?: item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun AdviceHeader(vehicle: Vehicle) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when(vehicle.type) {
                    VehicleType.CAR -> Icons.Default.DirectionsCar
                    VehicleType.MOTORCYCLE -> Icons.Default.TwoWheeler
                    VehicleType.UTILITY -> Icons.Default.LocalShipping
                },
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "${vehicle.brand} ${vehicle.model}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Compteur : ${vehicle.currentMileage} km",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun MileageAdvice(mileage: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (mileage > 80000) {
            AdviceCard(
                title = "Courroie de Distribution",
                description = "Entre 80k et 120k km, la courroie est un point critique. Une rupture peut détruire le moteur.",
                icon = Icons.Default.Warning,
                color = MaterialTheme.colorScheme.error
            )
        }
        
        AdviceCard(
            title = "Check-up des 10k km",
            description = "Tous les 10 000 km, vérifiez l'état des plaquettes de frein et l'usure symétrique des pneus.",
            icon = Icons.Default.Build,
            color = MaterialTheme.colorScheme.secondary
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
                color = Color(0xFF03A9F4)
            )
        }
        in 5..7 -> { // Été
            AdviceCard(
                title = "Fortes Chaleurs",
                description = "Vérifiez le niveau du liquide de refroidissement. La chaleur augmente la pression des pneus.",
                icon = Icons.Default.WbSunny,
                color = Color(0xFFFF9800)
            )
        }
        else -> {
            AdviceCard(
                title = "Visibilité Pluie",
                description = "En automne/printemps, changez vos essuie-glaces s'ils font du bruit ou laissent des traces.",
                icon = Icons.Default.Cloud,
                color = Color(0xFF607D8B)
            )
        }
    }
}

@Composable
private fun TypeSpecificAdvice(vehicle: Vehicle) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (vehicle.type) {
            VehicleType.MOTORCYCLE -> {
                AdviceCard("Kit Chaîne", "Nettoyage et graissage tous les 500km. Une chaîne détendue s'use 3x plus vite.", Icons.Default.Settings, Color(0xFF795548))
                AdviceCard("Pneus Moto", "La gomme chauffe vite mais s'use de façon inégale si vous faites beaucoup d'autoroute.", Icons.Default.Speed, Color(0xFF607D8B))
            }
            VehicleType.UTILITY -> {
                AdviceCard("Amortisseurs", "Avec les charges lourdes, ils s'usent plus vite. Vérifiez toute trace de fuite d'huile.", Icons.Default.KeyboardDoubleArrowDown, Color(0xFF673AB7))
            }
            else -> {
                AdviceCard("Climatisation", "Faites tourner la clim 10 min par mois, même en hiver, pour entretenir les joints.", Icons.Default.Air, Color(0xFF00BCD4))
            }
        }

        if (vehicle.fuelType == FuelType.ELECTRIC) {
            AdviceCard("Santé Batterie", "Privilégiez les recharges lentes à domicile. Gardez le niveau entre 20% et 80%.", Icons.Default.ElectricCar, Color(0xFF4CAF50))
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                color = color.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

private fun getVehicleTypeLabel(type: VehicleType) = when(type) {
    VehicleType.CAR -> "Voiture"
    VehicleType.MOTORCYCLE -> "Moto"
    VehicleType.UTILITY -> "Utilitaire"
}
