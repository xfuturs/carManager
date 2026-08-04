package com.carmanager.app.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.carmanager.app.core.data.local.converter.Converters
import com.carmanager.app.core.data.local.dao.*
import com.carmanager.app.core.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import android.util.Log

@Database(
    entities = [
        VehicleEntity::class,
        FuelRecordEntity::class,
        MaintenanceRecordEntity::class,
        MileageRecordEntity::class,
        DocumentEntity::class,
        VehicleReferenceEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CarManagerDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun fuelRecordDao(): FuelRecordDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun mileageDao(): MileageDao
    abstract fun documentDao(): DocumentDao
    abstract fun vehicleReferenceDao(): VehicleReferenceDao

    companion object {
        const val DATABASE_NAME = "car_manager.db"

        fun getCallback(context: Context): Callback {
            return object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    Log.d("CarManagerDB", "Database created, starting prepopulation...")
                    // On ne peut pas utiliser les DAOs ici car l'instance Room n'est pas prête.
                    // On passera par une insertion manuelle au premier accès ou via un flag.
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    Log.d("CarManagerDB", "Database opened.")
                }
            }
        }
    }
}
