package com.morimil.app.data.repository

import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.MigrationRecordEntity
import org.json.JSONArray

internal class RestRepairProposalStore(
    database: MemoryOrganDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val dao = database.memoryOrganDao()

    suspend fun ensurePlanned(
        migrationId: String,
        instanceId: String,
        birthRootEventHash: String,
        preSnapshotId: String,
        snapshotDigest: String,
        sourceSetDigest: String,
        proposalDigest: String,
        report: RestRepairProposalReport
    ): MigrationRecordEntity {
        require(report.hasCandidates) { "rest_repair_candidates_empty" }
        val candidate = buildCandidate(
            migrationId = migrationId,
            instanceId = instanceId,
            birthRootEventHash = birthRootEventHash,
            preSnapshotId = preSnapshotId,
            snapshotDigest = snapshotDigest,
            sourceSetDigest = sourceSetDigest,
            proposalDigest = proposalDigest,
            report = report,
            now = nowMillis()
        )
        val existing = dao.loadMigrationRecord(migrationId)
        if (existing != null) {
            requireSamePlan(existing, candidate)
            return existing
        }
        dao.insertMigrationRecord(candidate)
        return candidate
    }

    suspend fun load(migrationId: String): MigrationRecordEntity? = dao.loadMigrationRecord(migrationId)

    internal companion object {
        const val MIGRATION_TYPE = "rest_cycle.repair_proposal"
        const val STATUS_PLANNED = "planned"
        private const val CREATED_BY = "rest_repair_protocol"

        internal fun buildCandidate(
            migrationId: String,
            instanceId: String,
            birthRootEventHash: String,
            preSnapshotId: String,
            snapshotDigest: String,
            sourceSetDigest: String,
            proposalDigest: String,
            report: RestRepairProposalReport,
            now: Long
        ): MigrationRecordEntity {
            return MigrationRecordEntity(
                migrationId = migrationId,
                instanceId = instanceId,
                genesisCoreHash = birthRootEventHash,
                proposalId = null,
                migrationType = MIGRATION_TYPE,
                fromVersion = "canonical_snapshot:$snapshotDigest",
                toVersion = "canonical_memory_after_human_reviewed_repair",
                affectedArtifactsJson = JSONArray(report.affectedEventHashes).toString(),
                preSnapshotId = preSnapshotId,
                chainVerified = true,
                backupRequired = true,
                stepsJson = JSONArray(report.migrationSteps()).toString(),
                expectedEffect = buildString {
                    appendLine("rest_repair_schema=morimil.rest_repair_proposal.v2")
                    appendLine("mode=proposal_only")
                    appendLine("automatic_changes=false")
                    appendLine("approval_required=true")
                    appendLine("proposal_digest=$proposalDigest")
                    appendLine("source_set_digest=$sourceSetDigest")
                    appendLine("snapshot_digest=$snapshotDigest")
                    appendLine("candidate_count=${report.candidates.size}")
                    appendLine("risk_level=${report.riskLevel}")
                    appendLine("affected_events=${report.affectedEventHashes.joinToString(",")}")
                }.trim(),
                riskLevel = report.riskLevel,
                approvalRequired = true,
                approvedByUser = false,
                approvalId = null,
                status = STATUS_PLANNED,
                postSnapshotId = null,
                errorsJson = "[]",
                rollbackAvailable = true,
                rollbackStrategy = "proposal_only: no memory mutation occurs until a separately authorized append-only repair action exists",
                createdBy = CREATED_BY,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        }

        internal fun requireSamePlan(existing: MigrationRecordEntity, candidate: MigrationRecordEntity) {
            require(
                existing.instanceId == candidate.instanceId &&
                    existing.genesisCoreHash == candidate.genesisCoreHash &&
                    existing.migrationType == candidate.migrationType &&
                    existing.fromVersion == candidate.fromVersion &&
                    existing.toVersion == candidate.toVersion &&
                    existing.affectedArtifactsJson == candidate.affectedArtifactsJson &&
                    existing.preSnapshotId == candidate.preSnapshotId &&
                    existing.chainVerified == candidate.chainVerified &&
                    existing.backupRequired == candidate.backupRequired &&
                    existing.stepsJson == candidate.stepsJson &&
                    existing.expectedEffect == candidate.expectedEffect &&
                    existing.riskLevel == candidate.riskLevel &&
                    existing.approvalRequired &&
                    !existing.approvedByUser &&
                    existing.approvalId == null &&
                    existing.status == STATUS_PLANNED &&
                    existing.postSnapshotId == null &&
                    existing.rollbackAvailable == candidate.rollbackAvailable &&
                    existing.rollbackStrategy == candidate.rollbackStrategy &&
                    existing.createdBy == candidate.createdBy
            ) { "rest_repair_migration_id_payload_conflict" }
        }
    }
}
