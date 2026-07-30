package com.morimil.app.data.repository

import com.morimil.app.core.memory.CognitiveMigrationPlanner
import com.morimil.app.core.memory.VerifiedCognitiveMigrationPlan
import com.morimil.app.data.genesis.ultra.CognitiveMigrationCanonicalReadPort
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentityRepository
import com.morimil.app.data.genesis.ultra.VerifiedCognitiveMigrationPlanningInput
import com.morimil.app.data.local.CrossDatabaseOperationEntity
import com.morimil.app.data.local.CrossDatabaseOperationStatus
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.MigrationRecordEntity
import org.json.JSONObject

class CognitiveMigrationRepository internal constructor(
    organDatabase: MemoryOrganDatabase,
    private val identityRepository: GenesisUltraRuntimeIdentityRepository,
    private val canonicalReadPort: CognitiveMigrationCanonicalReadPort,
    private val protocol: CrossDatabaseOperationCoordinator
) {
    private val migrationRecords = MigrationRecordRepository(organDatabase)
    private val operationDao = organDatabase.crossDatabaseOperationDao()

    suspend fun proposeCognitiveMigration(): String? {
        val identity = identityRepository.readCommittedIdentity() ?: return null
        recoverBeforeMutation(identity)
        val input = canonicalReadPort.readVerifiedPlanningInput()
        requireInputIdentity(input, identity)
        if (input.sources.isEmpty()) return null

        val plan = CognitiveMigrationPlanner.buildVerifiedPlan(input)
        val command = CognitiveMigrationOperationFactory.propose(input, plan)
        val committed = protocol.execute(identity, command)
        require(committed.status == CrossDatabaseOperationStatus.COMMITTED) {
            "cognitive_migration_proposal_not_committed"
        }
        return committed.subjectId
    }

    suspend fun approveCognitiveMigration(migrationId: String): Boolean {
        val identity = identityRepository.readCommittedIdentity() ?: return false
        recoverBeforeMutation(identity)
        val record = migrationRecords.loadMigration(migrationId) ?: return false
        if (record.instanceId != identity.instanceId ||
            record.migrationType != CognitiveMigrationPlanner.MIGRATION_TYPE
        ) {
            return false
        }
        if (record.status == STATUS_APPROVED && record.approvalId != null) {
            return protocol.load(record.approvalId)?.status ==
                CrossDatabaseOperationStatus.COMMITTED
        }
        if (record.status != STATUS_PLANNED) return false

        val command = CognitiveMigrationOperationFactory.approve(
            identity = identity,
            record = record,
            plannedRecordDigest = migrationRecords.plannedRecordDigest(record)
        )
        return protocol.execute(identity, command).status ==
            CrossDatabaseOperationStatus.COMMITTED
    }

    suspend fun executeCognitiveMigration(migrationId: String): Boolean {
        val identity = identityRepository.readCommittedIdentity() ?: return false
        recoverBeforeMutation(identity)
        val record = migrationRecords.loadMigration(migrationId) ?: return false
        if (record.instanceId != identity.instanceId ||
            record.migrationType != CognitiveMigrationPlanner.MIGRATION_TYPE
        ) {
            return false
        }
        if (record.status in setOf(STATUS_COMPLETED, STATUS_FAILED)) {
            val existing = loadSingleOperation(
                migrationId,
                CognitiveMigrationProtocolTypes.EXECUTE
            ) ?: return false
            if (existing.status != CrossDatabaseOperationStatus.COMMITTED) return false
            return record.status == STATUS_COMPLETED
        }
        if (record.status != STATUS_APPROVED || !record.approvedByUser) return false

        val approvalOperationId = record.approvalId ?: return false
        val approval = protocol.load(approvalOperationId) ?: return false
        requireCommittedReceipt(approval, migrationId)
        val command = CognitiveMigrationOperationFactory.execute(
            identity = identity,
            record = record,
            plannedRecordDigest = migrationRecords.plannedRecordDigest(record),
            approval = approval
        )
        val committed = protocol.execute(identity, command)
        if (committed.status != CrossDatabaseOperationStatus.COMMITTED) return false
        val updated = migrationRecords.loadMigration(migrationId) ?: return false
        return updated.status == STATUS_COMPLETED
    }

    suspend fun rollbackCognitiveMigration(migrationId: String): Boolean {
        val identity = identityRepository.readCommittedIdentity() ?: return false
        recoverBeforeMutation(identity)
        val record = migrationRecords.loadMigration(migrationId) ?: return false
        if (record.instanceId != identity.instanceId ||
            record.migrationType != CognitiveMigrationPlanner.MIGRATION_TYPE ||
            !record.rollbackAvailable
        ) {
            return false
        }
        if (record.status == STATUS_ROLLED_BACK) {
            return loadSingleOperation(
                migrationId,
                CognitiveMigrationProtocolTypes.ROLLBACK
            )?.status == CrossDatabaseOperationStatus.COMMITTED
        }
        if (record.status !in setOf(STATUS_APPROVED, STATUS_COMPLETED, STATUS_FAILED)) {
            return false
        }

        val predecessor = if (record.status in setOf(STATUS_COMPLETED, STATUS_FAILED)) {
            loadSingleOperation(migrationId, CognitiveMigrationProtocolTypes.EXECUTE)
        } else {
            record.approvalId?.let { operationId -> protocol.load(operationId) }
        } ?: return false
        requireCommittedReceipt(predecessor, migrationId)
        val command = CognitiveMigrationOperationFactory.rollback(
            identity = identity,
            record = record,
            plannedRecordDigest = migrationRecords.plannedRecordDigest(record),
            predecessor = predecessor
        )
        return protocol.execute(identity, command).status ==
            CrossDatabaseOperationStatus.COMMITTED
    }

    private suspend fun recoverBeforeMutation(identity: GenesisUltraRuntimeIdentity) {
        val recovery = protocol.recoverBeforeMutation(
            identity = identity,
            ownerType = CognitiveMigrationProtocolTypes.OWNER_TYPE,
            limit = RECOVERY_LIMIT
        )
        if (recovery.blockedCount > 0) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )
        }
        if (recovery.retryableFailureCount > 0) {
            throw CrossDatabaseProtocolErrors.retryable(
                CrossDatabaseProtocolErrors.CANONICAL_APPEND_INTERRUPTED
            )
        }
    }

    private suspend fun loadSingleOperation(
        migrationId: String,
        operationType: String
    ): CrossDatabaseOperationEntity? {
        val operations = operationDao.loadAnyForOwnerSubjectAndOperationType(
            ownerType = CognitiveMigrationProtocolTypes.OWNER_TYPE,
            subjectId = migrationId,
            operationType = operationType
        )
        if (operations.size > 1) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )
        }
        return operations.singleOrNull()
    }

    private fun requireInputIdentity(
        input: VerifiedCognitiveMigrationPlanningInput,
        identity: GenesisUltraRuntimeIdentity
    ) {
        if (input.instanceId != identity.instanceId) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.WRONG_INSTANCE
            )
        }
        if (input.writerBodyId != identity.activeBody.bodyId) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.UNAUTHORIZED_WRITER_BODY
            )
        }
        if (input.writerEpoch != identity.activeBody.keyEpochId) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.STALE_WRITER_EPOCH
            )
        }
    }

    private fun requireCommittedReceipt(
        operation: CrossDatabaseOperationEntity,
        migrationId: String
    ) {
        val valid = operation.subjectId == migrationId &&
            operation.status == CrossDatabaseOperationStatus.COMMITTED &&
            operation.canonicalEventHash != null &&
            operation.canonicalSequence != null &&
            operation.canonicalProvenanceDigest != null
        if (!valid) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.PREDECESSOR_RECEIPT_MISSING
            )
        }
    }

    companion object {
        const val COGNITIVE_MIGRATION_TYPE = CognitiveMigrationPlanner.MIGRATION_TYPE
        private const val STATUS_PLANNED = "planned"
        private const val STATUS_APPROVED = "approved"
        private const val STATUS_COMPLETED = "completed"
        private const val STATUS_FAILED = "failed"
        private const val STATUS_ROLLED_BACK = "rolled_back"
        private const val RECOVERY_LIMIT = 64
    }
}

private object CognitiveMigrationOperationFactory {
    fun propose(
        input: VerifiedCognitiveMigrationPlanningInput,
        plan: VerifiedCognitiveMigrationPlan
    ): CrossDatabaseStageCommand {
        val payload = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "canonical_birth_root_hash" to input.canonicalBirthRootHash,
                "canonical_last_sequence" to input.canonicalLastSequence,
                "canonical_pre_snapshot_hash" to input.canonicalPreSnapshotHash,
                "from_version" to CognitiveMigrationPlanner.FROM_VERSION,
                "migration_id" to plan.migrationId,
                "migration_type" to CognitiveMigrationPlanner.MIGRATION_TYPE,
                "plan_core" to JSONObject(plan.planCoreJson),
                "plan_core_digest" to plan.planCoreDigest,
                "plan_schema" to CognitiveMigrationPlanner.VERIFIED_PLAN_SCHEMA,
                "planned_record" to JSONObject(plan.plannedRecordJson),
                "planned_record_digest" to plan.plannedRecordDigest,
                "proposal_id" to plan.proposalId,
                "schema" to COG_001_PAYLOAD_SCHEMA,
                "source_event_hashes_sorted" to
                    input.sources.map { source -> source.eventHash }.sorted(),
                "source_set_digest" to input.sourceSetDigest,
                "to_version" to CognitiveMigrationPlanner.TO_VERSION
            )
        )
        return command(
            operationType = CognitiveMigrationProtocolTypes.PROPOSE,
            eventType = CognitiveMigrationProtocolTypes.PROPOSED_EVENT,
            identity = input.identity(),
            subjectId = plan.migrationId,
            payloadSchema = COG_001_PAYLOAD_SCHEMA,
            payloadJson = payload,
            eventTransition = "planned",
            eventReferences = mapOf("proposal_id" to plan.proposalId),
            evidenceSchema = COG_001_EVIDENCE_SCHEMA,
            evidenceValues = mapOf(
                "chain_verified" to true,
                "legacy_input_used" to false,
                "migration_id" to plan.migrationId,
                "planned_record_digest" to plan.plannedRecordDigest,
                "proposal_id" to plan.proposalId,
                "source_count" to input.sources.size,
                "source_set_digest" to input.sourceSetDigest
            )
        )
    }

    fun approve(
        identity: GenesisUltraRuntimeIdentity,
        record: MigrationRecordEntity,
        plannedRecordDigest: String
    ): CrossDatabaseStageCommand {
        val payload = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "approval_scope" to "cognitive_migration_execution",
                "approved_by_user" to true,
                "decision" to "approve",
                "expected_owner_status" to "planned",
                "migration_id" to record.migrationId,
                "planned_record_digest" to plannedRecordDigest,
                "schema" to COG_002_PAYLOAD_SCHEMA
            )
        )
        return command(
            operationType = CognitiveMigrationProtocolTypes.APPROVE,
            eventType = CognitiveMigrationProtocolTypes.APPROVED_EVENT,
            identity = identity.toInputIdentity(),
            subjectId = record.migrationId,
            payloadSchema = COG_002_PAYLOAD_SCHEMA,
            payloadJson = payload,
            eventTransition = "approved",
            eventReferences = emptyMap(),
            evidenceSchema = COG_002_EVIDENCE_SCHEMA,
            evidenceValues = mapOf(
                "approved_by_user" to true,
                "decision_source" to "interactive_local_user",
                "migration_id" to record.migrationId,
                "ownership_conferred" to false,
                "planned_record_digest" to plannedRecordDigest
            )
        )
    }

    fun execute(
        identity: GenesisUltraRuntimeIdentity,
        record: MigrationRecordEntity,
        plannedRecordDigest: String,
        approval: CrossDatabaseOperationEntity
    ): CrossDatabaseStageCommand {
        val approvalEventHash = requireNotNull(approval.canonicalEventHash)
        val approvalSequence = requireNotNull(approval.canonicalSequence)
        val approvalProvenance = requireNotNull(approval.canonicalProvenanceDigest)
        val payload = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "approval_event_hash" to approvalEventHash,
                "approval_operation_id" to approval.operationId,
                "approval_provenance_digest" to approvalProvenance,
                "approval_sequence" to approvalSequence,
                "expected_owner_status" to "approved",
                "migration_id" to record.migrationId,
                "planned_record_digest" to plannedRecordDigest,
                "post_append_audit_policy" to "full_verified_canonical_chain",
                "schema" to COG_003_PAYLOAD_SCHEMA
            )
        )
        return command(
            operationType = CognitiveMigrationProtocolTypes.EXECUTE,
            eventType = CognitiveMigrationProtocolTypes.EXECUTED_EVENT,
            identity = identity.toInputIdentity(),
            subjectId = record.migrationId,
            payloadSchema = COG_003_PAYLOAD_SCHEMA,
            payloadJson = payload,
            eventTransition = "completed_or_failed_after_audit",
            eventReferences = mapOf("approval_id" to approval.operationId),
            evidenceSchema = COG_003_EVIDENCE_SCHEMA,
            evidenceValues = mapOf(
                "approval_event_hash" to approvalEventHash,
                "approval_operation_id" to approval.operationId,
                "approval_provenance_digest" to approvalProvenance,
                "approval_sequence" to approvalSequence,
                "migration_id" to record.migrationId,
                "original_memory_rewritten" to false,
                "post_append_audit_required" to true
            )
        )
    }

    fun rollback(
        identity: GenesisUltraRuntimeIdentity,
        record: MigrationRecordEntity,
        plannedRecordDigest: String,
        predecessor: CrossDatabaseOperationEntity
    ): CrossDatabaseStageCommand {
        val predecessorEventHash = requireNotNull(predecessor.canonicalEventHash)
        val predecessorSequence = requireNotNull(predecessor.canonicalSequence)
        val predecessorProvenance = requireNotNull(
            predecessor.canonicalProvenanceDigest
        )
        val rollbackStrategyDigest =
            CrossDatabaseOperationIdentity.digestUtf8(record.rollbackStrategy)
        val payload = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "compensation_mode" to "append_only",
                "expected_owner_status_one_of" to
                    listOf("approved", "completed", "failed"),
                "migration_id" to record.migrationId,
                "planned_record_digest" to plannedRecordDigest,
                "predecessor_event_hash" to predecessorEventHash,
                "predecessor_operation_id" to predecessor.operationId,
                "predecessor_provenance_digest" to predecessorProvenance,
                "predecessor_sequence" to predecessorSequence,
                "rollback_strategy_digest" to rollbackStrategyDigest,
                "schema" to COG_004_PAYLOAD_SCHEMA
            )
        )
        return command(
            operationType = CognitiveMigrationProtocolTypes.ROLLBACK,
            eventType = CognitiveMigrationProtocolTypes.ROLLBACK_EVENT,
            identity = identity.toInputIdentity(),
            subjectId = record.migrationId,
            payloadSchema = COG_004_PAYLOAD_SCHEMA,
            payloadJson = payload,
            eventTransition = "rolled_back",
            eventReferences = mapOf(
                "predecessor_operation_id" to predecessor.operationId
            ),
            evidenceSchema = COG_004_EVIDENCE_SCHEMA,
            evidenceValues = mapOf(
                "migration_id" to record.migrationId,
                "original_events_deleted" to false,
                "original_events_rewritten" to false,
                "predecessor_operation_id" to predecessor.operationId,
                "second_rollback_event_allowed" to false
            )
        )
    }

    private fun command(
        operationType: String,
        eventType: String,
        identity: InputIdentity,
        subjectId: String,
        payloadSchema: String,
        payloadJson: String,
        eventTransition: String,
        eventReferences: Map<String, String>,
        evidenceSchema: String,
        evidenceValues: Map<String, Any?>
    ): CrossDatabaseStageCommand {
        val payloadDigest =
            CrossDatabaseOperationIdentity.digestCanonicalJson(payloadJson)
        val operationId = CrossDatabaseOperationIdentity.operationId(
            operationType = operationType,
            operationVersion = CognitiveMigrationProtocolTypes.VERSION,
            instanceId = identity.instanceId,
            writerBodyId = identity.writerBodyId,
            writerEpoch = identity.writerEpoch,
            subjectId = subjectId,
            parentOperationId = null,
            childPhase = null,
            payloadDigest = payloadDigest
        )
        val eventId = CrossDatabaseOperationIdentity.eventId(operationId, eventType)
        val eventBody = buildString {
            appendLine("COGNITIVE_MIGRATION_PROTOCOL_V1")
            appendLine("operation_id=$operationId")
            appendLine("operation_type=$operationType")
            appendLine("operation_version=${CognitiveMigrationProtocolTypes.VERSION}")
            appendLine("migration_id=$subjectId")
            eventReferences.toSortedMap().forEach { (key, value) ->
                appendLine("$key=$value")
            }
            appendLine("payload_digest=$payloadDigest")
            append("transition=$eventTransition")
        }
        val evidenceJson = CrossDatabaseOperationIdentity.canonicalJson(
            buildMap {
                put("event_id", eventId)
                put("instance_id", identity.instanceId)
                put("operation_id", operationId)
                put("operation_type", operationType)
                put("operation_version", CognitiveMigrationProtocolTypes.VERSION)
                put("payload_digest", payloadDigest)
                put("schema", evidenceSchema)
                put("writer_body_id", identity.writerBodyId)
                put("writer_epoch", identity.writerEpoch)
                if (operationType == CognitiveMigrationProtocolTypes.APPROVE) {
                    put("approval_id", operationId)
                }
                putAll(evidenceValues)
            }
        )
        return CrossDatabaseStageCommand(
            operationId = operationId,
            ownerType = CognitiveMigrationProtocolTypes.OWNER_TYPE,
            operationType = operationType,
            operationVersion = CognitiveMigrationProtocolTypes.VERSION,
            instanceId = identity.instanceId,
            writerBodyId = identity.writerBodyId,
            writerEpoch = identity.writerEpoch,
            subjectId = subjectId,
            parentOperationId = null,
            childPhase = null,
            payloadSchema = payloadSchema,
            payloadJson = payloadJson,
            payloadDigest = payloadDigest,
            eventId = eventId,
            eventType = eventType,
            eventBody = eventBody,
            evidenceSchema = evidenceSchema,
            evidenceJson = evidenceJson,
            evidenceDigest =
                CrossDatabaseOperationIdentity.digestCanonicalJson(evidenceJson)
        )
    }

    private fun VerifiedCognitiveMigrationPlanningInput.identity(): InputIdentity {
        return InputIdentity(instanceId, writerBodyId, writerEpoch)
    }

    private fun GenesisUltraRuntimeIdentity.toInputIdentity(): InputIdentity {
        return InputIdentity(
            instanceId = instanceId,
            writerBodyId = activeBody.bodyId,
            writerEpoch = activeBody.keyEpochId
        )
    }

    private data class InputIdentity(
        val instanceId: String,
        val writerBodyId: String,
        val writerEpoch: String
    )

    private const val COG_001_PAYLOAD_SCHEMA =
        "morimil.cognitive_migration.cog_001.payload.v1"
    private const val COG_002_PAYLOAD_SCHEMA =
        "morimil.cognitive_migration.cog_002.payload.v1"
    private const val COG_003_PAYLOAD_SCHEMA =
        "morimil.cognitive_migration.cog_003.payload.v1"
    private const val COG_004_PAYLOAD_SCHEMA =
        "morimil.cognitive_migration.cog_004.payload.v1"

    private const val COG_001_EVIDENCE_SCHEMA =
        "morimil.cognitive_migration.cog_001.evidence.v1"
    private const val COG_002_EVIDENCE_SCHEMA =
        "morimil.cognitive_migration.cog_002.evidence.v1"
    private const val COG_003_EVIDENCE_SCHEMA =
        "morimil.cognitive_migration.cog_003.evidence.v1"
    private const val COG_004_EVIDENCE_SCHEMA =
        "morimil.cognitive_migration.cog_004.evidence.v1"
}
