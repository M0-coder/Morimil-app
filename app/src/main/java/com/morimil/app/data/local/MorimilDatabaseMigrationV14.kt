package com.morimil.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object MorimilDatabaseMigrationV14 {
    val MIGRATION_13_14: Migration = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS genesis_ultra_memory_payloads (
                    eventHash TEXT NOT NULL,
                    instanceId TEXT NOT NULL,
                    sequence INTEGER NOT NULL,
                    contentDigest TEXT NOT NULL,
                    contentType TEXT NOT NULL,
                    contentByteCount INTEGER NOT NULL,
                    contentBytes BLOB NOT NULL,
                    provenanceDigest TEXT NOT NULL,
                    provenanceType TEXT NOT NULL,
                    provenanceByteCount INTEGER NOT NULL,
                    provenanceBytes BLOB NOT NULL,
                    privacy TEXT NOT NULL,
                    persistedAtMillis INTEGER NOT NULL,
                    PRIMARY KEY(eventHash)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "index_genesis_ultra_memory_payloads_instanceId_sequence " +
                    "ON genesis_ultra_memory_payloads(instanceId, sequence)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "index_genesis_ultra_memory_payloads_contentDigest " +
                    "ON genesis_ultra_memory_payloads(contentDigest)"
            )
        }
    }
}
