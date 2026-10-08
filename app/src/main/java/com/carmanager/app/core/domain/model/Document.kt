package com.carmanager.app.core.domain.model

enum class DocumentCategory {
    ADMINISTRATIVE,      // Carte grise, certificat...
    INSURANCE,           // Contrats, cartes vertes...
    TECHNICAL_INSPECTION,// Contrôles techniques
    MAINTENANCE,         // Factures garage
    FUEL,                // Tickets carburant
    PHOTOS,              // Photos véhicule
    CLAIMS,              // Sinistres, constats
    OTHER,               // Divers
    REPORTS              // Rapports PDF générés par Car Manager
}

data class Document(
    val id: Long = 0,
    val vehicleId: Long,
    val title: String,
    val category: DocumentCategory,
    val filePath: String,
    val date: Long,
    val ownerKey: String = com.carmanager.app.core.domain.session.LocalGarageOwner.KEY,
)
