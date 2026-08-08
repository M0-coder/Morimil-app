package com.morimil.app.data.repository

import com.morimil.app.core.identity.StableIdDigest
import com.morimil.app.core.memory.RestCycleMode
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import java.text.Normalizer

internal data class RestCycleProtocolIdentity(
    val instanceId: String,
    val writerBodyId: String,
    val writerEpoch: String
) {
    init {
        require(instanceId.isNotBlank() && instanceId != writerBodyId) {
            "rest_cycle_identity_invalid"
        }
        require(writerBodyId.isNotBlank() && writerEpoch.isNotBlank()) {
            "rest_cycle_writer_invalid"
        }
    }
}

internal object RestCycleProtocolSchemas {
    const val REST_001_PAYLOAD = "morimil.rest_cycle.rest_001.payload.v1"
    const val REST_001_EVIDENCE = "morimil.rest_cycle.rest_001.evidence.v1"
    const val REST_001_LOCAL_RESULT = "morimil.rest_cycle.rest_001.local_result.v1"
    const val REST_001_PREPARATION = "morimil.rest_cycle.rest_001.preparation.v1"
}

internal object RestCycleOperationFactory {
    fun identityOf(identity: GenesisUltraRuntimeIdentity): RestCycleProtocolIdentity {
        return RestCycleProtocolIdentity(
            instanceId = identity.instanceId,
            writerBodyId = identity.activeBody.bodyId,
            writerEpoch = identity.activeBody.keyEpochId
        )
    }

    fun deterministicMigrationId(
        identity: RestCycleProtocolIdentity,
        sourceSetDigest: String,
        mode: RestCycleMode
    ): String {
        require(sourceSetDigest.matches(Regex("^sha256:[a-f0-9]{64}$"))) {
            "rest_cycle_source_set_digest_invalid"
        }
        val suffix = StableIdDigest.shortSha256Hex(
            namespace = "morimil.rest_cycle.migration.v1",
            parts = listOf(
                identity.instanceId,
                identity.writerEpoch,
                sourceSetDigest,
                mode.id
            ),
            hexLength = 64
        )
        return "rest_$suffix"
    }

    fun execute(
        identity: RestCycleProtocolIdentity,
        migrationId: String,
        mode: RestCycleMode,
        sourceSetDigest: String,
        snapshotDigest: String,
        birthRootEventHash: String,
        summary: String,
        sourceEvents: List<RestCycleSourceEvent>,
        approvalRequired: Boolean,
        approvalId: String?
    ): CrossDatabaseStageCommand {
        require(migrationId.startsWith("rest_") && migrationId.length > 16) {
            "rest_cycle_migration_id_invalid"
        }
        require(sourceSetDigest.matches(Regex("^sha256:[a-f0-9]{64}$"))) {
            "rest_cycle_source_set_digest_invalid"
        }
        require(snapshotDigest.matches(Regex("^sha256:[a-f0-9]{64}$"))) {
            "rest_cycle_snapshot_digest_invalid"
        }
        require(birthRootEventHash.matches(Regex("^evsha256:[a-f0-9]{64}$"))) {
            "rest_cycle_birth_root_hash_invalid"
        }
        val cleanSummary = Normalizer.normalize(summary, Normalizer.Form.NFC).trim()
        require(cleanSummary.isNotBlank()) { "rest_cycle_summary_empty" }
        require(sourceEvents.isNotEmpty()) { "rest_cycle_sources_empty" }
        val sourceRefs = sourceEvents.map { event ->
            mapOf(
                "confidence" to event.confidence,
                "event_hash" to event.eventHash,
                "importance" to event.importance,
                "memory_kind" to event.memoryKind,
                "user_confirmed" to event.userConfirmed
            )
        }
        val payload = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "approval_id" to approvalId,
                "approval_required" to approvalRequired,
                "birth_root_event_hash" to birthRootEventHash,
                "migration_id" to migrationId,
                "mode" to mode.id,
                "schema" to RestCycleProtocolSchemas.REST_001_PAYLOAD,
                "snapshot_digest" to snapshotDigest,
                "source_refs" to sourceRefs,
                "source_set_digest" to sourceSetDigest,
                "summary" to cleanSummary
            )
        )
        val payloadDigest = CrossDatabaseOperationIdentity.digestCanonicalJson(payload)
        val operationId = CrossDatabaseOperationIdentity.operationId(
            operationType = RestCycleProtocolTypes.EXECUTE,
            operationVersion = RestCycleProtocolTypes.VERSION,
            instanceId = identity.instanceId,
            writerBodyId = identity.writerBodyId,
            writerEpoch = identity.writerEpoch,
            subjectId = migrationId,
            parentOperationId = null,
            childPhase = null,
            payloadDigest = payloadDigest
        )
        val eventId = CrossDatabaseOperationIdentity.eventId(
            operationId = operationId,
            eventType = RestCycleProtocolTypes.EXECUTED_EVENT
        )
        val evidenceJson = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "approval_id" to approvalId,
                "approval_required" to approvalRequired,
                "event_id" to eventId,
                "migration_id" to migrationId,
                "operation_id" to operationId,
                "operation_type" to RestCycleProtocolTypes.EXECUTE,
                "owner_type" to RestCycleProtocolTypes.OWNER_TYPE,
                "ownership_conferred" to false,
                "schema" to RestCycleProtocolSchemas.REST_001_EVIDENCE,
                "source_count" to sourceEvents.size,
                "source_set_digest" to sourceSetDigest,
                "subject_id" to migrationId
            )
        )
        return CrossDatabaseStageCommand(
            operationId = operationId,
            ownerType = RestCycleProtocolTypes.OWNER_TYPE,
            operationType = RestCycleProtocolTypes.EXECUTE,
            operationVersion = RestCycleProtocolTypes.VERSION,
            instanceId = identity.instanceId,
            writerBodyId = identity.writerBodyId,
            writerEpoch = identity.writerEpoch,
            subjectId = migrationId,
            parentOperationId = null,
            childPhase = null,
            payloadSchema = RestCycleProtocolSchemas.REST_001_PAYLOAD,
            payloadJson = payload,
            payloadDigest = payloadDigest,
            eventId = eventId,
            eventType = RestCycleProtocolTypes.EXECUTED_EVENT,
            eventBody = cleanSummary,
            evidenceSchema = RestCycleProtocolSchemas.REST_001_EVIDENCE,
            evidenceJson = evidenceJson,
            evidenceDigest = CrossDatabaseOperationIdentity.digestCanonicalJson(evidenceJson)
        )
    }
}
