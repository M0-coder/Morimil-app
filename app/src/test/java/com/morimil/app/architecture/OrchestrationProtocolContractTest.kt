package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrchestrationProtocolContractTest {
    @Test
    fun orch002Through004UseCanonicalProtocolInsteadOfLegacyTwoCommitWrites() {
        val repository = production("data/repository/AgentOrchestrationRepository.kt")

        assertTrue(repository.contains("GenesisUltraRuntimeIdentityRepository"))
        assertTrue(repository.contains("recoverBeforeMutation(identity)"))
        assertTrue(repository.contains("OrchestrationOperationFactory.propose"))
        assertTrue(repository.contains("OrchestrationOperationFactory.approve"))
        assertTrue(repository.contains("OrchestrationOperationFactory.reject"))
        assertTrue(repository.contains("protocol.execute(identity, command)"))
        assertTrue(repository.contains("withTaskDecisionLock(taskId)"))

        assertFalse(repository.contains("recordSystemMemoryEvent("))
        assertFalse(repository.contains("recordDelegatedTaskDecision"))
        assertFalse(repository.contains("dao.insertDelegatedTask("))
        assertFalse(repository.contains("dao.approveDelegatedTask("))
        assertFalse(repository.contains("dao.rejectDelegatedTask("))
        assertFalse(repository.contains("buildTaskId("))
    }

    @Test
    fun decisionFinalizationUsesConditionalOwnerTransitionsAfterCanonicalReceipt() {
        val finalizer = production("data/repository/OrchestrationProtocolFinalizer.kt")
        val dao = production("data/local/MemoryOrganDao.kt")

        assertTrue(finalizer.contains("PENDING_LOCAL_COMMIT"))
        assertTrue(finalizer.contains("approveDelegatedTaskIfAwaitingApproval"))
        assertTrue(finalizer.contains("rejectDelegatedTaskIfAwaitingApproval"))
        assertTrue(finalizer.contains("canonical_event_hash"))
        assertTrue(finalizer.contains("canonical_provenance_digest"))
        assertTrue(finalizer.contains("task_identity_digest"))

        assertTrue(dao.contains("AND status = 'awaiting_approval'"))
        assertTrue(dao.contains("AND approvalId IS NULL"))
    }

    @Test
    fun orchestrationIdsAndProtocolRegistryAreClockFreeAndClosed() {
        val factory = production("data/repository/OrchestrationProtocolOperations.kt")
        val registry = production("data/repository/CrossDatabaseProtocolRegistry.kt")

        assertFalse(factory.contains("System.currentTimeMillis"))
        assertFalse(factory.contains("nowMillis"))
        assertTrue(factory.contains("morimil.orchestration.delegated_task.v1"))
        assertTrue(factory.contains("CrossDatabaseOperationIdentity.operationId"))
        assertTrue(factory.contains("CrossDatabaseOperationIdentity.eventId"))
        assertTrue(factory.contains("Normalizer.Form.NFC"))

        assertTrue(registry.contains("const val OWNER_TYPE = \"agent_orchestration\""))
        assertTrue(registry.contains("agent_orchestration.propose_delegated_task"))
        assertTrue(registry.contains("agent_orchestration.approve_delegated_task"))
        assertTrue(registry.contains("agent_orchestration.reject_delegated_task"))
    }

    @Test
    fun commonCoordinatorScopesRecoveryByOwnerBeforeMultiOwnerUse() {
        val coordinator = production("data/repository/CrossDatabaseOperationCoordinator.kt")

        assertTrue(coordinator.contains("private val protocolRegistry: CrossDatabaseProtocolRegistry"))
        assertTrue(coordinator.contains("store.loadRecoverableForOwner("))
        assertTrue(coordinator.contains("ownerType = protocolRegistry.ownerType"))
        assertTrue(coordinator.contains("store.countRecoverableForOwner("))
        assertTrue(coordinator.contains("command.ownerType != protocolRegistry.ownerType"))
    }

    @Test
    fun taskDecisionMutexRemainsValidOnlyInSingleProcessBody() {
        val repository = production("data/repository/AgentOrchestrationRepository.kt")
        val manifest = repositoryFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(repository.contains("DECISION_MUTEXES"))
        assertTrue(repository.contains("withTaskDecisionLock(taskId)"))
        assertFalse(manifest.contains("android:process"))
    }

    @Test
    fun compositionAndStartupRecoveryWireOnlyTheOrchestrationProtocol() {
        val composition = productionRoot("MorimilAppContainerCognitiveMigrationProtocol.kt")
        val container = productionRoot("MorimilAppContainer.kt")
        val runtimeGate = productionRoot("MorimilAppContainerRuntimeGate.kt")

        assertTrue(composition.contains("CanonicalOrchestrationCommitPort"))
        assertTrue(composition.contains("OrchestrationProtocolFinalizer"))
        assertTrue(composition.contains("protocolRegistry = OrchestrationProtocolTypes.REGISTRY"))
        assertTrue(container.contains("protocol = orchestrationProtocolCoordinator"))
        assertTrue(runtimeGate.contains("orchestrationRecovery.recoverAtStartup"))
        assertTrue(runtimeGate.contains("orchestration_protocol_blocked"))
        assertTrue(runtimeGate.contains("orchestration_protocol_recovery_incomplete"))
    }

    @Test
    fun orch001RemainsExplicitlyOutsideThisCandidate() {
        val repository = production("data/repository/AgentOrchestrationRepository.kt")
        val inventory = repositoryFile("docs/F3_CROSS_DATABASE_OPERATION_INVENTORY.md").readText()

        assertTrue(repository.contains("ORCH-001 remains a separate F1 convergence item"))
        assertTrue(repository.contains("memoryRepository.hasCompleteBirth()"))
        assertTrue(inventory.contains("`ORCH-001`"))
        assertTrue(inventory.contains("Open convergence/rebuild work"))
    }

    private fun production(relative: String): String =
        repositoryFile("app/src/main/java/com/morimil/app/$relative").readText()

    private fun productionRoot(fileName: String): String =
        repositoryFile("app/src/main/java/com/morimil/app/$fileName").readText()

    private fun repositoryFile(relativePath: String): File {
        return sequenceOf(File(relativePath), File("../$relativePath"))
            .firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")
    }
}
