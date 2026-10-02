package com.carmanager.app.core.domain.model

const val PREMIUM_PRODUCT_ID = "premium_upgrade"

data class PremiumOffer(val productId: String, val formattedPrice: String)
enum class PremiumEntitlement { FREE, PENDING, ACTIVE }
enum class PremiumIssue { STORE_UNAVAILABLE, OFFER_UNAVAILABLE, PURCHASE_FAILED, ACKNOWLEDGEMENT_FAILED }

/** État de session issu de Play, sans identité Firebase ni licence persistée. */
data class PremiumState(
    val entitlement: PremiumEntitlement = PremiumEntitlement.FREE,
    val offer: PremiumOffer? = null,
    val isLoading: Boolean = true,
    val isPurchasing: Boolean = false,
    val acknowledgementPending: Boolean = false,
    val issue: PremiumIssue? = null
) {
    val isPremium: Boolean get() = entitlement == PremiumEntitlement.ACTIVE
    val canPurchase: Boolean get() = entitlement == PremiumEntitlement.FREE && offer != null &&
        !isLoading && !isPurchasing
}
