package com.morimil.app.data.genesis.ultra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraBirthPreparationCoordinatorTest {
    @Test
    fun cleanStoresWithoutKeysRequireTheBodyIdentityFirst() {
        val assessment = assess()

        assertEquals(
            GenesisUltraBirthPreparationStatus.BODY_IDENTITY_REQUIRED,
            assessment.status
        )
        assertEquals(listOf("body_identity_root_not_provisioned"), assessment.blockers)
        assertFalse(assessment.candidateConstructionReady)
        assertFalse(assessment.birthCommitAuthorized)
    }

    @Test
    fun bodyIdentityWithoutGuardianAnchorRequiresExplicitGuardianTrust() {
        val assessment = assess(bodyState = GenesisUltraBodyIdentityRootState.READY)

        assertEquals(
            GenesisUltraBirthPreparationStatus.GUARDIAN_TRUST_REQUIRED,
            assessment.status
        )
        assertEquals(listOf("guardian_trust_anchor_not_pinned"), assessment.blockers)
        assertFalse(assessment.candidateConstructionReady)
    }

    @Test
    fun preparedInfrastructureStillCannotAuthorizeBirthCommit() {
        val assessment = assess(
            bodyState = GenesisUltraBodyIdentityRootState.READY,
            guardianState = GenesisUltraGuardianTrustAnchorState.READY
        )

        assertEquals(
            GenesisUltraBirthPreparationStatus.READY_FOR_SIGNED_CANDIDATE,
            assessment.status
        )
        assertTrue(assessment.candidateConstructionReady)
        assertFalse(assessment.birthCommitAuthorized)
        assertTrue(assessment.blockers.isEmpty())
        assertEquals(
            listOf(
                "signed_seed_release_not_verified",
                "canonical_companion_name_not_confirmed",
                "atomic_birth_evidence_not_verified",
                "explicit_host_birth_consent_not_recorded"
            ),
            assessment.remainingRequirements
        )
    }

    @Test
    fun legacyIdentityAndGenesisCoreBlockCandidateConstruction() {
        val assessment = assess(
            bodyState = GenesisUltraBodyIdentityRootState.READY,
            guardianState = GenesisUltraGuardianTrustAnchorState.READY,
            legacyLocalIdentityCount = 1,
            legacyGenesisCoreCount = 1
        )

        assertEquals(GenesisUltraBirthPreparationStatus.LEGACY_CONFLICT, assessment.status)
        assertEquals(
            listOf("legacy_local_identity_present", "legacy_genesis_core_present"),
            assessment.blockers
        )
        assertFalse(assessment.candidateConstructionReady)
    }

    @Test
    fun canonicalEventsWithoutBirthAreTreatedAsInconsistent() {
        val assessment = assess(
            bodyState = GenesisUltraBodyIdentityRootState.READY,
            guardianState = GenesisUltraGuardianTrustAnchorState.READY,
            canonicalMemoryEventCount = 1
        )

        assertEquals(GenesisUltraBirthPreparationStatus.INCONSISTENT, assessment.status)
        assertEquals(listOf("orphan_canonical_memory_events"), assessment.blockers)
    }

    @Test
    fun anyCryptographicStoreInconsistencyHasPriorityOverMissingRequirements() {
        val assessment = assess(
            birthState = GenesisUltraPersistedBirthState.INCONSISTENT,
            bodyState = GenesisUltraBodyIdentityRootState.INCONSISTENT,
            guardianState = GenesisUltraGuardianTrustAnchorState.INCONSISTENT
        )

        assertEquals(GenesisUltraBirthPreparationStatus.INCONSISTENT, assessment.status)
        assertEquals(
            listOf(
                "persisted_birth_inconsistent",
                "body_identity_root_inconsistent",
                "guardian_trust_anchor_inconsistent"
            ),
            assessment.blockers
        )
    }

    @Test
    fun committedBirthNeverReturnsToCandidateConstruction() {
        val assessment = assess(
            birthState = GenesisUltraPersistedBirthState.COMMITTED,
            bodyState = GenesisUltraBodyIdentityRootState.READY,
            guardianState = GenesisUltraGuardianTrustAnchorState.READY,
            canonicalMemoryEventCount = 1
        )

        assertEquals(GenesisUltraBirthPreparationStatus.ALREADY_COMMITTED, assessment.status)
        assertEquals(listOf("genesis_ultra_birth_already_committed"), assessment.blockers)
        assertFalse(assessment.candidateConstructionReady)
        assertFalse(assessment.birthCommitAuthorized)
    }

    private fun assess(
        birthState: GenesisUltraPersistedBirthState = GenesisUltraPersistedBirthState.ABSENT,
        bodyState: GenesisUltraBodyIdentityRootState = GenesisUltraBodyIdentityRootState.ABSENT,
        guardianState: GenesisUltraGuardianTrustAnchorState =
            GenesisUltraGuardianTrustAnchorState.ABSENT,
        legacyLocalIdentityCount: Int = 0,
        legacyGenesisCoreCount: Int = 0,
        canonicalMemoryEventCount: Int = 0
    ): GenesisUltraBirthPreparationAssessment {
        return GenesisUltraBirthPreparationClassifier.assess(
            GenesisUltraBirthPreparationFacts(
                persistedBirthState = birthState,
                bodyIdentityRootState = bodyState,
                guardianTrustAnchorState = guardianState,
                legacyLocalIdentityCount = legacyLocalIdentityCount,
                legacyGenesisCoreCount = legacyGenesisCoreCount,
                canonicalMemoryEventCount = canonicalMemoryEventCount
            )
        )
    }
}
