package com.carmanager.app.core.data.billing

import com.carmanager.app.core.domain.model.PREMIUM_PRODUCT_ID

internal data class OneTimeOption(
    val formattedPrice: String,
    val offerToken: String,
    val offerId: String? = null,
    val rental: Boolean = false,
    val preorder: Boolean = false,
    val discount: Boolean = false
)

/** V1 accepte une seule option BUY standard ; aucune sélection arbitraire. */
internal fun selectPremiumOption(productId: String, productType: String, options: List<OneTimeOption>): OneTimeOption? {
    if (productId != PREMIUM_PRODUCT_ID || productType != "inapp") return null
    return options.singleOrNull()?.takeIf {
        !it.rental && !it.preorder && !it.discount && it.offerId == null &&
            it.offerToken.isNotBlank() && it.formattedPrice.isNotBlank()
    }
}

internal fun isPremiumOfferFresh(fetchedAt: Long, now: Long): Boolean =
    now >= fetchedAt && now - fetchedAt <= 5 * 60_000L
