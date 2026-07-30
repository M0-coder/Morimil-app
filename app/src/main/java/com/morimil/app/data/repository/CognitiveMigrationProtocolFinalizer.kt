package com.morimil.app.data.repository

import com.morimil.app.core.memory.CognitiveMigrationPlanner
import com.morimil.app.data.genesis.ultra.CognitiveMigrationCanonicalAuditPort
import com.morimil.app.data.local.CrossDatabaseOperationEntity
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

    override suspend fun prepareOutsideTransaction(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseFinalizationPreparation? {
        if (operation.operationType != CognitiveMigrationProtocolTypes.EXECUTE) return null
        val audit = canonicalAuditPort.auditVerifiedCanonicalChain()
        if (audit.verified && audit.snapshotDigest == null) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.FINALIZATION_PREPARATION_CONFLICT
            )
        }
        audit.snapshotDigest?.let { digest ->
            if (!digest.matches(CrossDatabaseOperationEntity.SHA256_DIGEST)) {
                throw CrossDatabaseProtocolErrors.permanent(
                    CrossDatabaseProtocolErrors.FINALIZATION_PREPARATION_CONFLICT
                )
            }
        }
        val json = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "audit_notes" to audit.notes,
                "audit_verified" to audit.verified,
                "operation_id" to operation.operationId,
                "payload_digest" to operation.payloadDigest,
                "receipt_event_hash" to receipt.eventHash,
                "schema" to AUDIT_PREPARATION_SCHEMA,
                "snapshot_digest" to audit.snapshotDigest
            )
        )
        return CrossDatabaseFinalizationPreparation(
            operationId = operation.operationId,
            receiptEventHash = receipt.eventHash,
            payloadDigest = operation.payloadDigest,
            schema = AUDIT_PREPARATION_SCHEMA,
            json = json,
            digest = CrossDatabaseOperationIdentity.digestCanonicalJson(json)
        )
    }

    override suspend fun finalizeInsideTransaction(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult {
        return finalizePreparedInsideTransaction(operation, receipt, preparation = null)
    }

    override suspend fun finalizePreparedInsideTransaction(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt,
        preparation: CrossDatabaseFinalizationPreparation?
    ): CrossDatabaseLocalResult {
        permanentCheck(
            operation.status == CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT,
            CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT
        )
        permanentCheck(
            operation.operationVersion == CognitiveMigrationProtocolTypes.VERSION,
            CrossDatabaseProtocolErrors.UNSUPPORTED_OPERATION_VERSION
        )
        return when (operation.operationType) {
            CognitiveMigrationProtocolTypes.PROPOSE -> {
                permanentCheck(preparation == null)
                finalizeProposal(operation, receipt)
            }
            CognitiveMigrationProtocolTypes.APPROVE -> {
                permanentCheck(preparation == null)
                finalizeApproval(operation, receipt)
            }
            CognitiveMigrationProtocolTypes.EXECUTE -> {
                finalizeExecution(operation, receipt, preparation)
            }
            CognitiveMigrationProtocolTypes.ROLLBACK -> {
                permanentCheck(preparation == null)
                finalizeRollback(operation, receipt)
            }
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
        val plannedRecordJson = CrossDatabaseOperationIdentity.canonicalJson(plannedRecordObject)
        val plannedRecordDigest = payload.getString("planned_record_digest")
        requireDigestMatches(plannedRecordJson, plannedRecordDigest)
        permanentCheck(payload.getString("migration_id") == operation.subjectId)
        val candidate = plannedRecordObject.toEntity(operation)
        permanentCheck(
            MigrationRecordRepository.plannedRecordDigestOf(candidate) == plannedRecordDigest
        )

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
        return localResult(COG_001_LOCAL_RESULT_SCHEMA, json, STATUS_PLANNED)
    }

    private suspend fun finalizeApproval(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult {
        val payload = requirePayload(operation, COG_002_PAYLOAD_SCHEMA)
        val record = requireOwnerRecord(operation, payload)
        val plannedRecordDigest = payload.getString("planned_record_digest")
        requirePlannedDigest(record, plannedRecordDigest)
        permanentCheck(payload.getString("expected_owner_status") == STATUS_PLANNED)
        permanentCheck(payload.getString("decision") == "approve")
        val updated = when {
            record.status == STATUS_PLANNED -> {
                permanentCheck(
                    organDao.approveMigrationRecordIfPlanned(
                        migrationId = record.migrationId,
                        approvalId = operation.operationId,
                        updatedAtMillis = operation.updatedAtMillis
                    ) == 1,
                    CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT
                )
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
        receipt: CrossDatabaseCanonicalReceipt,
        preparation: CrossDatabaseFinalizationPreparation?
    ): CrossDatabaseLocalResult {
        val payload = requirePayload(operation, COG_003_PAYLOAD_SCHEMA)
        val record = requireOwnerRecord(operation, payload)
        val plannedRecordDigest = payload.getString("planned_record_digest")
        requirePlannedDigest(record, plannedRecordDigest)
        permanentCheck(record.status == STATUS_APPROVED && record.approvedByUser)
        val approvalOperationId = payload.getString("approval_operation_id")
        permanentCheck(
            record.approvalId == approvalOperationId,
            CrossDatabaseProtocolErrors.PREDECESSOR_RECEIPT_MISSING
        )
        requirePredecessorReceipt(
            operationId = approvalOperationId,
            eventHash = payload.getString("approval_event_hash"),
            sequence = payload.getLong("approval_sequence"),
            provenanceDigest = payload.getString("approval_provenance_digest"),
            subjectId = record.migrationId,
            expectedOperationType = CognitiveMigrationProtocolTypes.APPROVE
        )

        val audit = requireAuditPreparation(operation, receipt, preparation)
        val outcome = if (audit.verified) STATUS_COMPLETED else STATUS_FAILED
        val postSnapshotId = audit.snapshotDigest
        permanentCheck(
            organDao.finishMigrationRecordIfApproved(
                migrationId = record.migrationId,
                approvalId = approvalOperationId,
                outcome = outcome,
                postSnapshotId = postSnapshotId,
                errorsJson = JSONArray(audit.notes).toString(),
                updatedAtMillis = operation.updatedAtMillis
            ) == 1,
            CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT
        )
        val json = localResult(
            mapOf(
                "audit_chain_verified" to audit.verified,
                "audit_notes" to audit.notes,
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
        permanentCheck(record.status in setOf(STATUS_APPROVED, STATUS_COMPLETED, STATUS_FAILED))
        val predecessorOperationId = payload.getString("predecessor_operation_id")
        val expectedPredecessorType = if (record.status == STATUS_APPROVED) {
            CognitiveMigrationProtocolTypes.APPROVE
        } else {
            CognitiveMigrationProtocolTypes.EXECUTE
        }
        requirePredecessorReceipt(
            operationId = predecessorOperationId,
            eventHash = payload.getString("predecessor_event_hash"),
            sequence = payload.getLong("predecessor_sequence"),
            provenanceDigest = payload.getString("predecessor_provenance_digest"),
            subjectId = record.migrationId,
            expectedOperationType = expectedPredecessorType
        )
        val rollbackStrategyDigest = payload.getString("rollback_strategy_digest")
        permanentCheck(
            rollbackStrategyDigest ==
                CrossDatabaseOperationIdentity.digestUtf8(record.rollbackStrategy)
        )
        val notes = listOf(
            "rollback_requested_by_user",
            "append_only_compensation"
        )
        permanentCheck(
            organDao.rollbackMigrationRecordIfAllowed(
                migrationId = record.migrationId,
                rollbackEventHash = receipt.eventHash,
                notesJson = JSONArray(notes).toString(),
                updatedAtMillis = operation.updatedAtMillis
            ) == 1,
            CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT
        )
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

    private fun requireAuditPreparation(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt,
        preparation: CrossDatabaseFinalizationPreparation?
    ): PreparedAudit {
        val prepared = preparation ?: throw CrossDatabaseProtocolErrors.permanent(
            CrossDatabaseProtocolErrors.FINALIZATION_PREPARATION_CONFLICT
        )
        permanentCheck(
            prepared.schema == AUDIT_PREPARATION_SCHEMA &&
                prepared.operationId == operation.operationId &&
                prepared.receiptEventHash == receipt.eventHash &&
                prepared.payloadDigest == operation.payloadDigest,
            CrossDatabaseProtocolErrors.FINALIZATION_PREPARATION_CONFLICT
        )
        val json = try {
            JSONObject(prepared.json)
        } catch (failure: Throwable) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.FINALIZATION_PREPARATION_CONFLICT,
                failure
            )
        }
        permanentCheck(
            json.getString("schema") == AUDIT_PREPARATION_SCHEMA &&
                json.getString("operation_id") == operation.operationId &&
                json.getString("receipt_event_hash") == receipt.eventHash &&
                json.getString("payload_digest") == operation.payloadDigest,
            CrossDatabaseProtocolErrors.FINALIZATION_PREPARATION_CONFLICT
        )
        val verified = json.getBoolean("audit_verified")
        val snapshotDigest = if (json.isNull("snapshot_digest")) {
            null
        } else {
            json.getString("snapshot_digest")
        }
        if (verified) {
            permanentCheck(
                snapshotDigest?.matches(CrossDatabaseOperationEntity.SHA256_DIGEST) == true,
                CrossDatabaseProtocolErrors.FINALIZATION_PREPARATION_CONFLICT
            )
        } else {
            permanentCheck(
                snapshotDigest == null,
                CrossDatabaseProtocolErrors.FINALIZATION_PREPARATION_CONFLICT
            )
        }
        val notesArray = json.getJSONArray("audit_notes")
        val notes = (0 until notesArray.length()).map { index ->
            notesArray.getString(index)
        }
        permanentCheck(notes.isNotEmpty())
        return PreparedAudit(verified, snapshotDigest, notes)
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
        val payload = try {
            JSONObject(operation.payloadJson)
        } catch (failure: Throwable) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.UNSUPPORTED_PAYLOAD_SCHEMA,
                failure
            )
        }
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
        permanentCheck(payload.getString("migration_id") == operation.subjectId)
        return organDao.loadMigrationRecord(operation.subjectId)
            ?: throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )
    }

    private fun requirePlannedDigest(
        record: MigrationRecordEntity,
        expectedDigest: String
    ) {
        permanentCheck(
            MigrationRecordRepository.plannedRecordDigestOf(record) == expectedDigest
        )
    }

    private suspend fun requirePredecessorReceipt(
        operationId: String,
        eventHash: String,
        sequence: Long,
        provenanceDigest: String,
        subjectId: String,
        expectedOperationType: String
    ) {
        val predecessor = operationDao.loadOperation(operationId)
            ?: throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.PREDECESSOR_RECEIPT_MISSING
            )
        val exact = predecessor.ownerType == CognitiveMigrationProtocolTypes.OWNER_TYPE &&
            predecessor.operationType == expectedOperationType &&
            predecessor.operationVersion == CognitiveMigrationProtocolTypes.VERSION &&
            predecessor.subjectId == subjectId &&
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
        permanentCheck(
            existing.migrationId == candidate.migrationId &&
                MigrationRecordRepository.plannedRecordDigestOf(existing) == expectedDigest
        )
    }

    private fun requireDigestMatches(json: String, expectedDigest: String) {
        permanentCheck(
            CrossDatabaseOperationIdentity.digestCanonicalJson(json) == expectedDigest
        )
    }

    private fun JSONObject.toEntity(
        operation: CrossDatabaseOperationRecord
    ): MigrationRecordEntity {
        permanentCheck(getString("schema") == CognitiveMigrationPlanner.PLANNED_RECORD_SCHEMA)
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

    private fun permanentCheck(
        condition: Boolean,
        code: String = CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
    ) {
        if (!condition) throw CrossDatabaseProtocolErrors.permanent(code)
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

    private data class PreparedAudit(
        val verified: Boolean,
        val snapshotDigest: String?,
        val notes: List<String>
    )

    private companion object {
        const val AUDIT_PREPARATION_SCHEMA =
            "morimil.cognitive_migration.audit_preparation.v1"

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
