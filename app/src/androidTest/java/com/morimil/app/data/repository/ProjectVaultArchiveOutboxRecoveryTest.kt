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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectVaultArchiveOutboxRecoveryTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private var database: MemoryOrganDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
        database = null
    }

    @Test
    fun deathAfterArchiveEventPreservesActiveVaultUntilRecovery() = runBlocking {
        val db = openDatabase()
        val commitPort = CrashAfterCanonicalCommitPort(crashOnce = false)
        val repository = ProjectVaultRepository(db, commitPort)
        val createdAt = 1_700_000_000_000L
        val vaultId = repository.createProjectVaultFromIntent(
            displayName = "Morimil",
            mission = "Construir con evidencia.",
            nowMillis = createdAt
        )
        assertEquals(
            ProjectVaultRepository.STATUS_ACTIVE,
            db.memoryOrganDao().loadProjectVault(vaultId)?.status
        )

        commitPort.armCrash()
        val failure = runCatching {
            repository.archiveProjectVault(
                vaultId = vaultId,
                reason = "archivo verificado",
                nowMillis = createdAt + 20_000L
            )
        }.exceptionOrNull()

        assertEquals("simulated_process_death_after_canonical_commit", failure?.message)
        assertEquals(
            ProjectVaultRepository.STATUS_ACTIVE,
            db.memoryOrganDao().loadProjectVault(vaultId)?.status
        )
        assertEquals(1, db.projectVaultOutboxDao().countPending())

        val restartedProcess = ProjectVaultRepository(db, commitPort)
        val report = restartedProcess.recoverPendingOperations()
        val archived = db.memoryOrganDao().loadProjectVault(vaultId)
        val operations = db.projectVaultOutboxDao().loadAll()
        val archiveOperation = operations.single { operation ->
            operation.operationType == ProjectVaultOutboxEntity.OPERATION_ARCHIVE
        }

        assertEquals(1, report.recoveredCount)
        assertEquals(0, report.failedCount)
        assertEquals(0, report.pendingCount)
        assertEquals(0, report.blockedCount)
        assertEquals(ProjectVaultRepository.STATUS_ARCHIVED, archived?.status)
        assertEquals(ProjectVaultOutboxEntity.STATUS_COMMITTED, archiveOperation.status)
        assertEquals(2, commitPort.distinctCanonicalEventCount)
        assertTrue(commitPort.reusedExistingEvent)
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
        var reusedExistingEvent: Boolean = false
            private set

        val distinctCanonicalEventCount: Int
            get() = events.size

        override suspend fun ensureCommitted(
            command: ProjectVaultCommitCommand
        ): ProjectVaultCommitReceipt {
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
    }
}
