package com.carmanager.app.core.di

import android.content.Context
import androidx.room.Room
import com.carmanager.app.core.data.local.CarManagerDatabase
import com.carmanager.app.core.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CarManagerDatabase =
        Room.databaseBuilder(
            context,
            CarManagerDatabase::class.java,
            CarManagerDatabase.DATABASE_NAME,
        ).addCallback(CarManagerDatabase.getCallback(context))
            .build()

    @Provides
    fun provideVehicleDao(database: CarManagerDatabase): VehicleDao = database.vehicleDao()

    @Provides
    fun provideFuelRecordDao(database: CarManagerDatabase): FuelRecordDao = database.fuelRecordDao()

    @Provides
    fun provideMaintenanceDao(database: CarManagerDatabase): MaintenanceDao = database.maintenanceDao()

    @Provides
    fun provideMileageDao(database: CarManagerDatabase): MileageDao = database.mileageDao()

    @Provides
    fun provideDocumentDao(database: CarManagerDatabase): DocumentDao = database.documentDao()

    @Provides
    fun provideVehicleReferenceDao(database: CarManagerDatabase): VehicleReferenceDao = database.vehicleReferenceDao()
}
