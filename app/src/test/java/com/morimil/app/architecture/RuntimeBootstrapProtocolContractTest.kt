package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeBootstrapProtocolContractTest {
    @Test
    fun bootstrapCoordinatorUsesDurableProtocolInsteadOfDirectCrossDatabaseWrites() {
        val source = productionFile(
            "com/morimil/app/runtime/GenesisUltraRuntimeBootstrapCoordinator.kt"
        ).readText()
        val executable = executableSource(source)

        assertTrue(executable.contains("RuntimeBootstrapOperationFactory.initialize"))
        assertTrue(executable.contains("recoverBeforeMutation"))
        assertTrue(executable.contains("RuntimeBootstrapProtocolTypes.OWNER_TYPE"))
        assertTrue(executable.contains("CrossDatabaseOperationStatus.COMMITTED"))
        assertFalse(executable.contains("upsertWorkspace("))
        assertFalse(executable.contains("upsertProject("))
        assertFalse(executable.contains("insertAgentProfiles("))
        assertFalse(executable.contains("insertOrchestratorDevices("))
        assertFalse(executable.contains("insertLocalIdentity("))
        assertFalse(executable.contains("insertGenesisCore("))
        assertFalse(executable.contains("insertMemoryEvent("))
    }

    @Test
    fun bootRegistryIsClosedAndCompositionUsesExactCanonicalAdapter() {
        val registry = productionFile(
            "com/morimil/app/data/repository/CrossDatabaseProtocolRegistry.kt"
        ).readText()
        val composition = productionFile(
            "com/morimil/app/MorimilAppContainerRuntimeBootstrapProtocol.kt"
        ).readText()

        assertTrue(registry.contains("object RuntimeBootstrapProtocolTypes"))
        assertTrue(registry.contains("runtime_bootstrap.initialize"))
        assertTrue(registry.contains("runtime.bootstrap_initialized"))
        assertTrue(composition.contains("CanonicalRuntimeBootstrapCommitPort"))
        assertTrue(composition.contains("RuntimeBootstrapProtocolFinalizer"))
        assertTrue(composition.contains("RuntimeBootstrapProtocolTypes.REGISTRY"))
    }

    @Test
    fun bootstrapPayloadPreservesNoOwnershipAndFutureBodySuccessionBoundary() {
        val source = productionFile(
            "com/morimil/app/data/repository/RuntimeBootstrapProtocol.kt"
        ).readText()

        assertTrue(source.contains("ownershipConferred"))
        assertTrue(source.contains("runtime_bootstrap_ownership_conferred"))
        assertTrue(source.contains("custodian_witness"))
        assertTrue(source.contains("guardian_without_ownership"))
        assertTrue(source.contains("successor_body_rebootstrap_allowed"))
        assertTrue(source.contains("bootstrap:"))
        assertTrue(source.contains("activeBody.bodyId"))
        assertTrue(source.contains("activeBody.keyEpochId"))
        assertTrue(source.contains("memory=canonical"))
        assertFalse(source.contains("local_instance_identity"))
        assertFalse(source.contains("genesis_core"))
        assertFalse(source.contains("memory_events"))
    }

    @Test
    fun sagaPreparationTargetsMemoryDatabaseBeforeOwnerAtomicFinalization() {
        val source = productionFile(
            "com/morimil/app/data/repository/RuntimeBootstrapProtocolFinalizer.kt"
        ).readText()

        val prepare = source.indexOf("override suspend fun prepareOutsideTransaction")
        val memoryProjection = source.indexOf("memoryStore.ensureProjection", prepare)
        val finalize = source.indexOf("override suspend fun finalizePreparedInsideTransaction")
        val agentProjection = source.indexOf("organStore.seedAgentProfilesIfEmpty", finalize)
        val deviceProjection = source.indexOf("organStore.seedOrchestratorDevicesIfEmpty", finalize)

        assertTrue(prepare >= 0)
        assertTrue(memoryProjection > prepare)
        assertTrue(finalize > memoryProjection)
        assertTrue(agentProjection > finalize)
        assertTrue(deviceProjection > agentProjection)
        assertTrue(source.contains("FINALIZATION_PREPARATION_CONFLICT"))
        assertTrue(source.contains("PENDING_LOCAL_COMMIT"))
    }

    @Test
    fun bootPreservesNonemptyOrchestrationTablesForSeparateOrch001Convergence() {
        val source = productionFile(
            "com/morimil/app/data/repository/RuntimeBootstrapProtocolFinalizer.kt"
        ).readText()

        assertTrue(source.contains("seedAgentProfilesIfEmpty"))
        assertTrue(source.contains("seedOrchestratorDevicesIfEmpty"))
        assertTrue(source.contains("if (before > 0) return before"))
        assertTrue(source.contains("ORCH-001 owns convergence"))
        assertFalse(source.contains("delete FROM agent_profiles", ignoreCase = true))
        assertFalse(source.contains("delete FROM orchestrator_devices", ignoreCase = true))
    }

    @Test
    fun startupKeepsLegacyConvergenceBeforeDurableBootstrap() {
        val source = productionFile(
            "com/morimil/app/MorimilAppContainerRuntimeGate.kt"
        ).readText()

        val convergence = source.indexOf("convergence.converge(identity)")
        val bootstrap = source.indexOf("bootstrap.bootstrap(identity)")

        assertTrue(convergence >= 0)
        assertTrue(bootstrap > convergence)
        assertTrue(source.contains("protocol = runtimeBootstrapProtocolCoordinator"))
    }

    private fun productionFile(relativePath: String): File {
        return File(productionRoot(), relativePath).also { file ->
            require(file.isFile) { "Production source not found: $relativePath" }
        }
    }

    private fun productionRoot(): File {
        return sequenceOf(
            File("src/main/java"),
            File("app/src/main/java")
        ).firstOrNull(File::isDirectory)
            ?: error("Production source root not found")
    }

    private fun executableSource(source: String): String {
        return source
            .replace(Regex("\"\"\"[\\s\\S]*?\"\"\""), "")
            .replace(Regex("\"(?:\\\\.|[^\"\\\\])*\""), "")
            .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
            .replace(Regex("//.*"), "")
    }
}
