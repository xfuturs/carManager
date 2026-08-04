package com.carmanager.app.core.domain.repository

import com.carmanager.app.core.domain.model.Document
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun observeByVehicle(vehicleId: Long): Flow<List<Document>>
    fun observeAll(): Flow<List<Document>>
    suspend fun saveDocument(document: Document): Long
    suspend fun deleteDocument(document: Document)
}
