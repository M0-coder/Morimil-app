package com.morimil.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.ProjectVaultOutboxEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectVaultOutboxRecoveryTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private var database: MemoryOrganDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
        database = null
    }

    @Test
    fun deathAfterCanonicalAppendLeavesCreateInvisibleAndRecoveryDoesNotDuplicate() = runBlocking {
        val db = openDatabase()
        val commitPort = CrashAfterCanonicalCommitPort()
        val firstProcess = ProjectVaultRepository(db, commitPort)
        val now = 1_700_000_000_000L
        val expectedVaultId = ProjectVaultRepository.buildVaultId("Morimil", now)

        val failure = runCatching {
            firstProcess.createProjectVaultFromIntent(
                displayName = "Morimil",
                mission = "Construir la instancia libre.",
                nowMillis = now
            )
        }.exceptionOrNull()

        assertEquals("simulated_process_death_after_canonical_commit", failure?.message)
        assertNull(db.memoryOrganDao().loadProjectVault(expectedVaultId))
        assertEquals(1, db.projectVaultOutboxDao().countPending())
        assertEquals(1, commitPort.distinctCanonicalEventCount)
        assertEquals(1, commitPort.appendAttemptCount)

        val restartedProcess = ProjectVaultRepository(db, commitPort)
        val report = restartedProcess.recoverPendingOperations()
        val recoveredVault = db.memoryOrganDao().loadProjectVault(expectedVaultId)
        val operation = db.projectVaultOutboxDao().loadAll().single()

        assertEquals(1, report.recoveredCount)
        assertEquals(0, report.failedCount)
        assertEquals(0, report.pendingCount)
        assertEquals(0, report.blockedCount)
        assertEquals(1, report.committedCount)
        assertEquals("Morimil", recoveredVault?.displayName)
        assertEquals(ProjectVaultRepository.STATUS_ACTIVE, recoveredVault?.status)
        assertEquals(ProjectVaultOutboxEntity.STATUS_COMMITTED, operation.status)
        assertEquals(commitPort.receiptFor(operation.eventId)?.eventHash, operation.canonicalEventHash)
        assertEquals(1, commitPort.distinctCanonicalEventCount)
        assertEquals(2, commitPort.appendAttemptCount)
        assertTrue(commitPort.reusedExistingEvent)
    }

    @Test
    fun deathAfterCompletionEventPreservesPreviousVisibleStateUntilRecovery() = runBlocking {
        val db = openDatabase()
        val commitPort = CrashAfterCanonicalCommitPort(crashOnce = false)
        val repository = ProjectVaultRepository(db, commitPort)
        val createdAt = 1_700_000_000_000L
        val vaultId = repository.createProjectVaultFromIntent(
            displayName = "Morimil",
            mission = "Construir la instancia libre.",
            nowMillis = createdAt
        )
        assertEquals(ProjectVaultRepository.STATUS_ACTIVE, db.memoryOrganDao().loadProjectVault(vaultId)?.status)

        commitPort.armCrash()
        val failure = runCatching {
            repository.completeProjectVault(
                vaultId = vaultId,
                finalSummary = "Transición verificada.",
                nowMillis = createdAt + 10_000L
            )
        }.exceptionOrNull()

        assertEquals("simulated_process_death_after_canonical_commit", failure?.message)
        val beforeRecovery = db.memoryOrganDao().loadProjectVault(vaultId)
        assertEquals(ProjectVaultRepository.STATUS_ACTIVE, beforeRecovery?.status)
        assertFalse(beforeRecovery?.progressPercent == 100)
        assertEquals(1, db.projectVaultOutboxDao().countPending())

        val restartedProcess = ProjectVaultRepository(db, commitPort)
        val report = restartedProcess.recoverPendingOperations()
        val afterRecovery = db.memoryOrganDao().loadProjectVault(vaultId)

        assertEquals(1, report.recoveredCount)
        assertEquals(ProjectVaultRepository.STATUS_COMPLETED, afterRecovery?.status)
        assertEquals(100, afterRecovery?.progressPercent)
        assertEquals("Transición verificada.", afterRecovery?.roadmapSummary)
        assertEquals(2, commitPort.distinctCanonicalEventCount)
    }

    private fun openDatabase(): MemoryOrganDatabase {
        return Room.inMemoryDatabaseBuilder(context, MemoryOrganDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }
    }

    private class CrashAfterCanonicalCommitPort(
        crashOnce: Boolean = true
    ) : ProjectVaultCommitPort {
        private val events = linkedMapOf<String, ProjectVaultCommitReceipt>()
        private var shouldCrash = crashOnce
        var appendAttemptCount: Int = 0
            private set
        var reusedExistingEvent: Boolean = false
            private set

        val distinctCanonicalEventCount: Int
            get() = events.size

        override suspend fun ensureCommitted(
            command: ProjectVaultCommitCommand
        ): ProjectVaultCommitReceipt {
            appendAttemptCount += 1
            val existing = events[command.eventId]
            if (existing != null) {
                reusedExistingEvent = true
                return existing.copy(reusedExistingEvent = true)
            }
            val receipt = ProjectVaultCommitReceipt(
                eventId = command.eventId,
                eventHash = "sha256:${command.payloadDigest}",
                sequence = events.size.toLong() + 2L,
                reusedExistingEvent = false
            )
            events[command.eventId] = receipt
            if (shouldCrash) {
                shouldCrash = false
                error("simulated_process_death_after_canonical_commit")
            }
            return receipt
        }

        fun armCrash() {
            shouldCrash = true
        }

        fun receiptFor(eventId: String): ProjectVaultCommitReceipt? = events[eventId]
    }
}
