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
class MorimilDatabaseMigrationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun cleanUp() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migratesLegacyIdentityFromV4ThroughCurrentV14() {
        createVersion4DatabaseWithLegacyIdentity()

        val database = Room.databaseBuilder(
            context,
            MorimilDatabase::class.java,
            TEST_DATABASE
        )
            .addMigrations(
                MorimilDatabase.MIGRATION_4_5,
                MorimilDatabase.MIGRATION_5_6,
                MorimilDatabase.MIGRATION_6_7,
                MorimilDatabase.MIGRATION_7_8,
                MorimilDatabase.MIGRATION_8_9,
                MorimilDatabase.MIGRATION_9_10,
                MorimilDatabase.MIGRATION_10_11,
                MorimilDatabase.MIGRATION_11_12,
                MorimilDatabase.MIGRATION_12_13,
                MorimilDatabase.MIGRATION_13_14
            )
            .build()

        try {
            val migrated = database.openHelper.writableDatabase
            assertEquals(14, migrated.userVersion())
            assertTrue(
                migrated.columnNames("local_instance_identity").containsAll(
                    setOf("localMemoryOwner", "localMemoryName", "localMemoryUri")
                )
            )
            migrated.query(
                """
                SELECT localMemoryOwner, localMemoryName, localMemoryUri
                FROM local_instance_identity
                WHERE instanceId = 'instance-1'
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("morimilpabfelon-cell", cursor.getString(0))
                assertEquals("Morimil-app", cursor.getString(1))
                assertEquals(
                    "https://github.com/morimilpabfelon-cell/Morimil-app",
                    cursor.getString(2)
                )
            }
            assertTrue(migrated.tableNames().contains("genesis_ultra_birth_authorization"))
            assertTrue(migrated.tableNames().contains("genesis_ultra_memory_payloads"))
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

    private fun createVersion4DatabaseWithLegacyIdentity() {
        context.deleteDatabase(TEST_DATABASE)
        val file = context.getDatabasePath(TEST_DATABASE)
        file.parentFile?.mkdirs()
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            createVersion4Tables(database)
            database.execSQL(
                """
                INSERT INTO local_instance_identity (
                    instanceId,
                    alias,
                    bornAtMillis,
                    genesisAgentId,
                    genesisRole,
                    genesisRiskTier,
                    genesisSchemaVersion,
                    forkOwner,
                    forkRepo,
                    forkHtmlUrl
                ) VALUES (
                    'instance-1',
                    'Morimil',
                    1000,
                    'morimil',
                    'companion',
                    'local_only',
                    '1',
                    'morimilpabfelon-cell',
                    'Morimil-app',
                    'https://github.com/morimilpabfelon-cell/Morimil-app'
                )
                """.trimIndent()
            )
            database.version = 4
        } finally {
            database.close()
        }
    }

    private fun createVersion4Tables(database: SQLiteDatabase) {
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
            CREATE TABLE IF NOT EXISTS user_workspace (
                workspaceId TEXT NOT NULL PRIMARY KEY,
                displayName TEXT NOT NULL,
                genesisSource TEXT NOT NULL,
                localPrimary INTEGER NOT NULL,
                optionalRepoOwner TEXT,
                optionalRepoName TEXT,
                optionalRepoPrivate INTEGER NOT NULL,
                repoProposalApproved INTEGER NOT NULL,
                updatedAtMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS local_instance_identity (
                instanceId TEXT NOT NULL PRIMARY KEY,
                alias TEXT NOT NULL,
                bornAtMillis INTEGER NOT NULL,
                genesisAgentId TEXT NOT NULL,
                genesisRole TEXT NOT NULL,
                genesisRiskTier TEXT NOT NULL,
                genesisSchemaVersion TEXT NOT NULL,
                forkOwner TEXT NOT NULL,
                forkRepo TEXT NOT NULL,
                forkHtmlUrl TEXT NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS genesis_core (
                coreId TEXT NOT NULL PRIMARY KEY,
                instanceId TEXT NOT NULL,
                aliasAtBirth TEXT NOT NULL,
                copiedAtMillis INTEGER NOT NULL,
                sourceOrigin TEXT NOT NULL,
                schemaVersion TEXT NOT NULL,
                agentId TEXT NOT NULL,
                role TEXT NOT NULL,
                owner TEXT NOT NULL,
                riskTier TEXT NOT NULL,
                doctrineRef TEXT NOT NULL,
                policyRef TEXT NOT NULL,
                allowedActionsJson TEXT NOT NULL,
                disallowedActionsJson TEXT NOT NULL,
                doctrineText TEXT,
                policyText TEXT,
                contentSha256 TEXT NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memory_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                genesisCoreId TEXT NOT NULL,
                eventType TEXT NOT NULL,
                actor TEXT NOT NULL,
                body TEXT NOT NULL,
                importance INTEGER NOT NULL,
                createdAtMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memory_snapshots (
                snapshotId TEXT NOT NULL PRIMARY KEY,
                genesisCoreId TEXT NOT NULL,
                summary TEXT NOT NULL,
                eventCount INTEGER NOT NULL,
                messageCount INTEGER NOT NULL,
                updatedAtMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun SupportSQLiteDatabase.userVersion(): Int {
        return query("PRAGMA user_version").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
    }

    private fun SupportSQLiteDatabase.columnNames(tableName: String): Set<String> {
        return query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
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

    private companion object {
        const val TEST_DATABASE = "morimil-v4-v14-identity-migration-test"
    }
}
