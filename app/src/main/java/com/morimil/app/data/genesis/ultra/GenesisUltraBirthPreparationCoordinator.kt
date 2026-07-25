package com.morimil.app.data.genesis.ultra

import com.morimil.app.data.local.MorimilDatabase

internal enum class GenesisUltraBirthPreparationStatus {
    INCONSISTENT,
    ALREADY_COMMITTED,
    LEGACY_CONFLICT,
    BODY_IDENTITY_REQUIRED,
    GUARDIAN_TRUST_REQUIRED,
    READY_FOR_SIGNED_CANDIDATE
}

/**
 * Durable facts inspected before any Genesis Ultra candidate is constructed.
 * This is preparation state only; it is never an authorization to commit birth.
 */
internal data class GenesisUltraBirthPreparationFacts(
    val persistedBirthState: GenesisUltraPersistedBirthState,
    val bodyIdentityRootState: GenesisUltraBodyIdentityRootState,
    val guardianTrustAnchorState: GenesisUltraGuardianTrustAnchorState,
    val legacyLocalIdentityCount: Int,
    val legacyGenesisCoreCount: Int,
    val canonicalMemoryEventCount: Int
) {
    init {
        require(legacyLocalIdentityCount >= 0) { "birth_preparation_local_identity_count_invalid" }
        require(legacyGenesisCoreCount >= 0) { "birth_preparation_genesis_core_count_invalid" }
        require(canonicalMemoryEventCount >= 0) { "birth_preparation_canonical_memory_count_invalid" }
    }
}

internal data class GenesisUltraBirthPreparationAssessment(
    val status: GenesisUltraBirthPreparationStatus,
    val facts: GenesisUltraBirthPreparationFacts,
    val blockers: List<String>,
    val remainingRequirements: List<String>,
    val candidateConstructionReady: Boolean,
    val birthCommitAuthorized: Boolean
) {
    init {
        require(blockers.distinct().size == blockers.size) {
            "birth_preparation_duplicate_blocker"
        }
        require(remainingRequirements.distinct().size == remainingRequirements.size) {
            "birth_preparation_duplicate_requirement"
        }
        require(!birthCommitAuthorized) {
            "birth_preparation_cannot_authorize_commit"
        }
        require(candidateConstructionReady ==
            (status == GenesisUltraBirthPreparationStatus.READY_FOR_SIGNED_CANDIDATE)
        ) { "birth_preparation_candidate_readiness_mismatch" }
    }
}

/** Pure classifier kept separate from Android and Room for exhaustive JVM tests. */
internal object GenesisUltraBirthPreparationClassifier {
    fun assess(facts: GenesisUltraBirthPreparationFacts): GenesisUltraBirthPreparationAssessment {
        val inconsistencies = buildList {
            if (facts.persistedBirthState == GenesisUltraPersistedBirthState.INCONSISTENT) {
                add("persisted_birth_inconsistent")
            }
            if (facts.bodyIdentityRootState == GenesisUltraBodyIdentityRootState.INCONSISTENT) {
                add("body_identity_root_inconsistent")
            }
            if (facts.guardianTrustAnchorState == GenesisUltraGuardianTrustAnchorState.INCONSISTENT) {
                add("guardian_trust_anchor_inconsistent")
            }
            if (
                facts.persistedBirthState == GenesisUltraPersistedBirthState.ABSENT &&
                facts.canonicalMemoryEventCount > 0
            ) {
                add("orphan_canonical_memory_events")
            }
        }
        if (inconsistencies.isNotEmpty()) {
            return assessment(
                status = GenesisUltraBirthPreparationStatus.INCONSISTENT,
                facts = facts,
                blockers = inconsistencies
            )
        }

        if (facts.persistedBirthState == GenesisUltraPersistedBirthState.COMMITTED) {
            return assessment(
                status = GenesisUltraBirthPreparationStatus.ALREADY_COMMITTED,
                facts = facts,
                blockers = listOf("genesis_ultra_birth_already_committed")
            )
        }

        val legacyConflicts = buildList {
            if (facts.legacyLocalIdentityCount > 0) add("legacy_local_identity_present")
            if (facts.legacyGenesisCoreCount > 0) add("legacy_genesis_core_present")
        }
        if (legacyConflicts.isNotEmpty()) {
            return assessment(
                status = GenesisUltraBirthPreparationStatus.LEGACY_CONFLICT,
                facts = facts,
                blockers = legacyConflicts
            )
        }

        if (facts.bodyIdentityRootState == GenesisUltraBodyIdentityRootState.ABSENT) {
            return assessment(
                status = GenesisUltraBirthPreparationStatus.BODY_IDENTITY_REQUIRED,
                facts = facts,
                blockers = listOf("body_identity_root_not_provisioned")
            )
        }

        if (facts.guardianTrustAnchorState == GenesisUltraGuardianTrustAnchorState.ABSENT) {
            return assessment(
                status = GenesisUltraBirthPreparationStatus.GUARDIAN_TRUST_REQUIRED,
                facts = facts,
                blockers = listOf("guardian_trust_anchor_not_pinned")
            )
        }

        return assessment(
            status = GenesisUltraBirthPreparationStatus.READY_FOR_SIGNED_CANDIDATE,
            facts = facts,
            blockers = emptyList(),
            remainingRequirements = listOf(
                "signed_seed_release_not_verified",
                "canonical_companion_name_not_confirmed",
                "atomic_birth_evidence_not_verified",
                "explicit_host_birth_consent_not_recorded"
            )
        )
    }

    private fun assessment(
        status: GenesisUltraBirthPreparationStatus,
        facts: GenesisUltraBirthPreparationFacts,
        blockers: List<String>,
        remainingRequirements: List<String> = emptyList()
    ): GenesisUltraBirthPreparationAssessment {
        return GenesisUltraBirthPreparationAssessment(
            status = status,
            facts = facts,
            blockers = blockers,
            remainingRequirements = remainingRequirements,
            candidateConstructionReady =
                status == GenesisUltraBirthPreparationStatus.READY_FOR_SIGNED_CANDIDATE,
            birthCommitAuthorized = false
        )
    }
}

/**
 * Reads the actual Android/Room state needed before candidate construction.
 * It does not provision keys, pin authority, install a Seed, sign evidence or
 * invoke the atomic activation coordinator.
 */
internal class GenesisUltraBirthPreparationCoordinator(
    private val database: MorimilDatabase,
    private val bodyIdentityRootStore: GenesisUltraAndroidBodyIdentityRootStore,
    private val guardianTrustAnchorStore: GenesisUltraAndroidGuardianTrustAnchorStore
) {
    suspend fun inspect(): GenesisUltraBirthPreparationAssessment {
        val memoryDao = database.memoryDao()
        val facts = GenesisUltraBirthPreparationFacts(
            persistedBirthState = GenesisUltraAtomicBirthStore(database).readState(),
            bodyIdentityRootState = bodyIdentityRootStore.readState(),
            guardianTrustAnchorState = guardianTrustAnchorStore.readState(),
            legacyLocalIdentityCount = memoryDao.countLocalIdentity(),
            legacyGenesisCoreCount = memoryDao.countGenesisCore(),
            canonicalMemoryEventCount = database.genesisUltraMemoryDao().countAll()
        )
        return GenesisUltraBirthPreparationClassifier.assess(facts)
    }
}
