package com.carmanager.app.ownership

import androidx.sqlite.db.SupportSQLiteDatabase
import com.carmanager.app.core.data.local.WorkspaceMigration
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Contrat SQL exécuté sur un mock, pas validation runtime de Room Android. */
class WorkspaceMigrationTest {
    @Test fun `7 to 8 adds guest ownership and index without destructive statements`() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)
        val statements = mutableListOf<String>()
        every { database.execSQL(capture(statements)) } just Runs
        val migration = WorkspaceMigration.MIGRATION_7_8
        assertEquals(7, migration.startVersion)
        assertEquals(8, migration.endVersion)
        migration.migrate(database)
        assertEquals(listOf(
            "ALTER TABLE vehicles ADD COLUMN ownerKey TEXT NOT NULL DEFAULT 'guest:local'",
            "CREATE INDEX IF NOT EXISTS index_vehicles_ownerKey ON vehicles(ownerKey)"
        ), statements)
    }
}
