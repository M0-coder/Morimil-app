package com.morimil.app.data.repository

import com.morimil.app.data.genesis.ultra.CanonicalReadDisposition
import com.morimil.app.data.genesis.ultra.CanonicalReadFailure
import com.morimil.app.data.genesis.ultra.CanonicalReadFailureCode
import com.morimil.app.data.genesis.ultra.CanonicalReadResult
import com.morimil.app.data.genesis.ultra.CanonicalRecallCandidateBatch
import com.morimil.app.data.genesis.ultra.CanonicalSnapshotRef
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RecallBootstrapReadinessTest {
    @Test
    fun readyEmptyBatchIsStillReadableCanonicalRecallState() {
        val batch = batch()

        val resolved = RecallBootstrapReadiness.resolve(CanonicalReadResult.Ready(batch))

        assertSame(batch, resolved)
    }

    @Test
    fun notReadyReturnsNullWithoutThrowing() {
        val resolved = RecallBootstrapReadiness.resolve(
            CanonicalReadResult.Blocked(
                failure(CanonicalReadDisposition.NOT_READY, "canonical_memory_not_ready")
            )
        )

        assertNull(resolved)
    }

    @Test
    fun retryableFailsClosedWithOriginalFailure() {
        val failure = failure(
            CanonicalReadDisposition.RETRYABLE,
            "canonical_snapshot_changed_during_read"
        )

        val thrown = runCatching {
            RecallBootstrapReadiness.resolve(CanonicalReadResult.Blocked(failure))
        }.exceptionOrNull()

        require(thrown is CanonicalRecallReadException)
        assertSame(failure, thrown.failure)
        assertEquals(
            "canonical_recall_read_retryable:canonical_snapshot_changed_during_read",
            thrown.message
        )
    }

    @Test
    fun blockedFailsClosedWithOriginalFailure() {
        val failure = failure(CanonicalReadDisposition.BLOCKED, "canonical_chain_corrupt")

        val thrown = runCatching {
            RecallBootstrapReadiness.resolve(CanonicalReadResult.Blocked(failure))
        }.exceptionOrNull()

        require(thrown is CanonicalRecallReadException)
        assertSame(failure, thrown.failure)
        assertEquals("canonical_recall_read_blocked:canonical_chain_corrupt", thrown.message)
    }

    @Test
    fun identityBindingAcceptsVerifiedEmptyBatchForActiveBodyAndEpoch() {
        RecallBootstrapReadiness.requireIdentityBinding(validIdentity(), batch())
    }

    @Test
    fun identityBindingRejectsForeignInstance() {
        val thrown = runCatching {
            RecallBootstrapReadiness.requireIdentityBinding(
                validIdentity(),
                batch().copy(instanceId = "foreign_instance")
            )
        }.exceptionOrNull()

        assertEquals("canonical_recall_bootstrap_foreign_instance", thrown?.message)
    }

    @Test
    fun identityBindingRejectsWrongBody() {
        val thrown = runCatching {
            RecallBootstrapReadiness.requireIdentityBinding(
                validIdentity(),
                batch().copy(writerBodyId = "other_body")
            )
        }.exceptionOrNull()

        assertEquals("canonical_recall_bootstrap_wrong_body", thrown?.message)
    }

    @Test
    fun identityBindingRejectsStaleEpoch() {
        val thrown = runCatching {
            RecallBootstrapReadiness.requireIdentityBinding(
                validIdentity(),
                batch().copy(writerEpochId = "old_epoch")
            )
        }.exceptionOrNull()

        assertEquals("canonical_recall_bootstrap_stale_epoch", thrown?.message)
    }

    private fun batch(): CanonicalRecallCandidateBatch {
        return CanonicalRecallCandidateBatch(
            snapshot = CanonicalSnapshotRef(
                instanceId = INSTANCE_ID,
                birthRootEventHash = digest('a'),
                birthRootSequence = 1L,
                lastEventHash = digest('b'),
                lastSequence = 1L,
                postBirthEventCount = 0,
                snapshotDigest = digest('c')
            ),
            instanceId = INSTANCE_ID,
            writerBodyId = BODY_ID,
            writerEpochId = EPOCH_ID,
            candidates = emptyList()
        )
    }

    private fun failure(
        disposition: CanonicalReadDisposition,
        diagnosticCode: String
    ): CanonicalReadFailure {
        return CanonicalReadFailure(
            code = when (disposition) {
                CanonicalReadDisposition.NOT_READY -> CanonicalReadFailureCode.CANONICAL_MEMORY_ABSENT
                CanonicalReadDisposition.RETRYABLE -> CanonicalReadFailureCode.SNAPSHOT_CHANGED_DURING_READ
                CanonicalReadDisposition.BLOCKED -> CanonicalReadFailureCode.CHAIN_CORRUPT
            },
            disposition = disposition,
            diagnosticCode = diagnosticCode
        )
    }

    private fun validIdentity(): GenesisUltraRuntimeIdentity {
        val doctrine = document("doctrine/free-birth.md", "doctrine", "free birth doctrine")
        val charter = document("policy/freedom-charter.json", "freedom_charter", "{}")
        val recovery = document("policy/recovery-policy.json", "recovery_policy", "{}")
        return GenesisUltraRuntimeIdentity(
            instanceId = INSTANCE_ID,
            companionName = "Morimil",
            bornAt = "2026-07-25T00:00:00Z",
            identityDigest = GenesisUltraHashProfile.sha256("identity".utf8()),
            activeBody = GenesisUltraRuntimeActiveBody(
                bodyId = BODY_ID,
                status = "active_writer",
                platformProfile = "android",
                publicKeyFingerprint = GenesisUltraHashProfile.sha256("body-key".utf8()),
                keyEpochId = EPOCH_ID,
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

    private fun document(relativePath: String, kind: String, text: String): GenesisUltraRuntimeDocument {
        val bytes = text.utf8()
        return GenesisUltraRuntimeDocument(
            relativePath = relativePath,
            documentKind = kind,
            digest = GenesisUltraHashProfile.sha256(bytes),
            sourceBytes = bytes
        )
    }

    private fun String.utf8(): ByteArray = toByteArray(StandardCharsets.UTF_8)

    private fun digest(character: Char): String = "sha256:" + character.toString().repeat(64)

    private companion object {
        const val INSTANCE_ID = "instance_test"
        const val BODY_ID = "body_test"
        const val EPOCH_ID = "body_key_epoch_1"
    }
}
