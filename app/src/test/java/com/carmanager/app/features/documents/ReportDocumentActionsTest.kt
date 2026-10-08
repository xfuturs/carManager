package com.carmanager.app.features.documents

import com.carmanager.app.core.data.billing.BillingOutcome
import com.carmanager.app.core.data.billing.BillingReply
import com.carmanager.app.core.data.repository.PremiumRepositoryImpl
import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.ownership.TestDeletionRegistry
import com.carmanager.app.premium.FakePlayBillingGateway
import com.carmanager.app.premium.premiumPurchase
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReportDocumentActionsTest {
    @TempDir lateinit var directory: File
    private val session = WorkspaceSession(TestDeletionRegistry()).apply { completeBootstrap() }
    private val bytes = ByteArray(40_000) { (it % 123).toByte() }
    private val source by lazy { File(directory, "saved.pdf").apply { writeBytes(bytes) } }
    private val document get() = Document(1, 7, "Rapport du 03/10/2026", DocumentCategory.REPORTS,
        source.path, 0, "local:device")
    private val presented = mutableListOf<Pair<File, Boolean>>()
    private var resolves = 0
    private var resolveHook: () -> Unit = {}
    private fun actions() = ReportDocumentActions(session, { resolves++; resolveHook(); source }, { f, share -> presented += f to share })
    private suspend fun rejects(action: suspend () -> Unit) {
        try { action(); fail<Unit>("expected rejection") } catch (_: IllegalStateException) {}
        assertTrue(presented.isEmpty())
    }
    @Test fun `FREE user opens and shares same stored report`() = runTest {
        val premium = PremiumRepositoryImpl(FakePlayBillingGateway(), backgroundScope).also { it.initialize(); runCurrent() }
        assertFalse(premium.isPremium.value)
        val actions = actions(); actions.open(document); actions.share(document)
        assertEquals(listOf(source to false, source to true), presented)
        assertArrayEquals(bytes, source.readBytes()); assertFalse(premium.isPremium.value)
    }
    @Test fun `loss of entitlement does not revoke existing report actions`() = runTest {
        val gateway = FakePlayBillingGateway().apply { purchaseReply = BillingReply(BillingOutcome.OK, listOf(premiumPurchase())) }
        val premium = PremiumRepositoryImpl(gateway, backgroundScope).also { it.initialize(); runCurrent() }
        assertTrue(premium.isPremium.value)
        gateway.purchaseReply = BillingReply(BillingOutcome.OK, emptyList())
        premium.checkPremiumStatus(); runCurrent(); assertFalse(premium.isPremium.value)
        val output = ByteArrayOutputStream(); val actions = actions()
        actions.open(document); actions.share(document); assertTrue(actions.saveCopy(document) { output })
        assertArrayEquals(bytes, output.toByteArray()); assertEquals(2, presented.size)
    }
    @Test fun `export uses existing bytes without replacing source`() = runTest {
        val output = ByteArrayOutputStream()
        assertTrue(actions().saveCopy(document) { output }); assertArrayEquals(bytes, output.toByteArray())
        assertArrayEquals(bytes, source.readBytes()); assertEquals(1, directory.listFiles()!!.size)
    }
    @Test fun `cancel export does not even resolve source`() = runTest {
        assertFalse(actions().saveCopy(document, null)); assertEquals(0, resolves); assertTrue(source.isFile)
    }
    @Test fun `failed provider leaves report available for subsequent sharing`() = runTest {
        try { actions().saveCopy(document) { throw IOException("provider") }; fail<Unit>("failure") } catch (_: IOException) {}
        actions().share(document); assertEquals(source to true, presented.single()); assertArrayEquals(bytes, source.readBytes())
    }
    @Test fun `owner switch refuses stale report`() = runTest {
        val doc = document; session.beginBootstrap(); rejects { actions().open(doc) }; assertEquals(0, resolves)
    }
    @Test fun `owner switch during file resolution refuses sharing`() = runTest {
        resolveHook = { session.beginBootstrap() }; rejects { actions().share(document) }
    }
    @Test fun `non report cannot enter report actions`() = runTest {
        rejects { actions().open(document.copy(category = DocumentCategory.PHOTOS)) }; assertEquals(0, resolves)
    }
    @Test fun `missing file raises error without regeneration`() = runTest {
        val doc = document; assertTrue(source.delete()); rejects { actions().open(doc) }; assertFalse(source.exists())
    }
}
