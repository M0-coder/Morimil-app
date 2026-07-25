package com.morimil.app.data.genesis.ultra

import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraAtomicBirthExecutionCeremonyCoordinatorTest {
    @Test
    fun cleanCommitBuildsCanonicalSequenceOneRequestAndRetiresConsent() = runBlocking {
        val authorization = authorization()
        var executeCount = 0
        var retireCount = 0
        var capturedRequest: GenesisUltraCanonicalMemoryAppendRequest? = null
        val coordinator = GenesisUltraAtomicBirthExecutionCeremonyCoordinator.forTest(
            executeBirth = { actualAuthorization, activatedAt, request ->
                executeCount += 1
                capturedRequest = request
                assertTrue(actualAuthorization === authorization)
                assertEquals(COMMITTED_AT, activatedAt)
                committedEvidence(authorization, request)
            },
            retireCommittedConsent = {
                retireCount += 1
                GenesisUltraCommittedConsentRetirementResult.RETIRED
            },
            clock = { Instant.parse("2026-07-25T12:10:00.987Z") }
        )

        val result = coordinator.execute(authorization, requestFor(authorization))

        assertEquals(1, executeCount)
        assertEquals(1, retireCount)
        assertEquals(GenesisUltraAtomicBirthExecutionOutcome.COMMITTED_CLEAN, result.outcome)
        assertTrue(result.birthCommitted)
        assertNull(result.maintenanceError)
        assertEquals(COMMITTED_AT, result.committedAt)
        assertEquals(EVENT_HASH, result.firstPostBirthEventHash)

        val memory = requireNotNull(capturedRequest)
        assertEquals("instance.activation.confirmed", memory.eventType)
        assertEquals("host_confirmed_system", memory.actor)
        assertEquals(authorization.authorizationDigest, memory.contentDigest)
        assertEquals(authorization.receiptDigest, memory.provenanceDigest)
        assertEquals(COMMITTED_AT, memory.observedAt)
        assertEquals("private_local", memory.privacy)
        assertNull(memory.contentRef)
        assertNull(memory.provenanceRef)
        assertTrue(memory.eventId.startsWith("event_"))
        assertEquals(70, memory.eventId.length)
    }

    @Test
    fun invalidFinalCodeFailsBeforeExecutorOrRetirement() = runBlocking {
        val authorization = authorization()
        var executeCount = 0
        var retireCount = 0
        val coordinator = GenesisUltraAtomicBirthExecutionCeremonyCoordinator.forTest(
            executeBirth = { _, _, request ->
                executeCount += 1
                committedEvidence(authorization, request)
            },
            retireCommittedConsent = {
                retireCount += 1
                GenesisUltraCommittedConsentRetirementResult.RETIRED
            },
            clock = { Instant.parse(COMMITTED_AT) }
        )
        val invalid = requestFor(authorization).copy(
            presentedConfirmationCode = "000000000000"
        )

        val failure = runCatching { coordinator.execute(authorization, invalid) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("birth_execution_presented_code_mismatch"))
        assertEquals(0, executeCount)
        assertEquals(0, retireCount)
    }

    @Test
    fun transactionalFailureRemainsUncommittedAndDoesNotRetireConsent() = runBlocking {
        val authorization = authorization()
        var retireCount = 0
        val coordinator = GenesisUltraAtomicBirthExecutionCeremonyCoordinator.forTest(
            executeBirth = { _, _, _ -> error("simulated_transaction_rollback") },
            retireCommittedConsent = {
                retireCount += 1
                GenesisUltraCommittedConsentRetirementResult.RETIRED
            },
            clock = { Instant.parse(COMMITTED_AT) }
        )

        val failure = runCatching {
            coordinator.execute(authorization, requestFor(authorization))
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("simulated_transaction_rollback"))
        assertEquals(0, retireCount)
    }

    @Test
    fun evidenceMismatchAfterExecutorReturnIsCommittedMaintenancePending() = runBlocking {
        val authorization = authorization()
        var retireCount = 0
        val coordinator = GenesisUltraAtomicBirthExecutionCeremonyCoordinator.forTest(
            executeBirth = { _, _, request ->
                committedEvidence(authorization, request).copy(firstPostBirthSequence = 2L)
            },
            retireCommittedConsent = {
                retireCount += 1
                GenesisUltraCommittedConsentRetirementResult.RETIRED
            },
            clock = { Instant.parse(COMMITTED_AT) }
        )

        val result = coordinator.execute(authorization, requestFor(authorization))

        assertEquals(1, retireCount)
        assertTrue(result.birthCommitted)
        assertEquals(
            GenesisUltraAtomicBirthExecutionOutcome.COMMITTED_MAINTENANCE_PENDING,
            result.outcome
        )
        assertTrue(
            result.maintenanceError.orEmpty()
                .contains("birth_execution_committed_memory_sequence_invalid")
        )
        assertEquals(EVENT_HASH, result.firstPostBirthEventHash)
    }

    @Test
    fun resultMappingFailureAfterExecutorReturnNeverBecomesRetryableFailure() = runBlocking {
        val authorization = authorization()
        var retireCount = 0
        val coordinator = GenesisUltraAtomicBirthExecutionCeremonyCoordinator.forCommittedReturnTest(
            executeBirth = { _, _, _ ->
                GenesisUltraAtomicBirthCommittedReturn(
                    evidence = null,
                    postCommitFailure = IllegalStateException("simulated_post_commit_mapping_failure")
                )
            },
            retireCommittedConsent = {
                retireCount += 1
                GenesisUltraCommittedConsentRetirementResult.ALREADY_ABSENT
            },
            clock = { Instant.parse(COMMITTED_AT) }
        )

        val result = coordinator.execute(authorization, requestFor(authorization))

        assertEquals(1, retireCount)
        assertTrue(result.birthCommitted)
        assertEquals(
            GenesisUltraAtomicBirthExecutionOutcome.COMMITTED_MAINTENANCE_PENDING,
            result.outcome
        )
        assertNull(result.firstPostBirthEventHash)
        assertTrue(result.maintenanceError.orEmpty().contains("simulated_post_commit_mapping_failure"))
        assertTrue(result.maintenanceError.orEmpty().contains("committed_event_hash_unavailable"))
    }

    @Test
    fun retirementFailureAfterCommitIsMaintenanceOnly() = runBlocking {
        val authorization = authorization()
        val coordinator = GenesisUltraAtomicBirthExecutionCeremonyCoordinator.forTest(
            executeBirth = { _, _, request -> committedEvidence(authorization, request) },
            retireCommittedConsent = { error("simulated_retirement_failure") },
            clock = { Instant.parse(COMMITTED_AT) }
        )

        val result = coordinator.execute(authorization, requestFor(authorization))

        assertTrue(result.birthCommitted)
        assertEquals(
            GenesisUltraAtomicBirthExecutionOutcome.COMMITTED_MAINTENANCE_PENDING,
            result.outcome
        )
        assertTrue(result.maintenanceError.orEmpty().contains("simulated_retirement_failure"))
        assertEquals(EVENT_HASH, result.firstPostBirthEventHash)
    }

    private fun requestFor(
        authorization: GenesisUltraAuthorizedAtomicBirth
    ): GenesisUltraAtomicBirthExecutionCeremonyRequest {
        return GenesisUltraAtomicBirthExecutionCeremonyRequest(
            presentedAuthorizationDigest = authorization.authorizationDigest,
            presentedCandidateDigest = authorization.candidateDigest,
            presentedConsentDigest = authorization.consentDigest,
            presentedConfirmationCode =
                GenesisUltraAtomicBirthExecutionCeremonyRequest.confirmationCode(
                    authorization.authorizationDigest
                ),
            decision = GenesisUltraAtomicBirthExecutionCeremonyRequest.COMMIT_DECISION,
            confirmationMode =
                GenesisUltraAtomicBirthExecutionCeremonyRequest.INTERACTIVE_CONFIRMATION_MODE,
            confirmationPurpose =
                GenesisUltraAtomicBirthExecutionCeremonyRequest.EXECUTION_CONFIRMATION_PURPOSE,
            userPresenceConfirmed = true
        )
    }

    private fun committedEvidence(
        authorization: GenesisUltraAuthorizedAtomicBirth,
        request: GenesisUltraCanonicalMemoryAppendRequest
    ): GenesisUltraAtomicBirthCommittedEvidence {
        return GenesisUltraAtomicBirthCommittedEvidence(
            birthId = BIRTH_ID,
            instanceId = INSTANCE_ID,
            companionName = COMPANION_NAME,
            authorizationDigest = authorization.authorizationDigest,
            birthStateDigest = authorization.birthStateDigest,
            receiptDigest = authorization.receiptDigest,
            firstPostBirthEventHash = EVENT_HASH,
            firstPostBirthSequence = 1L,
            firstPostBirthContentDigest = request.contentDigest,
            firstPostBirthProvenanceDigest = request.provenanceDigest,
            firstPostBirthObservedAt = request.observedAt
        )
    }

    private fun authorization(): GenesisUltraAuthorizedAtomicBirth {
        val seedRoot = digest("seed-root")
        val identityDigest = digest("identity")
        val birthStateDigest = digest("birth-state")
        val receiptDigest = digest("receipt")
        val manifest = GenesisUltraSeedManifest(
            schemaVersion = "genesis.seed.manifest.v0.1",
            protocolVersion = "0.1",
            hashProfile = GenesisUltraHashProfile.FIELD_PROFILE,
            seedId = "seed_0123456789abcdef",
            identityDigest = digest("seed-identity"),
            doctrineDigest = digest("seed-doctrine"),
            files = emptyList(),
            rootHash = seedRoot
        )
        val identity = GenesisUltraInstanceIdentity(
            schemaVersion = "genesis.instance.identity.v0.1",
            instanceId = INSTANCE_ID,
            seedId = manifest.seedId,
            seedRootHash = seedRoot,
            companionName = COMPANION_NAME,
            guardianId = "guardian-test",
            bornAt = AUTHORIZED_AT,
            identityDigest = identityDigest
        )
        val state = GenesisUltraBirthState(
            schemaVersion = "genesis.birth.state.v0.1",
            birthId = BIRTH_ID,
            instanceId = INSTANCE_ID,
            seedId = manifest.seedId,
            seedRootHash = seedRoot,
            identityDigest = identityDigest,
            freedomCharterDigest = digest("freedom"),
            initialBodyId = BODY_ID,
            initialBodyRegistryDigest = digest("registry"),
            initialBodyKeyEpochDigest = digest("epoch"),
            initialBodyPossessionDigest = digest("possession"),
            firstMemoryEventHash = "evsha256:" + digest("root-memory").removePrefix("sha256:"),
            recoveryStateDigest = digest("recovery"),
            bornAt = AUTHORIZED_AT,
            activeWriterCount = 1L,
            stateDigest = birthStateDigest
        )
        val bodyEnvelope = envelope("body", BODY_ID, "body-epoch-000000", digest("body-key"))
        val guardianEnvelope = envelope(
            "guardian",
            "guardian-test",
            "guardian-epoch-000000",
            digest("guardian-key")
        )
        val receipt = GenesisUltraBirthReceipt(
            schemaVersion = "genesis.birth.receipt.v0.1",
            birthId = BIRTH_ID,
            instanceId = INSTANCE_ID,
            journalId = "journal_0123456789abcdef",
            birthStateDigest = birthStateDigest,
            seedRootHash = seedRoot,
            identityDigest = identityDigest,
            freedomCharterDigest = state.freedomCharterDigest,
            initialBodyRegistryDigest = state.initialBodyRegistryDigest,
            initialBodyKeyEpochDigest = state.initialBodyKeyEpochDigest,
            initialBodyPossessionDigest = state.initialBodyPossessionDigest,
            firstMemoryEventHash = state.firstMemoryEventHash,
            recoveryStateDigest = state.recoveryStateDigest,
            bornAt = AUTHORIZED_AT,
            birthStatus = "born",
            activeWriterBodyId = BODY_ID,
            activeWriterCount = 1L,
            guardianRole = "custodian_witness",
            ownershipConferred = false,
            receiptDigest = receiptDigest,
            bodyAcknowledgement = bodyEnvelope,
            guardianWitness = guardianEnvelope
        )
        val bundle = GenesisUltraAtomicBirthPersistenceBundle(
            seedManifest = manifest,
            instanceIdentity = identity,
            birthState = state,
            birthReceipt = receipt,
            artifacts = emptyList(),
            journal = emptyList()
        )
        val verifiedConstructor = GenesisUltraVerifiedAtomicBirth::class.java
            .getDeclaredConstructor(GenesisUltraAtomicBirthPersistenceBundle::class.java)
        verifiedConstructor.isAccessible = true
        val verified = verifiedConstructor.newInstance(bundle)

        val constructor = GenesisUltraAuthorizedAtomicBirth::class.java.getDeclaredConstructor(
            GenesisUltraVerifiedAtomicBirth::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(
            verified,
            digest("candidate"),
            digest("consent"),
            birthStateDigest,
            receiptDigest,
            digest("authorization"),
            AUTHORIZED_AT,
            EXPIRES_AT
        )
    }

    private fun envelope(
        signerType: String,
        signerId: String,
        keyEpochId: String,
        publicKeyRef: String
    ): GenesisUltraSignatureEnvelope {
        return GenesisUltraSignatureEnvelope(
            schemaVersion = "genesis.signature.envelope.v0.1",
            signatureProfile = "genesis.signature.ed25519.v0.1",
            signerType = signerType,
            signerId = signerId,
            keyEpochId = keyEpochId,
            signedDomain = "test.only",
            signedDigest = digest("signed-$signerType"),
            signatureValue = "0".repeat(128),
            createdAt = AUTHORIZED_AT,
            publicKeyRef = publicKeyRef
        )
    }

    private fun digest(value: String): String = GenesisUltraHashProfile.sha256(
        value.toByteArray(StandardCharsets.UTF_8)
    )

    private companion object {
        const val AUTHORIZED_AT = "2026-07-25T12:00:00Z"
        const val COMMITTED_AT = "2026-07-25T12:10:00Z"
        const val EXPIRES_AT = "2026-07-25T12:20:00Z"
        const val BIRTH_ID = "birth_0123456789abcdef"
        const val INSTANCE_ID =
            "inst_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val BODY_ID =
            "body_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val COMPANION_NAME = "Morimil"
        const val EVENT_HASH =
            "evsha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
