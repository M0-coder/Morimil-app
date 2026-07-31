package com.morimil.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class FullChainDatabaseMigrationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun cleanUp() {
        context.deleteDatabase(TEST_DATABASE)
        context.deleteDatabase(FRESH_GUARD_DATABASE)
    }

    @Test
    fun morimilDatabaseMigratesFrom1To15ThroughFullChain() {
        createVersion1Database()

        val database = Room.databaseBuilder(
            context,
            MorimilDatabase::class.java,
            TEST_DATABASE
        )
            .addMigrations(
                MorimilDatabase.MIGRATION_1_2,
                MorimilDatabase.MIGRATION_2_3,
                MorimilDatabase.MIGRATION_3_4,
                MorimilDatabase.MIGRATION_4_5,
                MorimilDatabase.MIGRATION_5_6,
                MorimilDatabase.MIGRATION_6_7,
                MorimilDatabase.MIGRATION_7_8,
                MorimilDatabase.MIGRATION_8_9,
                MorimilDatabase.MIGRATION_9_10,
                MorimilDatabase.MIGRATION_10_11,
                MorimilDatabase.MIGRATION_11_12,
                MorimilDatabase.MIGRATION_12_13,
                MorimilDatabase.MIGRATION_13_14,
                MorimilDatabase.MIGRATION_14_15
            )
            .build()

        try {
            val migrated = database.openHelper.writableDatabase
            assertEquals(15, migrated.userVersion())
            assertTrue(
                migrated.tableNames().containsAll(
                    setOf(
                        "reasoning_turns",
                        "decision_log",
                        "project_state",
                        "user_workspace",
                        "local_instance_identity",
                        "genesis_core",
                        "memory_events",
                        "memory_snapshots",
                        "genesis_ultra_birth_commit",
                        "genesis_ultra_birth_artifacts",
                        "genesis_ultra_birth_journal",
                        "genesis_ultra_memory_events",
                        "genesis_ultra_birth_authorization",
                        "genesis_ultra_memory_payloads"
                    )
                )
            )
            assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM reasoning_turns"))
            assertEquals(
                "seed message survives migration",
                migrated.singleString("SELECT body FROM reasoning_turns WHERE id = 1")
            )
            assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM decision_log"))
            assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM project_state"))
            assertTrue(!migrated.tableNames().contains("memory_messages"))
            assertEquals(
                0,
                migrated.singleInt("SELECT COUNT(*) FROM genesis_ultra_birth_authorization")
            )
            assertEquals(
                0,
                migrated.singleInt("SELECT COUNT(*) FROM genesis_ultra_memory_payloads")
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun memoryOrganCurrentSchemaTerminatesAtV9WithDurableGuardedJournal() {
        val database = Room.inMemoryDatabaseBuilder(
            context,
            MemoryOrganDatabase::class.java
        )
            .addCallback(MemoryOrganDatabaseMigrationV9.CALLBACK)
            .build()

        try {
            val current = database.openHelper.writableDatabase
            assertEquals(9, current.userVersion())
            assertTrue(current.tableNames().contains("cross_database_operations"))
            assertEquals(
                0,
                current.singleInt("SELECT COUNT(*) FROM cross_database_operations")
            )
            assertTrue(
                current.triggerNames().containsAll(
                    setOf(
                        "cross_database_operations_validate_insert",
                        "cross_database_operations_validate_update"
                    )
                )
            )
            val rejected = runCatching {
                current.execSQL(invalidJournalInsert())
            }.isFailure
            assertTrue("Fresh v9 store accepted a malformed journal row", rejected)
            assertEquals(
                0,
                current.singleInt("SELECT COUNT(*) FROM cross_database_operations")
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun freshV9RejectsEveryPartialCanonicalReceiptCombination() {
        withFreshV9Database { database ->
            assertPartialGroupMatrix(database, canonicalReceipt = true)
        }
    }

    @Test
    fun freshV9RejectsEveryPartialLocalResultCombination() {
        withFreshV9Database { database ->
            assertPartialGroupMatrix(database, canonicalReceipt = false)
        }
    }

    @Test
    fun freshAndMigratedV9AcceptOnlyAllNullOrAllCompleteGroups() {
        withFreshV9Database { database ->
            database.execSQL(INSERT_OPERATION, validOperationArgs(seed = 600))
            database.execSQL(
                INSERT_OPERATION,
                validOperationArgs(
                    seed = 601,
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
                arrayOf<Any?>(EVENT_HASH, 8L, PROVENANCE_DIGEST, operationId(600))
            )
            database.execSQL(
                UPDATE_LOCAL_RESULT,
                arrayOf(LOCAL_RESULT_SCHEMA, LOCAL_RESULT_JSON, LOCAL_RESULT_DIGEST, operationId(600))
            )
            database.execSQL(
                UPDATE_RECEIPT,
                arrayOf(null, null, null, operationId(601))
            )
            database.execSQL(
                UPDATE_LOCAL_RESULT,
                arrayOf(null, null, null, operationId(601))
            )
            assertEquals(2, database.singleInt("SELECT COUNT(*) FROM cross_database_operations"))
        }
    }

    @Test
    fun committedRowsRequireCompleteReceiptAndCompleteLocalResult() {
        withFreshV9Database { database ->
            listOf(
                validOperationArgs(
                    seed = 610,
                    status = "COMMITTED",
                    canonicalEventHash = EVENT_HASH,
                    canonicalSequence = 7,
                    canonicalProvenanceDigest = PROVENANCE_DIGEST,
                    committedAtMillis = 1001
                ),
                validOperationArgs(
                    seed = 611,
                    status = "COMMITTED",
                    localResultSchema = LOCAL_RESULT_SCHEMA,
                    localResultJson = LOCAL_RESULT_JSON,
                    localResultDigest = LOCAL_RESULT_DIGEST,
                    committedAtMillis = 1001
                ),
                validOperationArgs(
                    seed = 612,
                    status = "COMMITTED",
                    canonicalEventHash = EVENT_HASH,
                    canonicalSequence = 7,
                    canonicalProvenanceDigest = PROVENANCE_DIGEST,
                    localResultSchema = LOCAL_RESULT_SCHEMA,
                    localResultJson = LOCAL_RESULT_JSON,
                    localResultDigest = LOCAL_RESULT_DIGEST
                )
            ).forEach { args -> assertInsertRejected(database, args) }

            database.execSQL(
                INSERT_OPERATION,
                validOperationArgs(
                    seed = 613,
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

    @Test
    fun callbackReplacesPreviouslyInstalledVulnerableTrigger() {
        context.deleteDatabase(FRESH_GUARD_DATABASE)
        var room = Room.databaseBuilder(
            context,
            MemoryOrganDatabase::class.java,
            FRESH_GUARD_DATABASE
        ).addCallback(MemoryOrganDatabaseMigrationV9.CALLBACK).build()
        room.openHelper.writableDatabase.execSQL(
            "DROP TRIGGER IF EXISTS cross_database_operations_validate_insert"
        )
        room.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER cross_database_operations_validate_insert
            BEFORE INSERT ON cross_database_operations
            WHEN NOT (NEW.canonicalSequence >= 1)
            BEGIN
                SELECT RAISE(ABORT, 'vulnerable_guard');
            END
            """.trimIndent()
        )
        room.close()

        room = Room.databaseBuilder(
            context,
            MemoryOrganDatabase::class.java,
            FRESH_GUARD_DATABASE
        ).addCallback(MemoryOrganDatabaseMigrationV9.CALLBACK).build()
        try {
            val database = room.openHelper.writableDatabase
            val triggerSql = database.singleString(
                "SELECT sql FROM sqlite_master WHERE type = 'trigger' " +
                    "AND name = 'cross_database_operations_validate_insert'"
            )
            assertTrue(triggerSql.contains("IS NOT TRUE"))
            assertTrue(!triggerSql.contains("vulnerable_guard"))
            val partial = validOperationArgs(seed = 620).apply {
                this[22] = EVENT_HASH
                this[24] = PROVENANCE_DIGEST
            }
            assertInsertRejected(database, partial)
        } finally {
            room.close()
        }
    }

    @Test
    fun rollbackFromApprovedPreservesNullPostSnapshotId() = runBlocking {
        assertRollbackPreservesSnapshot("approved", null, 700)
    }

    @Test
    fun rollbackFromCompletedPreservesAuditedSha256PostSnapshotId() = runBlocking {
        assertRollbackPreservesSnapshot("completed", AUDITED_SNAPSHOT, 701)
    }

    @Test
    fun rollbackFromFailedPreservesExistingPostSnapshotId() = runBlocking {
        assertRollbackPreservesSnapshot("failed", null, 702)
    }

    @Test
    fun rollbackCanonicalEventRemainsAvailableInJournalAndLocalResult() = runBlocking {
        val room = freshRoomDatabase()
        try {
            val database = room.openHelper.writableDatabase
            insertMigrationRecord(database, "migration-703", "completed", AUDITED_SNAPSHOT)
            val localResult = "{\"canonical_event_hash\":\"$EVENT_HASH\",\"owner_status\":\"rolled_back\"}"
            database.execSQL(
                INSERT_OPERATION,
                validOperationArgs(
                    seed = 703,
                    status = "COMMITTED",
                    canonicalEventHash = EVENT_HASH,
                    canonicalSequence = 7,
                    canonicalProvenanceDigest = PROVENANCE_DIGEST,
                    localResultSchema = LOCAL_RESULT_SCHEMA,
                    localResultJson = localResult,
                    localResultDigest = LOCAL_RESULT_DIGEST,
                    committedAtMillis = 1001
                )
            )
            assertEquals(
                1,
                room.memoryOrganDao().rollbackMigrationRecordIfAllowed(
                    migrationId = "migration-703",
                    notesJson = "[]",
                    updatedAtMillis = 1002
                )
            )
            assertEquals(
                EVENT_HASH,
                database.singleString(
                    "SELECT canonicalEventHash FROM cross_database_operations " +
                        "WHERE operationId = '${operationId(703)}'"
                )
            )
            assertEquals(
                localResult,
                database.singleString(
                    "SELECT localResultJson FROM cross_database_operations " +
                        "WHERE operationId = '${operationId(703)}'"
                )
            )
        } finally {
            room.close()
        }
    }

    @Test
    fun postSnapshotIdNeverStartsWithEvsha256() = runBlocking {
        val room = freshRoomDatabase()
        try {
            val database = room.openHelper.writableDatabase
            listOf(
                Triple("migration-710", "approved", null),
                Triple("migration-711", "completed", AUDITED_SNAPSHOT),
                Triple("migration-712", "failed", null)
            ).forEachIndexed { index, (migrationId, status, snapshot) ->
                insertMigrationRecord(database, migrationId, status, snapshot)
                assertEquals(
                    1,
                    room.memoryOrganDao().rollbackMigrationRecordIfAllowed(
                        migrationId = migrationId,
                        notesJson = "[]",
                        updatedAtMillis = 1100L + index
                    )
                )
            }
            assertEquals(
                0,
                database.singleInt(
                    "SELECT COUNT(*) FROM migration_records " +
                        "WHERE postSnapshotId LIKE 'evsha256:%'"
                )
            )
        } finally {
            room.close()
        }
    }

    private suspend fun assertRollbackPreservesSnapshot(
        status: String,
        snapshot: String?,
        seed: Int
    ) {
        val room = freshRoomDatabase()
        try {
            val database = room.openHelper.writableDatabase
            val migrationId = "migration-$seed"
            insertMigrationRecord(database, migrationId, status, snapshot)
            assertEquals(
                1,
                room.memoryOrganDao().rollbackMigrationRecordIfAllowed(
                    migrationId = migrationId,
                    notesJson = "[\"rollback\"]",
                    updatedAtMillis = 1001
                )
            )
            assertEquals(
                "rolled_back",
                database.singleString(
                    "SELECT status FROM migration_records WHERE migrationId = '$migrationId'"
                )
            )
            assertEquals(
                snapshot,
                database.singleNullableString(
                    "SELECT postSnapshotId FROM migration_records WHERE migrationId = '$migrationId'"
                )
            )
        } finally {
            room.close()
        }
    }

    private fun insertMigrationRecord(
        database: SupportSQLiteDatabase,
        migrationId: String,
        status: String,
        postSnapshotId: String?
    ) {
        database.execSQL(
            """
            INSERT INTO migration_records (
                migrationId, instanceId, genesisCoreHash, proposalId, migrationType,
                fromVersion, toVersion, affectedArtifactsJson, preSnapshotId,
                chainVerified, backupRequired, stepsJson, expectedEffect, riskLevel,
                approvalRequired, approvedByUser, approvalId, status, postSnapshotId,
                errorsJson, rollbackAvailable, rollbackStrategy, createdBy,
                createdAtMillis, updatedAtMillis
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                migrationId, "instance-test", "sha256:" + "9".repeat(64), "proposal-test",
                "test", "v1", "v2", "[]", "sha256:" + "8".repeat(64), 1, 1,
                "[]", "preserve", "low", 1, 1, "approval-test", status,
                postSnapshotId, "[]", 1, "append_only", "test", 1000L, 1000L
            )
        )
    }

    private fun assertPartialGroupMatrix(
        database: SupportSQLiteDatabase,
        canonicalReceipt: Boolean
    ) {
        (1..6).forEach { mask ->
            val insertSeed = if (canonicalReceipt) 800 + mask else 900 + mask
            val updateSeed = if (canonicalReceipt) 1000 + mask else 1100 + mask
            val partial = groupValues(mask, canonicalReceipt)
            val insertArgs = validOperationArgs(seed = insertSeed).apply {
                applyGroup(this, partial, canonicalReceipt)
            }
            assertInsertRejected(database, insertArgs)
            assertEquals(
                0,
                database.singleInt(
                    "SELECT COUNT(*) FROM cross_database_operations " +
                        "WHERE operationId = '${operationId(insertSeed)}'"
                )
            )

            database.execSQL(INSERT_OPERATION, validOperationArgs(seed = updateSeed))
            val updateRejected = runCatching {
                database.execSQL(
                    if (canonicalReceipt) UPDATE_RECEIPT else UPDATE_LOCAL_RESULT,
                    arrayOf(partial[0], partial[1], partial[2], operationId(updateSeed))
                )
            }.isFailure
            assertTrue("Fresh v9 accepted partial UPDATE mask=$mask", updateRejected)
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

    private fun assertInsertRejected(
        database: SupportSQLiteDatabase,
        args: Array<Any?>
    ) {
        val rejected = runCatching { database.execSQL(INSERT_OPERATION, args) }.isFailure
        assertTrue("Fresh v9 accepted an invalid journal row", rejected)
    }

    private fun withFreshV9Database(block: (SupportSQLiteDatabase) -> Unit) {
        val room = freshRoomDatabase()
        try {
            block(room.openHelper.writableDatabase)
        } finally {
            room.close()
        }
    }

    private fun freshRoomDatabase(): MemoryOrganDatabase {
        return Room.inMemoryDatabaseBuilder(context, MemoryOrganDatabase::class.java)
            .addCallback(MemoryOrganDatabaseMigrationV9.CALLBACK)
            .build()
    }

    private fun validOperationArgs(
        seed: Int,
        status: String = "STAGED",
        canonicalEventHash: String? = null,
        canonicalSequence: Long? = null,
        canonicalProvenanceDigest: String? = null,
        localResultSchema: String? = null,
        localResultJson: String? = null,
        localResultDigest: String? = null,
        committedAtMillis: Long? = null
    ): Array<Any?> {
        return arrayOf(
            operationId(seed), "cognitive_migration", "cognitive_migration.propose", 1,
            "instance-test", "body-test", "epoch-test", "migration-test", null, null,
            "test.payload.v1", "{}", "sha256:" + "1".repeat(64), eventId(seed),
            "cognitive_migration.proposed", "body", "test.evidence.v1", "{}",
            "sha256:" + "2".repeat(64), status, 0, null, canonicalEventHash,
            canonicalSequence, canonicalProvenanceDigest, localResultSchema, localResultJson,
            localResultDigest, 1000L, 1000L, 1000L, committedAtMillis
        )
    }

    private fun operationId(seed: Int): String =
        "xop_" + seed.toString(16).padStart(64, '0')

    private fun eventId(seed: Int): String =
        "xevt_" + (seed + 2000).toString(16).padStart(64, '0')

    private fun invalidJournalInsert(): String {
        val operationId = "xop_" + "a".repeat(64)
        val eventId = "xevt_" + "b".repeat(64)
        val evidenceDigest = "sha256:" + "c".repeat(64)
        return """
            INSERT INTO cross_database_operations (
                operationId, ownerType, operationType, operationVersion, instanceId,
                writerBodyId, writerEpoch, subjectId, parentOperationId, childPhase,
                payloadSchema, payloadJson, payloadDigest, eventId, eventType, eventBody,
                evidenceSchema, evidenceJson, evidenceDigest, status, attemptCount,
                lastErrorCode, canonicalEventHash, canonicalSequence,
                canonicalProvenanceDigest, localResultSchema, localResultJson,
                localResultDigest, occurredAtMillis, createdAtMillis, updatedAtMillis,
                committedAtMillis
            ) VALUES (
                '$operationId', 'cognitive_migration', 'cognitive_migration.propose', 1,
                'instance-test', 'body-test', 'epoch-test', 'migration-test', NULL, NULL,
                'test.payload.v1', '{}', 'sha256:bad', '$eventId',
                'cognitive_migration.proposed', 'body', 'test.evidence.v1', '{}',
                '$evidenceDigest', 'STAGED', 0, NULL, NULL, NULL, NULL, NULL, NULL,
                NULL, 1000, 1000, 1000, NULL
            )
        """.trimIndent()
    }

    private fun createVersion1Database() {
        context.deleteDatabase(TEST_DATABASE)
        val file = context.getDatabasePath(TEST_DATABASE)
        file.parentFile?.mkdirs()
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS memory_messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    author TEXT NOT NULL,
                    body TEXT NOT NULL,
                    createdAtMillis INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO memory_messages (id, author, body, createdAtMillis)
                VALUES (1, 'user', 'seed message survives migration', 1000)
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS decision_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    status TEXT NOT NULL,
                    createdAtMillis INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO decision_log (id, title, status, createdAtMillis)
                VALUES (1, 'Keep local memory', 'accepted', 1000)
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS project_state (
                    projectId TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    status TEXT NOT NULL,
                    updatedAtMillis INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO project_state (projectId, title, status, updatedAtMillis)
                VALUES ('morimil-app', 'Morimil App', 'active', 1000)
                """.trimIndent()
            )
            database.version = 1
        } finally {
            database.close()
        }
    }

    private fun SupportSQLiteDatabase.userVersion(): Int {
        return query("PRAGMA user_version").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
    }

    private fun SupportSQLiteDatabase.tableNames(): Set<String> {
        return query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    private fun SupportSQLiteDatabase.triggerNames(): Set<String> {
        return query("SELECT name FROM sqlite_master WHERE type = 'trigger'").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    private fun SupportSQLiteDatabase.singleInt(sql: String): Int {
        return query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
    }

    private fun SupportSQLiteDatabase.singleString(sql: String): String {
        return query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }
    }

    private fun SupportSQLiteDatabase.singleNullableString(sql: String): String? {
        return query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            if (cursor.isNull(0)) null else cursor.getString(0)
        }
    }

    private companion object {
        const val TEST_DATABASE = "morimil-v1-v15-full-chain-test"
        const val FRESH_GUARD_DATABASE = "memory-organ-fresh-v9-guard-test"
        val EVENT_HASH = "evsha256:" + "3".repeat(64)
        val PROVENANCE_DIGEST = "sha256:" + "4".repeat(64)
        const val LOCAL_RESULT_SCHEMA = "test.local_result.v1"
        const val LOCAL_RESULT_JSON = "{\"owner_status\":\"planned\"}"
        val LOCAL_RESULT_DIGEST = "sha256:" + "5".repeat(64)
        val AUDITED_SNAPSHOT = "sha256:" + "6".repeat(64)
        const val UPDATE_RECEIPT =
            "UPDATE cross_database_operations SET canonicalEventHash = ?, " +
                "canonicalSequence = ?, canonicalProvenanceDigest = ? WHERE operationId = ?"
        const val UPDATE_LOCAL_RESULT =
            "UPDATE cross_database_operations SET localResultSchema = ?, " +
                "localResultJson = ?, localResultDigest = ? WHERE operationId = ?"
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
    }
}
