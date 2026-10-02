package com.carmanager.app.core.di

import com.carmanager.app.core.data.billing.GooglePlayBillingGateway
import com.carmanager.app.core.data.billing.PlayBillingGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {
    @Binds @Singleton
    abstract fun bindPlayBillingGateway(impl: GooglePlayBillingGateway): PlayBillingGateway
}
