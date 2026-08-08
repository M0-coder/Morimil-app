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
import java.nio.charset.StandardCharsets
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeBootstrapOperationFactoryTest {
    @Test
    fun exactSameIdentityProducesExactSameSemanticOperation() {
        val identity = identity()

        val first = RuntimeBootstrapOperationFactory.initialize(identity)
        val second = RuntimeBootstrapOperationFactory.initialize(identity)

        assertEquals(first, second)
        assertEquals(RuntimeBootstrapProtocolTypes.OWNER_TYPE, first.ownerType)
        assertEquals(RuntimeBootstrapProtocolTypes.INITIALIZE, first.operationType)
        assertEquals(RuntimeBootstrapProtocolTypes.INITIALIZED_EVENT, first.eventType)
        assertFalse(first.payloadJson.contains("occurred_at"))
        assertFalse(first.evidenceJson.contains("occurred_at"))
    }

    @Test
    fun successorBodyKeepsInstanceProjectionButGetsDistinctBootstrapOperation() {
        val original = identity()
        val successor = original.copy(
            activeBody = original.activeBody.copy(
                bodyId = "body_successor",
                keyEpochId = "epoch_successor",
                keyEpochDigest = digest("epoch_successor"),
                publicKeyFingerprint = digest("body_successor_key"),
                registryEpoch = 2,
                registryDigest = digest("registry_successor")
            )
        )

        val originalCommand = RuntimeBootstrapOperationFactory.initialize(original)
        val successorCommand = RuntimeBootstrapOperationFactory.initialize(successor)
        val originalPayload = JSONObject(originalCommand.payloadJson)
        val successorPayload = JSONObject(successorCommand.payloadJson)

        assertEquals(original.instanceId, successor.instanceId)
        assertEquals(
            originalPayload.getJSONObject("workspace").getString("workspace_id"),
            successorPayload.getJSONObject("workspace").getString("workspace_id")
        )
        assertEquals(
            originalPayload.getJSONObject("project").getString("project_id"),
            successorPayload.getJSONObject("project").getString("project_id")
        )
        assertNotEquals(originalCommand.subjectId, successorCommand.subjectId)
        assertNotEquals(originalCommand.operationId, successorCommand.operationId)
        assertNotEquals(originalCommand.eventId, successorCommand.eventId)
        assertTrue(JSONObject(successorCommand.evidenceJson).getBoolean("successor_body_rebootstrap_allowed"))
    }

    @Test
    fun ownershipConferredIdentityCannotCreateBootstrapOperation() {
        val base = identity()
        val invalid = base.copy(
            authorization = base.authorization.copy(ownershipConferred = true)
        )

        val failure = runCatching {
            RuntimeBootstrapOperationFactory.initialize(invalid)
        }.exceptionOrNull()

        assertEquals("runtime_bootstrap_ownership_conferred", failure?.message)
    }

    @Test
    fun nonCanonicalGuardianRoleCannotCreateBootstrapOperation() {
        val base = identity()
        val invalid = base.copy(
            guardian = base.guardian.copy(role = "custodian_without_ownership")
        )

        val failure = runCatching {
            RuntimeBootstrapOperationFactory.initialize(invalid)
        }.exceptionOrNull()

        assertEquals("runtime_bootstrap_guardian_role_invalid", failure?.message)
    }

    @Test
    fun payloadPreservesCanonicalGuardianWitnessAndNoOwnershipTruth() {
        val command = RuntimeBootstrapOperationFactory.initialize(identity())
        val payload = JSONObject(command.payloadJson)
        val project = payload.getJSONObject("project")

        assertFalse(payload.getBoolean("ownership_conferred"))
        assertEquals("custodian_witness", payload.getString("guardian_role"))
        assertEquals(
            "genesis_ultra_runtime_ready;memory=canonical;boot=durable;" +
                "rest_cycle=canonical_adapter_pending;recalls=canonical_adapter_pending;health=ready",
            project.getString("status")
        )
        assertEquals(7, payload.getJSONArray("agent_profiles").length())
        assertEquals(4, payload.getJSONArray("orchestrator_devices").length())
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
}
