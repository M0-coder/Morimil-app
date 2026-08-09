package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthLivingMemoryBoundaryContractTest {
    @Test
    fun localNervousSystemReadsOnlyCanonicalHealthBoundary() {
        val repository = repositoryFile(REPOSITORY_PATH).readText()

        assertTrue(repository.contains("CanonicalConsumerReadPort"))
        assertTrue(repository.contains("readHealthInput"))
        assertTrue(repository.contains("CanonicalReadResult.Ready"))
        assertTrue(repository.contains("CanonicalReadResult.Blocked"))
        assertTrue(repository.contains("CanonicalReadDisposition.NOT_READY"))
        assertTrue(repository.contains("CanonicalReadDisposition.RETRYABLE"))
        assertTrue(repository.contains("CanonicalReadDisposition.BLOCKED"))

        LEGACY_HEALTH_TOKENS.forEach { token ->
            assertFalse("Legacy Health dependency returned: $token", repository.contains(token))
        }
    }

    @Test
    fun healthHasNoMemoryWriteAuthority() {
        val repository = repositoryFile(REPOSITORY_PATH).readText()
        val model = repositoryFile(HEALTH_MODEL_PATH).readText()
        val executable = repository + "\n" + model

        WRITE_AUTHORITY_TOKENS.forEach { token ->
            assertFalse("Health write authority is forbidden: $token", executable.contains(token))
        }
        assertTrue(model.contains("memory_authority\", false"))
        assertTrue(model.contains("canonical_memory_write\", false"))
        assertTrue(model.contains("legacy_memory_event_write\", false"))
        assertTrue(model.contains("class\", \"operational_health"))
    }

    @Test
    fun coreHealthModelUsesLivingMemorySemanticsInsteadOfLegacyTableCounts() {
        val model = repositoryFile(HEALTH_MODEL_PATH).readText()

        listOf(
            "LivingMemoryReadStatus",
            "LocalLivingMemoryHealthInput",
            "canonical_memory_integrity",
            "canonical_binding",
            "canonical_memory_activity",
            "canonical_quarantine",
            "canonical_memory_read_latency"
        ).forEach { token -> assertTrue("Missing living-memory Health token $token", model.contains(token)) }

        LEGACY_MODEL_TOKENS.forEach { token ->
            assertFalse("Legacy Health model token returned: $token", model.contains(token))
        }
    }

    @Test
    fun operationalTelemetryIsDerivedAndNotCanonicalMemory() {
        val model = repositoryFile(HEALTH_MODEL_PATH).readText()

        assertTrue(model.contains("LocalHealthTelemetry"))
        assertTrue(model.contains("operationalTelemetry"))
        assertTrue(model.contains("morimil.local_nervous_system.v2"))
        assertFalse(model.contains("MemoryEventEntity"))
        assertFalse(model.contains("CanonicalLivingMemoryPort"))
    }

    private fun repositoryFile(relativePath: String): File =
        sequenceOf(File(relativePath), File("../$relativePath")).firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")

    private companion object {
        const val REPOSITORY_PATH =
            "app/src/main/java/com/morimil/app/data/repository/LocalNervousSystemRepository.kt"
        const val HEALTH_MODEL_PATH =
            "app/src/main/java/com/morimil/app/core/health/LocalNervousSystemHealth.kt"

        val LEGACY_HEALTH_TOKENS = setOf(
            "MemoryDao",
            "MemoryRepository",
            "MorimilDatabase",
            "MemoryEventEntity",
            "MemoryOrganReconciliationReport",
            "countGenesisCore(",
            "countLocalIdentity(",
            "countMemoryEvents(",
            "countLivingMemorySnapshot(",
            "loadMemoryContext(",
            "loadLatestMemoryEventByType(",
            "recordSystemMemoryEvent(",
            "fullMemoryChain",
            "memoryChainVerified"
        )

        val LEGACY_MODEL_TOKENS = setOf(
            "genesisCoreCount",
            "localIdentityCount",
            "memoryEventCount",
            "messageCount",
            "livingSnapshotCount",
            "recentContextCount",
            "memoryChainVerified",
            "organReconciliationHasIssues",
            "orphanedLinkCount",
            "orphanedRecallCount",
            "orphanedCapsuleCount",
            "migrationMissingRefCount"
        )

        val WRITE_AUTHORITY_TOKENS = setOf(
            "recordSystemMemoryEvent(",
            ".insert(",
            ".update(",
            ".delete(",
            ".upsert(",
            "withTransaction"
        )
    }
}
