package com.morimil.app.data.repository

import com.morimil.app.core.memory.CognitiveMigrationPlanner
import com.morimil.app.data.genesis.ultra.CognitiveMigrationCanonicalReadPort
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentityRepository
import com.morimil.app.data.genesis.ultra.VerifiedCognitiveMigrationPlanningInput
import com.morimil.app.data.genesis.ultra.VerifiedCognitiveMigrationSource
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.MorimilDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CognitiveMigrationRepositoryTest {
    @Test
    fun repositoryDependsOnCanonicalBoundariesAndNotLegacyMemory() {
        val parameterTypes = CognitiveMigrationRepository::class.java
            .declaredConstructors
            .flatMap { constructor -> constructor.parameterTypes.toList() }

        assertTrue(parameterTypes.contains(MemoryOrganDatabase::class.java))
        assertTrue(parameterTypes.contains(GenesisUltraRuntimeIdentityRepository::class.java))
        assertTrue(parameterTypes.contains(CognitiveMigrationCanonicalReadPort::class.java))
        assertTrue(parameterTypes.contains(CrossDatabaseOperationCoordinator::class.java))
        assertFalse(parameterTypes.contains(MorimilDatabase::class.java))
        assertFalse(parameterTypes.contains(MemoryRepository::class.java))
    }

    @Test
    fun verifiedPlanIdentitiesAreDeterministicAndContainNoClockIdentity() {
        val input = verifiedInput()
        val first = CognitiveMigrationPlanner.buildVerifiedPlan(input)
        val replay = CognitiveMigrationPlanner.buildVerifiedPlan(input)

        assertEquals(first, replay)
        assertTrue(first.proposalId.matches(Regex("^cog_proposal_[a-f0-9]{64}$")))
        assertTrue(first.migrationId.matches(Regex("^cog_migration_[a-f0-9]{64}$")))
        assertTrue(first.planCoreDigest.matches(Regex("^sha256:[a-f0-9]{64}$")))
        assertTrue(first.plannedRecordDigest.matches(Regex("^sha256:[a-f0-9]{64}$")))
        assertFalse(first.planCoreJson.contains("created_at"))
        assertFalse(first.plannedRecordJson.contains("created_at"))
        assertFalse(first.plannedRecordJson.contains("updated_at"))
        assertTrue(first.expectedEffect.contains("append_only_original_memory_unchanged"))
    }

    @Test
    fun changedVerifiedSourceChangesProposalAndMigrationIdentities() {
        val first = CognitiveMigrationPlanner.buildVerifiedPlan(verifiedInput())
        val changed = CognitiveMigrationPlanner.buildVerifiedPlan(
            verifiedInput().copy(
                sources = listOf(
                    verifiedInput().sources.single().copy(
                        eventHash = "evsha256:" + "9".repeat(64),
                        content = "different verified content"
                    )
                ),
                sourceSetDigest = "sha256:" + "8".repeat(64)
            )
        )

        assertNotEquals(first.planCoreDigest, changed.planCoreDigest)
        assertNotEquals(first.proposalId, changed.proposalId)
        assertNotEquals(first.migrationId, changed.migrationId)
    }

    @Test
    fun acceptedLocalResultVectorsRemainByteExact() {
        val vectors = listOf(
            VECTOR_COG_001 to "b8096f410eb77c98cffbf0b7c9ac972ca59b57286061dee1cff9b4ff82226a17",
            VECTOR_COG_002 to "fa3194ee6084af0450937446707d24da7a0f3fa1691b46c7cb205dc7c1d9249a",
            VECTOR_COG_003_COMPLETED to
                "29b75d95b4d7119145ed30b79afa004287ea75051a66d1bf469eed2938f0b299",
            VECTOR_COG_003_FAILED to
                "3c3c773ae40ffa8b06301ddeab784da01f224563bf10e223900b694fff98ad6f",
            VECTOR_COG_004 to "e9ef67abdcc2c14ba12f918c8d5276d291545f89053f609e730f881884e9625b"
        )

        vectors.forEach { (json, expectedRawHash) ->
            assertEquals(
                "sha256:$expectedRawHash",
                CrossDatabaseOperationIdentity.digestCanonicalJson(json)
            )
        }
    }

    private fun verifiedInput(): VerifiedCognitiveMigrationPlanningInput {
        return VerifiedCognitiveMigrationPlanningInput(
            instanceId = "instance_test",
            writerBodyId = "body_test",
            writerEpoch = "epoch_test",
            canonicalBirthRootHash = "evsha256:" + "1".repeat(64),
            canonicalLastSequence = 12,
            canonicalLastEventHash = "evsha256:" + "2".repeat(64),
            canonicalRecordSetDigest = "sha256:" + "3".repeat(64),
            canonicalPreSnapshotHash = "sha256:" + "4".repeat(64),
            sourceSetDigest = "sha256:" + "5".repeat(64),
            sources = listOf(
                VerifiedCognitiveMigrationSource(
                    eventId = "memory_test",
                    eventHash = "evsha256:" + "6".repeat(64),
                    sequence = 12,
                    eventType = "memory.user_confirmed",
                    actor = "user",
                    content = "Verified canonical content",
                    observedAt = "2026-07-29T00:00:00Z",
                    provenanceDigest = "sha256:" + "7".repeat(64)
                )
            )
        )
    }

    private companion object {
        const val VECTOR_COG_001 =
            """{"canonical_event_hash":"evsha256:8111111111111111111111111111111111111111111111111111111111111111","canonical_event_id":"xevt_03ade88b6059215e97f192f91bcdb8f665b4805041e85c99fe4a6e487f048004","canonical_provenance_digest":"sha256:8211111111111111111111111111111111111111111111111111111111111111","canonical_sequence":101,"migration_id":"cog_migration_07529f57cb85d4a36a7b57a7e8d383a5d997c798fec097e55a2c98d36643857a","owner_status":"planned","planned_record_digest":"sha256:a0c73a3fd3020e5ad4eacdcf054e04faa113669df03fce087cd74e95e6bdecd1","proposal_id":"cog_proposal_d9a9da5c4c6dd042c4a6a1ae8ad0e64fde1f988f0574b4cce01a89a248afbf90","record_inserted":true,"reused_existing_event":false,"schema":"morimil.cognitive_migration.cog_001.local_result.v1"}"""
        const val VECTOR_COG_002 =
            """{"approval_id":"xop_fd5bf2a290896d2dab465d28b1497e9e56b1056c5abc499de0a2dd67ca6bba37","canonical_event_hash":"evsha256:8222222222222222222222222222222222222222222222222222222222222222","canonical_event_id":"xevt_58b11ddec834e0134f06ba3a51cc999de47c724e78ee093e4fbf924f9e84babf","canonical_provenance_digest":"sha256:8322222222222222222222222222222222222222222222222222222222222222","canonical_sequence":102,"migration_id":"cog_migration_07529f57cb85d4a36a7b57a7e8d383a5d997c798fec097e55a2c98d36643857a","owner_status":"approved","planned_record_digest":"sha256:a0c73a3fd3020e5ad4eacdcf054e04faa113669df03fce087cd74e95e6bdecd1","record_updated":true,"reused_existing_event":false,"schema":"morimil.cognitive_migration.cog_002.local_result.v1"}"""
        const val VECTOR_COG_003_COMPLETED =
            """{"audit_chain_verified":true,"audit_notes":["canonical_chain_verified","append_only_refinement_committed"],"canonical_event_hash":"evsha256:8333333333333333333333333333333333333333333333333333333333333333","canonical_event_id":"xevt_5e5895a52af4313f50522313756daf03152ca2baa737309f3738975dbae0819f","canonical_provenance_digest":"sha256:8433333333333333333333333333333333333333333333333333333333333333","canonical_sequence":103,"migration_id":"cog_migration_07529f57cb85d4a36a7b57a7e8d383a5d997c798fec097e55a2c98d36643857a","migration_outcome":"completed","owner_status":"completed","planned_record_digest":"sha256:a0c73a3fd3020e5ad4eacdcf054e04faa113669df03fce087cd74e95e6bdecd1","post_snapshot_id":"sha256:8333333333333333333333333333333333333333333333333333333333333333","record_updated":true,"reused_existing_event":false,"schema":"morimil.cognitive_migration.cog_003.local_result.v1"}"""
        const val VECTOR_COG_003_FAILED =
            """{"audit_chain_verified":false,"audit_notes":["canonical_chain_audit_failed"],"canonical_event_hash":"evsha256:8333333333333333333333333333333333333333333333333333333333333333","canonical_event_id":"xevt_5e5895a52af4313f50522313756daf03152ca2baa737309f3738975dbae0819f","canonical_provenance_digest":"sha256:8433333333333333333333333333333333333333333333333333333333333333","canonical_sequence":103,"migration_id":"cog_migration_07529f57cb85d4a36a7b57a7e8d383a5d997c798fec097e55a2c98d36643857a","migration_outcome":"failed","owner_status":"failed","planned_record_digest":"sha256:a0c73a3fd3020e5ad4eacdcf054e04faa113669df03fce087cd74e95e6bdecd1","post_snapshot_id":"sha256:8333333333333333333333333333333333333333333333333333333333333333","record_updated":true,"reused_existing_event":false,"schema":"morimil.cognitive_migration.cog_003.local_result.v1"}"""
        const val VECTOR_COG_004 =
            """{"canonical_event_hash":"evsha256:8444444444444444444444444444444444444444444444444444444444444444","canonical_event_id":"xevt_40a4edfe50e4b99045bd3c063045fdcc5a4412438898fca1522fadffe6963b03","canonical_provenance_digest":"sha256:8544444444444444444444444444444444444444444444444444444444444444","canonical_sequence":104,"migration_id":"cog_migration_07529f57cb85d4a36a7b57a7e8d383a5d997c798fec097e55a2c98d36643857a","notes":["rollback_requested_by_user","append_only_compensation"],"owner_status":"rolled_back","planned_record_digest":"sha256:a0c73a3fd3020e5ad4eacdcf054e04faa113669df03fce087cd74e95e6bdecd1","predecessor_operation_id":"xop_504c4b50dffe5dee2af508dcf101877d377c766bef4f7e2336870548f9ee2069","record_updated":true,"reused_existing_event":false,"rollback_operation_id":"xop_66c3ec395e9b96cf90830d3a8378225f78efe8059591473ef4b1a20aecb95bad","rollback_strategy_digest":"sha256:d5bab39c214551e3c28a380c4fa3a9bac9aa00fa4af069760a19252391ba46f7","schema":"morimil.cognitive_migration.cog_004.local_result.v1"}"""
    }
}
