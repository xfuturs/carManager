package com.carmanager.app.core.data.repository

import com.carmanager.app.core.data.local.dao.DocumentDao
import com.carmanager.app.core.data.local.entity.DocumentEntity
import com.carmanager.app.core.domain.model.Document
import com.carmanager.app.core.domain.model.DocumentCategory
import com.carmanager.app.core.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DocumentRepositoryImpl @Inject constructor(
    private val documentDao: DocumentDao
) : DocumentRepository {

    override fun observeByVehicle(vehicleId: Long): Flow<List<Document>> {
        return documentDao.observeByVehicle(vehicleId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeAll(): Flow<List<Document>> {
        return documentDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveDocument(document: Document): Long {
        return documentDao.insert(document.toEntity())
    }

    override suspend fun deleteDocument(document: Document) {
        documentDao.delete(document.toEntity())
    }

    private fun DocumentEntity.toDomain() = Document(
        id = id,
        vehicleId = vehicleId,
        title = title,
        category = try {
            DocumentCategory.valueOf(category)
        } catch (e: Exception) {
            // Rétro-compatibilité pour les anciens noms
            when(category) {
                "PHOTO" -> DocumentCategory.PHOTOS
                "INVOICE" -> DocumentCategory.MAINTENANCE
                else -> DocumentCategory.OTHER
            }
        },
        filePath = filePath,
        date = date
    )

    private fun Document.toEntity() = DocumentEntity(
        id = id,
        vehicleId = vehicleId,
        title = title,
        category = category.name,
        filePath = filePath,
        date = date
    )
}
