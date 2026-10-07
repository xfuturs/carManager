package com.carmanager.app.a15_2

import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.repository.DocumentRepository
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.core.util.*
import com.carmanager.app.features.dashboard.*
import com.carmanager.app.ownership.TestDeletionRegistry
import io.mockk.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ReportPresentationSnapshotTest {
    @TempDir lateinit var directory: File
    private val session=WorkspaceSession(TestDeletionRegistry()).apply { setAuthenticatedUid(null) }
    private val documents=mockk<DocumentRepository>()
    private var settings=ReportPresentationSettings()
    private var captures=0
    private val order=mutableListOf<String>()
    private var rendered: GenerateVehicleReportUseCase.ReportData?=null
    private val renderedRows=mutableListOf<StructuredReportRow>()
    private var capture: suspend () -> ReportPresentationSettings = { captures++; order+="settings"; settings }
    private var duringRender: () -> Unit = {}
    private var read: suspend (Long) -> GenerateVehicleReportUseCase.ReportData? = {
        order+="snapshot"; GenerateVehicleReportUseCase.ReportData(vehicle(),listOf(fill(cost=42.5),fill(2,1000,cost=42.5)),listOf(intervention(cost=42.5)))
    }
    private fun useCase(): StoreVehicleReportUseCase {
        coEvery { documents.saveDocument(any()) } coAnswers { order+="insert"; 1L }
        return StoreVehicleReportUseCase(read,documents,session,object: ReportStorage {
            override fun create(data: GenerateVehicleReportUseCase.ReportData,date: Long): String {
                order+="render"; rendered=data
                val first=StructuredReportRows.fuel(data.fuelRecords.take(1),data.presentation).single()
                duringRender()
                val rest=StructuredReportRows.fuel(data.fuelRecords.drop(1),data.presentation)
                renderedRows += first; renderedRows += rest
                return ReportFileIO.create(directory,ReportFileIO.filename("fixture",date)) { it.write((first.toString()+rest.toString()).toByteArray()) }
            }
            override fun delete(path: String)=File(path).delete()
        }, { owner,id,action -> order+="commit"; session.requireWritable(owner); assertEquals(1L,id); action() }, { 123 }, { capture() })
    }
    @Test fun `one immutable preference capture outside snapshot and before render and commit`()=runTest {
        settings=ReportPresentationSettings("mi","$"); useCase()(1)
        assertEquals(listOf("snapshot","settings","render","commit","insert"),order)
        assertEquals(1,captures); assertEquals(settings,rendered!!.presentation)
    }
    @Test fun `preference change during render cannot mix currency or distance conventions`()=runTest {
        duringRender={settings=ReportPresentationSettings("mi","$")}
        val report=useCase()(1); val bytes=File(report.filePath).readText()
        assertEquals(1,captures); assertEquals(ReportPresentationSettings(),rendered!!.presentation)
        assertTrue(bytes.contains("42,50 €")); assertFalse(bytes.contains("42,50 $"))
        assertEquals(listOf("0 km",DistancePresentation.recorded(1000,"km")),renderedRows.map { it.mileage })
        assertEquals(listOf("42,50 €","42,50 €"),renderedRows.map { it.cost })
    }
    @Test fun `later generation captures new preferences and leaves existing saved bytes intact`()=runTest {
        val useCase=useCase(); val first=useCase(1); val old=File(first.filePath).readBytes()
        settings=ReportPresentationSettings("mi","$"); val second=useCase(1)
        assertEquals(2,captures); assertNotEquals(first.filePath,second.filePath)
        assertArrayEquals(old,File(first.filePath).readBytes()); assertTrue(File(second.filePath).readText().contains("42,50 $"))
    }
    @Test fun `settings read failure does not render or create indexed document`()=runTest {
        capture={throw IOException("preferences")}
        try { useCase()(1); fail<Unit>("failure") } catch (_: IOException) {}
        assertNull(rendered); assertTrue(directory.listFiles()!!.isEmpty())
        coVerify(exactly=0) { documents.saveDocument(any()) }
    }
    @Test fun `owner switch during settings capture rejects before rendering`()=runTest {
        capture={session.setAuthenticatedUid("A"); settings}
        try { useCase()(1); fail<Unit>("owner guard") } catch (_: IllegalStateException) {}
        assertNull(rendered); assertTrue(directory.listFiles()!!.isEmpty())
        coVerify(exactly=0) { documents.saveDocument(any()) }
    }
    @Test fun `four sections selection and owned inputs are preserved with presentation settings`()=runTest {
        val sections=setOf(ReportSection.FUEL_AND_CHARGING_HISTORY,ReportSection.MAINTENANCE_HISTORY)
        useCase()(1,sections); assertEquals(sections,rendered!!.sections)
        assertEquals(42.5,rendered!!.maintenanceRecords.single().cost)
        assertEquals("guest:local",rendered!!.vehicle.ownerKey)
        assertEquals(listOf(0,1000),rendered!!.fuelRecords.map { it.mileage })
    }
}
