package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecallCanonicalDerivedRebuildContractTest {
    @Test
    fun recallSeedUsesCanonicalReadBoundaryWithoutLegacyIdentityOrMemoryReads() {
        val source = repositoryFile(
            "app/src/main/java/com/morimil/app/data/repository/RecallScheduleRepository.kt"
        ).readText()

        listOf(
            "CanonicalConsumerReadPort",
            "readRecallCandidates",
            "canonical_recall_schedule_v1",
            "canonical_memory_event",
            "organDatabase.withTransaction",
            "sourceId = \"recall:$id\"",
            "batch.instanceId",
            "batch.snapshot.birthRootEventHash"
        ).forEach { token ->
            assertTrue("Missing canonical recall token $token", source.contains(token))
        }

        listOf(
            "loadGenesisCore(",
            "loadLocalIdentity(",
            "loadMemoryContext(",
            "local_instance_pending",
            "MemoryEventEntity",
            "memoryDatabase.memoryDao()"
        ).forEach { token ->
            assertFalse("Legacy recall dependency returned: $token", source.contains(token))
        }
    }

    @Test
    fun applicationContainerInjectsSharedCanonicalConsumerBoundary() {
        val source = repositoryFile(
            "app/src/main/java/com/morimil/app/MorimilAppContainer.kt"
        ).readText()
        val recallBlock = source.substringAfter("val recallScheduleRepository")
            .substringBefore("val projectVaultRepository")

        assertTrue(recallBlock.contains("RecallScheduleRepository("))
        assertTrue(recallBlock.contains("canonicalReadPort = canonicalConsumerReadPort"))
        assertFalse(recallBlock.contains("memoryDatabase = memoryDatabase"))
    }

    @Test
    fun legacyReconciliationCannotInvalidateCanonicalRecallProjection() {
        val source = repositoryFile(
            "app/src/main/java/com/morimil/app/core/memory/MemoryOrganReconciliation.kt"
        ).readText()

        assertTrue(source.contains("recall.source != CANONICAL_MEMORY_SOURCE"))
        assertTrue(source.contains("CANONICAL_MEMORY_SOURCE = \"canonical_memory_event\""))
    }

    private fun repositoryFile(relativePath: String): File =
        sequenceOf(File(relativePath), File("../$relativePath")).firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")
}
