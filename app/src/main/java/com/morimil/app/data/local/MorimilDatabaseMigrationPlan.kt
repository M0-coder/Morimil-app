package com.morimil.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Effective migration plan used by production and tests.
 *
 * The exported Room 7 schema predates the defaults and indexes later declared
 * on MemoryEventEntity. SQLite cannot add those defaults to existing columns
 * with ALTER TABLE, so 7 -> 8 must rebuild the table transactionally instead
 * of only appending the five v8 columns.
 */
internal object MorimilDatabaseMigrationPlan {
    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE memory_events_v8 (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    genesisCoreId TEXT NOT NULL,
                    genesisCoreHash TEXT NOT NULL DEFAULT 'sha256:legacy-unverified',
                    previousEventHash TEXT,
                    eventHash TEXT NOT NULL DEFAULT 'sha256:legacy-unverified',
                    hashAlgorithm TEXT NOT NULL DEFAULT 'sha256',
                    canonicalization TEXT NOT NULL DEFAULT 'morimil.memory_event_hash.v1',
                    signatureAlgorithm TEXT,
                    eventSignature TEXT,
                    eventType TEXT NOT NULL,
                    actor TEXT NOT NULL,
                    source TEXT NOT NULL DEFAULT 'system',
                    contextTag TEXT NOT NULL DEFAULT 'local_runtime',
                    privacyVisibility TEXT NOT NULL DEFAULT 'private_local',
                    memoryKind TEXT NOT NULL DEFAULT 'observation',
                    tagsJson TEXT NOT NULL DEFAULT '[]',
                    evidenceJson TEXT NOT NULL DEFAULT '{}',
                    confidence INTEGER NOT NULL DEFAULT 70,
                    userConfirmed INTEGER NOT NULL DEFAULT 0,
                    body TEXT NOT NULL,
                    importance INTEGER NOT NULL,
                    createdAtMillis INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO memory_events_v8 (
                    id,
                    genesisCoreId,
                    genesisCoreHash,
                    previousEventHash,
                    eventHash,
                    hashAlgorithm,
                    canonicalization,
                    signatureAlgorithm,
                    eventSignature,
                    eventType,
                    actor,
                    source,
                    contextTag,
                    privacyVisibility,
                    memoryKind,
                    tagsJson,
                    evidenceJson,
                    confidence,
                    userConfirmed,
                    body,
                    importance,
                    createdAtMillis
                )
                SELECT
                    id,
                    genesisCoreId,
                    genesisCoreHash,
                    previousEventHash,
                    eventHash,
                    hashAlgorithm,
                    canonicalization,
                    signatureAlgorithm,
                    eventSignature,
                    eventType,
                    actor,
                    source,
                    contextTag,
                    privacyVisibility,
                    'observation',
                    '[]',
                    '{}',
                    70,
                    0,
                    body,
                    importance,
                    createdAtMillis
                FROM memory_events
                ORDER BY id ASC
                """.trimIndent()
            )
            db.execSQL("DROP TABLE memory_events")
            db.execSQL("ALTER TABLE memory_events_v8 RENAME TO memory_events")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_memory_events_eventHash " +
                    "ON memory_events(eventHash)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_memory_events_createdAtMillis " +
                    "ON memory_events(createdAtMillis)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_memory_events_memoryKind " +
                    "ON memory_events(memoryKind)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_memory_events_importance " +
                    "ON memory_events(importance)"
            )
        }
    }

    val MIGRATION_13_14: Migration = MorimilDatabaseMigrationV14.MIGRATION_13_14
    val MIGRATION_14_15: Migration = MorimilDatabaseMigrationV15.MIGRATION_14_15

    val ALL: Array<Migration> = arrayOf(
        MorimilDatabaseMigrations.MIGRATION_1_2,
        MorimilDatabaseMigrations.MIGRATION_2_3,
        MorimilDatabaseMigrations.MIGRATION_3_4,
        MorimilDatabaseMigrations.MIGRATION_4_5,
        MorimilDatabaseMigrations.MIGRATION_5_6,
        MorimilDatabaseMigrations.MIGRATION_6_7,
        MIGRATION_7_8,
        MorimilDatabaseMigrations.MIGRATION_8_9,
        MorimilDatabaseMigrations.MIGRATION_9_10,
        MorimilDatabaseMigrations.MIGRATION_10_11,
        MorimilDatabaseMigrations.MIGRATION_11_12,
        MorimilDatabaseMigrations.MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15
    )

    /**
     * Retained for callers that still initialize the legacy array before open.
     * Production itself consumes [ALL].
     */
    fun installIntoLegacyRegistry() {
        val legacy = MorimilDatabaseMigrations.ALL
        val index = legacy.indexOfFirst { migration ->
            migration.startVersion == 7 && migration.endVersion == 8
        }
        check(index >= 0) { "morimil_database_migration_7_8_slot_missing" }
        legacy[index] = MIGRATION_7_8
    }
}
