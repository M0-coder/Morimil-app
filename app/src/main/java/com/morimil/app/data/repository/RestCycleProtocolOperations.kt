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
    const val REST_002_PAYLOAD = "morimil.rest_cycle.rest_002.payload.v1"
    const val REST_002_EVIDENCE = "morimil.rest_cycle.rest_002.evidence.v1"
    const val REST_002_LOCAL_RESULT = "morimil.rest_cycle.rest_002.local_result.v1"
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
        require(sourceSetDigest.matches(SHA256_DIGEST)) {
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

    fun deterministicRepairMigrationId(
        identity: RestCycleProtocolIdentity,
        report: RestRepairProposalReport
    ): String {
        require(report.hasCandidates) { "rest_repair_candidates_empty" }
        val proposalDigest = repairProposalDigest(report)
        val suffix = StableIdDigest.shortSha256Hex(
            namespace = "morimil.rest_cycle.repair_migration.v1",
            parts = listOf(identity.instanceId, identity.writerEpoch, proposalDigest),
            hexLength = 64
        )
        return "repair_$suffix"
    }

    fun execute(
        identity: RestCycleProtocolIdentity,
        companionName: String,
        migrationId: String,
        mode: RestCycleMode,
        sourceSetDigest: String,
        snapshotDigest: String,
        birthRootEventHash: String,
        summary: String,
        sourceEvents: List<RestCycleSourceEvent>,
        autobiography: AutobiographicalMemoryDraft,
        approvalRequired: Boolean,
        approvalId: String?
    ): CrossDatabaseStageCommand {
        require(companionName.isNotBlank()) { "rest_cycle_companion_name_empty" }
        require(migrationId.startsWith("rest_") && migrationId.length > 16) {
            "rest_cycle_migration_id_invalid"
        }
        require(sourceSetDigest.matches(SHA256_DIGEST)) {
            "rest_cycle_source_set_digest_invalid"
        }
        require(snapshotDigest.matches(SHA256_DIGEST)) {
            "rest_cycle_snapshot_digest_invalid"
        }
        require(birthRootEventHash.matches(EVENT_HASH)) {
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
        val autobiographyProjection = mapOf(
            "active_goals" to autobiography.activeGoals,
            "alias" to autobiography.alias,
            "important_constraints" to autobiography.importantConstraints,
            "self_summary" to autobiography.selfSummary,
            "stable_traits" to autobiography.stableTraits
        )
        val payload = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "approval_id" to approvalId,
                "approval_required" to approvalRequired,
                "autobiography" to autobiographyProjection,
                "birth_root_event_hash" to birthRootEventHash,
                "companion_name" to companionName,
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
        return stageCommand(
            identity = identity,
            operationId = operationId,
            operationType = RestCycleProtocolTypes.EXECUTE,
            subjectId = migrationId,
            payloadSchema = RestCycleProtocolSchemas.REST_001_PAYLOAD,
            payloadJson = payload,
            payloadDigest = payloadDigest,
            eventId = eventId,
            eventType = RestCycleProtocolTypes.EXECUTED_EVENT,
            eventBody = cleanSummary,
            evidenceSchema = RestCycleProtocolSchemas.REST_001_EVIDENCE,
            evidenceJson = evidenceJson
        )
    }

    fun proposeRepair(
        identity: RestCycleProtocolIdentity,
        migrationId: String,
        sourceSetDigest: String,
        snapshotDigest: String,
        birthRootEventHash: String,
        report: RestRepairProposalReport
    ): CrossDatabaseStageCommand {
        require(report.hasCandidates) { "rest_repair_candidates_empty" }
        require(migrationId == deterministicRepairMigrationId(identity, report)) {
            "rest_repair_migration_id_mismatch"
        }
        require(sourceSetDigest.matches(SHA256_DIGEST)) { "rest_repair_source_set_digest_invalid" }
        require(snapshotDigest.matches(SHA256_DIGEST)) { "rest_repair_snapshot_digest_invalid" }
        require(birthRootEventHash.matches(EVENT_HASH)) { "rest_repair_birth_root_hash_invalid" }

        val candidates = canonicalRepairCandidates(report)
        val affectedHashes = report.affectedEventHashes
        require(affectedHashes.isNotEmpty()) { "rest_repair_affected_events_empty" }
        val proposalDigest = repairProposalDigest(report)
        val body = report.eventBody(migrationId)
        val payload = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "affected_event_hashes" to affectedHashes,
                "approval_required" to true,
                "automatic_changes" to false,
                "birth_root_event_hash" to birthRootEventHash,
                "candidates" to candidates,
                "migration_id" to migrationId,
                "mode" to "proposal_only",
                "proposal_digest" to proposalDigest,
                "risk_level" to report.riskLevel,
                "schema" to RestCycleProtocolSchemas.REST_002_PAYLOAD,
                "snapshot_digest" to snapshotDigest,
                "source_set_digest" to sourceSetDigest
            )
        )
        val payloadDigest = CrossDatabaseOperationIdentity.digestCanonicalJson(payload)
        val operationId = CrossDatabaseOperationIdentity.operationId(
            operationType = RestCycleProtocolTypes.PROPOSE_REPAIR,
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
            eventType = RestCycleProtocolTypes.REPAIR_PROPOSED_EVENT
        )
        val evidenceJson = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "affected_event_count" to affectedHashes.size,
                "approval_required" to true,
                "automatic_changes" to false,
                "event_id" to eventId,
                "migration_id" to migrationId,
                "operation_id" to operationId,
                "operation_type" to RestCycleProtocolTypes.PROPOSE_REPAIR,
                "owner_type" to RestCycleProtocolTypes.OWNER_TYPE,
                "ownership_conferred" to false,
                "proposal_digest" to proposalDigest,
                "schema" to RestCycleProtocolSchemas.REST_002_EVIDENCE,
                "subject_id" to migrationId
            )
        )
        return stageCommand(
            identity = identity,
            operationId = operationId,
            operationType = RestCycleProtocolTypes.PROPOSE_REPAIR,
            subjectId = migrationId,
            payloadSchema = RestCycleProtocolSchemas.REST_002_PAYLOAD,
            payloadJson = payload,
            payloadDigest = payloadDigest,
            eventId = eventId,
            eventType = RestCycleProtocolTypes.REPAIR_PROPOSED_EVENT,
            eventBody = body,
            evidenceSchema = RestCycleProtocolSchemas.REST_002_EVIDENCE,
            evidenceJson = evidenceJson
        )
    }

    internal fun repairProposalDigest(report: RestRepairProposalReport): String {
        val canonical = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "approval_required" to true,
                "automatic_changes" to false,
                "candidates" to canonicalRepairCandidates(report),
                "mode" to "proposal_only",
                "risk_level" to report.riskLevel,
                "schema" to "morimil.rest_repair_proposal.identity.v1"
            )
        )
        return CrossDatabaseOperationIdentity.digestCanonicalJson(canonical)
    }

    private fun canonicalRepairCandidates(report: RestRepairProposalReport): List<Map<String, Any>> {
        return report.candidates.map { candidate ->
            mapOf(
                "event_hashes" to candidate.eventHashes,
                "kind" to candidate.kind,
                "reason" to candidate.reason,
                "risk_level" to candidate.riskLevel,
                "suggested_action" to candidate.suggestedAction
            )
        }
    }

    private fun stageCommand(
        identity: RestCycleProtocolIdentity,
        operationId: String,
        operationType: String,
        subjectId: String,
        payloadSchema: String,
        payloadJson: String,
        payloadDigest: String,
        eventId: String,
        eventType: String,
        eventBody: String,
        evidenceSchema: String,
        evidenceJson: String
    ): CrossDatabaseStageCommand {
        return CrossDatabaseStageCommand(
            operationId = operationId,
            ownerType = RestCycleProtocolTypes.OWNER_TYPE,
            operationType = operationType,
            operationVersion = RestCycleProtocolTypes.VERSION,
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
            evidenceDigest = CrossDatabaseOperationIdentity.digestCanonicalJson(evidenceJson)
        )
    }

    private val SHA256_DIGEST = Regex("^sha256:[a-f0-9]{64}$")
    private val EVENT_HASH = Regex("^evsha256:[a-f0-9]{64}$")
}
