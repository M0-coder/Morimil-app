package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestBootReadinessContractTest {
    @Test
    fun bootstrapRestReadinessUsesTheExistingCanonicalPlanningBoundary() {
        val repository = production("data/repository/RestCycleRepository.kt")
        val resolver = production("data/repository/RestCycleBootstrapReadiness.kt")
        val readiness = repository
            .substringAfter("internal suspend fun isBootstrapReady")
            .substringBefore("suspend fun runLocalRestCycleIfDue")

        assertTrue(readiness.contains("readRestCyclePlanningInput"))
        assertTrue(readiness.contains("RestCycleBootstrapReadiness.resolve"))
        assertTrue(readiness.contains("requireCanonicalPlanning(identity, planning)"))
        assertTrue(resolver.contains("CanonicalReadDisposition.NOT_READY"))
        assertTrue(resolver.contains("CanonicalRestCycleReadException"))
        assertFalse(readiness.contains("recoverBeforeMutation"))
        assertFalse(readiness.contains("protocol.execute"))
        assertFalse(readiness.contains("migrationStore"))
        assertFalse(readiness.contains("repairStore"))
        assertFalse(resolver.contains("recoverBeforeMutation"))
        assertFalse(resolver.contains("protocol.execute"))
    }

    @Test
    fun runtimeBootstrapComposesIndependentReadOnlyRestAndRecallProbes() {
        val gate = productionRoot("MorimilAppContainerRuntimeGate.kt")
        val bootstrap = production("runtime/GenesisUltraRuntimeBootstrapCoordinator.kt")

        assertTrue(gate.contains("probeRestCycleReady = { identity ->"))
        assertTrue(gate.contains("restCycleRepository.isBootstrapReady(identity)"))
        assertTrue(gate.contains("probeRecallReady = { identity ->"))
        assertTrue(gate.contains("recallScheduleRepository.isBootstrapReady(identity)"))
        assertTrue(bootstrap.contains("val restCycleState = if (probeRestCycleReady(identity))"))
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
