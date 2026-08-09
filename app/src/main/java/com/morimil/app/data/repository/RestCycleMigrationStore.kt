package com.morimil.app.data.repository

import com.morimil.app.core.memory.RestCycleMode
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.MigrationRecordEntity
import org.json.JSONArray

internal class RestCycleMigrationStore(
    database: MemoryOrganDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val dao = database.memoryOrganDao()

    suspend fun ensurePlanned(
        migrationId: String,
        instanceId: String,
        birthRootEventHash: String,
        sourceEventHashes: List<String>,
        preSnapshotId: String,
        snapshotDigest: String,
        sourceSetDigest: String,
        mode: RestCycleMode,
        approvalRequired: Boolean,
        riskLevel: String,
        summary: String
    ): MigrationRecordEntity {
        val candidate = buildCandidate(
            migrationId = migrationId,
            instanceId = instanceId,
            birthRootEventHash = birthRootEventHash,
            sourceEventHashes = sourceEventHashes,
            preSnapshotId = preSnapshotId,
            snapshotDigest = snapshotDigest,
            sourceSetDigest = sourceSetDigest,
            mode = mode,
            approvalRequired = approvalRequired,
            riskLevel = riskLevel,
            summary = summary,
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

    suspend fun approveExact(migrationId: String, approvalId: String): MigrationRecordEntity {
        val existing = requireNotNull(dao.loadMigrationRecord(migrationId)) {
            "rest_cycle_migration_missing"
        }
        require(existing.migrationType == REST_CYCLE_MIGRATION_TYPE) {
            "rest_cycle_migration_type_mismatch"
        }
        if (existing.status == STATUS_APPROVED && existing.approvalId == approvalId) return existing
        require(existing.status == STATUS_PLANNED && existing.approvalRequired) {
            "rest_cycle_migration_not_approvable"
        }
        val changed = dao.approveMigrationRecordIfPlanned(
            migrationId = migrationId,
            approvalId = approvalId,
            updatedAtMillis = nowMillis()
        )
        if (changed == 1) {
            return requireNotNull(dao.loadMigrationRecord(migrationId))
        }
        val durable = requireNotNull(dao.loadMigrationRecord(migrationId))
        require(durable.status == STATUS_APPROVED && durable.approvalId == approvalId) {
            "rest_cycle_approval_conflict"
        }
        return durable
    }

    suspend fun load(migrationId: String): MigrationRecordEntity? = dao.loadMigrationRecord(migrationId)

    internal companion object {
        const val REST_CYCLE_MIGRATION_TYPE = "rest_cycle.local_consolidation"
        const val STATUS_PLANNED = "planned"
        const val STATUS_APPROVED = "approved"
        const val STATUS_COMPLETED = "completed"
        private const val CREATED_BY = "rest_cycle_protocol"

        internal fun buildCandidate(
            migrationId: String,
            instanceId: String,
            birthRootEventHash: String,
            sourceEventHashes: List<String>,
            preSnapshotId: String,
            snapshotDigest: String,
            sourceSetDigest: String,
            mode: RestCycleMode,
            approvalRequired: Boolean,
            riskLevel: String,
            summary: String,
            now: Long
        ): MigrationRecordEntity {
            return MigrationRecordEntity(
                migrationId = migrationId,
                instanceId = instanceId,
                // Legacy-named projection column retained until F3.3. This is the
                // canonical Genesis Ultra birth-root event hash, never genesis_core authority.
                genesisCoreHash = birthRootEventHash,
                proposalId = null,
                migrationType = REST_CYCLE_MIGRATION_TYPE,
                fromVersion = "canonical_snapshot:$snapshotDigest",
                toVersion = "canonical_memory_after_rest_cycle",
                affectedArtifactsJson = JSONArray(sourceEventHashes).toString(),
                preSnapshotId = preSnapshotId,
                chainVerified = true,
                backupRequired = approvalRequired,
                stepsJson = JSONArray(
                    listOf(
                        "verify_canonical_rest_planning_input",
                        "stage_durable_rest_cycle_operation",
                        "ensure_exact_canonical_rest_cycle_event",
                        "finalize_local_links_and_autobiography_atomically"
                    )
                ).toString(),
                expectedEffect = buildString {
                    appendLine("rest_cycle_schema=morimil.rest_cycle.rest_001.v1")
                    appendLine("mode=${mode.id}")
                    appendLine("source_set_digest=$sourceSetDigest")
                    appendLine("snapshot_digest=$snapshotDigest")
                    appendLine("source_events=${sourceEventHashes.size}")
                    appendLine("approval_required=$approvalRequired")
                    appendLine("summary=${summary.take(500)}")
                }.trim(),
                riskLevel = riskLevel,
                approvalRequired = approvalRequired,
                approvedByUser = false,
                approvalId = null,
                status = STATUS_PLANNED,
                postSnapshotId = null,
                errorsJson = "[]",
                rollbackAvailable = true,
                rollbackStrategy = "append_only: supersede a completed rest cycle with a later canonical correction or consolidation",
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
                    existing.approvalRequired == candidate.approvalRequired &&
                    existing.rollbackAvailable == candidate.rollbackAvailable &&
                    existing.rollbackStrategy == candidate.rollbackStrategy &&
                    existing.createdBy == candidate.createdBy
            ) { "rest_cycle_migration_id_payload_conflict" }
        }
    }
}
