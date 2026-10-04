package com.carmanager.app.features.settings

import com.carmanager.app.core.domain.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class PremiumOfferPresentationTest {
    @Test fun `price preserves Play formatting currency and localized spacing`() {
        for (price in listOf("12,99 €", "CHF 12.00", "¥1,500", "USD 4.99", "9\u00a0999 Ft"))
            assertEquals(price, premiumDisplayPrice(PremiumOffer(PREMIUM_PRODUCT_ID, price)))
    }
    @Test fun `missing offer has no fallback price`() { assertNull(premiumDisplayPrice(null)) }
    @Test fun `blank price is not shown`() { assertNull(premiumDisplayPrice(PremiumOffer(PREMIUM_PRODUCT_ID, " "))) }
    private fun strings(): Map<String, String> {
        val file = listOf(File("src/main/res/values/strings.xml"), File("app/src/main/res/values/strings.xml")).first { it.isFile }
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).associate { val n = nodes.item(it); n.attributes.getNamedItem("name").nodeValue to n.textContent }
    }
    @Test fun `price resource accepts Play price without hardcoded amount`() { assertEquals("%1\$s", strings().getValue("premium_one_time_price")) }
    @Test fun `short disclosure states one time no subscription no renewal`() {
        val text = strings().getValue("premium_purchase_disclosure")
        for (clause in listOf("Achat unique", "Aucun abonnement", "Aucun renouvellement automatique")) assertTrue(text.contains(clause))
    }
    @Test fun `terms preserve historical rights and separate future services without automatic migration`() {
        val resources = strings(); val text = (1..8).joinToString(" ") { resources.getValue("premium_terms_$it") }
        assertTrue(text.contains("achat unique")); assertTrue(text.contains("abonnement"))
        assertTrue(text.contains("automatiquement")); assertTrue(text.contains("fonctionnalités"))
        assertTrue(text.contains("achat unique déjà acquis")); assertTrue(text.contains("sépar"))
    }
}
