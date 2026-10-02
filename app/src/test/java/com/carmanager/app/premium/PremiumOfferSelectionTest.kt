package com.carmanager.app.premium

import com.carmanager.app.core.data.billing.*
import com.carmanager.app.core.domain.model.PREMIUM_PRODUCT_ID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PremiumOfferSelectionTest {
    private val buy = OneTimeOption("12,00 \$US", "offer-token")

    @Test fun `single standard buy retains localized price and correct token`() {
        val selected = selectPremiumOption(PREMIUM_PRODUCT_ID, "inapp", listOf(buy))
        assertEquals("12,00 \$US", selected?.formattedPrice)
        assertEquals("offer-token", selected?.offerToken)
    }
    @Test fun `different product and subscriptions are rejected`() {
        assertNull(selectPremiumOption("other", "inapp", listOf(buy)))
        assertNull(selectPremiumOption(PREMIUM_PRODUCT_ID, "subs", listOf(buy)))
    }
    @Test fun `missing or multiple eligible options are never selected arbitrarily`() {
        assertNull(selectPremiumOption(PREMIUM_PRODUCT_ID, "inapp", emptyList()))
        assertNull(selectPremiumOption(PREMIUM_PRODUCT_ID, "inapp", listOf(buy, buy.copy(offerToken = "second-token"))))
    }
    @Test fun `rental preorder discount and named offer require unsupported policy`() {
        for (option in listOf(buy.copy(rental = true), buy.copy(preorder = true), buy.copy(discount = true), buy.copy(offerId = "offer"))) {
            assertNull(selectPremiumOption(PREMIUM_PRODUCT_ID, "inapp", listOf(option)))
        }
    }
    @Test fun `blank price or token never becomes an offer`() {
        assertNull(selectPremiumOption(PREMIUM_PRODUCT_ID, "inapp", listOf(buy.copy(formattedPrice = " "))))
        assertNull(selectPremiumOption(PREMIUM_PRODUCT_ID, "inapp", listOf(buy.copy(offerToken = ""))))
    }
    @Test fun `launch freshness expires after five minutes and rejects backward time`() {
        assertTrue(isPremiumOfferFresh(1_000, 301_000))
        assertFalse(isPremiumOfferFresh(1_000, 301_001))
        assertFalse(isPremiumOfferFresh(1_000, 999))
    }
}
