package com.morimil.app.runtime

import com.morimil.app.data.genesis.ultra.GenesisUltraHashProfile
import com.morimil.app.data.genesis.ultra.GenesisUltraPersistedBirthState
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeActiveBody
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeAuthorization
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeAuthorizationState
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeDocument
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeGuardian
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimePolicy
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeVerifiedSeed
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraRuntimeBootstrapCoordinatorTest {
    @Test
    fun cleanUltraIdentityBootstrapsCanonicalProjectionWithoutLegacyRows() = runBlocking {
        val identity = validIdentity()
        var projectionWrites = 0
        var orchestrationSeeds = 0
        val coordinator = GenesisUltraRuntimeBootstrapCoordinator.forTest(
            inspectLegacyCounts = {
                GenesisUltraRuntimeLegacyCounts(
                    localIdentityCount = 0,
                    genesisCoreCount = 0
                )
            },
            writeRuntimeProjection = { received, nowMillis ->
                assertSame(identity, received)
                assertEquals(5_000L, nowMillis)
                projectionWrites += 1
                GenesisUltraRuntimeProjection(
                    workspaceId = received.instanceId,
                    projectId = "morimil_app:${received.instanceId}"
                )
            },
            seedOrchestration = { received, nowMillis ->
                assertSame(identity, received)
                assertEquals(5_000L, nowMillis)
                orchestrationSeeds += 1
                GenesisUltraRuntimeOrchestrationSeed(
                    agentProfileCount = 7,
                    orchestratorDeviceCount = 4
                )
            },
            countCanonicalMemoryEvents = { 1 }
        )

        val report = coordinator.bootstrap(identity, nowMillis = 5_000L)

        assertEquals(1, projectionWrites)
        assertEquals(1, orchestrationSeeds)
        assertEquals(identity.instanceId, report.workspaceId)
        assertEquals("morimil_app:${identity.instanceId}", report.projectId)
        assertEquals(7, report.agentProfileCount)
        assertEquals(4, report.orchestratorDeviceCount)
        assertEquals(1, report.canonicalMemoryEventCount)
        assertEquals(GenesisUltraRuntimeSubsystemState.READY, report.healthState)
        assertEquals(
            GenesisUltraRuntimeSubsystemState.WAITING_FOR_CANONICAL_MEMORY_ADAPTER,
            report.restCycleState
        )
        assertEquals(
            GenesisUltraRuntimeSubsystemState.WAITING_FOR_CANONICAL_MEMORY_ADAPTER,
            report.recallState
        )
        assertTrue(report.legacyCounts.isEmpty)
    }

    @Test
    fun legacyRowsBlockBeforeAnyCanonicalProjectionIsWritten() = runBlocking {
        var projectionWrites = 0
        var orchestrationSeeds = 0
        val coordinator = GenesisUltraRuntimeBootstrapCoordinator.forTest(
            inspectLegacyCounts = {
                GenesisUltraRuntimeLegacyCounts(
                    localIdentityCount = 1,
                    genesisCoreCount = 0
                )
            },
            writeRuntimeProjection = { identity, _ ->
                projectionWrites += 1
                GenesisUltraRuntimeProjection(
                    workspaceId = identity.instanceId,
                    projectId = "morimil_app:${identity.instanceId}"
                )
            },
            seedOrchestration = { _, _ ->
                orchestrationSeeds += 1
                GenesisUltraRuntimeOrchestrationSeed(7, 4)
            },
            countCanonicalMemoryEvents = { 0 }
        )

        val failure = runCatching {
            coordinator.bootstrap(validIdentity())
        }.exceptionOrNull()

        assertEquals("runtime_bootstrap_legacy_identity_conflict", failure?.message)
        assertEquals(0, projectionWrites)
        assertEquals(0, orchestrationSeeds)
    }

    @Test
    fun bootstrapFailsIfAnyLegacyIdentityRowAppearsAfterProjection() = runBlocking {
        var inspections = 0
        val coordinator = GenesisUltraRuntimeBootstrapCoordinator.forTest(
            inspectLegacyCounts = {
                inspections += 1
                if (inspections == 1) {
                    GenesisUltraRuntimeLegacyCounts(0, 0)
                } else {
                    GenesisUltraRuntimeLegacyCounts(0, 1)
                }
            },
            writeRuntimeProjection = { identity, _ ->
                GenesisUltraRuntimeProjection(
                    workspaceId = identity.instanceId,
                    projectId = "morimil_app:${identity.instanceId}"
                )
            },
            seedOrchestration = { _, _ ->
                GenesisUltraRuntimeOrchestrationSeed(7, 4)
            },
            countCanonicalMemoryEvents = { 0 }
        )

        val failure = runCatching {
            coordinator.bootstrap(validIdentity())
        }.exceptionOrNull()

        assertEquals("runtime_bootstrap_created_legacy_identity", failure?.message)
        assertEquals(2, inspections)
    }

    @Test
    fun startupGateBootstrapsOnlyAfterIdentityIsFullyVerified() = runBlocking {
        val identity = validIdentity()
        var bootstrapped: GenesisUltraRuntimeIdentity? = null
        val gate = GenesisUltraRuntimeStartupGate.forTest(
            readState = { GenesisUltraPersistedBirthState.COMMITTED },
            readCommittedIdentity = { identity },
            bootstrapVerifiedIdentity = { verified -> bootstrapped = verified }
        )

        val ready = gate.requireReady()

        assertSame(identity, ready)
        assertSame(identity, bootstrapped)
    }

    private fun validIdentity(): GenesisUltraRuntimeIdentity {
        val doctrine = document(
            relativePath = "doctrine/free-birth.md",
            kind = "doctrine",
            text = "free birth doctrine"
        )
        val charter = document(
            relativePath = "policy/freedom-charter.json",
            kind = "freedom_charter",
            text = "{}"
        )
        val recovery = document(
            relativePath = "policy/recovery-policy.json",
            kind = "recovery_policy",
            text = "{}"
        )
        return GenesisUltraRuntimeIdentity(
            instanceId = "instance_test",
            companionName = "Morimil",
            bornAt = "2026-07-25T00:00:00Z",
            identityDigest = GenesisUltraHashProfile.sha256("identity".utf8()),
            activeBody = GenesisUltraRuntimeActiveBody(
                bodyId = "body_test",
                status = "active_writer",
                platformProfile = "android",
                publicKeyFingerprint = GenesisUltraHashProfile.sha256("body-key".utf8()),
                keyEpochId = "body_key_epoch_1",
                keyEpochDigest = GenesisUltraHashProfile.sha256("body-epoch".utf8()),
                registryEpoch = 1L,
                registryDigest = GenesisUltraHashProfile.sha256("registry".utf8())
            ),
            guardian = GenesisUltraRuntimeGuardian(
                guardianId = "guardian_test",
                keyEpochId = "guardian_key_epoch_1",
                publicKeyRef = GenesisUltraHashProfile.sha256("guardian-key".utf8()),
                status = "active",
                role = "custodian_without_ownership",
                anchorDigest = GenesisUltraHashProfile.sha256("guardian-anchor".utf8())
            ),
            seed = GenesisUltraRuntimeVerifiedSeed(
                seedId = "seed_test",
                rootHash = GenesisUltraHashProfile.sha256("seed-root".utf8()),
                protocolVersion = "genesis-ultra-v1",
                hashProfile = "sha256",
                identityDigest = GenesisUltraHashProfile.sha256("seed-identity".utf8()),
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
                authorizationDigest = GenesisUltraHashProfile.sha256("authorization".utf8()),
                candidateDigest = GenesisUltraHashProfile.sha256("candidate".utf8()),
                consentDigest = GenesisUltraHashProfile.sha256("consent".utf8()),
                authorizedAt = "2026-07-25T00:00:00Z",
                expiresAt = "2026-07-25T00:05:00Z",
                receiptDigest = GenesisUltraHashProfile.sha256("receipt".utf8()),
                birthStatus = "born",
                ownershipConferred = false
            )
        )
    }

    private fun document(
        relativePath: String,
        kind: String,
        text: String
    ): GenesisUltraRuntimeDocument {
        val bytes = text.utf8()
        return GenesisUltraRuntimeDocument(
            relativePath = relativePath,
            documentKind = kind,
            digest = GenesisUltraHashProfile.sha256(bytes),
            sourceBytes = bytes
        )
    }

    private fun String.utf8(): ByteArray = toByteArray(StandardCharsets.UTF_8)
}
