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
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MorimilDatabase::class.java
    )

    @Test
    fun morimilDatabaseMigratesFrom7To14WithMemoryDefaultsAndCanonicalPayloadStore() {
        val source = helper.createDatabase(TEST_DATABASE, 7)
        try {
            source.execSQL(
                """
                INSERT INTO memory_events (
                    id,
                    genesisCoreId,
                    eventType,
                    actor,
                    body,
                    importance,
                    createdAtMillis,
                    genesisCoreHash,
                    eventHash,
                    hashAlgorithm,
                    canonicalization,
                    source,
                    contextTag,
                    privacyVisibility
                ) VALUES (
                    1,
                    'primary_genesis',
                    'test.event',
                    'system',
                    'old memory event',
                    77,
                    1000,
                    'sha256:legacy-unverified',
                    'sha256:legacy-unverified',
                    'sha256',
                    'morimil.memory_event_hash.v1',
                    'system',
                    'local_runtime',
                    'private_local'
                )
                """.trimIndent()
            )
        } finally {
            source.close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            14,
            true,
            MorimilDatabase.MIGRATION_7_8,
            MorimilDatabase.MIGRATION_8_9,
            MorimilDatabase.MIGRATION_9_10,
            MorimilDatabase.MIGRATION_10_11,
            MorimilDatabase.MIGRATION_11_12,
            MorimilDatabase.MIGRATION_12_13,
            MorimilDatabase.MIGRATION_13_14
        )
        try {
            assertEquals(14, migrated.userVersion())
            assertTrue(migrated.tableNames().contains("genesis_ultra_birth_authorization"))
            assertTrue(migrated.tableNames().contains("genesis_ultra_memory_payloads"))
            assertTrue(
                migrated.indexNames("genesis_ultra_birth_authorization").containsAll(
                    setOf(
                        "index_genesis_ultra_birth_authorization_candidateDigest",
                        "index_genesis_ultra_birth_authorization_consentDigest",
                        "index_genesis_ultra_birth_authorization_authorizationDigest"
                    )
                )
            )
            assertTrue(
                migrated.indexNames("genesis_ultra_memory_payloads").containsAll(
                    setOf(
                        "index_genesis_ultra_memory_payloads_instanceId_sequence",
                        "index_genesis_ultra_memory_payloads_contentDigest"
                    )
                )
            )
            migrated.query(
                "SELECT memoryKind, tagsJson, evidenceJson, confidence, userConfirmed " +
                    "FROM memory_events WHERE id = 1"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("observation", cursor.getString(0))
                assertEquals("[]", cursor.getString(1))
                assertEquals("{}", cursor.getString(2))
                assertEquals(70, cursor.getInt(3))
                assertEquals(0, cursor.getInt(4))
            }
            assertEquals(
                0,
                migrated.query("SELECT COUNT(*) FROM genesis_ultra_memory_payloads").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    cursor.getInt(0)
                }
            )
        } finally {
            migrated.close()
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.userVersion(): Int {
        return query("PRAGMA user_version").use { cursor ->
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

    private fun androidx.sqlite.db.SupportSQLiteDatabase.indexNames(tableName: String): Set<String> {
        return query("PRAGMA index_list(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "morimil-v7-v14-focused-migration-test"
    }
}
