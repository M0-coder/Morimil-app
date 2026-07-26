package com.morimil.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object MemoryOrganDatabaseMigrationV8 {
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS project_vault_outbox (
                    operationId TEXT NOT NULL PRIMARY KEY,
                    vaultId TEXT NOT NULL,
                    operationType TEXT NOT NULL,
                    eventId TEXT NOT NULL,
                    eventType TEXT NOT NULL,
                    eventBody TEXT NOT NULL,
                    evidenceJson TEXT NOT NULL,
                    payloadJson TEXT NOT NULL,
                    payloadDigest TEXT NOT NULL,
                    status TEXT NOT NULL,
                    attemptCount INTEGER NOT NULL,
                    lastError TEXT,
                    canonicalEventHash TEXT,
                    canonicalSequence INTEGER,
                    occurredAtMillis INTEGER NOT NULL,
                    createdAtMillis INTEGER NOT NULL,
                    updatedAtMillis INTEGER NOT NULL,
                    committedAtMillis INTEGER
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_project_vault_outbox_vaultId " +
                    "ON project_vault_outbox(vaultId)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_project_vault_outbox_status " +
                    "ON project_vault_outbox(status)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_project_vault_outbox_vaultId_status " +
                    "ON project_vault_outbox(vaultId, status)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_project_vault_outbox_updatedAtMillis " +
                    "ON project_vault_outbox(updatedAtMillis)"
            )
        }
    }
}
