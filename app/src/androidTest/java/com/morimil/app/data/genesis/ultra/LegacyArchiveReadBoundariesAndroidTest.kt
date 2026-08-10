package com.morimil.app.data.genesis.ultra

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.morimil.app.data.local.MorimilDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyArchiveReadBoundariesAndroidTest {
    private lateinit var database: MorimilDatabase

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MorimilDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun productionArchivePortReadsFrozenLegacyChainThroughDedicatedDao() = runBlocking {
        // Test-fixture seeding only. Production exposes no legacy writer capability.
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO memory_events (
                genesisCoreId,
                previousEventHash,
                signatureAlgorithm,
                eventSignature,
                eventType,
                actor,
                body,
                importance,
                createdAtMillis
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                "legacy_core_fixture",
                null,
                null,
                null,
                "legacy.fixture",
                "system",
                "frozen legacy fixture",
                50,
                1_000L
            )
        )

        val archive = LegacyMemoryArchiveReadPort.production(database)
        val events = archive.loadAuditChain()

        assertEquals(1, archive.countEvents())
        assertEquals(1, events.size)
        assertEquals("legacy_core_fixture", events.single().genesisCoreId)
        assertEquals("legacy.fixture", events.single().eventType)
        assertEquals("frozen legacy fixture", events.single().body)
        assertTrue(events.single().eventHash.startsWith("sha256:"))
    }
}
