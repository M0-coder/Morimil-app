package com.morimil.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryOrganDatabaseEncryptionTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val databaseFile: File
        get() = context.getDatabasePath(TEST_DATABASE_NAME)

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        deleteTestFiles()
    }

    @After
    fun cleanUp() {
        deleteTestFiles()
    }

    @Test
    fun migratesRealRoomSchemaAndRejectsMissingOrWrongKeys() {
        val plaintext = Room.databaseBuilder(
            context,
            MemoryOrganDatabase::class.java,
            TEST_DATABASE_NAME
        )
            .addMigrations(
                MemoryOrganDatabase.MIGRATION_1_2,
                MemoryOrganDatabase.MIGRATION_2_3,
                MemoryOrganDatabase.MIGRATION_3_4,
                MemoryOrganDatabase.MIGRATION_4_5,
                MemoryOrganDatabase.MIGRATION_5_6,
                MemoryOrganDatabase.MIGRATION_6_7
            )
            .build()
        plaintext.openHelper.writableDatabase.query(
            "SELECT count(*) FROM sqlite_schema"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.getLong(0) > 0L)
        }
        plaintext.close()
        assertTrue(hasPlaintextHeader(databaseFile))

        val encrypted = MemoryOrganDatabaseEncryption.openWithPassphrase(
            context = context,
            databaseName = TEST_DATABASE_NAME,
            passphrase = PASSPHRASE.copyOf()
        )
        try {
            encrypted.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0).lowercase())
            }
            encrypted.openHelper.writableDatabase.query(
                "SELECT count(*) FROM sqlite_schema WHERE name = 'knowledge_capsules'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
            }
        } finally {
            encrypted.close()
        }

        assertFalse(hasPlaintextHeader(databaseFile))
        assertFalse(canOpen(databaseFile, ByteArray(0)))
        assertFalse(canOpen(databaseFile, WRONG_PASSPHRASE))
        assertTrue(canOpen(databaseFile, PASSPHRASE))
        assertNoMigrationArtifacts()
    }

    private fun assertNoMigrationArtifacts() {
        migrationArtifacts().forEach { artifact ->
            assertFalse("Migration artifact remained: ${artifact.name}", artifact.exists())
        }
    }

    private fun deleteTestFiles() {
        CipherDatabase.deleteDatabase(databaseFile)
        migrationArtifacts().forEach { artifact ->
            CipherDatabase.deleteDatabase(artifact)
        }
    }

    private fun migrationArtifacts(): List<File> {
        val parent = requireNotNull(databaseFile.parentFile)
        return listOf(
            File(parent, "${databaseFile.name}.encrypted.tmp"),
            File(parent, "${databaseFile.name}.plaintext.backup")
        )
    }

    private fun hasPlaintextHeader(file: File): Boolean {
        if (!file.isFile || file.length() < SQLITE_HEADER.size) return false
        val header = ByteArray(SQLITE_HEADER.size)
        file.inputStream().use { input ->
            assertEquals(header.size, input.read(header))
        }
        return header.contentEquals(SQLITE_HEADER)
    }

    private fun canOpen(file: File, passphrase: ByteArray): Boolean {
        var database: CipherDatabase? = null
        return try {
            database = CipherDatabase.openDatabase(
                file.absolutePath,
                passphrase,
                null,
                CipherDatabase.OPEN_READWRITE,
                null
            )
            database.rawQuery(
                "SELECT count(*) FROM sqlite_schema",
                emptyArray<String>()
            ).use { cursor -> cursor.moveToFirst() }
        } catch (_: Throwable) {
            false
        } finally {
            database?.close()
        }
    }

    private companion object {
        const val TEST_DATABASE_NAME = "morimil_memory_organs_encryption_test.db"
        val PASSPHRASE =
            "instrumented-morimil-memory-organs-key-v1".toByteArray(Charsets.UTF_8)
        val WRONG_PASSPHRASE =
            "instrumented-wrong-memory-organs-key".toByteArray(Charsets.UTF_8)
        val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
