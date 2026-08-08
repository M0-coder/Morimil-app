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
                operation.ownerType == RestCycleProtocolTypes.OWNER_TYPE &&
                operation.operationType == RestCycleProtocolTypes.EXECUTE,
            CrossDatabaseProtocolErrors.UNSUPPORTED_OPERATION_VERSION
        )
        val payload = requirePayload(operation)
        val migrationId = payload.getString("migration_id")
        permanentCheck(migrationId == operation.subjectId)
        permanentCheck(payload.getString("summary") == operation.eventBody)
        val birthRootEventHash = payload.getString("birth_root_event_hash")
        val approvalRequired = payload.getBoolean("approval_required")
        val approvalId = payload.nullableString("approval_id")
        val sourceRefs = payload.getJSONArray("source_refs")
        val sourceHashes = (0 until sourceRefs.length()).map { index ->
            sourceRefs.getJSONObject(index).getString("event_hash")
        }
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
            jsonArrayValues(migration.affectedArtifactsJson) == sourceHashes,
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

        val linkRows = buildLinks(
            operation = operation,
            receipt = receipt,
            birthRootEventHash = birthRootEventHash,
            sourceRefs = sourceRefs
        )
        val insertedLinks = dao.insertMemoryLinks(linkRows).count { rowId -> rowId > 0 }

        val autobiography = payload.getJSONObject("autobiography")
        permanentCheck(autobiography.getString("alias") == payload.getString("companion_name"))
        dao.upsertSelfSnapshot(
            AutobiographicalSnapshotEntity(
                snapshotId = "current",
                // Legacy-named projection column retained until F3.3. This is the
                // canonical birth-root event hash, not a genesis_core row.
                genesisCoreId = birthRootEventHash,
                alias = autobiography.getString("alias"),
                selfSummary = autobiography.getString("self_summary"),
                stableTraits = autobiography.getString("stable_traits"),
                activeGoals = autobiography.getString("active_goals"),
                importantConstraints = autobiography.getString("important_constraints"),
                sourceEventHash = receipt.eventHash,
                updatedAtMillis = operation.occurredAtMillis
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

    private fun buildLinks(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt,
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
                    createdAtMillis = operation.occurredAtMillis + index,
                    sourceId = receipt.eventHash,
                    targetId = targetHash,
                    relation = RELATION_DERIVED_FROM
                ),
                instanceId = operation.instanceId,
                genesisCoreHash = birthRootEventHash,
                sourceId = receipt.eventHash,
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
                createdAtMillis = operation.occurredAtMillis + index
            )
        }
    }

    private fun requirePayload(operation: CrossDatabaseOperationRecord): JSONObject {
        if (operation.payloadSchema != RestCycleProtocolSchemas.REST_001_PAYLOAD) {
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
            payload.getString("schema") == RestCycleProtocolSchemas.REST_001_PAYLOAD,
            CrossDatabaseProtocolErrors.UNSUPPORTED_PAYLOAD_SCHEMA
        )
        return payload
    }

    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)

    private fun jsonArrayValues(json: String): List<String> {
        val array = JSONArray(json)
        return (0 until array.length()).map { index -> array.getString(index) }
    }

    private fun permanentCheck(
        condition: Boolean,
        code: String = CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
    ) {
        if (!condition) throw CrossDatabaseProtocolErrors.permanent(code)
    }

    private companion object {
        const val CANONICAL_MEMORY_EVENT_NODE_TYPE = "canonical_memory_event"
        const val RELATION_DERIVED_FROM = "derived_from"
        const val CREATED_BY = "rest_cycle"
        const val PRIVATE_LOCAL = "private_local"
        const val VERIFICATION_VALID = "valid"
    }
}
