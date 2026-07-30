package com.morimil.app.data.local

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object MemoryOrganDatabaseMigrationV9 {
    val CALLBACK = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            installGuards(db)
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            installGuards(db)
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS cross_database_operations (
                    operationId TEXT NOT NULL PRIMARY KEY,
                    ownerType TEXT NOT NULL,
                    operationType TEXT NOT NULL,
                    operationVersion INTEGER NOT NULL CHECK(operationVersion >= 1),
                    instanceId TEXT NOT NULL,
                    writerBodyId TEXT NOT NULL CHECK(instanceId != writerBodyId),
                    writerEpoch TEXT NOT NULL,
                    subjectId TEXT NOT NULL,
                    parentOperationId TEXT,
                    childPhase TEXT,
                    payloadSchema TEXT NOT NULL,
                    payloadJson TEXT NOT NULL,
                    payloadDigest TEXT NOT NULL
                        CHECK(
                            length(payloadDigest) = 71
                            AND substr(payloadDigest, 1, 7) = 'sha256:'
                            AND substr(payloadDigest, 8) NOT GLOB '*[^0-9a-f]*'
                        ),
                    eventId TEXT NOT NULL,
                    eventType TEXT NOT NULL,
                    eventBody TEXT NOT NULL,
                    evidenceSchema TEXT NOT NULL,
                    evidenceJson TEXT NOT NULL,
                    evidenceDigest TEXT NOT NULL
                        CHECK(
                            length(evidenceDigest) = 71
                            AND substr(evidenceDigest, 1, 7) = 'sha256:'
                            AND substr(evidenceDigest, 8) NOT GLOB '*[^0-9a-f]*'
                        ),
                    status TEXT NOT NULL
                        CHECK(
                            status IN (
                                'STAGED',
                                'PENDING_CANONICAL',
                                'CANONICAL_COMMITTED',
                                'PENDING_LOCAL_COMMIT',
                                'COMMITTED',
                                'BLOCKED'
                            )
                        ),
                    attemptCount INTEGER NOT NULL DEFAULT 0 CHECK(attemptCount >= 0),
                    lastErrorCode TEXT,
                    canonicalEventHash TEXT,
                    canonicalSequence INTEGER,
                    canonicalProvenanceDigest TEXT,
                    localResultSchema TEXT,
                    localResultJson TEXT,
                    localResultDigest TEXT,
                    occurredAtMillis INTEGER NOT NULL CHECK(occurredAtMillis >= 0),
                    createdAtMillis INTEGER NOT NULL CHECK(createdAtMillis >= 0),
                    updatedAtMillis INTEGER NOT NULL CHECK(updatedAtMillis >= createdAtMillis),
                    committedAtMillis INTEGER,
                    CHECK((parentOperationId IS NULL) = (childPhase IS NULL)),
                    CHECK(
                        (canonicalEventHash IS NULL
                            AND canonicalSequence IS NULL
                            AND canonicalProvenanceDigest IS NULL)
                        OR
                        (canonicalEventHash IS NOT NULL
                            AND canonicalSequence IS NOT NULL
                            AND canonicalSequence >= 1
                            AND canonicalProvenanceDigest IS NOT NULL
                            AND length(canonicalEventHash) = 73
                            AND substr(canonicalEventHash, 1, 9) = 'evsha256:'
                            AND substr(canonicalEventHash, 10) NOT GLOB '*[^0-9a-f]*'
                            AND length(canonicalProvenanceDigest) = 71
                            AND substr(canonicalProvenanceDigest, 1, 7) = 'sha256:'
                            AND substr(canonicalProvenanceDigest, 8) NOT GLOB '*[^0-9a-f]*')
                    ),
                    CHECK(
                        (localResultSchema IS NULL
                            AND localResultJson IS NULL
                            AND localResultDigest IS NULL)
                        OR
                        (localResultSchema IS NOT NULL
                            AND localResultJson IS NOT NULL
                            AND localResultDigest IS NOT NULL
                            AND length(localResultDigest) = 71
                            AND substr(localResultDigest, 1, 7) = 'sha256:'
                            AND substr(localResultDigest, 8) NOT GLOB '*[^0-9a-f]*')
                    ),
                    CHECK(
                        (status = 'COMMITTED'
                            AND canonicalEventHash IS NOT NULL
                            AND localResultDigest IS NOT NULL
                            AND committedAtMillis IS NOT NULL
                            AND committedAtMillis >= createdAtMillis)
                        OR
                        (status != 'COMMITTED' AND committedAtMillis IS NULL)
                    )
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS index_cross_database_operations_eventId
                ON cross_database_operations(eventId)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_cross_database_operations_instance_status_created
                ON cross_database_operations(instanceId, status, createdAtMillis, operationId)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_cross_database_operations_owner_subject_status
                ON cross_database_operations(ownerType, subjectId, operationType, status)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_cross_database_operations_status_updated
                ON cross_database_operations(status, updatedAtMillis, operationId)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_cross_database_operations_writer_epoch_status
                ON cross_database_operations(instanceId, writerEpoch, status)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_cross_database_operations_parent_child
                ON cross_database_operations(parentOperationId, childPhase)
                """.trimIndent()
            )
            installGuards(db)
        }
    }

    internal fun installGuards(db: SupportSQLiteDatabase) {
        db.execSQL(validationTrigger("cross_database_operations_validate_insert", "INSERT"))
        db.execSQL(validationTrigger("cross_database_operations_validate_update", "UPDATE"))
    }

    private fun validationTrigger(name: String, operation: String): String {
        return """
            CREATE TRIGGER IF NOT EXISTS $name
            BEFORE $operation ON cross_database_operations
            WHEN NOT ($VALID_ROW_EXPRESSION)
            BEGIN
                SELECT RAISE(ABORT, 'xop_sql_guard_rejected');
            END
        """.trimIndent()
    }

    private const val VALID_ROW_EXPRESSION = """
        length(NEW.operationId) = 68
        AND substr(NEW.operationId, 1, 4) = 'xop_'
        AND substr(NEW.operationId, 5) NOT GLOB '*[^0-9a-f]*'
        AND length(NEW.eventId) = 69
        AND substr(NEW.eventId, 1, 5) = 'xevt_'
        AND substr(NEW.eventId, 6) NOT GLOB '*[^0-9a-f]*'
        AND length(NEW.ownerType) > 0
        AND length(NEW.operationType) > 0
        AND NEW.operationVersion >= 1
        AND length(NEW.instanceId) > 0
        AND length(NEW.writerBodyId) > 0
        AND NEW.instanceId != NEW.writerBodyId
        AND length(NEW.writerEpoch) > 0
        AND length(NEW.subjectId) > 0
        AND ((NEW.parentOperationId IS NULL AND NEW.childPhase IS NULL)
            OR (NEW.parentOperationId IS NOT NULL AND NEW.childPhase IS NOT NULL))
        AND length(NEW.payloadSchema) > 0
        AND length(NEW.payloadJson) > 0
        AND length(NEW.payloadDigest) = 71
        AND substr(NEW.payloadDigest, 1, 7) = 'sha256:'
        AND substr(NEW.payloadDigest, 8) NOT GLOB '*[^0-9a-f]*'
        AND length(NEW.eventType) > 0
        AND length(NEW.eventBody) > 0
        AND length(NEW.evidenceSchema) > 0
        AND length(NEW.evidenceJson) > 0
        AND length(NEW.evidenceDigest) = 71
        AND substr(NEW.evidenceDigest, 1, 7) = 'sha256:'
        AND substr(NEW.evidenceDigest, 8) NOT GLOB '*[^0-9a-f]*'
        AND NEW.status IN (
            'STAGED',
            'PENDING_CANONICAL',
            'CANONICAL_COMMITTED',
            'PENDING_LOCAL_COMMIT',
            'COMMITTED',
            'BLOCKED'
        )
        AND NEW.attemptCount >= 0
        AND (NEW.lastErrorCode IS NULL
            OR (length(NEW.lastErrorCode) BETWEEN 7 AND 100
                AND substr(NEW.lastErrorCode, 1, 4) = 'XOP_'
                AND NEW.lastErrorCode NOT GLOB '*[^A-Z0-9_]*'))
        AND (
            (NEW.canonicalEventHash IS NULL
                AND NEW.canonicalSequence IS NULL
                AND NEW.canonicalProvenanceDigest IS NULL)
            OR
            (NEW.canonicalEventHash IS NOT NULL
                AND NEW.canonicalSequence >= 1
                AND NEW.canonicalProvenanceDigest IS NOT NULL
                AND length(NEW.canonicalEventHash) = 73
                AND substr(NEW.canonicalEventHash, 1, 9) = 'evsha256:'
                AND substr(NEW.canonicalEventHash, 10) NOT GLOB '*[^0-9a-f]*'
                AND length(NEW.canonicalProvenanceDigest) = 71
                AND substr(NEW.canonicalProvenanceDigest, 1, 7) = 'sha256:'
                AND substr(NEW.canonicalProvenanceDigest, 8) NOT GLOB '*[^0-9a-f]*')
        )
        AND (
            (NEW.localResultSchema IS NULL
                AND NEW.localResultJson IS NULL
                AND NEW.localResultDigest IS NULL)
            OR
            (length(NEW.localResultSchema) > 0
                AND NEW.localResultJson IS NOT NULL
                AND length(NEW.localResultDigest) = 71
                AND substr(NEW.localResultDigest, 1, 7) = 'sha256:'
                AND substr(NEW.localResultDigest, 8) NOT GLOB '*[^0-9a-f]*')
        )
        AND NEW.occurredAtMillis >= 0
        AND NEW.createdAtMillis >= 0
        AND NEW.updatedAtMillis >= NEW.createdAtMillis
        AND (
            (NEW.status = 'COMMITTED'
                AND NEW.canonicalEventHash IS NOT NULL
                AND NEW.localResultDigest IS NOT NULL
                AND NEW.committedAtMillis IS NOT NULL
                AND NEW.committedAtMillis >= NEW.createdAtMillis)
            OR
            (NEW.status != 'COMMITTED' AND NEW.committedAtMillis IS NULL)
        )
    """
}
