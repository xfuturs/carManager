package com.carmanager.app.core.data.repository

import com.carmanager.app.core.data.local.dao.DocumentDao
import com.carmanager.app.core.data.local.entity.DocumentEntity
import com.carmanager.app.core.domain.model.Document
import com.carmanager.app.core.domain.model.DocumentCategory
import com.carmanager.app.core.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.core.data.local.OwnedDatabaseAccess
import javax.inject.Inject

class DocumentRepositoryImpl @Inject constructor(
    private val documentDao: DocumentDao,
    private val session: WorkspaceSession,
    private val access: OwnedDatabaseAccess
) : DocumentRepository {

    override fun observeByVehicle(vehicleId: Long): Flow<List<Document>> {
        return session.observe { owner -> documentDao.observeByVehicle(vehicleId, owner).map { entities ->
            entities.map { it.toDomain(owner) }
        } }
    }

    override fun observeAll(): Flow<List<Document>> {
        return session.observe { owner -> documentDao.observeAll(owner).map { entities ->
            entities.map { it.toDomain(owner) }
        } }
    }

    override suspend fun saveDocument(document: Document): Long {
        return access.write(document.ownerKey, document.vehicleId) {
            if (document.id != 0L) check(documentDao.getById(document.id, document.ownerKey) != null) { "Enregistrement absent de cet espace." }
            documentDao.insert(document.toEntity())
        }
    }

    override suspend fun deleteDocument(document: Document) {
        access.write(document.ownerKey, document.vehicleId) {
            check(documentDao.getById(document.id, document.ownerKey) != null) { "Enregistrement absent de cet espace." }
            documentDao.delete(document.toEntity())
        }
    }

    private fun DocumentEntity.toDomain(ownerKey: String) = Document(
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
        date = date,
        ownerKey = ownerKey
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
