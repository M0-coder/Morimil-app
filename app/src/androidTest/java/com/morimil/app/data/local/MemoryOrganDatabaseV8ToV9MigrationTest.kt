package com.morimil.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryOrganDatabaseV8ToV9MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MemoryOrganDatabase::class.java
    )

    @Test
    fun migrationPreservesOwnerRowsAndEnforcesJournalConstraints() {
        val source = helper.createDatabase(TEST_DATABASE, 8)
        try {
            insertMigrationRecord(source)
            insertProjectVaultOutbox(source)
        } finally {
            source.close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            9,
            true,
            MemoryOrganDatabaseMigrationV9.MIGRATION_8_9
        )
        try {
            assertEquals(9, database.singleInt("PRAGMA user_version"))
            assertEquals(1, database.singleInt("SELECT COUNT(*) FROM migration_records"))
            assertEquals(1, database.singleInt("SELECT COUNT(*) FROM project_vault_outbox"))
            assertEquals(
                0,
                database.singleInt("SELECT COUNT(*) FROM cross_database_operations")
            )
            assertTrue(
                database.columnNames("cross_database_operations").containsAll(
                    REQUIRED_COLUMNS
                )
            )
            assertTrue(
                database.indexNames("cross_database_operations").containsAll(
                    REQUIRED_INDICES
                )
            )

            database.execSQL(INSERT_OPERATION, validOperationArgs())
            assertEquals(
                1,
                database.singleInt("SELECT COUNT(*) FROM cross_database_operations")
            )
            assertSqlRejected(database, validOperationArgs(payloadDigest = "sha256:bad"))
            assertSqlRejected(database, validOperationArgs(status = "UNKNOWN"))
            assertSqlRejected(
                database,
                validOperationArgs(
                    canonicalEventHash = "evsha256:" + "4".repeat(64)
                )
            )
            assertSqlRejected(
                database,
                validOperationArgs(
                    parentOperationId = "xop_" + "5".repeat(64),
                    childPhase = null
                )
            )
        } finally {
            database.close()
        }
    }

    private fun assertSqlRejected(
        database: SupportSQLiteDatabase,
        bindArgs: Array<Any?>
    ) {
        val rejected = runCatching {
            database.execSQL(
                INSERT_OPERATION,
                bindArgs.also { args ->
                    args[0] = "xop_" +
                        args.joinToString("|").hashCode().toUInt().toString(16)
                            .padStart(64, '0')
                }
            )
        }.isFailure
        assertTrue("Malformed journal row was accepted", rejected)
    }

    private fun validOperationArgs(
        payloadDigest: String = "sha256:" + "1".repeat(64),
        status: String = "STAGED",
        parentOperationId: String? = null,
        childPhase: String? = null,
        canonicalEventHash: String? = null,
        canonicalSequence: Long? = null,
        canonicalProvenanceDigest: String? = null
    ): Array<Any?> {
        return arrayOf(
            "xop_" + "a".repeat(64),
            "cognitive_migration",
            "cognitive_migration.propose",
            1,
            "instance-test",
            "body-test",
            "epoch-test",
            "cog_migration_" + "b".repeat(64),
            parentOperationId,
            childPhase,
            "test.payload.v1",
            "{}",
            payloadDigest,
            "xevt_" + "c".repeat(64),
            "cognitive_migration.proposed",
            "deterministic body",
            "test.evidence.v1",
            "{}",
            "sha256:" + "2".repeat(64),
            status,
            0,
            null,
            canonicalEventHash,
            canonicalSequence,
            canonicalProvenanceDigest,
            null,
            null,
            null,
            1000L,
            1000L,
            1000L,
            null
        )
    }

    private fun insertMigrationRecord(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            INSERT INTO migration_records (
                migrationId, instanceId, genesisCoreHash, proposalId, migrationType,
                fromVersion, toVersion, affectedArtifactsJson, preSnapshotId,
                chainVerified, backupRequired, stepsJson, expectedEffect, riskLevel,
                approvalRequired, approvedByUser, approvalId, status, postSnapshotId,
                errorsJson, rollbackAvailable, rollbackStrategy, createdBy,
                createdAtMillis, updatedAtMillis
            ) VALUES (
                'migration-existing', 'instance-test', 'sha256:genesis', 'proposal',
                'test', 'v1', 'v2', '[]', 'snapshot', 1, 1, '[]', 'preserve',
                'low', 1, 0, NULL, 'planned', NULL, '[]', 1, 'append_only',
                'test', 1000, 1000
            )
            """.trimIndent()
        )
    }

    private fun insertProjectVaultOutbox(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            INSERT INTO project_vault_outbox (
                operationId, vaultId, operationType, eventId, eventType, eventBody,
                evidenceJson, payloadJson, payloadDigest, status, attemptCount,
                lastError, canonicalEventHash, canonicalSequence, occurredAtMillis,
                createdAtMillis, updatedAtMillis, committedAtMillis
            ) VALUES (
                'op-existing', 'vault-existing', 'project_vault.create',
                'event-existing', 'project_vault.created', 'body', '{}', '{}',
                'sha256:payload', 'pending', 0, NULL, NULL, NULL, 1000, 1000, 1000,
                NULL
            )
            """.trimIndent()
        )
    }

    private fun SupportSQLiteDatabase.singleInt(sql: String): Int {
        return query(sql).use { cursor ->
            check(cursor.moveToFirst()) { "No row returned for: $sql" }
            cursor.getInt(0)
        }
    }

    private fun SupportSQLiteDatabase.columnNames(table: String): Set<String> {
        return query("PRAGMA table_info(`$table`)").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
        }
    }

    private fun SupportSQLiteDatabase.indexNames(table: String): Set<String> {
        return query("PRAGMA index_list(`$table`)").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "memory-organ-v8-to-v9.db"
        const val INSERT_OPERATION =
            """
            INSERT INTO cross_database_operations (
                operationId, ownerType, operationType, operationVersion, instanceId,
                writerBodyId, writerEpoch, subjectId, parentOperationId, childPhase,
                payloadSchema, payloadJson, payloadDigest, eventId, eventType, eventBody,
                evidenceSchema, evidenceJson, evidenceDigest, status, attemptCount,
                lastErrorCode, canonicalEventHash, canonicalSequence,
                canonicalProvenanceDigest, localResultSchema, localResultJson,
                localResultDigest, occurredAtMillis, createdAtMillis, updatedAtMillis,
                committedAtMillis
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                      ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """

        val REQUIRED_COLUMNS = setOf(
            "operationId",
            "ownerType",
            "operationType",
            "operationVersion",
            "instanceId",
            "writerBodyId",
            "writerEpoch",
            "subjectId",
            "parentOperationId",
            "childPhase",
            "payloadSchema",
            "payloadJson",
            "payloadDigest",
            "eventId",
            "eventType",
            "eventBody",
            "evidenceSchema",
            "evidenceJson",
            "evidenceDigest",
            "status",
            "attemptCount",
            "lastErrorCode",
            "canonicalEventHash",
            "canonicalSequence",
            "canonicalProvenanceDigest",
            "localResultSchema",
            "localResultJson",
            "localResultDigest",
            "occurredAtMillis",
            "createdAtMillis",
            "updatedAtMillis",
            "committedAtMillis"
        )
        val REQUIRED_INDICES = setOf(
            "index_cross_database_operations_eventId",
            "index_cross_database_operations_instance_status_created",
            "index_cross_database_operations_owner_subject_status",
            "index_cross_database_operations_status_updated",
            "index_cross_database_operations_writer_epoch_status",
            "index_cross_database_operations_parent_child"
        )
    }
}
