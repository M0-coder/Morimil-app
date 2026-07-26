package com.morimil.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MorimilDatabaseV14ToV15MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MorimilDatabase::class.java
    )

    @Test
    fun migrationCreatesConvergenceEvidenceAndFreezesLegacyEvents() {
        val source = helper.createDatabase(TEST_DATABASE, 14)
        try {
            source.execSQL(insertSql(id = 1L, eventHash = HASH_ONE))
        } finally {
            source.close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            15,
            true,
            MorimilDatabase.MIGRATION_14_15
        )
        try {
            assertEquals(15, database.singleInt("PRAGMA user_version"))
            assertTrue(
                database.tableNames().containsAll(
                    setOf(
                        "genesis_ultra_legacy_memory_convergence",
                        "genesis_ultra_legacy_memory_imports"
                    )
                )
            )
            assertEquals(
                setOf(
                    MorimilDatabaseMigrationV15.INSERT_TRIGGER,
                    MorimilDatabaseMigrationV15.UPDATE_TRIGGER,
                    MorimilDatabaseMigrationV15.DELETE_TRIGGER
                ),
                database.readOnlyTriggerNames()
            )
            assertEquals(1, database.singleInt("SELECT COUNT(*) FROM memory_events"))

            assertReadOnlyFailure {
                database.execSQL(insertSql(id = 2L, eventHash = HASH_TWO))
            }
            assertReadOnlyFailure {
                database.execSQL("UPDATE memory_events SET importance = 99 WHERE id = 1")
            }
            assertReadOnlyFailure {
                database.execSQL("DELETE FROM memory_events WHERE id = 1")
            }

            assertEquals(1, database.singleInt("SELECT COUNT(*) FROM memory_events"))
            assertEquals(70, database.singleInt("SELECT importance FROM memory_events WHERE id = 1"))
        } finally {
            database.close()
        }
    }

    private fun assertReadOnlyFailure(block: () -> Unit) {
        val failure = runCatching(block).exceptionOrNull()
        assertTrue(failure != null)
        assertTrue(
            generateSequence(failure) { error -> error.cause }
                .any { error ->
                    error.message?.contains(MorimilDatabaseMigrationV15.READ_ONLY_ERROR) == true
                }
        )
    }

    private fun insertSql(id: Long, eventHash: String): String {
        return """
            INSERT INTO memory_events (
                id, genesisCoreId, genesisCoreHash, previousEventHash, eventHash,
                hashAlgorithm, canonicalization, signatureAlgorithm, eventSignature,
                eventType, actor, source, contextTag, privacyVisibility, memoryKind,
                tagsJson, evidenceJson, confidence, userConfirmed, body, importance,
                createdAtMillis
            ) VALUES (
                $id,
                'primary_genesis',
                '$GENESIS_HASH',
                NULL,
                '$eventHash',
                'sha256',
                'morimil.memory_event_hash.v3',
                'android_keystore_ec_p256_sha256_ecdsa_v1',
                'signature-$id',
                'legacy.test',
                'system',
                'legacy_runtime',
                'local_runtime',
                'private_local',
                'observation',
                '[]',
                '{}',
                80,
                0,
                'legacy event $id',
                70,
                ${id * 1000L}
            )
        """.trimIndent()
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.singleInt(sql: String): Int {
        return query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.tableNames(): Set<String> {
        return query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.readOnlyTriggerNames(): Set<String> {
        return query(
            "SELECT name FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'memory_events_genesis_ultra_read_only_%'"
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "morimil-v14-v15-convergence-migration-test"
        const val GENESIS_HASH =
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val HASH_ONE =
            "sha256:1111111111111111111111111111111111111111111111111111111111111111"
        const val HASH_TWO =
            "sha256:2222222222222222222222222222222222222222222222222222222222222222"
    }
}
