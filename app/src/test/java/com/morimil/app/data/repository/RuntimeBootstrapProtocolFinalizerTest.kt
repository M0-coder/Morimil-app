package com.morimil.app.data.repository

import com.morimil.app.data.genesis.ultra.GenesisUltraHashProfile
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeActiveBody
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeAuthorization
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeAuthorizationState
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeDocument
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeGuardian
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimePolicy
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeVerifiedSeed
import com.morimil.app.data.local.AgentProfileEntity
import com.morimil.app.data.local.CrossDatabaseOperationEntity
import com.morimil.app.data.local.CrossDatabaseOperationStatus
import com.morimil.app.data.local.OrchestratorDeviceEntity
import com.morimil.app.data.local.ProjectStateEntity
import com.morimil.app.data.local.UserWorkspaceEntity
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeBootstrapProtocolFinalizerTest {
    @Test
    fun preparationProjectsCanonicalWorkspaceBeforeOwnerFinalization() = runBlocking {
        val memory = FakeMemoryStore()
        val organ = FakeOrganStore()
        val finalizer = RuntimeBootstrapProtocolFinalizer.testing(memory, organ)
        val command = RuntimeBootstrapOperationFactory.initialize(identity())
        val operation = operation(command)
        val receipt = receipt(command, 41)

        val preparation = finalizer.prepareOutsideTransaction(operation, receipt)

        val workspace = requireNotNull(memory.workspace)
        val project = requireNotNull(memory.project)
        assertEquals(command.instanceId, workspace.workspaceId)
        assertEquals("Morimil", workspace.displayName)
        assertTrue(workspace.genesisSource.startsWith("genesis-ultra:"))
        assertEquals("morimil_app:${command.instanceId}", project.projectId)
        assertTrue(project.status.contains("memory=canonical"))
        assertTrue(project.status.contains("boot=durable"))
        assertEquals(0, organ.agentCalls)
        assertEquals(0, organ.deviceCalls)
        assertEquals(RuntimeBootstrapProtocolSchemas.BOOT_001_PREPARATION, preparation.schema)
    }

    @Test
    fun preparedFinalizationSeedsOrganStateAndReturnsActualCounts() = runBlocking {
        val memory = FakeMemoryStore()
        val organ = FakeOrganStore()
        val finalizer = RuntimeBootstrapProtocolFinalizer.testing(memory, organ)
        val command = RuntimeBootstrapOperationFactory.initialize(identity())
        val operation = operation(command)
        val receipt = receipt(command, 42)
        val preparation = finalizer.prepareOutsideTransaction(operation, receipt)

        val result = finalizer.finalizePreparedInsideTransaction(
            operation = operation,
            receipt = receipt,
            preparation = preparation
        )

        assertEquals(1, organ.agentCalls)
        assertEquals(1, organ.deviceCalls)
        assertEquals(7, organ.lastAgents.size)
        assertEquals(4, organ.lastDevices.size)
        assertTrue(organ.lastDevices.any { device ->
            device.deviceId == command.writerBodyId &&
                device.authorizationStatus == "authorized" &&
                device.pairingState == "genesis_ultra_bound"
        })
        assertEquals(RuntimeBootstrapProtocolSchemas.BOOT_001_LOCAL_RESULT, result.schema)
        assertEquals(RuntimeBootstrapProtocolFinalizer.OWNER_STATUS, result.ownerStatus)
        assertTrue(result.json.contains("\"agent_profile_count\":7"))
        assertTrue(result.json.contains("\"orchestrator_device_count\":4"))
        assertTrue(result.json.contains(receipt.eventHash))
    }

    @Test
    fun preexistingOrganCountsArePreservedInLocalResult() = runBlocking {
        val memory = FakeMemoryStore()
        val organ = FakeOrganStore(agentCount = 3, deviceCount = 2)
        val finalizer = RuntimeBootstrapProtocolFinalizer.testing(memory, organ)
        val command = RuntimeBootstrapOperationFactory.initialize(identity())
        val operation = operation(command)
        val receipt = receipt(command, 43)
        val preparation = finalizer.prepareOutsideTransaction(operation, receipt)

        val result = finalizer.finalizePreparedInsideTransaction(
            operation,
            receipt,
            preparation
        )

        assertEquals(3, organ.agentCount)
        assertEquals(2, organ.deviceCount)
        assertTrue(result.json.contains("\"agent_profile_count\":3"))
        assertTrue(result.json.contains("\"orchestrator_device_count\":2"))
    }

    @Test
    fun missingOrMismatchedPreparationFailsClosed() = runBlocking {
        val finalizer = RuntimeBootstrapProtocolFinalizer.testing(
            FakeMemoryStore(),
            FakeOrganStore()
        )
        val command = RuntimeBootstrapOperationFactory.initialize(identity())
        val operation = operation(command)
        val receipt = receipt(command, 44)

        assertFailure(
            CrossDatabaseProtocolErrors.FINALIZATION_PREPARATION_CONFLICT,
            runCatching {
                finalizer.finalizePreparedInsideTransaction(operation, receipt, null)
            }.exceptionOrNull()
        )

        val exact = finalizer.prepareOutsideTransaction(operation, receipt)
        val wrongJson = exact.json.replace("\"workspace_id\"", "\"workspace_id_wrong\"")
        val wrong = exact.copy(
            json = wrongJson,
            digest = CrossDatabaseOperationIdentity.digestCanonicalJson(wrongJson)
        )
        assertFailure(
            CrossDatabaseProtocolErrors.FINALIZATION_PREPARATION_CONFLICT,
            runCatching {
                finalizer.finalizePreparedInsideTransaction(operation, receipt, wrong)
            }.exceptionOrNull()
        )
    }

    @Test
    fun malformedOwnerStateAndPayloadFailClosed() = runBlocking {
        val finalizer = RuntimeBootstrapProtocolFinalizer.testing(
            FakeMemoryStore(),
            FakeOrganStore()
        )
        val command = RuntimeBootstrapOperationFactory.initialize(identity())
        val receipt = receipt(command, 45)

        assertFailure(
            CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT,
            runCatching {
                finalizer.prepareOutsideTransaction(
                    operation(command).copy(status = CrossDatabaseOperationStatus.STAGED),
                    receipt
                )
            }.exceptionOrNull()
        )
        assertFailure(
            CrossDatabaseProtocolErrors.UNSUPPORTED_OPERATION_VERSION,
            runCatching {
                finalizer.prepareOutsideTransaction(
                    operation(command).copy(ownerType = "foreign_owner"),
                    receipt
                )
            }.exceptionOrNull()
        )
        assertFailure(
            CrossDatabaseProtocolErrors.UNSUPPORTED_PAYLOAD_SCHEMA,
            runCatching {
                finalizer.prepareOutsideTransaction(
                    operation(command).copy(payloadSchema = "wrong.schema"),
                    receipt
                )
            }.exceptionOrNull()
        )
    }

    private fun operation(command: CrossDatabaseStageCommand): CrossDatabaseOperationEntity {
        val receipt = receipt(command, 1)
        return CrossDatabaseOperationEntity(
            operationId = command.operationId,
            ownerType = command.ownerType,
            operationType = command.operationType,
            operationVersion = command.operationVersion,
            instanceId = command.instanceId,
            writerBodyId = command.writerBodyId,
            writerEpoch = command.writerEpoch,
            subjectId = command.subjectId,
            parentOperationId = command.parentOperationId,
            childPhase = command.childPhase,
            payloadSchema = command.payloadSchema,
            payloadJson = command.payloadJson,
            payloadDigest = command.payloadDigest,
            eventId = command.eventId,
            eventType = command.eventType,
            eventBody = command.eventBody,
            evidenceSchema = command.evidenceSchema,
            evidenceJson = command.evidenceJson,
            evidenceDigest = command.evidenceDigest,
            status = CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT,
            attemptCount = 0,
            lastErrorCode = null,
            canonicalEventHash = receipt.eventHash,
            canonicalSequence = receipt.sequence,
            canonicalProvenanceDigest = receipt.provenanceDigest,
            localResultSchema = null,
            localResultJson = null,
            localResultDigest = null,
            occurredAtMillis = 1_000,
            createdAtMillis = 1_000,
            updatedAtMillis = 1_000,
            committedAtMillis = null
        )
    }

    private fun receipt(
        command: CrossDatabaseStageCommand,
        sequence: Long
    ): CrossDatabaseCanonicalReceipt {
        return CrossDatabaseCanonicalReceipt(
            eventId = command.eventId,
            eventHash = "evsha256:" + "1".repeat(64),
            sequence = sequence,
            provenanceDigest = "sha256:" + "2".repeat(64),
            reusedExistingEvent = true
        )
    }

    private fun identity(): GenesisUltraRuntimeIdentity {
        val doctrine = document("doctrine/test.md", "doctrine", "doctrine")
        val charter = document("policy/charter.json", "freedom_charter", "{}")
        val recovery = document("policy/recovery.json", "recovery_policy", "{}")
        return GenesisUltraRuntimeIdentity(
            instanceId = "instance_test",
            companionName = "Morimil",
            bornAt = "2026-08-08T00:00:00Z",
            identityDigest = digest("identity"),
            activeBody = GenesisUltraRuntimeActiveBody(
                bodyId = "body_test",
                status = "active_writer",
                platformProfile = "android",
                publicKeyFingerprint = digest("body_key"),
                keyEpochId = "epoch_test",
                keyEpochDigest = digest("epoch"),
                registryEpoch = 1,
                registryDigest = digest("registry")
            ),
            guardian = GenesisUltraRuntimeGuardian(
                guardianId = "guardian_test",
                keyEpochId = "guardian_epoch",
                publicKeyRef = digest("guardian_key"),
                status = "active",
                role = "custodian_witness",
                anchorDigest = digest("guardian_anchor")
            ),
            seed = GenesisUltraRuntimeVerifiedSeed(
                seedId = "seed_test",
                rootHash = digest("seed"),
                protocolVersion = "genesis-ultra-v1",
                hashProfile = GenesisUltraHashProfile.FIELD_PROFILE,
                identityDigest = digest("identity"),
                doctrineDigest = doctrine.digest
            ),
            doctrine = doctrine,
            policy = GenesisUltraRuntimePolicy(
                freedomCharter = charter,
                recoveryPolicy = recovery,
                freedomCharterDigest = charter.digest,
                recoveryPolicyDigest = recovery.digest
            ),
            authorization = GenesisUltraRuntimeAuthorization(
                state = GenesisUltraRuntimeAuthorizationState.COMMITTED,
                authorizationDigest = digest("authorization"),
                candidateDigest = digest("candidate"),
                consentDigest = digest("consent"),
                authorizedAt = "2026-08-08T00:00:00Z",
                expiresAt = "2026-08-08T01:00:00Z",
                receiptDigest = digest("receipt"),
                birthStatus = "born",
                ownershipConferred = false
            )
        )
    }

    private fun document(path: String, kind: String, text: String): GenesisUltraRuntimeDocument {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        return GenesisUltraRuntimeDocument(
            relativePath = path,
            documentKind = kind,
            digest = GenesisUltraHashProfile.sha256(bytes),
            sourceBytes = bytes
        )
    }

    private fun digest(value: String): String =
        GenesisUltraHashProfile.sha256(value.toByteArray(StandardCharsets.UTF_8))

    private fun assertFailure(expectedCode: String, error: Throwable?) {
        val failure = error as CrossDatabaseProtocolFailure
        assertEquals(expectedCode, failure.stableCode)
        assertTrue(failure.permanent)
    }

    private class FakeMemoryStore : RuntimeBootstrapMemoryProjectionStore {
        var workspace: UserWorkspaceEntity? = null
        var project: ProjectStateEntity? = null
        var calls = 0

        override suspend fun ensureProjection(
            workspace: UserWorkspaceEntity,
            project: ProjectStateEntity
        ) {
            calls += 1
            this.workspace = workspace
            this.project = project
        }
    }

    private class FakeOrganStore(
        var agentCount: Int = 0,
        var deviceCount: Int = 0
    ) : RuntimeBootstrapOrganProjectionStore {
        var agentCalls = 0
        var deviceCalls = 0
        var lastAgents: List<AgentProfileEntity> = emptyList()
        var lastDevices: List<OrchestratorDeviceEntity> = emptyList()

        override suspend fun seedAgentProfilesIfEmpty(
            agents: List<AgentProfileEntity>
        ): Int {
            agentCalls += 1
            lastAgents = agents
            if (agentCount == 0) agentCount = agents.size
            return agentCount
        }

        override suspend fun seedOrchestratorDevicesIfEmpty(
            devices: List<OrchestratorDeviceEntity>
        ): Int {
            deviceCalls += 1
            lastDevices = devices
            if (deviceCount == 0) deviceCount = devices.size
            return deviceCount
        }
    }
}
