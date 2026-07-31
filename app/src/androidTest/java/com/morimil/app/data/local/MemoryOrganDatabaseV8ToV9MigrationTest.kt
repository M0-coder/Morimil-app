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

    @Test
    fun migratedV9RejectsEveryPartialCanonicalReceiptCombination() {
        withMigratedV9Database("receipt-matrix") { database ->
            assertPartialGroupMatrix(database, canonicalReceipt = true)
        }
    }

    @Test
    fun migratedV9RejectsEveryPartialLocalResultCombination() {
        withMigratedV9Database("local-result-matrix") { database ->
            assertPartialGroupMatrix(database, canonicalReceipt = false)
        }
    }

    @Test
    fun migratedV9AcceptsOnlyAllNullOrAllCompleteGroups() {
        withMigratedV9Database("complete-groups") { database ->
            database.execSQL(INSERT_OPERATION, validOperationArgs(seed = 100))
            database.execSQL(
                INSERT_OPERATION,
                validOperationArgs(
                    seed = 101,
                    canonicalEventHash = EVENT_HASH,
                    canonicalSequence = 7,
                    canonicalProvenanceDigest = PROVENANCE_DIGEST,
                    localResultSchema = LOCAL_RESULT_SCHEMA,
                    localResultJson = LOCAL_RESULT_JSON,
                    localResultDigest = LOCAL_RESULT_DIGEST
                )
            )
            database.execSQL(
                UPDATE_RECEIPT,
                arrayOf<Any?>(EVENT_HASH, 8L, PROVENANCE_DIGEST, operationId(100))
            )
            database.execSQL(
                UPDATE_LOCAL_RESULT,
                arrayOf(LOCAL_RESULT_SCHEMA, LOCAL_RESULT_JSON, LOCAL_RESULT_DIGEST, operationId(100))
            )
            database.execSQL(
                UPDATE_RECEIPT,
                arrayOf(null, null, null, operationId(101))
            )
            database.execSQL(
                UPDATE_LOCAL_RESULT,
                arrayOf(null, null, null, operationId(101))
            )
            assertEquals(
                2,
                database.singleInt("SELECT COUNT(*) FROM cross_database_operations")
            )
        }
    }

    @Test
    fun migratedCommittedRowsRequireCompleteReceiptAndCompleteLocalResult() {
        withMigratedV9Database("committed-groups") { database ->
            listOf(
                validOperationArgs(
                    seed = 110,
                    status = "COMMITTED",
                    canonicalEventHash = EVENT_HASH,
                    canonicalSequence = 7,
                    canonicalProvenanceDigest = PROVENANCE_DIGEST,
                    committedAtMillis = 1001
                ),
                validOperationArgs(
                    seed = 111,
                    status = "COMMITTED",
                    localResultSchema = LOCAL_RESULT_SCHEMA,
                    localResultJson = LOCAL_RESULT_JSON,
                    localResultDigest = LOCAL_RESULT_DIGEST,
                    committedAtMillis = 1001
                ),
                validOperationArgs(
                    seed = 112,
                    status = "COMMITTED",
                    canonicalEventHash = EVENT_HASH,
                    canonicalSequence = 7,
                    canonicalProvenanceDigest = PROVENANCE_DIGEST,
                    localResultSchema = LOCAL_RESULT_SCHEMA,
                    localResultJson = LOCAL_RESULT_JSON,
                    localResultDigest = LOCAL_RESULT_DIGEST
                )
            ).forEach { args -> assertSqlRejected(database, args, rewriteOperationId = false) }

            database.execSQL(
                INSERT_OPERATION,
                validOperationArgs(
                    seed = 113,
                    status = "COMMITTED",
                    canonicalEventHash = EVENT_HASH,
                    canonicalSequence = 7,
                    canonicalProvenanceDigest = PROVENANCE_DIGEST,
                    localResultSchema = LOCAL_RESULT_SCHEMA,
                    localResultJson = LOCAL_RESULT_JSON,
                    localResultDigest = LOCAL_RESULT_DIGEST,
                    committedAtMillis = 1001
                )
            )
        }
    }

    private fun assertPartialGroupMatrix(
        database: SupportSQLiteDatabase,
        canonicalReceipt: Boolean
    ) {
        (1..6).forEach { mask ->
            val insertSeed = if (canonicalReceipt) 200 + mask else 300 + mask
            val updateSeed = if (canonicalReceipt) 400 + mask else 500 + mask
            val partial = groupValues(mask, canonicalReceipt)
            val insertArgs = validOperationArgs(seed = insertSeed).apply {
                applyGroup(this, partial, canonicalReceipt)
            }
            assertSqlRejected(database, insertArgs, rewriteOperationId = false)
            assertEquals(
                0,
                database.singleInt(
                    "SELECT COUNT(*) FROM cross_database_operations " +
                        "WHERE operationId = '${operationId(insertSeed)}'"
                )
            )

            val baseline = validOperationArgs(seed = updateSeed)
            database.execSQL(INSERT_OPERATION, baseline)
            val updateRejected = runCatching {
                database.execSQL(
                    if (canonicalReceipt) UPDATE_RECEIPT else UPDATE_LOCAL_RESULT,
                    arrayOf(partial[0], partial[1], partial[2], operationId(updateSeed))
                )
            }.isFailure
            assertTrue("Partial journal group UPDATE was accepted for mask=$mask", updateRejected)
            assertEquals(
                0,
                database.singleInt(
                    if (canonicalReceipt) {
                        "SELECT COUNT(*) FROM cross_database_operations WHERE " +
                            "operationId = '${operationId(updateSeed)}' AND " +
                            "(canonicalEventHash IS NOT NULL OR canonicalSequence IS NOT NULL " +
                            "OR canonicalProvenanceDigest IS NOT NULL)"
                    } else {
                        "SELECT COUNT(*) FROM cross_database_operations WHERE " +
                            "operationId = '${operationId(updateSeed)}' AND " +
                            "(localResultSchema IS NOT NULL OR localResultJson IS NOT NULL " +
                            "OR localResultDigest IS NOT NULL)"
                    }
                )
            )
        }
    }

    private fun groupValues(mask: Int, canonicalReceipt: Boolean): Array<Any?> {
        val complete = if (canonicalReceipt) {
            arrayOf<Any?>(EVENT_HASH, 7L, PROVENANCE_DIGEST)
        } else {
            arrayOf<Any?>(LOCAL_RESULT_SCHEMA, LOCAL_RESULT_JSON, LOCAL_RESULT_DIGEST)
        }
        return Array(3) { index ->
            if (mask and (1 shl index) != 0) complete[index] else null
        }
    }

    private fun applyGroup(
        args: Array<Any?>,
        values: Array<Any?>,
        canonicalReceipt: Boolean
    ) {
        val offset = if (canonicalReceipt) 22 else 25
        values.forEachIndexed { index, value -> args[offset + index] = value }
    }

    private fun withMigratedV9Database(
        suffix: String,
        block: (SupportSQLiteDatabase) -> Unit
    ) {
        val name = "memory-organ-v8-to-v9-$suffix.db"
        helper.createDatabase(name, 8).close()
        val database = helper.runMigrationsAndValidate(
            name,
            9,
            true,
            MemoryOrganDatabaseMigrationV9.MIGRATION_8_9
        )
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    private fun assertSqlRejected(
        database: SupportSQLiteDatabase,
        bindArgs: Array<Any?>,
        rewriteOperationId: Boolean = true
    ) {
        val rejected = runCatching {
            database.execSQL(
                INSERT_OPERATION,
                bindArgs.also { args ->
                    if (rewriteOperationId) {
                        args[0] = "xop_" +
                            args.joinToString("|").hashCode().toUInt().toString(16)
                                .padStart(64, '0')
                    }
                }
            )
        }.isFailure
        assertTrue("Malformed journal row was accepted", rejected)
    }

    private fun validOperationArgs(
        seed: Int = 10,
        payloadDigest: String = "sha256:" + "1".repeat(64),
        status: String = "STAGED",
        parentOperationId: String? = null,
        childPhase: String? = null,
        canonicalEventHash: String? = null,
        canonicalSequence: Long? = null,
        canonicalProvenanceDigest: String? = null,
        localResultSchema: String? = null,
        localResultJson: String? = null,
        localResultDigest: String? = null,
        committedAtMillis: Long? = null
    ): Array<Any?> {
        return arrayOf(
            operationId(seed),
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
            eventId(seed),
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
            localResultSchema,
            localResultJson,
            localResultDigest,
            1000L,
            1000L,
            1000L,
            committedAtMillis
        )
    }

    private fun operationId(seed: Int): String =
        "xop_" + seed.toString(16).padStart(64, '0')

    private fun eventId(seed: Int): String =
        "xevt_" + (seed + 1000).toString(16).padStart(64, '0')

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

        val EVENT_HASH = "evsha256:" +
            "3".repeat(64)
        val PROVENANCE_DIGEST = "sha256:" +
            "4".repeat(64)
        const val LOCAL_RESULT_SCHEMA = "test.local_result.v1"
        const val LOCAL_RESULT_JSON = "{\"owner_status\":\"planned\"}"
        val LOCAL_RESULT_DIGEST = "sha256:" +
            "5".repeat(64)
        const val UPDATE_RECEIPT =
            "UPDATE cross_database_operations SET canonicalEventHash = ?, " +
                "canonicalSequence = ?, canonicalProvenanceDigest = ? " +
                "WHERE operationId = ?"
        const val UPDATE_LOCAL_RESULT =
            "UPDATE cross_database_operations SET localResultSchema = ?, " +
                "localResultJson = ?, localResultDigest = ? WHERE operationId = ?"

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
