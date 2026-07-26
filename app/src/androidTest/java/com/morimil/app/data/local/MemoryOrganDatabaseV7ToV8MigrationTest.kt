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
class MemoryOrganDatabaseV7ToV8MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MemoryOrganDatabase::class.java
    )

    @Test
    fun migrationPreservesVaultsAndCreatesRecoverableOutbox() {
        val source = helper.createDatabase(TEST_DATABASE, 7)
        try {
            source.execSQL(
                """
                INSERT INTO project_vaults (
                    vaultId, displayName, companyName, projectType, mission, status,
                    roadmapSummary, progressPercent, activeAgentCount, healthStatus,
                    sourceContext, createdAtMillis, updatedAtMillis, completedAtMillis
                ) VALUES (
                    'vault-existing', 'Morimil', 'Morimil', 'software', 'Continue',
                    'active', 'Roadmap', 25, 2, 'planning', 'test', 1000, 1000, NULL
                )
                """.trimIndent()
            )
        } finally {
            source.close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            8,
            true,
            MemoryOrganDatabaseMigrationV8.MIGRATION_7_8
        )
        try {
            assertEquals(8, database.singleInt("PRAGMA user_version"))
            assertEquals(1, database.singleInt("SELECT COUNT(*) FROM project_vaults"))
            assertEquals(
                "Morimil",
                database.singleString(
                    "SELECT displayName FROM project_vaults WHERE vaultId = 'vault-existing'"
                )
            )
            assertTrue(database.tableNames().contains("project_vault_outbox"))
            assertTrue(
                database.columnNames("project_vault_outbox").containsAll(
                    setOf(
                        "operationId",
                        "vaultId",
                        "operationType",
                        "eventId",
                        "eventType",
                        "eventBody",
                        "evidenceJson",
                        "payloadJson",
                        "payloadDigest",
                        "status",
                        "attemptCount",
                        "lastError",
                        "canonicalEventHash",
                        "canonicalSequence",
                        "occurredAtMillis",
                        "createdAtMillis",
                        "updatedAtMillis",
                        "committedAtMillis"
                    )
                )
            )
            assertTrue(
                database.indexNames("project_vault_outbox").containsAll(
                    setOf(
                        "index_project_vault_outbox_vaultId",
                        "index_project_vault_outbox_status",
                        "index_project_vault_outbox_vaultId_status",
                        "index_project_vault_outbox_updatedAtMillis"
                    )
                )
            )
        } finally {
            database.close()
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.singleInt(sql: String): Int {
        return query(sql).use { cursor ->
            check(cursor.moveToFirst()) { "No row returned for: $sql" }
            cursor.getInt(0)
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.singleString(sql: String): String {
        return query(sql).use { cursor ->
            check(cursor.moveToFirst()) { "No row returned for: $sql" }
            cursor.getString(0)
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.tableNames(): Set<String> {
        return query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.columnNames(table: String): Set<String> {
        return query("PRAGMA table_info(`$table`)").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.indexNames(table: String): Set<String> {
        return query("PRAGMA index_list(`$table`)").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "memory-organ-v7-to-v8.db"
    }
}
