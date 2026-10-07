package com.carmanager.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.carmanager.app.core.data.local.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT documents.* FROM documents JOIN vehicles ON vehicles.id = documents.vehicleId WHERE vehicles.ownerKey = :ownerKey AND documents.vehicleId = :vehicleId ORDER BY date DESC")
    suspend fun getByVehicle(vehicleId: Long, ownerKey: String): List<DocumentEntity>

    // Protection des fichiers : inclut aussi les autres véhicules du même espace.
    @Query("SELECT filePath FROM documents WHERE vehicleId != :vehicleId")
    suspend fun filePathsOutsideVehicle(vehicleId: Long): List<String>

    @Query("SELECT filePath FROM documents")
    suspend fun allFilePaths(): List<String>

    @Query("SELECT documents.* FROM documents JOIN vehicles ON vehicles.id = documents.vehicleId WHERE vehicles.ownerKey = :ownerKey AND documents.vehicleId = :vehicleId ORDER BY date DESC")
    fun observeByVehicle(vehicleId: Long, ownerKey: String): Flow<List<DocumentEntity>>

    @Query("SELECT documents.* FROM documents JOIN vehicles ON vehicles.id = documents.vehicleId WHERE vehicles.ownerKey = :ownerKey")
    fun observeAll(ownerKey: String): Flow<List<DocumentEntity>>

    @Query("SELECT documents.* FROM documents JOIN vehicles ON vehicles.id = documents.vehicleId WHERE vehicles.ownerKey = :ownerKey AND documents.id = :id")
    suspend fun getById(id: Long, ownerKey: String): DocumentEntity?

    @Query("SELECT documents.* FROM documents JOIN vehicles ON vehicles.id = documents.vehicleId WHERE vehicles.ownerKey = :ownerKey")
    suspend fun getAll(ownerKey: String): List<DocumentEntity>

    // Administratif : sert uniquement à refuser la suppression d'un fichier partagé.
    @Query("SELECT filePath FROM documents JOIN vehicles ON vehicles.id = documents.vehicleId WHERE vehicles.ownerKey != :ownerKey")
    suspend fun filePathsOutsideOwner(ownerKey: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: DocumentEntity): Long

    @Delete
    suspend fun delete(document: DocumentEntity)
}
