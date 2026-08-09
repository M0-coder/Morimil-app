package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestBootReadinessContractTest {
    @Test
    fun bootstrapRestReadinessUsesTheExistingCanonicalPlanningBoundary() {
        val repository = production("data/repository/RestCycleRepository.kt")
        val readiness = repository
            .substringAfter("internal suspend fun isBootstrapReady")
            .substringBefore("suspend fun runLocalRestCycleIfDue")

        assertTrue(readiness.contains("readRestCyclePlanningInput"))
        assertTrue(readiness.contains("CanonicalReadDisposition.NOT_READY"))
        assertTrue(readiness.contains("requireCanonicalPlanning(identity, planning)"))
        assertTrue(readiness.contains("CanonicalRestCycleReadException"))
        assertFalse(readiness.contains("recoverBeforeMutation"))
        assertFalse(readiness.contains("protocol.execute"))
        assertFalse(readiness.contains("migrationStore"))
        assertFalse(readiness.contains("repairStore"))
    }

    @Test
    fun runtimeBootstrapComposesTheRestProbeWithoutPromotingRecall() {
        val gate = productionRoot("MorimilAppContainerRuntimeGate.kt")
        val bootstrap = production("runtime/GenesisUltraRuntimeBootstrapCoordinator.kt")

        assertTrue(gate.contains("probeRestCycleReady = { identity ->"))
        assertTrue(gate.contains("restCycleRepository.isBootstrapReady(identity)"))
        assertTrue(bootstrap.contains("val restCycleState = if (probeRestCycleReady(identity))"))
        assertTrue(
            bootstrap.contains(
                "val recallState =\n            GenesisUltraRuntimeSubsystemState.WAITING_FOR_CANONICAL_MEMORY_ADAPTER"
            )
        )
        assertFalse(gate.contains("recallScheduleRepository.isBootstrapReady"))
    }

    private fun production(relative: String): String =
        repositoryFile("app/src/main/java/com/morimil/app/$relative").readText()

    private fun productionRoot(fileName: String): String =
        repositoryFile("app/src/main/java/com/morimil/app/$fileName").readText()

    private fun repositoryFile(relativePath: String): File =
        sequenceOf(File(relativePath), File("../$relativePath")).firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")
}
