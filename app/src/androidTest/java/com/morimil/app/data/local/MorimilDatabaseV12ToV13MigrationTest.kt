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
class MorimilDatabaseV12ToV13MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MorimilDatabase::class.java
    )

    @Test
    fun migrate12To13CreatesImmutableBirthAuthorizationReceipt() {
        helper.createDatabase(TEST_DATABASE, 12).close()

        val database = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            13,
            true,
            MorimilDatabase.MIGRATION_12_13
        )
        try {
            assertEquals(13, database.query("PRAGMA user_version").use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            })
            assertTrue(database.tableNames().contains("genesis_ultra_birth_authorization"))
            assertTrue(
                database.indexNames("genesis_ultra_birth_authorization").containsAll(
                    setOf(
                        "index_genesis_ultra_birth_authorization_candidateDigest",
                        "index_genesis_ultra_birth_authorization_consentDigest",
                        "index_genesis_ultra_birth_authorization_authorizationDigest"
                    )
                )
            )
            assertEquals(
                0,
                database.query("SELECT COUNT(*) FROM genesis_ultra_birth_authorization").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    cursor.getInt(0)
                }
            )
        } finally {
            database.close()
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
        const val TEST_DATABASE = "morimil-v12-v13-migration-test"
    }
}
