package com.morimil.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectVaultOutboxEntityTest {
    @Test
    fun committedStateRequiresCanonicalEvidence() {
        val failure = runCatching {
            pending().copy(
                status = ProjectVaultOutboxEntity.STATUS_COMMITTED,
                canonicalEventHash = null,
                canonicalSequence = null,
                committedAtMillis = null
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals("project_vault_outbox_committed_hash_missing", failure?.message)
    }

    @Test
    fun pendingStateCarriesNoFalseCanonicalReceipt() {
        val operation = pending()

        assertEquals(ProjectVaultOutboxEntity.STATUS_PENDING, operation.status)
        assertEquals(null, operation.canonicalEventHash)
        assertEquals(null, operation.canonicalSequence)
        assertEquals(null, operation.committedAtMillis)
    }

    private fun pending(): ProjectVaultOutboxEntity {
        return ProjectVaultOutboxEntity(
            operationId = "project_vault_create_${DIGEST}",
            vaultId = "vault-1",
            operationType = ProjectVaultOutboxEntity.OPERATION_CREATE,
            eventId = "project_vault_event_${DIGEST}",
            eventType = "project.vault_created",
            eventBody = "Boveda creada",
            evidenceJson = "{}",
            payloadJson = "{}",
            payloadDigest = DIGEST,
            status = ProjectVaultOutboxEntity.STATUS_PENDING,
            attemptCount = 0,
            lastError = null,
            canonicalEventHash = null,
            canonicalSequence = null,
            occurredAtMillis = 1_000L,
            createdAtMillis = 1_000L,
            updatedAtMillis = 1_000L,
            committedAtMillis = null
        )
    }

    private companion object {
        const val DIGEST =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
