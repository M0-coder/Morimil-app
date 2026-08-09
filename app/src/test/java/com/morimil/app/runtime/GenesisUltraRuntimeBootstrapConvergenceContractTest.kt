package com.morimil.app.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraRuntimeBootstrapConvergenceContractTest {
    @Test
    fun convergedReadOnlyLegacyMetadataIsAcceptedByTheRuntimeReport() {
        val report = report(
            legacyMemoryConverged = true,
            legacyCounts = GenesisUltraRuntimeLegacyCounts(
                localIdentityCount = 1,
                genesisCoreCount = 1
            )
        )

        assertTrue(report.legacyMemoryConverged)
        assertEquals(1, report.legacyCounts.localIdentityCount)
        assertEquals(1, report.legacyCounts.genesisCoreCount)
        assertEquals(
            GenesisUltraRuntimeHealthState.WAITING_FOR_DEPENDENCIES,
            report.healthState
        )
    }

    @Test
    fun unconvergedLegacyMetadataIsRejected() {
        val failure = runCatching {
            report(
                legacyMemoryConverged = false,
                legacyCounts = GenesisUltraRuntimeLegacyCounts(
                    localIdentityCount = 1,
                    genesisCoreCount = 1
                )
            )
        }.exceptionOrNull()

        assertEquals("runtime_bootstrap_legacy_rows_not_converged", failure?.message)
    }

    private fun report(
        legacyMemoryConverged: Boolean,
        legacyCounts: GenesisUltraRuntimeLegacyCounts
    ): GenesisUltraRuntimeBootstrapReport {
        val restCycleState = GenesisUltraRuntimeSubsystemState.WAITING_FOR_CANONICAL_MEMORY_ADAPTER
        val recallState = GenesisUltraRuntimeSubsystemState.WAITING_FOR_CANONICAL_MEMORY_ADAPTER
        return GenesisUltraRuntimeBootstrapReport(
            instanceId = INSTANCE_ID,
            companionName = "Morimil",
            workspaceId = INSTANCE_ID,
            projectId = "morimil_app:$INSTANCE_ID",
            agentProfileCount = 7,
            orchestratorDeviceCount = 4,
            canonicalMemoryEventCount = 3,
            legacyMemoryConverged = legacyMemoryConverged,
            healthState = GenesisUltraRuntimeHealthConvergence.evaluate(
                legacyMemoryConverged = legacyMemoryConverged,
                restCycleState = restCycleState,
                recallState = recallState
            ),
            restCycleState = restCycleState,
            recallState = recallState,
            legacyCounts = legacyCounts
        )
    }

    private companion object {
        const val INSTANCE_ID = "instance_test"
    }
}
