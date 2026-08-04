package com.carmanager.app.core.di

import com.carmanager.app.core.data.repository.FuelRepositoryImpl
import com.carmanager.app.core.data.repository.MaintenanceRepositoryImpl
import com.carmanager.app.core.data.repository.MileageRepositoryImpl
import com.carmanager.app.core.data.repository.DocumentRepositoryImpl
import com.carmanager.app.core.data.repository.VehicleRepositoryImpl
import com.carmanager.app.core.domain.repository.FuelRepository
import com.carmanager.app.core.domain.repository.MaintenanceRepository
import com.carmanager.app.core.domain.repository.MileageRepository
import com.carmanager.app.core.domain.repository.DocumentRepository
import com.carmanager.app.core.domain.repository.VehicleRepository
import com.carmanager.app.core.data.repository.AuthRepositoryImpl
import com.carmanager.app.core.data.repository.PremiumRepositoryImpl
import com.carmanager.app.core.data.repository.SettingsRepositoryImpl
import com.carmanager.app.core.data.repository.SyncRepositoryImpl
import com.carmanager.app.core.domain.repository.AuthRepository
import com.carmanager.app.core.domain.repository.PremiumRepository
import com.carmanager.app.core.domain.repository.SettingsRepository
import com.carmanager.app.core.domain.repository.SyncRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(
        syncRepositoryImpl: SyncRepositoryImpl
    ): SyncRepository

    @Binds
    @Singleton
    abstract fun bindPremiumRepository(
        premiumRepositoryImpl: PremiumRepositoryImpl
    ): PremiumRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindVehicleRepository(
        vehicleRepositoryImpl: VehicleRepositoryImpl
    ): VehicleRepository

    @Binds
    @Singleton
    abstract fun bindFuelRepository(
        fuelRepositoryImpl: FuelRepositoryImpl
    ): FuelRepository

    @Binds
    @Singleton
    abstract fun bindMaintenanceRepository(
        maintenanceRepositoryImpl: MaintenanceRepositoryImpl
    ): MaintenanceRepository

    @Binds
    @Singleton
    abstract fun bindMileageRepository(
        mileageRepositoryImpl: MileageRepositoryImpl
    ): MileageRepository

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(
        documentRepositoryImpl: DocumentRepositoryImpl
    ): DocumentRepository
}
