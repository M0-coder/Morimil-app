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

@RunWith(AndroidJUnit4::class)
class FullChainDatabaseMigrationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun cleanUp() {
        context.deleteDatabase(TEST_DATABASE)
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
    fun memoryOrganCurrentSchemaTerminatesAtV9WithDurableJournal() {
        val database = Room.inMemoryDatabaseBuilder(
            context,
            MemoryOrganDatabase::class.java
        ).build()

        try {
            val current = database.openHelper.writableDatabase
            assertEquals(9, current.userVersion())
            assertTrue(current.tableNames().contains("cross_database_operations"))
            assertEquals(
                0,
                current.singleInt("SELECT COUNT(*) FROM cross_database_operations")
            )
        } finally {
            database.close()
        }
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

    private companion object {
        const val TEST_DATABASE = "morimil-v1-v15-full-chain-test"
    }
}
