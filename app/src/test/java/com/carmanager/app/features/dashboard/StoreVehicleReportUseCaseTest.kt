package com.carmanager.app.features.dashboard

import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.repository.DocumentRepository
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.core.util.ReportFileIO
import com.carmanager.app.ownership.TestDeletionRegistry
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

class StoreVehicleReportUseCaseTest {
    @TempDir lateinit var directory: File
    private val registry = TestDeletionRegistry()
    private val session = WorkspaceSession(registry).apply { completeBootstrap() }
    private val owner get() = session.owner.value
    private val repository = mockk<DocumentRepository>()
    private val rows = mutableListOf<Document>()
    private var nextId = 0L
    private var failInsert = false
    private var failRender = false
    private var afterRender: () -> Unit = {}
    private var beforeCommit: () -> Unit = {}
    private var report: GenerateVehicleReportUseCase.ReportData? = GenerateVehicleReportUseCase.ReportData(
        Vehicle(7, "Renault", "Clio", 2020, 1000, FuelType.GASOLINE, powerHp = 90, licensePlate = null,
            createdAt = 0, updatedAt = 0, ownerKey = owner), emptyList(), emptyList())
    private val storage = object : ReportStorage {
        override fun create(data: GenerateVehicleReportUseCase.ReportData, date: Long): String {
            val path = ReportFileIO.create(directory, ReportFileIO.filename(data.vehicle.model, date)) {
                it.write("%PDF-test-stored".toByteArray()); if (failRender) throw IOException("render")
            }
            afterRender(); return path
        }
        override fun delete(path: String) = File(path).delete()
    }
    private fun useCase(): StoreVehicleReportUseCase {
        coEvery { repository.saveDocument(any()) } coAnswers {
            if (failInsert) throw IOException("database")
            val doc = firstArg<Document>().copy(id = ++nextId); rows += doc; doc.id
        }
        return StoreVehicleReportUseCase({ report }, repository, session, storage, { expected, vehicle, action ->
            assertEquals(7, vehicle); session.requireWritable(expected)
            val snapshot = rows.toList()
            try { action().also { beforeCommit(); session.requireWritable(expected) } }
            catch (e: Throwable) { rows.clear(); rows.addAll(snapshot); throw e }
        }, { 1_790_000_000_000 })
    }
    private suspend fun fails(type: Class<out Throwable> = IllegalStateException::class.java) {
        try { useCase()(7); fail<Unit>("expected failure") } catch (e: Throwable) { assertTrue(type.isInstance(e), e.toString()) }
        assertTrue(rows.isEmpty()); assertEquals(0, directory.listFiles()!!.size)
    }
    @Test fun `successful report is durable indexed and owned`() = runTest {
        val doc = useCase()(7)
        assertEquals(DocumentCategory.REPORTS, doc.category); assertEquals("local:device", doc.ownerKey)
        assertEquals(7, doc.vehicleId); assertTrue(doc.title.startsWith("Rapport du "))
        assertEquals(doc, rows.single()); assertTrue(File(doc.filePath).length() > 0)
        assertEquals(doc, rows.single().copy()); assertEquals(1, directory.listFiles()!!.size)
    }
    @Test fun `each explicit generation retains a distinct report`() = runTest {
        val useCase = useCase(); val a = useCase(7); val b = useCase(7)
        assertNotEquals(a.id, b.id); assertNotEquals(a.filePath, b.filePath)
        assertEquals(2, rows.size); assertEquals(2, directory.listFiles()!!.size)
    }
    @Test fun `render failure creates neither file nor row`() = runTest { failRender = true; fails(IOException::class.java) }
    @Test fun `database rejection deletes newly generated file`() = runTest { failInsert = true; fails(IOException::class.java) }
    @Test fun `switch during rendering rolls back and cleans file`() = runTest { afterRender = { session.beginBootstrap() }; fails() }
    @Test fun `switch before transaction commit rolls back and cleans file`() = runTest { beforeCommit = { session.beginBootstrap() }; fails() }
    @Test fun `missing vehicle creates nothing`() = runTest { report = null; fails() }
    @Test fun `foreign owner cannot generate`() = runTest { report = report!!.copy(vehicle = report!!.vehicle.copy(ownerKey = "firebase:B")); fails() }
    @Test fun `wrong vehicle cannot generate`() = runTest { report = report!!.copy(vehicle = report!!.vehicle.copy(id = 9)); fails() }
    @Test fun `blocked account cannot generate`() = runTest { registry.block(owner); fails() }
    @Test fun `cancelled commit cleans file and row`() = runTest { beforeCommit = { throw CancellationException() }; fails(CancellationException::class.java) }
    @Test fun `failed next generation preserves earlier report`() = runTest {
        val first = useCase()(7); val bytes = File(first.filePath).readBytes(); failInsert = true
        try { useCase()(7); fail<Unit>("expected failure") } catch (_: IOException) {}
        assertEquals(listOf(first), rows); assertEquals(1, directory.listFiles()!!.size)
        assertArrayEquals(bytes, File(first.filePath).readBytes())
    }
}
