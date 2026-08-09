package com.morimil.app.runtime

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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GenesisUltraRuntimeRestBootReadinessTest {
    @Test
    fun verifiedRestPlanningPromotesOnlyRestWhileRecallAndHealthRemainWaiting() = runBlocking {
        val identity = validIdentity()
        var probedIdentity: GenesisUltraRuntimeIdentity? = null
        val coordinator = coordinator(
            probeRestCycleReady = { received ->
                probedIdentity = received
                true
            }
        )

        val report = coordinator.bootstrap(identity)

        assertSame(identity, probedIdentity)
        assertEquals(GenesisUltraRuntimeSubsystemState.READY, report.restCycleState)
        assertEquals(
            GenesisUltraRuntimeSubsystemState.WAITING_FOR_CANONICAL_MEMORY_ADAPTER,
            report.recallState
        )
        assertEquals(
            GenesisUltraRuntimeHealthState.WAITING_FOR_DEPENDENCIES,
            report.healthState
        )
    }

    @Test
    fun notReadyRestPlanningKeepsRestAndHealthWaiting() = runBlocking {
        val report = coordinator(probeRestCycleReady = { false }).bootstrap(validIdentity())

        assertEquals(
            GenesisUltraRuntimeSubsystemState.WAITING_FOR_CANONICAL_MEMORY_ADAPTER,
            report.restCycleState
        )
        assertEquals(
            GenesisUltraRuntimeHealthState.WAITING_FOR_DEPENDENCIES,
            report.healthState
        )
    }

    @Test
    fun restReadinessFailureIsNotConvertedIntoReadyOrWaiting() = runBlocking {
        val failure = runCatching {
            coordinator(
                probeRestCycleReady = { error("canonical_rest_cycle_read_blocked:test") }
            ).bootstrap(validIdentity())
        }.exceptionOrNull()

        assertEquals("canonical_rest_cycle_read_blocked:test", failure?.message)
    }

    private fun coordinator(
        probeRestCycleReady: suspend (GenesisUltraRuntimeIdentity) -> Boolean
    ): GenesisUltraRuntimeBootstrapCoordinator {
        return GenesisUltraRuntimeBootstrapCoordinator.forTest(
            inspectLegacyCounts = { GenesisUltraRuntimeLegacyCounts(0, 0) },
            executeDurableBootstrap = { identity, _ ->
                GenesisUltraRuntimeProjection(
                    workspaceId = identity.instanceId,
                    projectId = "morimil_app:${identity.instanceId}"
                )
            },
            countAgentProfiles = { 7 },
            countOrchestratorDevices = { 4 },
            countCanonicalMemoryEvents = { 3 },
            probeRestCycleReady = probeRestCycleReady
        )
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
                role = "custodian_witness",
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
