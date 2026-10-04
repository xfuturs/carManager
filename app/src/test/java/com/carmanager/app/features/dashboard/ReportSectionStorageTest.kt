package com.carmanager.app.features.dashboard

import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.repository.*
import com.carmanager.app.core.domain.session.WorkspaceSession
import com.carmanager.app.core.util.ReportFileIO
import com.carmanager.app.features.documents.ReportSummary
import com.carmanager.app.ownership.TestDeletionRegistry
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ReportSectionStorageTest {
    @TempDir lateinit var directory: File
    private val session = WorkspaceSession(TestDeletionRegistry()).apply { setAuthenticatedUid("A") }
    private val owner = "firebase:A"
    private val repository = mockk<DocumentRepository>()
    private val rows = mutableListOf<Document>()
    private val captured = mutableListOf<GenerateVehicleReportUseCase.ReportData>()
    private val vehicle = Vehicle(7,"Renault","Clio",2020,1000,FuelType.GASOLINE,powerHp=90,licensePlate=null,createdAt=0,updatedAt=0,ownerKey=owner)
    private var data = GenerateVehicleReportUseCase.ReportData(vehicle,emptyList(),emptyList())
    private var reads = 0
    private var afterRead: () -> Unit = {}
    private fun service(): StoreVehicleReportUseCase {
        every { repository.observeByVehicle(7) } answers { flowOf(rows.toList()) }
        coEvery { repository.saveDocument(any()) } coAnswers { val doc = firstArg<Document>().copy(id = rows.size + 1L); rows += doc; doc.id }
        val storage = object : ReportStorage {
            override fun create(data: GenerateVehicleReportUseCase.ReportData, date: Long): String {
                captured += data
                return ReportFileIO.create(directory,ReportFileIO.filename(data.vehicle.model,date)) { it.write("%PDF-test".toByteArray()) }
            }
            override fun delete(path: String) = File(path).delete()
        }
        return StoreVehicleReportUseCase({ reads++; afterRead(); data },repository,session,storage,
            { owner, _, action -> session.requireWritable(owner); action() },{ 1_790_000_000_000 })
    }
    @Test fun `zero sections reject before repository read storage or transaction`() = runTest {
        try { service()(7,emptySet()); fail<Unit>("zero selection") } catch (_: IllegalArgumentException) {}
        assertEquals(0,reads); assertTrue(captured.isEmpty()); assertTrue(rows.isEmpty()); assertTrue(directory.listFiles()!!.isEmpty())
    }
    @Test fun `selection is copied before suspended reads and passed unchanged to renderer storage`() = runTest {
        val selected = mutableSetOf(ReportSection.MILEAGE_HISTORY)
        afterRead = { selected.clear() }; service()(7,selected)
        assertEquals(setOf(ReportSection.MILEAGE_HISTORY),captured.single().sections)
    }
    @Test fun `partial report then default report retains distinct files and all sections reset`() = runTest {
        val service = service(); val first = service(7,setOf(ReportSection.FUEL_AND_CHARGING_HISTORY)); val bytes = File(first.filePath).readBytes()
        val second = service(7)
        assertEquals(2, rows.size); assertNotEquals(first.id,second.id); assertNotEquals(first.filePath,second.filePath)
        assertEquals(ReportSection.entries.toSet(),captured.last().sections); assertArrayEquals(bytes,File(first.filePath).readBytes())
        assertEquals(ReportSummary(2,first.date),service.previousReports(7))
    }
    @Test fun `summary excludes ordinary documents and does not generate a file`() = runTest {
        val service = service()
        rows += Document(1,7,"Photo",DocumentCategory.PHOTOS,"/photo",999,owner)
        assertEquals(ReportSummary(0,null),service.previousReports(7)); assertTrue(captured.isEmpty()); assertTrue(directory.listFiles()!!.isEmpty())
    }
    @Test fun `foreign summary is rejected without generation`() = runTest {
        val service = service(); rows += Document(1,7,"Foreign",DocumentCategory.REPORTS,"/foreign",999,"firebase:B")
        try { service.previousReports(7); fail<Unit>("foreign summary") } catch (_: IllegalStateException) {}
        assertTrue(captured.isEmpty())
    }
    @Test fun `foreign mileage and fuel or maintenance parent mismatch prevent storage`() = runTest {
        val mileage = MileageRecord(1,7,0,1000,MileageSource.MANUAL,"firebase:B")
        for (bad in listOf(data.copy(mileageRecords = listOf(mileage)),
            data.copy(fuelRecords = listOf(FuelRecord(1,99,0,1000,1.0,2.0,ownerKey=owner))),
            data.copy(maintenanceRecords = listOf(MaintenanceRecord(1,99,MaintenanceType.OIL_CHANGE,date=0,mileage=1000,cost=2.0,ownerKey=owner))))) {
            data = bad
            try { service()(7); fail<Unit>("foreign source") } catch (_: IllegalStateException) {}
        }
        assertTrue(captured.isEmpty()); assertTrue(rows.isEmpty()); assertTrue(directory.listFiles()!!.isEmpty())
    }
    @Test fun `data reader uses existing owned mileage query and preserves stored source rows`() = runTest {
        val vehicles = mockk<VehicleRepository>(); val fuel = mockk<FuelRepository>(); val maintenance = mockk<MaintenanceRepository>(); val mileage = mockk<MileageRepository>()
        val records = listOf(MileageRecord(1,7,0,900,MileageSource.FUEL,owner),MileageRecord(2,7,1,1000,MileageSource.MANUAL,owner))
        every { vehicles.observeById(7) } returns flowOf(vehicle); every { fuel.observeByVehicle(7) } returns flowOf(emptyList())
        every { maintenance.observeByVehicle(7) } returns flowOf(emptyList()); every { mileage.observeByVehicle(7) } returns flowOf(records)
        val result = GenerateVehicleReportUseCase(vehicles,fuel,maintenance,session,mileage)(7)!!
        assertEquals(records,result.mileageRecords); assertEquals(ReportSection.entries.toSet(),result.sections)
        verify(exactly=1) { mileage.observeByVehicle(7) }
    }
    @Test fun `foreign mileage query snapshot is rejected`() = runTest {
        val vehicles = mockk<VehicleRepository>(); val fuel = mockk<FuelRepository>(); val maintenance = mockk<MaintenanceRepository>(); val mileage = mockk<MileageRepository>()
        every { vehicles.observeById(7) } returns flowOf(vehicle); every { fuel.observeByVehicle(7) } returns flowOf(emptyList())
        every { maintenance.observeByVehicle(7) } returns flowOf(emptyList())
        every { mileage.observeByVehicle(7) } returns flowOf(listOf(MileageRecord(1,7,0,1,MileageSource.MANUAL,"firebase:B")))
        try { GenerateVehicleReportUseCase(vehicles,fuel,maintenance,session,mileage)(7); fail<Unit>("foreign mileage") } catch (_: IllegalStateException) {}
    }
}
