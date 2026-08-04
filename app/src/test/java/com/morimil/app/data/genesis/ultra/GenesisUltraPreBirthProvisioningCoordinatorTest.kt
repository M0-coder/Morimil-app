package com.morimil.app.data.genesis.ultra

import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraPreBirthProvisioningCoordinatorTest {
    @Test
    fun bodyProvisioningRequiresPresenceAndTheExactBodyRequiredState() = runBlocking {
        val fixture = Fixture()
        val missingPresence = runCatching {
            GenesisUltraBodyProvisioningCeremonyRequest(
                confirmationPurpose =
                    GenesisUltraBodyProvisioningCeremonyRequest.CONFIRMATION_PURPOSE,
                userPresenceConfirmed = false
            )
        }.exceptionOrNull()

        assertNotNull(missingPresence)
        assertEquals(0, fixture.bodyProvisionCalls)

        fixture.legacyLocalIdentityCount = 1
        val blocked = runCatching {
            fixture.coordinator.provisionBody(bodyRequest())
        }.exceptionOrNull()

        assertNotNull(blocked)
        assertEquals(0, fixture.bodyProvisionCalls)
        assertEquals(GenesisUltraBodyIdentityRootState.ABSENT, fixture.bodyState)
    }

    @Test
    fun explicitBodyCeremonyCreatesAReconstructableReceiptAndAdvancesOnce() = runBlocking {
        val fixture = Fixture()

        val result = fixture.coordinator.provisionBody(bodyRequest())
        val restarted = fixture.coordinator.inspect()

        assertEquals(1, fixture.bodyProvisionCalls)
        assertEquals(
            GenesisUltraBirthPreparationStatus.GUARDIAN_TRUST_REQUIRED,
            result.assessment.status
        )
        assertEquals(fixture.bodyRoot.bodyId, result.bodyReceipt?.bodyId)
        assertEquals(result.bodyReceipt, restarted.bodyReceipt)
        assertTrue(requireNotNull(result.bodyReceipt).receiptDigest.startsWith("sha256:"))
        assertFalse(result.assessment.birthCommitAuthorized)

        val replay = runCatching {
            fixture.coordinator.provisionBody(bodyRequest())
        }.exceptionOrNull()
        assertNotNull(replay)
        assertEquals(1, fixture.bodyProvisionCalls)
    }

    @Test
    fun guardianCeremonyRequiresIndependentFingerprintAndLocalPresence() {
        val preview = GenesisUltraGuardianPublicKeyPreview(GUARDIAN_PUBLIC_KEY)

        val missingIndependentConfirmation = runCatching {
            preview.ceremonyRequest(
                guardianId = GUARDIAN_ID,
                keyEpochId = GUARDIAN_EPOCH_ID,
                confirmedPublicKeyRef = preview.publicKeyRef,
                independentConfirmationAcknowledged = false,
                userPresenceConfirmed = true
            )
        }.exceptionOrNull()
        val missingPresence = runCatching {
            preview.ceremonyRequest(
                guardianId = GUARDIAN_ID,
                keyEpochId = GUARDIAN_EPOCH_ID,
                confirmedPublicKeyRef = preview.publicKeyRef,
                independentConfirmationAcknowledged = true,
                userPresenceConfirmed = false
            )
        }.exceptionOrNull()
        val wrongFingerprint = runCatching {
            preview.ceremonyRequest(
                guardianId = GUARDIAN_ID,
                keyEpochId = GUARDIAN_EPOCH_ID,
                confirmedPublicKeyRef = "sha256:" + "0".repeat(64),
                independentConfirmationAcknowledged = true,
                userPresenceConfirmed = true
            )
        }.exceptionOrNull()

        assertNotNull(missingIndependentConfirmation)
        assertNotNull(missingPresence)
        assertNotNull(wrongFingerprint)
    }

    @Test
    fun exactGuardianCeremonyPinsOnceAndReachesSignedCandidateReadiness() = runBlocking {
        val fixture = Fixture(bodyState = GenesisUltraBodyIdentityRootState.READY)
        val preview = GenesisUltraGuardianPublicKeyPreview(GUARDIAN_PUBLIC_KEY)

        val result = fixture.coordinator.provisionGuardian(
            preview.ceremonyRequest(
                guardianId = GUARDIAN_ID,
                keyEpochId = GUARDIAN_EPOCH_ID,
                confirmedPublicKeyRef = preview.publicKeyRef,
                independentConfirmationAcknowledged = true,
                userPresenceConfirmed = true
            )
        )

        assertEquals(1, fixture.guardianProvisionCalls)
        assertEquals(
            GenesisUltraBirthPreparationStatus.READY_FOR_SIGNED_CANDIDATE,
            result.assessment.status
        )
        assertEquals(GUARDIAN_ID, result.guardianReceipt?.guardianId)
        assertEquals(preview.publicKeyRef, result.guardianReceipt?.publicKeyRef)
        assertTrue(requireNotNull(result.guardianReceipt).receiptDigest.startsWith("sha256:"))
        assertFalse(result.assessment.birthCommitAuthorized)
    }

    @Test
    fun rawGuardianReaderRejectsEverySizeExceptExactlyThirtyTwoBytes() {
        val exact = readExactGuardianPublicKey(ByteArrayInputStream(GUARDIAN_PUBLIC_KEY))
        val shortFailure = runCatching {
            readExactGuardianPublicKey(ByteArrayInputStream(ByteArray(31)))
        }.exceptionOrNull()
        val longFailure = runCatching {
            readExactGuardianPublicKey(ByteArrayInputStream(ByteArray(33)))
        }.exceptionOrNull()

        assertArrayEquals(GUARDIAN_PUBLIC_KEY, exact)
        assertNotNull(shortFailure)
        assertNotNull(longFailure)
    }

    private class Fixture(
        bodyState: GenesisUltraBodyIdentityRootState = GenesisUltraBodyIdentityRootState.ABSENT,
        guardianState: GenesisUltraGuardianTrustAnchorState =
            GenesisUltraGuardianTrustAnchorState.ABSENT
    ) {
        var bodyState = bodyState
        var guardianState = guardianState
        var legacyLocalIdentityCount = 0
        var bodyProvisionCalls = 0
        var guardianProvisionCalls = 0

        val bodyRoot: GenesisUltraBodyIdentityRoot = run {
            val publicKey = ByteArray(32) { index -> (index + 65).toByte() }
            val publicKeyRef = GenesisUltraHashProfile.sha256(publicKey)
            GenesisUltraBodyIdentityRoot(
                schemaVersion = GenesisUltraBodyIdentityRoot.ROOT_SCHEMA,
                bodyId = GenesisUltraBodyIdentityRoot.bodyIdFor(publicKeyRef),
                keyEpochId = GenesisUltraBodyIdentityRoot.keyEpochIdFor(publicKeyRef),
                publicKeyRef = publicKeyRef,
                protectionProfile = GenesisUltraBodyIdentityRoot.PROTECTION_PROFILE,
                rawPublicKey = publicKey
            )
        }
        val guardianAnchor = GenesisUltraGuardianTrustAnchor(
            schemaVersion = GenesisUltraGuardianTrustAnchor.ANCHOR_SCHEMA,
            guardianId = GUARDIAN_ID,
            keyEpochId = GUARDIAN_EPOCH_ID,
            publicKeyRef = GenesisUltraHashProfile.sha256(GUARDIAN_PUBLIC_KEY),
            status = GenesisUltraGuardianTrustAnchor.ACTIVE_STATUS,
            confirmationMode = GenesisUltraGuardianTrustAnchor.CONFIRMATION_MODE,
            confirmationPurpose =
                GenesisUltraGuardianTrustAnchorProvisioningRequest.CONFIRMATION_PURPOSE,
            protectionProfile = GenesisUltraGuardianTrustAnchor.PROTECTION_PROFILE,
            pinnedAtMillis = 1_786_000_000_000L,
            rawPublicKey = GUARDIAN_PUBLIC_KEY
        )

        val coordinator = GenesisUltraPreBirthProvisioningCoordinator(
            inspectPreparation = {
                GenesisUltraBirthPreparationClassifier.assess(
                    GenesisUltraBirthPreparationFacts(
                        persistedBirthState = GenesisUltraPersistedBirthState.ABSENT,
                        bodyIdentityRootState = this@Fixture.bodyState,
                        guardianTrustAnchorState = this@Fixture.guardianState,
                        legacyLocalIdentityCount = legacyLocalIdentityCount,
                        legacyGenesisCoreCount = 0,
                        canonicalMemoryEventCount = 0
                    )
                )
            },
            provisionBodyRoot = {
                bodyProvisionCalls += 1
                this@Fixture.bodyState = GenesisUltraBodyIdentityRootState.READY
                bodyRoot
            },
            loadBodyRoot = {
                check(this@Fixture.bodyState == GenesisUltraBodyIdentityRootState.READY)
                bodyRoot
            },
            provisionGuardianAnchor = { request ->
                guardianProvisionCalls += 1
                check(request.confirmedPublicKeyRef == guardianAnchor.publicKeyRef)
                this@Fixture.guardianState = GenesisUltraGuardianTrustAnchorState.READY
                guardianAnchor
            },
            loadGuardianAnchor = {
                check(this@Fixture.guardianState == GenesisUltraGuardianTrustAnchorState.READY)
                guardianAnchor
            }
        )
    }

    private fun bodyRequest() = GenesisUltraBodyProvisioningCeremonyRequest(
        confirmationPurpose = GenesisUltraBodyProvisioningCeremonyRequest.CONFIRMATION_PURPOSE,
        userPresenceConfirmed = true
    )

    private companion object {
        const val GUARDIAN_ID = "guardian_01HMORIMILCUSTODIAN0001"
        const val GUARDIAN_EPOCH_ID = "guardian_epoch_01HMORIMIL000001"
        val GUARDIAN_PUBLIC_KEY = ByteArray(32) { index -> (index + 1).toByte() }
    }
}
