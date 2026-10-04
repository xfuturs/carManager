package com.carmanager.app.a14

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class PremiumClarityTest {
    private fun strings(): Map<String,String> {
        val file = listOf(File("src/main/res/values/strings.xml"), File("app/src/main/res/values/strings.xml")).first { it.isFile }
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).associate { val n = nodes.item(it); n.attributes.getNamedItem("name").nodeValue to n.textContent }
    }
    @Test fun `five concrete benefits explain Ads creation selection library and save share`() {
        val s = strings()
        for ((name, content) in mapOf("ads" to "publicités", "pdf" to "PDF", "sections" to "sections", "library" to "Rapports Car Manager", "save_share" to "partage")) {
            assertTrue(s.getValue("premium_benefit_$name").contains(content))
        }
        assertTrue(s.getValue("premium_existing_reports_free").contains("sans droit Premium actif"))
    }
    @Test fun `refund copy keeps commercial refusal conditional and preserves statutory rights`() {
        val s = strings()
        assertTrue(s.getValue("premium_refund_2").contains("certaines demandes effectuées dans les 48 heures"))
        assertTrue(s.getValue("premium_refund_3").contains("uniquement sur un changement d’avis"))
        assertTrue(s.getValue("premium_refund_3").contains("peut refuser"))
        assertTrue(s.getValue("premium_refund_3").contains("plus de 14 jours calendaires"))
        assertTrue(s.getValue("premium_refund_4").contains("ne limite pas les droits impératifs"))
        assertTrue(s.getValue("premium_refund_5").contains("conditions légales nécessaires"))
    }
    @Test fun `revocation copy never deletes locally retained reports`() {
        val s = strings(); val text = s.getValue("premium_refund_6")
        assertTrue(text.contains("redeviennent indisponibles")); assertTrue(text.contains("rapports déjà enregistrés localement ne sont pas supprimés"))
        assertEquals("Politique de remboursement",s.getValue("premium_refund_title"))
        assertTrue((1..6).all { s.getValue("premium_refund_$it").isNotBlank() })
    }
    @Test fun `purchase disclosure and refund copy do not invent a price subscription or rights waiver`() {
        val s = strings(); assertEquals("%1\$s",s.getValue("premium_one_time_price"))
        assertTrue(s.getValue("premium_purchase_disclosure").contains("Aucun abonnement"))
        val text = (1..6).joinToString(" ") { s.getValue("premium_refund_$it") }
        for (prohibited in listOf("Aucun remboursement après", "impossible après 48", "automatiquement perdu", "Je renonce")) assertFalse(text.contains(prohibited))
    }
}
