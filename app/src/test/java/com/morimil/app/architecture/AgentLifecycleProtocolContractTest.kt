package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLifecycleProtocolContractTest {
    @Test
    fun lifecycleOwnerHasNoLegacyMemoryAuthorityOrClockDerivedIds() {
        val repository = productionFile(
            "com/morimil/app/data/repository/AgentInstanceLifecycleRepository.kt"
        ).readText()
        val protocol = productionFile(
            "com/morimil/app/data/repository/AgentLifecycleProtocol.kt"
        ).readText()

        listOf(
            "MemoryRepository",
            "recordSystemMemoryEvent",
            "memory_events",
            "buildAgentInstanceId",
            "buildProjectTaskId",
            "StableIdDigest.shortSha256Hex(\n                namespace = \"agent_instance\""
        ).forEach { forbidden ->
            assertFalse("Legacy lifecycle boundary leaked: $forbidden", repository.contains(forbidden))
        }
        assertFalse(protocol.contains("nowMillis.toString()"))
        assertFalse(protocol.contains("createdAtMillis.toString()"))
        assertTrue(protocol.contains("morimil.agent_lifecycle.agent_instance.v1"))
        assertTrue(protocol.contains("morimil.agent_lifecycle.project_task.v1"))
        assertTrue(protocol.contains("writerEpoch"))
    }

    @Test
    fun allInventoryOperationsAreClosedInTheAgentRegistry() {
        val registry = productionFile(
            "com/morimil/app/data/repository/CrossDatabaseProtocolRegistry.kt"
        ).readText()
        val protocol = productionFile(
            "com/morimil/app/data/repository/AgentLifecycleProtocol.kt"
        ).readText()

        listOf(
            "agent_lifecycle.create_agent",
            "agent_lifecycle.assign_task",
            "agent_lifecycle.submit_result",
            "agent_lifecycle.evaluate_agent",
            "agent_lifecycle.retire_agent",
            "agent_lifecycle.promote_agent",
            "agent_lifecycle.quarantine_agent"
        ).forEach { operation -> assertTrue("Missing AGENT operation $operation", registry.contains(operation)) }
        listOf(
            "AGENT_001_PAYLOAD",
            "AGENT_002_PAYLOAD",
            "AGENT_003_PAYLOAD",
            "AGENT_004_PAYLOAD",
            "AGENT_005_PAYLOAD",
            "AGENT_006_PAYLOAD"
        ).forEach { schema -> assertTrue("Missing AGENT schema $schema", protocol.contains(schema)) }
    }

    @Test
    fun lifecycleUsesGenesisIdentityOwnerScopedRecoveryAndCanonicalReceiptBeforeLocalState() {
        val repository = productionFile(
            "com/morimil/app/data/repository/AgentInstanceLifecycleRepository.kt"
        ).readText()
        val composition = productionFile(
            "com/morimil/app/MorimilAppContainerCognitiveMigrationProtocol.kt"
        ).readText()
        val startup = productionFile(
            "com/morimil/app/MorimilAppContainerRuntimeGate.kt"
        ).readText()

        assertTrue(repository.contains("GenesisUltraRuntimeIdentityRepository"))
        assertTrue(repository.contains("recoverBeforeMutation"))
        assertTrue(repository.contains("ownerType = AgentLifecycleProtocolTypes.OWNER_TYPE"))
        assertTrue(repository.contains("protocol.execute(identity, command)"))
        assertTrue(composition.contains("CanonicalAgentLifecycleCommitPort"))
        assertTrue(composition.contains("AgentLifecycleProtocolFinalizer"))
        assertTrue(composition.contains("protocolRegistry = AgentLifecycleProtocolTypes.REGISTRY"))

        val cog = startup.indexOf("cognitiveRecovery.recoverAtStartup")
        val orch = startup.indexOf("orchestrationRecovery.recoverAtStartup")
        val agent = startup.indexOf("agentLifecycleRecovery.recoverAtStartup")
        val convergence = startup.indexOf("convergence.converge")
        assertTrue(cog >= 0 && orch > cog && agent > orch && convergence > agent)
    }

    @Test
    fun resultSubmissionRequiresCanonicalOrchestrationApproval() {
        val repository = productionFile(
            "com/morimil/app/data/repository/AgentInstanceLifecycleRepository.kt"
        ).readText()
        val finalizer = productionFile(
            "com/morimil/app/data/repository/AgentLifecycleProtocol.kt"
        ).readText()

        assertTrue(repository.contains("task.status != AgentCapabilityPolicy.STATUS_APPROVED"))
        assertTrue(repository.contains("task.approvalId == null"))
        assertTrue(finalizer.contains("task.status == AgentCapabilityPolicy.STATUS_APPROVED"))
        assertTrue(finalizer.contains("task.approvalId != null"))
    }

    @Test
    fun quarantineAndReplacementAreOneFinalizationWithoutRecursiveCreate() {
        val repository = productionFile(
            "com/morimil/app/data/repository/AgentInstanceLifecycleRepository.kt"
        ).readText()
        val protocol = productionFile(
            "com/morimil/app/data/repository/AgentLifecycleProtocol.kt"
        ).readText()

        val quarantineSection = repository.substringAfter("suspend fun quarantineAgent")
            .substringBefore("suspend fun promoteAgent")
        assertFalse(quarantineSection.contains("createAgentForVault("))
        assertTrue(protocol.contains("replacement_agent_id"))
        assertTrue(protocol.contains("store.insertAgentInstance(replacement)"))
        assertTrue(protocol.contains("STATUS_QUARANTINED"))
    }

    private fun productionFile(relativePath: String): File =
        repositoryFile("app/src/main/java/$relativePath")

    private fun repositoryFile(relativePath: String): File {
        return sequenceOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")
    }
}
