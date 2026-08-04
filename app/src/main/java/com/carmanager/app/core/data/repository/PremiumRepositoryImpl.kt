package com.carmanager.app.core.data.repository

import android.content.Context
import com.android.billingclient.api.*
import com.carmanager.app.core.domain.repository.AuthRepository
import com.carmanager.app.core.domain.repository.PremiumRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository
) : PremiumRepository, PurchasesUpdatedListener {

    private val _isPremium = MutableStateFlow(false)
    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private var billingClient: BillingClient? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Liste des comptes administrateurs / testeurs
    private val adminEmails = listOf(
        "admin@xfuturs.com",
        "tester@xfuturs.com"
    )

    private var isPlayStorePremium = false
    private var isAdminPremium = false

    override fun initialize() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        startConnection()
        observeCurrentUser()
    }

    private fun observeCurrentUser() {
        scope.launch {
            authRepository.currentUser.collect { user ->
                isAdminPremium = user?.email?.lowercase() in adminEmails
                updateFinalStatus()
            }
        }
    }

    private fun startConnection() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    checkPremiumStatus()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Reconnect if needed
            }
        })
    }

    override fun checkPremiumStatus() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient?.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                isPlayStorePremium = purchases.any { purchase ->
                    purchase.products.contains("premium_upgrade") && 
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                updateFinalStatus()
            }
        }
    }

    private fun updateFinalStatus() {
        _isPremium.value = isPlayStorePremium || isAdminPremium
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            checkPremiumStatus()
        }
    }
}
