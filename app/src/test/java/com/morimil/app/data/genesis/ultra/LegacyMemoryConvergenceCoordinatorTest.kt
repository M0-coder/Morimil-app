package com.morimil.app.data.genesis.ultra

import com.morimil.app.core.memory.MemoryIntegrityCore
import com.morimil.app.data.local.LegacyMemoryConvergenceEntity
import com.morimil.app.data.local.LegacyMemoryImportEntity
import com.morimil.app.data.local.MemoryEventEntity
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyMemoryConvergenceCoordinatorTest {
    @Test
    fun verifiedLegacyChainImportsOnceAndTransfersWriterToUltra() = runBlocking {
        val harness = Harness(events = listOf(event(1), event(2)))

        val first = harness.coordinator().converge(validIdentity())
        val second = harness.coordinator().converge(validIdentity())

        assertEquals(LegacyMemoryConvergenceOutcome.IMPORTED, first.outcome)
        assertEquals(LegacyMemoryConvergenceOutcome.ALREADY_COMPLETE, second.outcome)
        assertEquals(2, harness.appendCalls)
        assertEquals(2, harness.imports.size)
        assertEquals(LegacyMemoryConvergenceEntity.STATUS_COMPLETE, harness.state?.status)
        assertEquals(LegacyMemoryConvergenceEntity.WRITER_GENESIS_ULTRA, first.activeWriter)
        assertTrue(first.legacyReadOnly)
    }

    @Test
    fun brokenLegacyChainBlocksBeforeAnyCanonicalAppend() = runBlocking {
        val harness = Harness(
            events = listOf(event(1)),
            chainVerified = false
        )

        val failure = runCatching {
            harness.coordinator().converge(validIdentity())
        }.exceptionOrNull()

        assertEquals("legacy_convergence_chain_unverified", failure?.message)
        assertEquals(0, harness.appendCalls)
        assertEquals(LegacyMemoryConvergenceEntity.STATUS_BLOCKED, harness.state?.status)
        assertEquals("legacy_convergence_chain_unverified", harness.state?.failureCode)
    }

    @Test
    fun rerunReconcilesCanonicalAppendThatLostItsMapping() = runBlocking {
        val source = event(1)
        val harness = Harness(events = listOf(source))
        val firstFailure = runCatching {
            harness.failFirstMapping = true
            harness.coordinator().converge(validIdentity())
        }.exceptionOrNull()

        assertTrue(firstFailure != null)
        assertEquals(1, harness.appendCalls)
        assertEquals(0, harness.imports.size)
        assertEquals(1, harness.canonical.size)

        harness.failFirstMapping = false
        val report = harness.coordinator().converge(validIdentity())

        assertEquals(LegacyMemoryConvergenceOutcome.IMPORTED, report.outcome)
        assertEquals(1, harness.appendCalls)
        assertEquals(1, harness.imports.size)
        assertEquals(LegacyMemoryConvergenceEntity.STATUS_COMPLETE, harness.state?.status)
    }

    @Test
    fun legacyIdentityWithoutMemoryIsBlockedInsteadOfInventingHistory() = runBlocking {
        val harness = Harness(
            events = emptyList(),
            legacyCounts = 1 to 1
        )

        val failure = runCatching {
            harness.coordinator().converge(validIdentity())
        }.exceptionOrNull()

        assertEquals("legacy_convergence_identity_without_memory", failure?.message)
        assertEquals(LegacyMemoryConvergenceEntity.STATUS_BLOCKED, harness.state?.status)
        assertEquals(0, harness.appendCalls)
    }

    @Test
    fun missingReadOnlyTriggersBlocksTheTransition() = runBlocking {
        val harness = Harness(
            events = listOf(event(1)),
            triggerCount = 2
        )

        val failure = runCatching {
            harness.coordinator().converge(validIdentity())
        }.exceptionOrNull()

        assertEquals("legacy_convergence_read_only_triggers_missing", failure?.message)
        assertEquals(0, harness.appendCalls)
    }

    private class Harness(
        private val events: List<MemoryEventEntity>,
        private val chainVerified: Boolean = true,
        private val legacyCounts: Pair<Int, Int> = 1 to 1,
        private val triggerCount: Int = 3
    ) {
        var state: LegacyMemoryConvergenceEntity? = null
        val imports = mutableListOf<LegacyMemoryImportEntity>()
        val canonical = mutableListOf<CanonicalLegacyImportEvidence>()
        var appendCalls = 0
        var failFirstMapping = false
        private var now = 10_000L

        fun coordinator(): LegacyMemoryConvergenceCoordinator {
            return LegacyMemoryConvergenceCoordinator.forTest(
                countReadOnlyTriggers = { triggerCount },
                loadLegacyIdentityCounts = { legacyCounts },
                loadLegacyEvents = { events },
                verifyLegacyChain = { chainVerified },
                loadState = { state },
                saveState = { value -> state = value },
                loadImports = { imports.toList() },
                saveImport = { entry ->
                    if (failFirstMapping) {
                        failFirstMapping = false
                        error("simulated_mapping_commit_loss")
                    }
                    imports += entry
                },
                loadCanonicalEvidence = { canonical.toList() },
                appendCanonical = { plan ->
                    appendCalls += 1
                    CanonicalLegacyImportEvidence(
                        deterministicEventId = plan.deterministicEventId,
                        canonicalEventHash = "evsha256:" + appendCalls.toString().padStart(64, '0'),
                        canonicalSequence = appendCalls.toLong(),
                        content = plan.content,
                        legacyEventHash = plan.legacyEventHash,
                        rowDigest = plan.rowDigest,
                        provenanceDigest = "sha256:" + appendCalls.toString().padStart(64, 'a')
                    ).also(canonical::add)
                },
                clockMillis = { now++ }
            )
        }
    }

    private fun event(id: Long): MemoryEventEntity {
        return MemoryEventEntity(
            id = id,
            genesisCoreId = "primary_genesis",
            genesisCoreHash = digest("genesis"),
            previousEventHash = if (id == 1L) null else digest("event-${id - 1}"),
            eventHash = digest("event-$id"),
            hashAlgorithm = MemoryIntegrityCore.HASH_ALGORITHM_SHA256,
            canonicalization = MemoryIntegrityCore.MEMORY_EVENT_CANONICALIZATION_V3,
            signatureAlgorithm = "android_keystore_ec_p256_sha256_ecdsa_v1",
            eventSignature = "signature-$id",
            eventType = "legacy.event.$id",
            actor = "system",
            source = "legacy_runtime",
            contextTag = "local_runtime",
            privacyVisibility = "private_local",
            memoryKind = "observation",
            tagsJson = "[]",
            evidenceJson = "{}",
            confidence = 80,
            userConfirmed = false,
            body = "Legacy event $id",
            importance = 70,
            createdAtMillis = id * 1000L
        )
    }

    private fun validIdentity(): GenesisUltraRuntimeIdentity {
        val doctrine = document("doctrine/free-birth.md", "doctrine", "free birth doctrine")
        val charter = document("policy/freedom-charter.json", "freedom_charter", "{}")
        val recovery = document("policy/recovery-policy.json", "recovery_policy", "{}")
        return GenesisUltraRuntimeIdentity(
            instanceId = "instance_test",
            companionName = "Morimil",
            bornAt = "2026-07-25T00:00:00Z",
            identityDigest = digest("identity"),
            activeBody = GenesisUltraRuntimeActiveBody(
                bodyId = "body_test",
                status = "active_writer",
                platformProfile = "android",
                publicKeyFingerprint = digest("body-key"),
                keyEpochId = "body_key_epoch_1",
                keyEpochDigest = digest("body-epoch"),
                registryEpoch = 1L,
                registryDigest = digest("registry")
            ),
            guardian = GenesisUltraRuntimeGuardian(
                guardianId = "guardian_test",
                keyEpochId = "guardian_key_epoch_1",
                publicKeyRef = digest("guardian-key"),
                status = "active",
                role = "custodian_without_ownership",
                anchorDigest = digest("guardian-anchor")
            ),
            seed = GenesisUltraRuntimeVerifiedSeed(
                seedId = "seed_test",
                rootHash = digest("seed-root"),
                protocolVersion = "genesis-ultra-v1",
                hashProfile = "sha256",
                identityDigest = digest("seed-identity"),
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
                authorizedAt = "2026-07-25T00:00:00Z",
                expiresAt = "2026-07-25T00:05:00Z",
                receiptDigest = digest("receipt"),
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
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        return GenesisUltraRuntimeDocument(
            relativePath = relativePath,
            documentKind = kind,
            digest = GenesisUltraHashProfile.sha256(bytes),
            sourceBytes = bytes
        )
    }

    private fun digest(value: String): String =
        GenesisUltraHashProfile.sha256(value.toByteArray(StandardCharsets.UTF_8))
}
