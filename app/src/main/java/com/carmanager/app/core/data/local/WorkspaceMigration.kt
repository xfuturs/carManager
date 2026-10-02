package com.carmanager.app.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object WorkspaceMigration {
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE vehicles ADD COLUMN ownerKey TEXT NOT NULL DEFAULT 'guest:local'")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_vehicles_ownerKey ON vehicles(ownerKey)")
        }
    }
}
