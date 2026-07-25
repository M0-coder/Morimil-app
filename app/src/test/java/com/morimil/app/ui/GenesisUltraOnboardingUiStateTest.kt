package com.morimil.app.ui

import com.morimil.app.data.genesis.ultra.GenesisUltraBirthPreparationAssessment
import com.morimil.app.data.genesis.ultra.GenesisUltraBirthPreparationClassifier
import com.morimil.app.data.genesis.ultra.GenesisUltraBirthPreparationFacts
import com.morimil.app.data.genesis.ultra.GenesisUltraBirthPreparationStatus
import com.morimil.app.data.genesis.ultra.GenesisUltraBodyIdentityRootState
import com.morimil.app.data.genesis.ultra.GenesisUltraGuardianTrustAnchorState
import com.morimil.app.data.genesis.ultra.GenesisUltraPersistedBirthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraOnboardingUiStateTest {
    @Test
    fun onlyACommittedUltraBirthCanRouteToRuntime() {
        GenesisUltraBirthPreparationStatus.entries.forEach { status ->
            val state = GenesisUltraOnboardingUiStateMapper.from(assessment(status))
            val expected = if (status == GenesisUltraBirthPreparationStatus.ALREADY_COMMITTED) {
                GenesisUltraAppRoute.RUNTIME
            } else {
                GenesisUltraAppRoute.ONBOARDING
            }
            assertEquals(status.name, expected, state.route)
            assertFalse(state.birthCommitAuthorized)
        }
    }

    @Test
    fun legacyIdentityStaysBlockedOutsideTheRuntime() {
        val state = GenesisUltraOnboardingUiStateMapper.from(
            GenesisUltraBirthPreparationClassifier.assess(
                facts(
                    legacyLocalIdentityCount = 1,
                    legacyGenesisCoreCount = 1
                )
            )
        )

        assertEquals(GenesisUltraAppRoute.ONBOARDING, state.route)
        assertEquals(GenesisUltraBirthPreparationStatus.LEGACY_CONFLICT, state.preparationStatus)
        assertFalse(state.canonicalNameInputEnabled)
        assertTrue(state.blockers.contains("legacy_local_identity_present"))
    }

    @Test
    fun preparedInfrastructureAllowsNameDraftButNotBirth() {
        val state = GenesisUltraOnboardingUiStateMapper.from(
            GenesisUltraBirthPreparationClassifier.assess(
                facts(
                    bodyState = GenesisUltraBodyIdentityRootState.READY,
                    guardianState = GenesisUltraGuardianTrustAnchorState.READY
                )
            )
        )

        assertEquals(
            GenesisUltraBirthPreparationStatus.READY_FOR_SIGNED_CANDIDATE,
            state.preparationStatus
        )
        assertTrue(state.canonicalNameInputEnabled)
        assertTrue(state.candidateConstructionReady)
        assertFalse(state.birthCommitAuthorized)
        assertEquals(GenesisUltraAppRoute.ONBOARDING, state.route)
    }

    private fun assessment(
        status: GenesisUltraBirthPreparationStatus
    ): GenesisUltraBirthPreparationAssessment {
        return when (status) {
            GenesisUltraBirthPreparationStatus.INCONSISTENT ->
                GenesisUltraBirthPreparationClassifier.assess(
                    facts(birthState = GenesisUltraPersistedBirthState.INCONSISTENT)
                )

            GenesisUltraBirthPreparationStatus.ALREADY_COMMITTED ->
                GenesisUltraBirthPreparationClassifier.assess(
                    facts(
                        birthState = GenesisUltraPersistedBirthState.COMMITTED,
                        bodyState = GenesisUltraBodyIdentityRootState.READY,
                        guardianState = GenesisUltraGuardianTrustAnchorState.READY,
                        canonicalMemoryEventCount = 1
                    )
                )

            GenesisUltraBirthPreparationStatus.LEGACY_CONFLICT ->
                GenesisUltraBirthPreparationClassifier.assess(
                    facts(legacyLocalIdentityCount = 1)
                )

            GenesisUltraBirthPreparationStatus.BODY_IDENTITY_REQUIRED ->
                GenesisUltraBirthPreparationClassifier.assess(facts())

            GenesisUltraBirthPreparationStatus.GUARDIAN_TRUST_REQUIRED ->
                GenesisUltraBirthPreparationClassifier.assess(
                    facts(bodyState = GenesisUltraBodyIdentityRootState.READY)
                )

            GenesisUltraBirthPreparationStatus.READY_FOR_SIGNED_CANDIDATE ->
                GenesisUltraBirthPreparationClassifier.assess(
                    facts(
                        bodyState = GenesisUltraBodyIdentityRootState.READY,
                        guardianState = GenesisUltraGuardianTrustAnchorState.READY
                    )
                )
        }
    }

    private fun facts(
        birthState: GenesisUltraPersistedBirthState = GenesisUltraPersistedBirthState.ABSENT,
        bodyState: GenesisUltraBodyIdentityRootState = GenesisUltraBodyIdentityRootState.ABSENT,
        guardianState: GenesisUltraGuardianTrustAnchorState =
            GenesisUltraGuardianTrustAnchorState.ABSENT,
        legacyLocalIdentityCount: Int = 0,
        legacyGenesisCoreCount: Int = 0,
        canonicalMemoryEventCount: Int = 0
    ): GenesisUltraBirthPreparationFacts {
        return GenesisUltraBirthPreparationFacts(
            persistedBirthState = birthState,
            bodyIdentityRootState = bodyState,
            guardianTrustAnchorState = guardianState,
            legacyLocalIdentityCount = legacyLocalIdentityCount,
            legacyGenesisCoreCount = legacyGenesisCoreCount,
            canonicalMemoryEventCount = canonicalMemoryEventCount
        )
    }
}
