package com.carmanager.app.a15_1

import com.carmanager.app.consistency.GarageFixture
import com.carmanager.app.core.data.local.*
import com.carmanager.app.core.data.local.entity.*
import com.carmanager.app.core.domain.model.*
import com.carmanager.app.core.domain.repository.DocumentRepository
import com.carmanager.app.core.util.ReportFileIO
import com.carmanager.app.features.dashboard.*
import io.mockk.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Frontières/ordre via ports ; ce n'est pas une preuve d'isolation SQLite installée. */
class ReportSnapshotStagingTest {
    @TempDir lateinit var root: File
    private val garage=GarageFixture()
    private val database=mockk<CarManagerDatabase>()
    private val documents=mockk<DocumentRepository>()
    private var inRead=false
    private var inWrite=false
    private var reads=0
    private var failCommit=false
    private var afterRender: () -> Unit={}
    private val indexed=mutableListOf<Document>()
    private var rendered: GenerateVehicleReportUseCase.ReportData?=null
    private var liveMileage=1000
    private fun reader(): OwnedReportSnapshotReader {
        every { database.vehicleDao() } returns garage.vehicleDao
        every { database.fuelRecordDao() } returns garage.fuelDao
        every { database.maintenanceDao() } returns garage.maintenanceDao
        every { database.mileageDao() } returns garage.mileageDao
        // Le fake fige sa vue à l'entrée. Une mutation du backing store entre lectures reste invisible.
        var frozen=1000
        coEvery { garage.access.read<Any>(any(),any(),any()) } coAnswers {
            garage.session.requireWritable(firstArg()); inRead=true; reads++; frozen=liveMileage
            try { thirdArg<suspend () -> Any>()().also { garage.session.requireWritable(firstArg()) } } finally { inRead=false }
        }
        coEvery { garage.vehicleDao.getById(1,"firebase:A") } coAnswers {
            assertTrue(inRead); garage.vehicles[1]!!.copy(currentMileage=frozen).also { liveMileage=2000 }
        }
        coEvery { garage.fuelDao.getByVehicle(1,"firebase:A") } coAnswers {
            assertTrue(inRead); listOf(FuelRecordEntity(1,1,0,frozen,10.0,20.0))
        }
        coEvery { garage.maintenanceDao.getByVehicle(1,"firebase:A") } coAnswers {
            assertTrue(inRead); listOf(MaintenanceRecordEntity(1,1,MaintenanceTypeEntity.OIL_CHANGE,date=0,mileage=frozen,cost=20.0))
        }
        coEvery { garage.mileageDao.getByVehicle(1,"firebase:A") } coAnswers {
            assertTrue(inRead); listOf(MileageRecordEntity(1,1,0,frozen,"MANUAL"))
        }
        return OwnedReportSnapshotReader(garage.session,database,garage.access)
    }
    private fun service(read: OwnedReportSnapshotReader=reader()): StoreVehicleReportUseCase {
        coEvery { documents.saveDocument(any()) } coAnswers {
            assertTrue(inWrite); assertFalse(inRead)
            if (failCommit) throw IOException("commit")
            firstArg<Document>().copy(id=9).also { indexed += it }.id
        }
        val storage=object: ReportStorage {
            override fun create(data: GenerateVehicleReportUseCase.ReportData,date: Long): String {
                assertFalse(inRead); assertFalse(inWrite); rendered=data
                return ReportFileIO.create(root,"report.pdf") { it.write("%PDF-test".toByteArray()) }.also { afterRender() }
            }
            override fun delete(path: String)=File(path).delete()
        }
        return StoreVehicleReportUseCase({ read.read(it) },documents,garage.session,storage,
            { owner, id, action ->
                garage.session.requireWritable(owner); check(garage.vehicles[id]?.ownerKey==owner)
                inWrite=true
                try { action().also { garage.session.requireWritable(owner) } } finally { inWrite=false }
            }, { 123 })
    }
    private suspend fun rejected(action: suspend () -> Unit) {
        try { action(); fail<Unit>("rejected") } catch (_: IllegalStateException) {}
    }
    @Test fun `all four DAO sources are queried inside one owned read boundary with frozen view`()=runTest {
        val data=reader().read(1)!!
        assertEquals(1,reads); assertFalse(inRead); assertEquals(2000,liveMileage)
        assertEquals(listOf(1000,1000,1000,1000),listOf(data.vehicle.currentMileage,data.fuelRecords.single().mileage,data.maintenanceRecords.single().mileage,data.mileageRecords.single().mileage))
        coVerify(exactly=1) { garage.access.read<Any>("firebase:A",1,any()) }
    }
    @Test fun `foreign source owner or wrong parent is rejected`()=runTest {
        for (data in listOf(
            GenerateVehicleReportUseCase.ReportData(garage.vehicle().copy(ownerKey="firebase:B"),emptyList(),emptyList()),
            GenerateVehicleReportUseCase.ReportData(garage.vehicle(),listOf(garage.fuel().copy(vehicleId=2)),emptyList()))) {
            rejected { OwnedReportSnapshotReader(garage.session) { _, _ -> data }.read(1) }
        }
    }
    @Test fun `blocked owner is rejected before snapshot query`()=runTest {
        val reader=reader(); garage.registry.block("firebase:A"); rejected { reader.read(1) }; assertEquals(0,reads)
    }
    @Test fun `owner changing during snapshot prevents data escape`()=runTest {
        val reader=OwnedReportSnapshotReader(garage.session) { _, _ -> garage.session.setAuthenticatedUid("B"); GenerateVehicleReportUseCase.ReportData(garage.vehicle(),emptyList(),emptyList()) }
        rejected { reader.read(1) }
    }
    @Test fun `render starts after snapshot closes and metadata commit stays owned and short`()=runTest {
        val doc=service()(1,setOf(ReportSection.MILEAGE_HISTORY))
        assertEquals(1,reads); assertEquals(setOf(ReportSection.MILEAGE_HISTORY),rendered!!.sections)
        assertEquals(DocumentCategory.REPORTS,doc.category); assertEquals(doc,indexed.single()); assertTrue(File(doc.filePath).exists())
    }
    @Test fun `DB commit failure removes newly rendered report`()=runTest {
        failCommit=true
        try { service()(1); fail<Unit>("failure") } catch (_: IOException) {}
        assertTrue(root.listFiles()!!.isEmpty()); assertTrue(indexed.isEmpty())
    }
    @Test fun `owner changed after render is revalidated and staged report removed`()=runTest {
        afterRender={ garage.session.setAuthenticatedUid("B") }; rejected { service()(1) }
        assertTrue(root.listFiles()!!.isEmpty()); assertTrue(indexed.isEmpty()); coVerify(exactly=0) { documents.saveDocument(any()) }
    }
    @Test fun `deleted vehicle after render fails commit and cleans report`()=runTest {
        afterRender={ garage.vehicles.remove(1) }; rejected { service()(1) }
        assertTrue(root.listFiles()!!.isEmpty()); assertTrue(indexed.isEmpty())
    }
}
