package com.morimil.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.ProjectVaultOutboxEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectVaultArchiveOutboxRecoveryTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun createDeathAfterCanonicalCommitRecoversAfterDatabaseReopen() = runBlocking {
        val name = testDatabaseName("create")
        context.deleteDatabase(name)
        var db = openDatabase(name)
        try {
            val commitPort = CrashAfterCanonicalCommitPort()
            val repository = ProjectVaultRepository(db, commitPort)
            val now = 1_700_000_000_000L
            val expectedVaultId = ProjectVaultRepository.buildVaultId("Morimil", now)

            val failure = runCatching {
                repository.createProjectVaultFromIntent(
                    displayName = "Morimil",
                    mission = "Construir con evidencia.",
                    nowMillis = now
                )
            }.exceptionOrNull()

            assertEquals("simulated_process_death_after_canonical_commit", failure?.message)
            assertNull(db.memoryOrganDao().loadProjectVault(expectedVaultId))
            assertEquals(1, db.projectVaultOutboxDao().countPending())
            db.close()
            db = openDatabase(name)

            val report = ProjectVaultRepository(db, commitPort).recoverPendingOperations()
            val recovered = db.memoryOrganDao().loadProjectVault(expectedVaultId)

            assertEquals(1, report.recoveredCount)
            assertEquals(ProjectVaultRepository.STATUS_ACTIVE, recovered?.status)
            assertEquals(1, commitPort.distinctCanonicalEventCount)
            assertTrue(commitPort.reusedExistingEvent)
        } finally {
            if (db.isOpen) db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun completeDeathAfterCanonicalCommitRecoversAfterDatabaseReopen() = runBlocking {
        val name = testDatabaseName("complete")
        context.deleteDatabase(name)
        var db = openDatabase(name)
        try {
            val commitPort = CrashAfterCanonicalCommitPort(crashOnce = false)
            val repository = ProjectVaultRepository(db, commitPort)
            val createdAt = 1_700_000_000_000L
            val vaultId = repository.createProjectVaultFromIntent(
                displayName = "Morimil",
                mission = "Construir con evidencia.",
                nowMillis = createdAt
            )
            commitPort.armCrash()

            val failure = runCatching {
                repository.completeProjectVault(
                    vaultId = vaultId,
                    finalSummary = "Transición verificada.",
                    nowMillis = createdAt + 10_000L
                )
            }.exceptionOrNull()

            assertEquals("simulated_process_death_after_canonical_commit", failure?.message)
            assertEquals(ProjectVaultRepository.STATUS_ACTIVE, db.memoryOrganDao().loadProjectVault(vaultId)?.status)
            assertEquals(1, db.projectVaultOutboxDao().countPending())
            db.close()
            db = openDatabase(name)

            val report = ProjectVaultRepository(db, commitPort).recoverPendingOperations()
            val completed = db.memoryOrganDao().loadProjectVault(vaultId)

            assertEquals(1, report.recoveredCount)
            assertEquals(ProjectVaultRepository.STATUS_COMPLETED, completed?.status)
            assertEquals(100, completed?.progressPercent)
            assertEquals("Transición verificada.", completed?.roadmapSummary)
            assertTrue(commitPort.reusedExistingEvent)
        } finally {
            if (db.isOpen) db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun archiveDeathAfterCanonicalCommitRecoversAfterDatabaseReopen() = runBlocking {
        val name = testDatabaseName("archive")
        context.deleteDatabase(name)
        var db = openDatabase(name)
        try {
            val commitPort = CrashAfterCanonicalCommitPort(crashOnce = false)
            val repository = ProjectVaultRepository(db, commitPort)
            val createdAt = 1_700_000_000_000L
            val vaultId = repository.createProjectVaultFromIntent(
                displayName = "Morimil",
                mission = "Construir con evidencia.",
                nowMillis = createdAt
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
            assertEquals(ProjectVaultRepository.STATUS_ACTIVE, db.memoryOrganDao().loadProjectVault(vaultId)?.status)
            assertEquals(1, db.projectVaultOutboxDao().countPending())
            db.close()
            db = openDatabase(name)

            val report = ProjectVaultRepository(db, commitPort).recoverPendingOperations()
            val archived = db.memoryOrganDao().loadProjectVault(vaultId)
            val archiveOperation = db.projectVaultOutboxDao().loadAll().single { operation ->
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
        } finally {
            if (db.isOpen) db.close()
            context.deleteDatabase(name)
        }
    }

    private fun openDatabase(name: String): MemoryOrganDatabase =
        Room.databaseBuilder(context, MemoryOrganDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()

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

    private fun testDatabaseName(suffix: String): String =
        "project-vault-durable-reopen-$suffix.db"
}
