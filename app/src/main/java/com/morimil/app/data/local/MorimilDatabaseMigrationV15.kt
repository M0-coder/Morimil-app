package com.morimil.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object MorimilDatabaseMigrationV15 {
    const val INSERT_TRIGGER = "memory_events_genesis_ultra_read_only_insert"
    const val UPDATE_TRIGGER = "memory_events_genesis_ultra_read_only_update"
    const val DELETE_TRIGGER = "memory_events_genesis_ultra_read_only_delete"
    const val READ_ONLY_ERROR = "legacy_memory_events_read_only"

    val MIGRATION_14_15: Migration = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS genesis_ultra_legacy_memory_convergence (
                    slotId TEXT NOT NULL,
                    status TEXT NOT NULL,
                    sourceEventCount INTEGER NOT NULL,
                    acceptedEventCount INTEGER NOT NULL,
                    importedEventCount INTEGER NOT NULL,
                    sourceTipHash TEXT,
                    dryRunDigest TEXT NOT NULL,
                    activeWriter TEXT NOT NULL,
                    legacyReadOnly INTEGER NOT NULL,
                    failureCode TEXT,
                    updatedAtMillis INTEGER NOT NULL,
                    PRIMARY KEY(slotId)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS genesis_ultra_legacy_memory_imports (
                    legacyEventHash TEXT NOT NULL,
                    legacyEventId INTEGER NOT NULL,
                    instanceId TEXT NOT NULL,
                    canonicalEventHash TEXT NOT NULL,
                    canonicalSequence INTEGER NOT NULL,
                    provenanceDigest TEXT NOT NULL,
                    importedAtMillis INTEGER NOT NULL,
                    PRIMARY KEY(legacyEventHash)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "index_genesis_ultra_legacy_memory_imports_canonicalEventHash " +
                    "ON genesis_ultra_legacy_memory_imports(canonicalEventHash)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "index_genesis_ultra_legacy_memory_imports_instanceId_canonicalSequence " +
                    "ON genesis_ultra_legacy_memory_imports(instanceId, canonicalSequence)"
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS $INSERT_TRIGGER
                BEFORE INSERT ON memory_events
                BEGIN
                    SELECT RAISE(ABORT, '$READ_ONLY_ERROR');
                END
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS $UPDATE_TRIGGER
                BEFORE UPDATE ON memory_events
                BEGIN
                    SELECT RAISE(ABORT, '$READ_ONLY_ERROR');
                END
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS $DELETE_TRIGGER
                BEFORE DELETE ON memory_events
                BEGIN
                    SELECT RAISE(ABORT, '$READ_ONLY_ERROR');
                END
                """.trimIndent()
            )
        }
    }
}
