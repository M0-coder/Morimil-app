package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecallBootReadinessContractTest {
    @Test
    fun bootstrapRecallReadinessUsesCanonicalCandidatesWithoutProjectionMutation() {
        val repository = production("data/repository/RecallScheduleRepository.kt")
        val resolver = production("data/repository/RecallBootstrapReadiness.kt")
        val readiness = repository
            .substringAfter("internal suspend fun isBootstrapReady")
            .substringBefore("suspend fun seedFromRecentMemoryIfNeeded")

        assertTrue(readiness.contains("readRecallCandidates"))
        assertTrue(readiness.contains("RecallBootstrapReadiness.resolve"))
        assertTrue(readiness.contains("requireCanonicalBatch(batch)"))
        assertTrue(readiness.contains("RecallBootstrapReadiness.requireIdentityBinding(identity, batch)"))
        assertTrue(resolver.contains("CanonicalReadDisposition.NOT_READY"))
        assertTrue(resolver.contains("CanonicalRecallReadException"))
        assertTrue(resolver.contains("batch.writerBodyId == identity.activeBody.bodyId"))
        assertTrue(resolver.contains("batch.writerEpochId == identity.activeBody.keyEpochId"))

        assertFalse(readiness.contains("withTransaction"))
        assertFalse(readiness.contains("insertRecallSchedule"))
        assertFalse(readiness.contains("createMemoryLink"))
        assertFalse(readiness.contains("seedFromRecentMemoryIfNeeded"))
        assertFalse(resolver.contains("withTransaction"))
        assertFalse(resolver.contains("insertRecallSchedule"))
    }

    @Test
    fun runtimeGateProbesRecallButNeverSeedsItDuringStartup() {
        val gate = productionRoot("MorimilAppContainerRuntimeGate.kt")
        val bootstrap = production("runtime/GenesisUltraRuntimeBootstrapCoordinator.kt")

        assertTrue(gate.contains("probeRecallReady = { identity ->"))
        assertTrue(gate.contains("recallScheduleRepository.isBootstrapReady(identity)"))
        assertTrue(bootstrap.contains("val recallState = if (probeRecallReady(identity))"))
        assertFalse(gate.contains("seedFromRecentMemoryIfNeeded"))
    }

    private fun production(relative: String): String =
        repositoryFile("app/src/main/java/com/morimil/app/$relative").readText()

    private fun productionRoot(fileName: String): String =
        repositoryFile("app/src/main/java/com/morimil/app/$fileName").readText()

    private fun repositoryFile(relativePath: String): File =
        sequenceOf(File(relativePath), File("../$relativePath")).firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")
}
