package com.morimil.app.data.genesis.ultra

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraHostBirthConsentTest {
    @Test
    fun confirmationRequestRequiresExactInteractiveApproval() {
        val digest = "sha256:" + "a".repeat(64)
        val request = GenesisUltraHostBirthConsentRequest(
            presentedCandidateDigest = digest,
            presentedInstanceId = "inst_" + "b".repeat(64),
            presentedCompanionName = "Morimil",
            presentedConfirmationCode = "a".repeat(12),
            decision = GenesisUltraHostBirthConsentRequest.APPROVE_DECISION,
            confirmationMode = GenesisUltraHostBirthConsentRequest.INTERACTIVE_CONFIRMATION_MODE,
            confirmationPurpose = GenesisUltraHostBirthConsentRequest.BIRTH_CONFIRMATION_PURPOSE,
            userPresenceConfirmed = true
        )

        assertTrue(
            request.presentedConfirmationCode ==
                GenesisUltraHostBirthConsentRequest.confirmationCode(digest)
        )
        assertTrue(
            runCatching {
                request.copy(userPresenceConfirmed = false)
            }.exceptionOrNull()?.message.orEmpty().contains("user_presence_required")
        )
        assertTrue(
            runCatching {
                request.copy(decision = "decline_birth")
            }.exceptionOrNull()?.message.orEmpty().contains("decision_invalid")
        )
    }

    @Test
    fun verifiedConsentIsCandidateBoundAndCannotAuthorizeCommit() {
        val fields = Fields()
        val digest = GenesisUltraVerifiedHostBirthConsent.digestForFields(
            schemaVersion = fields.schemaVersion,
            consentId = fields.consentId,
            candidateDigest = fields.candidateDigest,
            instanceId = fields.instanceId,
            companionName = fields.companionName,
            seedRootHash = fields.seedRootHash,
            bodyId = fields.bodyId,
            guardianId = fields.guardianId,
            guardianKeyEpochId = fields.guardianKeyEpochId,
            decision = fields.decision,
            confirmationMode = fields.confirmationMode,
            confirmationPurpose = fields.confirmationPurpose,
            consentedAt = fields.consentedAt,
            expiresAt = fields.expiresAt,
            protectionProfile = fields.protectionProfile
        )
        val consent = fields.toConsent(digest)

        assertTrue(consent.isValidAt("2026-07-25T03:02:30Z"))
        assertFalse(consent.isValidAt(fields.expiresAt))
        assertFalse(consent.birthCommitAuthorized)

        val altered = runCatching {
            fields.copy(candidateDigest = "sha256:" + "9".repeat(64)).toConsent(digest)
        }.exceptionOrNull()
        assertTrue(altered?.message.orEmpty().contains("digest_mismatch"))
    }

    private data class Fields(
        val schemaVersion: String = GenesisUltraVerifiedHostBirthConsent.CONSENT_SCHEMA,
        val consentId: String = "consent_" + "1".repeat(64),
        val candidateDigest: String = "sha256:" + "2".repeat(64),
        val instanceId: String = "inst_" + "3".repeat(64),
        val companionName: String = "Morimil",
        val seedRootHash: String = "sha256:" + "4".repeat(64),
        val bodyId: String = "body_" + "5".repeat(64),
        val guardianId: String = "guardian_01HMORIMILCONSENT0001",
        val guardianKeyEpochId: String = "guardian_epoch_01HMORIMILCONSENT0001",
        val decision: String = GenesisUltraHostBirthConsentRequest.APPROVE_DECISION,
        val confirmationMode: String =
            GenesisUltraHostBirthConsentRequest.INTERACTIVE_CONFIRMATION_MODE,
        val confirmationPurpose: String =
            GenesisUltraHostBirthConsentRequest.BIRTH_CONFIRMATION_PURPOSE,
        val consentedAt: String = "2026-07-25T03:02:00Z",
        val expiresAt: String = "2026-07-25T03:04:00Z",
        val protectionProfile: String = GenesisUltraVerifiedHostBirthConsent.PROTECTION_PROFILE
    ) {
        fun toConsent(digest: String): GenesisUltraVerifiedHostBirthConsent {
            return GenesisUltraVerifiedHostBirthConsent(
                schemaVersion = schemaVersion,
                consentId = consentId,
                candidateDigest = candidateDigest,
                instanceId = instanceId,
                companionName = companionName,
                seedRootHash = seedRootHash,
                bodyId = bodyId,
                guardianId = guardianId,
                guardianKeyEpochId = guardianKeyEpochId,
                decision = decision,
                confirmationMode = confirmationMode,
                confirmationPurpose = confirmationPurpose,
                consentedAt = consentedAt,
                expiresAt = expiresAt,
                protectionProfile = protectionProfile,
                consentDigest = digest
            )
        }
    }
}
