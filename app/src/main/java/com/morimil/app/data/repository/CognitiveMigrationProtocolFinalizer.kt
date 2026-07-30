package com.morimil.app.data.repository

import com.morimil.app.core.memory.CognitiveMigrationPlanner
import com.morimil.app.data.genesis.ultra.CognitiveMigrationCanonicalAuditPort
import com.morimil.app.data.local.CrossDatabaseOperationStatus
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.MigrationRecordEntity
import org.json.JSONArray
import org.json.JSONObject

internal class CognitiveMigrationProtocolFinalizer(
    database: MemoryOrganDatabase,
    private val canonicalAuditPort: CognitiveMigrationCanonicalAuditPort
) : CrossDatabaseTypedFinalizer {
    private val organDao = database.memoryOrganDao()
    private val operationDao = database.crossDatabaseOperationDao()

    override val supportedOperationTypes: Set<String> =
        CognitiveMigrationProtocolTypes.CLOSED_REGISTRY.keys

    override suspend fun finalizeInsideTransaction(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult {
        require(operation.status == CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT) {
            "xop_finalizer_status_invalid"
        }
        require(operation.operationVersion == CognitiveMigrationProtocolTypes.VERSION) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.UNSUPPORTED_OPERATION_VERSION
            )
        }
        return when (operation.operationType) {
            CognitiveMigrationProtocolTypes.PROPOSE -> finalizeProposal(operation, receipt)
            CognitiveMigrationProtocolTypes.APPROVE -> finalizeApproval(operation, receipt)
            CognitiveMigrationProtocolTypes.EXECUTE -> finalizeExecution(operation, receipt)
            CognitiveMigrationProtocolTypes.ROLLBACK -> finalizeRollback(operation, receipt)
            else -> throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.UNSUPPORTED_OPERATION_VERSION
            )
        }
    }

    private suspend fun finalizeProposal(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult {
        val payload = requirePayload(operation, COG_001_PAYLOAD_SCHEMA)
        val plannedRecordObject = payload.getJSONObject("planned_record")
        val plannedRecordJson =
            CrossDatabaseOperationIdentity.canonicalJson(plannedRecordObject)
        val plannedRecordDigest = payload.getString("planned_record_digest")
        requireDigestMatches(plannedRecordJson, plannedRecordDigest)
        require(payload.getString("migration_id") == operation.subjectId) {
            "xop_cog_001_subject_mismatch"
        }
        val candidate = plannedRecordObject.toEntity(operation)
        require(
            MigrationRecordRepository.plannedRecordDigestOf(candidate) == plannedRecordDigest
        ) { "xop_cog_001_projection_digest_mismatch" }

        val existing = organDao.loadMigrationRecord(candidate.migrationId)
        val inserted = if (existing == null) {
            organDao.insertMigrationRecord(candidate)
            true
        } else {
            requireExactPlannedRecord(existing, candidate, plannedRecordDigest)
            false
        }
        val json = localResult(
            mapOf(
                "canonical_event_hash" to receipt.eventHash,
                "canonical_event_id" to receipt.eventId,
                "canonical_provenance_digest" to receipt.provenanceDigest,
                "canonical_sequence" to receipt.sequence,
                "migration_id" to candidate.migrationId,
                "owner_status" to STATUS_PLANNED,
                "planned_record_digest" to plannedRecordDigest,
                "proposal_id" to candidate.proposalId,
                "record_inserted" to inserted,
                "schema" to COG_001_LOCAL_RESULT_SCHEMA
            )
        )
        return CrossDatabaseLocalResult(
            schema = COG_001_LOCAL_RESULT_SCHEMA,
            json = json,
            digest = CrossDatabaseOperationIdentity.digestCanonicalJson(json),
            ownerStatus = STATUS_PLANNED
        )
    }

    private suspend fun finalizeApproval(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult {
        val payload = requirePayload(operation, COG_002_PAYLOAD_SCHEMA)
        val record = requireOwnerRecord(operation, payload)
        val plannedRecordDigest = payload.getString("planned_record_digest")
        requirePlannedDigest(record, plannedRecordDigest)
        require(payload.getString("expected_owner_status") == STATUS_PLANNED) {
            "xop_cog_002_expected_status_invalid"
        }
        require(payload.getString("decision") == "approve") {
            "xop_cog_002_decision_invalid"
        }
        val updated = when {
            record.status == STATUS_PLANNED -> {
                require(
                    organDao.approveMigrationRecordIfPlanned(
                        migrationId = record.migrationId,
                        approvalId = operation.operationId,
                        updatedAtMillis = operation.updatedAtMillis
                    ) == 1
                ) { "xop_cog_002_owner_transition_failed" }
                true
            }
            record.status == STATUS_APPROVED &&
                record.approvalId == operation.operationId &&
                record.approvedByUser -> false
            else -> throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )
        }
        val json = localResult(
            mapOf(
                "approval_id" to operation.operationId,
                "canonical_event_hash" to receipt.eventHash,
                "canonical_event_id" to receipt.eventId,
                "canonical_provenance_digest" to receipt.provenanceDigest,
                "canonical_sequence" to receipt.sequence,
                "migration_id" to record.migrationId,
                "owner_status" to STATUS_APPROVED,
                "planned_record_digest" to plannedRecordDigest,
                "record_updated" to updated,
                "schema" to COG_002_LOCAL_RESULT_SCHEMA
            )
        )
        return localResult(COG_002_LOCAL_RESULT_SCHEMA, json, STATUS_APPROVED)
    }

    private suspend fun finalizeExecution(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult {
        val payload = requirePayload(operation, COG_003_PAYLOAD_SCHEMA)
        val record = requireOwnerRecord(operation, payload)
        val plannedRecordDigest = payload.getString("planned_record_digest")
        requirePlannedDigest(record, plannedRecordDigest)
        require(record.status == STATUS_APPROVED && record.approvedByUser) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )
        }
        val approvalOperationId = payload.getString("approval_operation_id")
        require(record.approvalId == approvalOperationId) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.PREDECESSOR_RECEIPT_MISSING
            )
        }
        requirePredecessorReceipt(
            operationId = approvalOperationId,
            eventHash = payload.getString("approval_event_hash"),
            sequence = payload.getLong("approval_sequence"),
            provenanceDigest = payload.getString("approval_provenance_digest"),
            subjectId = record.migrationId
        )

        val audit = canonicalAuditPort.auditVerifiedCanonicalChain()
        val outcome = if (audit.verified) STATUS_COMPLETED else STATUS_FAILED
        val notes = if (audit.verified) {
            listOf("canonical_chain_verified", "append_only_refinement_committed")
        } else {
            listOf("canonical_chain_audit_failed")
        }
        val postSnapshotId = "sha256:" + receipt.eventHash.removePrefix("evsha256:")
        require(
            organDao.finishMigrationRecordIfApproved(
                migrationId = record.migrationId,
                approvalId = approvalOperationId,
                outcome = outcome,
                postSnapshotId = postSnapshotId,
                errorsJson = JSONArray(notes).toString(),
                updatedAtMillis = operation.updatedAtMillis
            ) == 1
        ) { "xop_cog_003_owner_transition_failed" }
        val json = localResult(
            mapOf(
                "audit_chain_verified" to audit.verified,
                "audit_notes" to notes,
                "canonical_event_hash" to receipt.eventHash,
                "canonical_event_id" to receipt.eventId,
                "canonical_provenance_digest" to receipt.provenanceDigest,
                "canonical_sequence" to receipt.sequence,
                "migration_id" to record.migrationId,
                "migration_outcome" to outcome,
                "owner_status" to outcome,
                "planned_record_digest" to plannedRecordDigest,
                "post_snapshot_id" to postSnapshotId,
                "record_updated" to true,
                "schema" to COG_003_LOCAL_RESULT_SCHEMA
            )
        )
        return localResult(COG_003_LOCAL_RESULT_SCHEMA, json, outcome)
    }

    private suspend fun finalizeRollback(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult {
        val payload = requirePayload(operation, COG_004_PAYLOAD_SCHEMA)
        val record = requireOwnerRecord(operation, payload)
        val plannedRecordDigest = payload.getString("planned_record_digest")
        requirePlannedDigest(record, plannedRecordDigest)
        require(record.status in setOf(STATUS_APPROVED, STATUS_COMPLETED, STATUS_FAILED)) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )
        }
        val predecessorOperationId = payload.getString("predecessor_operation_id")
        requirePredecessorReceipt(
            operationId = predecessorOperationId,
            eventHash = payload.getString("predecessor_event_hash"),
            sequence = payload.getLong("predecessor_sequence"),
            provenanceDigest = payload.getString("predecessor_provenance_digest"),
            subjectId = record.migrationId
        )
        val rollbackStrategyDigest = payload.getString("rollback_strategy_digest")
        require(
            rollbackStrategyDigest ==
                CrossDatabaseOperationIdentity.digestUtf8(record.rollbackStrategy)
        ) { "xop_cog_004_strategy_digest_mismatch" }
        val notes = listOf(
            "rollback_requested_by_user",
            "append_only_compensation"
        )
        require(
            organDao.rollbackMigrationRecordIfAllowed(
                migrationId = record.migrationId,
                rollbackEventHash = receipt.eventHash,
                notesJson = JSONArray(notes).toString(),
                updatedAtMillis = operation.updatedAtMillis
            ) == 1
        ) { "xop_cog_004_owner_transition_failed" }
        val json = localResult(
            mapOf(
                "canonical_event_hash" to receipt.eventHash,
                "canonical_event_id" to receipt.eventId,
                "canonical_provenance_digest" to receipt.provenanceDigest,
                "canonical_sequence" to receipt.sequence,
                "migration_id" to record.migrationId,
                "notes" to notes,
                "owner_status" to STATUS_ROLLED_BACK,
                "planned_record_digest" to plannedRecordDigest,
                "predecessor_operation_id" to predecessorOperationId,
                "record_updated" to true,
                "rollback_operation_id" to operation.operationId,
                "rollback_strategy_digest" to rollbackStrategyDigest,
                "schema" to COG_004_LOCAL_RESULT_SCHEMA
            )
        )
        return localResult(COG_004_LOCAL_RESULT_SCHEMA, json, STATUS_ROLLED_BACK)
    }

    private fun requirePayload(
        operation: CrossDatabaseOperationRecord,
        expectedSchema: String
    ): JSONObject {
        if (operation.payloadSchema != expectedSchema) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.UNSUPPORTED_PAYLOAD_SCHEMA
            )
        }
        val payload = JSONObject(operation.payloadJson)
        if (payload.getString("schema") != expectedSchema) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.UNSUPPORTED_PAYLOAD_SCHEMA
            )
        }
        return payload
    }

    private suspend fun requireOwnerRecord(
        operation: CrossDatabaseOperationRecord,
        payload: JSONObject
    ): MigrationRecordEntity {
        require(payload.getString("migration_id") == operation.subjectId) {
            "xop_owner_subject_mismatch"
        }
        return organDao.loadMigrationRecord(operation.subjectId)
            ?: throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )
    }

    private fun requirePlannedDigest(
        record: MigrationRecordEntity,
        expectedDigest: String
    ) {
        if (MigrationRecordRepository.plannedRecordDigestOf(record) != expectedDigest) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )
        }
    }

    private suspend fun requirePredecessorReceipt(
        operationId: String,
        eventHash: String,
        sequence: Long,
        provenanceDigest: String,
        subjectId: String
    ) {
        val predecessor = operationDao.loadOperation(operationId)
            ?: throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.PREDECESSOR_RECEIPT_MISSING
            )
        val exact = predecessor.subjectId == subjectId &&
            predecessor.status == CrossDatabaseOperationStatus.COMMITTED &&
            predecessor.canonicalEventHash == eventHash &&
            predecessor.canonicalSequence == sequence &&
            predecessor.canonicalProvenanceDigest == provenanceDigest
        if (!exact) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.PREDECESSOR_RECEIPT_MISSING
            )
        }
    }

    private fun requireExactPlannedRecord(
        existing: MigrationRecordEntity,
        candidate: MigrationRecordEntity,
        expectedDigest: String
    ) {
        if (
            existing.migrationId != candidate.migrationId ||
            MigrationRecordRepository.plannedRecordDigestOf(existing) != expectedDigest
        ) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )
        }
    }

    private fun requireDigestMatches(json: String, expectedDigest: String) {
        require(CrossDatabaseOperationIdentity.digestCanonicalJson(json) == expectedDigest) {
            "xop_projection_digest_mismatch"
        }
    }

    private fun JSONObject.toEntity(
        operation: CrossDatabaseOperationRecord
    ): MigrationRecordEntity {
        require(getString("schema") == CognitiveMigrationPlanner.PLANNED_RECORD_SCHEMA) {
            "xop_planned_record_schema_invalid"
        }
        return MigrationRecordEntity(
            migrationId = getString("migration_id"),
            instanceId = getString("instance_id"),
            genesisCoreHash = getString("genesis_core_hash"),
            proposalId = getString("proposal_id"),
            migrationType = getString("migration_type"),
            fromVersion = getString("from_version"),
            toVersion = getString("to_version"),
            affectedArtifactsJson = getJSONArray("affected_artifacts").toString(),
            preSnapshotId = getString("pre_snapshot_id"),
            chainVerified = getBoolean("chain_verified"),
            backupRequired = getBoolean("backup_required"),
            stepsJson = getJSONArray("steps").toString(),
            expectedEffect = getString("expected_effect"),
            riskLevel = getString("risk_level"),
            approvalRequired = getBoolean("approval_required"),
            approvedByUser = false,
            approvalId = null,
            status = STATUS_PLANNED,
            postSnapshotId = null,
            errorsJson = "[]",
            rollbackAvailable = getBoolean("rollback_available"),
            rollbackStrategy = getString("rollback_strategy"),
            createdBy = getString("created_by"),
            createdAtMillis = operation.createdAtMillis,
            updatedAtMillis = operation.updatedAtMillis
        )
    }

    private fun localResult(values: Map<String, Any?>): String {
        return CrossDatabaseOperationIdentity.canonicalJson(values)
    }

    private fun localResult(
        schema: String,
        json: String,
        ownerStatus: String
    ): CrossDatabaseLocalResult {
        return CrossDatabaseLocalResult(
            schema = schema,
            json = json,
            digest = CrossDatabaseOperationIdentity.digestCanonicalJson(json),
            ownerStatus = ownerStatus
        )
    }

    private companion object {
        const val COG_001_PAYLOAD_SCHEMA =
            "morimil.cognitive_migration.cog_001.payload.v2"
        const val COG_002_PAYLOAD_SCHEMA =
            "morimil.cognitive_migration.cog_002.payload.v1"
        const val COG_003_PAYLOAD_SCHEMA =
            "morimil.cognitive_migration.cog_003.payload.v1"
        const val COG_004_PAYLOAD_SCHEMA =
            "morimil.cognitive_migration.cog_004.payload.v1"

        const val COG_001_LOCAL_RESULT_SCHEMA =
            "morimil.cognitive_migration.cog_001.local_result.v2"
        const val COG_002_LOCAL_RESULT_SCHEMA =
            "morimil.cognitive_migration.cog_002.local_result.v2"
        const val COG_003_LOCAL_RESULT_SCHEMA =
            "morimil.cognitive_migration.cog_003.local_result.v2"
        const val COG_004_LOCAL_RESULT_SCHEMA =
            "morimil.cognitive_migration.cog_004.local_result.v2"

        const val STATUS_PLANNED = "planned"
        const val STATUS_APPROVED = "approved"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
        const val STATUS_ROLLED_BACK = "rolled_back"
    }
}
