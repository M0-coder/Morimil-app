package com.morimil.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object MemoryOrganDatabaseMigrationV9 {
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
        }
    }
}
