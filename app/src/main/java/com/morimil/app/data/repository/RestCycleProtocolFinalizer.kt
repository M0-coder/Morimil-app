package com.morimil.app.data.repository

import com.morimil.app.data.local.AutobiographicalSnapshotEntity
import com.morimil.app.data.local.CrossDatabaseOperationStatus
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MemoryOrganDatabase
import org.json.JSONArray
import org.json.JSONObject

internal class RestCycleProtocolFinalizer(
    database: MemoryOrganDatabase
) : CrossDatabaseTypedFinalizer {
    private val dao = database.memoryOrganDao()

    override val supportedOperationTypes: Set<String> = RestCycleProtocolTypes.CLOSED_REGISTRY.keys

    override suspend fun finalizeInsideTransaction(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult {
        permanentCheck(
            operation.status == CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT,
            CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT
        )
        permanentCheck(
            operation.operationVersion == RestCycleProtocolTypes.VERSION &&
                operation.ownerType == RestCycleProtocolTypes.OWNER_TYPE,
            CrossDatabaseProtocolErrors.UNSUPPORTED_OPERATION_VERSION
        )
        return when (operation.operationType) {
            RestCycleProtocolTypes.EXECUTE -> finalizeRest001(operation, receipt)
            RestCycleProtocolTypes.PROPOSE_REPAIR -> finalizeRest002(operation, receipt)
            else -> throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.UNSUPPORTED_OPERATION_VERSION
            )
        }
    }

    private suspend fun finalizeRest001(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult {
        val payload = requirePayload(operation, RestCycleProtocolSchemas.REST_001_PAYLOAD)
        val migrationId = payload.getString("migration_id")
        permanentCheck(migrationId == operation.subjectId)
        permanentCheck(payload.getString("summary") == operation.eventBody)
        val birthRootEventHash = payload.getString("birth_root_event_hash")
        val approvalRequired = payload.getBoolean("approval_required")
        val approvalId = payload.nullableString("approval_id")
        val sourceRefs = payload.getJSONArray("source_refs")
        val sourceHashes = RestCycleLocalProjection.sourceEventHashes(sourceRefs)
        permanentCheck(sourceHashes.isNotEmpty())
        permanentCheck(sourceHashes.distinct().size == sourceHashes.size)

        val migration = dao.loadMigrationRecord(migrationId)
            ?: throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )
        permanentCheck(migration.instanceId == operation.instanceId)
        permanentCheck(migration.genesisCoreHash == birthRootEventHash)
        permanentCheck(migration.migrationType == RestCycleMigrationStore.REST_CYCLE_MIGRATION_TYPE)
        permanentCheck(migration.approvalRequired == approvalRequired)
        permanentCheck(
            RestCycleLocalProjection.jsonArrayValues(migration.affectedArtifactsJson) == sourceHashes,
            CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
        )

        if (migration.status == RestCycleMigrationStore.STATUS_COMPLETED) {
            permanentCheck(migration.postSnapshotId == receipt.eventHash)
        } else if (approvalRequired) {
            permanentCheck(approvalId != null)
            permanentCheck(
                migration.status == RestCycleMigrationStore.STATUS_APPROVED &&
                    migration.approvedByUser &&
                    migration.approvalId == approvalId,
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )
            completeMigration(migrationId, receipt.eventHash)
        } else {
            permanentCheck(approvalId == null)
            permanentCheck(
                migration.status == RestCycleMigrationStore.STATUS_PLANNED &&
                    !migration.approvedByUser &&
                    migration.approvalId == null,
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )
            completeMigration(migrationId, receipt.eventHash)
        }

        val linkRows = RestCycleLocalProjection.buildLinks(
            instanceId = operation.instanceId,
            occurredAtMillis = operation.occurredAtMillis,
            receiptEventHash = receipt.eventHash,
            birthRootEventHash = birthRootEventHash,
            sourceRefs = sourceRefs
        )
        val insertedLinks = dao.insertMemoryLinks(linkRows).count { rowId -> rowId > 0 }

        val autobiography = payload.getJSONObject("autobiography")
        permanentCheck(autobiography.getString("alias") == payload.getString("companion_name"))
        dao.upsertSelfSnapshot(
            RestCycleLocalProjection.buildSelfSnapshot(
                birthRootEventHash = birthRootEventHash,
                receiptEventHash = receipt.eventHash,
                occurredAtMillis = operation.occurredAtMillis,
                autobiography = autobiography
            )
        )

        val resultJson = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "autobiography_snapshot" to "current",
                "canonical_event_hash" to receipt.eventHash,
                "canonical_event_id" to receipt.eventId,
                "canonical_provenance_digest" to receipt.provenanceDigest,
                "canonical_sequence" to receipt.sequence,
                "links_inserted" to insertedLinks,
                "links_total" to linkRows.size,
                "migration_id" to migrationId,
                "owner_status" to RestCycleMigrationStore.STATUS_COMPLETED,
                "schema" to RestCycleProtocolSchemas.REST_001_LOCAL_RESULT,
                "source_set_digest" to payload.getString("source_set_digest")
            )
        )
        return CrossDatabaseLocalResult(
            schema = RestCycleProtocolSchemas.REST_001_LOCAL_RESULT,
            json = resultJson,
            digest = CrossDatabaseOperationIdentity.digestCanonicalJson(resultJson),
            ownerStatus = RestCycleMigrationStore.STATUS_COMPLETED
        )
    }

    private suspend fun finalizeRest002(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult {
        val payload = requirePayload(operation, RestCycleProtocolSchemas.REST_002_PAYLOAD)
        val migrationId = payload.getString("migration_id")
        val birthRootEventHash = payload.getString("birth_root_event_hash")
        val affectedHashes = RestCycleLocalProjection.jsonArrayValues(
            payload.getJSONArray("affected_event_hashes").toString()
        )
        permanentCheck(migrationId == operation.subjectId)
        permanentCheck(payload.getString("mode") == "proposal_only")
        permanentCheck(payload.getBoolean("approval_required"))
        permanentCheck(!payload.getBoolean("automatic_changes"))
        permanentCheck(affectedHashes.isNotEmpty())
        permanentCheck(affectedHashes.distinct().size == affectedHashes.size)

        val migration = dao.loadMigrationRecord(migrationId)
            ?: throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )
        permanentCheck(migration.instanceId == operation.instanceId)
        permanentCheck(migration.genesisCoreHash == birthRootEventHash)
        permanentCheck(migration.migrationType == RestRepairProposalStore.MIGRATION_TYPE)
        permanentCheck(migration.status == RestRepairProposalStore.STATUS_PLANNED)
        permanentCheck(migration.approvalRequired)
        permanentCheck(!migration.approvedByUser)
        permanentCheck(migration.approvalId == null)
        permanentCheck(migration.postSnapshotId == null)
        permanentCheck(
            RestCycleLocalProjection.jsonArrayValues(migration.affectedArtifactsJson) == affectedHashes,
            CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
        )

        val resultJson = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "approval_required" to true,
                "automatic_changes" to false,
                "canonical_event_hash" to receipt.eventHash,
                "canonical_event_id" to receipt.eventId,
                "canonical_provenance_digest" to receipt.provenanceDigest,
                "canonical_sequence" to receipt.sequence,
                "migration_id" to migrationId,
                "owner_status" to RestRepairProposalStore.STATUS_PLANNED,
                "proposal_digest" to payload.getString("proposal_digest"),
                "repair_execution" to "not_implemented",
                "schema" to RestCycleProtocolSchemas.REST_002_LOCAL_RESULT
            )
        )
        return CrossDatabaseLocalResult(
            schema = RestCycleProtocolSchemas.REST_002_LOCAL_RESULT,
            json = resultJson,
            digest = CrossDatabaseOperationIdentity.digestCanonicalJson(resultJson),
            ownerStatus = RestRepairProposalStore.STATUS_PLANNED
        )
    }

    private suspend fun completeMigration(migrationId: String, eventHash: String) {
        val changed = dao.updateMigrationRecordResult(
            migrationId = migrationId,
            status = RestCycleMigrationStore.STATUS_COMPLETED,
            postSnapshotId = eventHash,
            errorsJson = "[]",
            updatedAtMillis = System.currentTimeMillis()
        )
        permanentCheck(changed == 1, CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT)
    }

    private fun requirePayload(operation: CrossDatabaseOperationRecord, schema: String): JSONObject {
        if (operation.payloadSchema != schema) {
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
        permanentCheck(
            payload.getString("schema") == schema,
            CrossDatabaseProtocolErrors.UNSUPPORTED_PAYLOAD_SCHEMA
        )
        return payload
    }

    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)

    private fun permanentCheck(
        condition: Boolean,
        code: String = CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
    ) {
        if (!condition) throw CrossDatabaseProtocolErrors.permanent(code)
    }
}

internal object RestCycleLocalProjection {
    private const val CANONICAL_MEMORY_EVENT_NODE_TYPE = "canonical_memory_event"
    private const val RELATION_DERIVED_FROM = "derived_from"
    private const val CREATED_BY = "rest_cycle"
    private const val PRIVATE_LOCAL = "private_local"
    private const val VERIFICATION_VALID = "valid"

    internal fun sourceEventHashes(sourceRefs: JSONArray): List<String> {
        return (0 until sourceRefs.length()).map { index ->
            sourceRefs.getJSONObject(index).getString("event_hash")
        }
    }

    internal fun jsonArrayValues(json: String): List<String> {
        val array = JSONArray(json)
        return (0 until array.length()).map { index -> array.getString(index) }
    }

    internal fun buildLinks(
        instanceId: String,
        occurredAtMillis: Long,
        receiptEventHash: String,
        birthRootEventHash: String,
        sourceRefs: JSONArray
    ): List<MemoryLinkEntity> {
        return (0 until sourceRefs.length()).map { index ->
            val source = sourceRefs.getJSONObject(index)
            val importance = source.getInt("importance").coerceIn(0, 100)
            val confidence = source.getInt("confidence").coerceIn(0, 100)
            val targetHash = source.getString("event_hash")
            val strength = ((importance * 0.6) + (confidence * 0.4)) / 100.0
            MemoryLinkEntity(
                linkId = MemoryLinkRepository.buildMemoryLinkId(
                    createdAtMillis = occurredAtMillis + index,
                    sourceId = receiptEventHash,
                    targetId = targetHash,
                    relation = RELATION_DERIVED_FROM
                ),
                instanceId = instanceId,
                genesisCoreHash = birthRootEventHash,
                sourceId = receiptEventHash,
                sourceType = CANONICAL_MEMORY_EVENT_NODE_TYPE,
                targetId = targetHash,
                targetType = CANONICAL_MEMORY_EVENT_NODE_TYPE,
                relation = RELATION_DERIVED_FROM,
                strength = strength,
                reason = "canonical_rest_cycle:${source.getString("memory_kind")}/i$importance/c$confidence",
                createdBy = CREATED_BY,
                privacyVisibility = PRIVATE_LOCAL,
                cloudSyncAllowed = false,
                exportAllowed = false,
                verificationState = VERIFICATION_VALID,
                createdAtMillis = occurredAtMillis + index
            )
        }
    }

    internal fun buildSelfSnapshot(
        birthRootEventHash: String,
        receiptEventHash: String,
        occurredAtMillis: Long,
        autobiography: JSONObject
    ): AutobiographicalSnapshotEntity {
        return AutobiographicalSnapshotEntity(
            snapshotId = "current",
            genesisCoreId = birthRootEventHash,
            alias = autobiography.getString("alias"),
            selfSummary = autobiography.getString("self_summary"),
            stableTraits = autobiography.getString("stable_traits"),
            activeGoals = autobiography.getString("active_goals"),
            importantConstraints = autobiography.getString("important_constraints"),
            sourceEventHash = receiptEventHash,
            updatedAtMillis = occurredAtMillis
        )
    }
}
