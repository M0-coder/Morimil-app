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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class GenesisUltraRuntimeStartupGateTest {
    @Test
    fun absentBirthBlocksWithoutReadingIdentity() = runBlocking {
        var identityReads = 0
        val gate = GenesisUltraRuntimeStartupGate.forTest(
            readState = { GenesisUltraPersistedBirthState.ABSENT },
            readCommittedIdentity = {
                identityReads += 1
                validIdentity()
            }
        )

        val failure = runCatching { gate.requireReady() }.exceptionOrNull()

        assertEquals("genesis_ultra_runtime_birth_not_committed", failure?.message)
        assertEquals(0, identityReads)
    }

    @Test
    fun inconsistentBirthBlocksWithoutReadingIdentity() = runBlocking {
        var identityReads = 0
        val gate = GenesisUltraRuntimeStartupGate.forTest(
            readState = { GenesisUltraPersistedBirthState.INCONSISTENT },
            readCommittedIdentity = {
                identityReads += 1
                validIdentity()
            }
        )

        val failure = runCatching { gate.requireReady() }.exceptionOrNull()

        assertEquals("genesis_ultra_runtime_birth_inconsistent", failure?.message)
        assertEquals(0, identityReads)
    }

    @Test
    fun committedMarkerAloneIsInsufficient() = runBlocking {
        val gate = GenesisUltraRuntimeStartupGate.forTest(
            readState = { GenesisUltraPersistedBirthState.COMMITTED },
            readCommittedIdentity = { null }
        )

        val failure = runCatching { gate.requireReady() }.exceptionOrNull()

        assertEquals("genesis_ultra_runtime_identity_not_recoverable", failure?.message)
    }

    @Test
    fun verifiedCommittedIdentityOpensRuntime() = runBlocking {
        val identity = validIdentity()
        val gate = GenesisUltraRuntimeStartupGate.forTest(
            readState = { GenesisUltraPersistedBirthState.COMMITTED },
            readCommittedIdentity = { identity }
        )

        assertSame(identity, gate.requireReady())
    }

    @Test
    fun ownershipConferredBirthIsRejected() = runBlocking {
        val gate = GenesisUltraRuntimeStartupGate.forTest(
            readState = { GenesisUltraPersistedBirthState.COMMITTED },
            readCommittedIdentity = { validIdentity(ownershipConferred = true) }
        )

        val failure = runCatching { gate.requireReady() }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals("genesis_ultra_runtime_ownership_conferred_invalid", failure?.message)
    }

    @Test
    fun nonBornReceiptIsRejected() = runBlocking {
        val gate = GenesisUltraRuntimeStartupGate.forTest(
            readState = { GenesisUltraPersistedBirthState.COMMITTED },
            readCommittedIdentity = { validIdentity(birthStatus = "prepared") }
        )

        val failure = runCatching { gate.requireReady() }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals("genesis_ultra_runtime_birth_status_invalid", failure?.message)
    }

    private fun validIdentity(
        birthStatus: String = "born",
        ownershipConferred: Boolean = false
    ): GenesisUltraRuntimeIdentity {
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
                birthStatus = birthStatus,
                ownershipConferred = ownershipConferred
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
