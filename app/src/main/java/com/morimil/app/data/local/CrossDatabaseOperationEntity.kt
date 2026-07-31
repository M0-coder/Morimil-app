package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cross_database_operations",
    indices = [
        Index(
            value = ["eventId"],
            name = "index_cross_database_operations_eventId",
            unique = true
        ),
        Index(
            value = ["instanceId", "status", "createdAtMillis", "operationId"],
            name = "index_cross_database_operations_instance_status_created"
        ),
        Index(
            value = ["ownerType", "subjectId", "operationType", "status"],
            name = "index_cross_database_operations_owner_subject_status"
        ),
        Index(
            value = ["status", "updatedAtMillis", "operationId"],
            name = "index_cross_database_operations_status_updated"
        ),
        Index(
            value = ["instanceId", "writerEpoch", "status"],
            name = "index_cross_database_operations_writer_epoch_status"
        ),
        Index(
            value = ["parentOperationId", "childPhase"],
            name = "index_cross_database_operations_parent_child"
        )
    ]
)
data class CrossDatabaseOperationEntity(
    @PrimaryKey
    val operationId: String,
    val ownerType: String,
    val operationType: String,
    val operationVersion: Int,
    val instanceId: String,
    val writerBodyId: String,
    val writerEpoch: String,
    val subjectId: String,
    val parentOperationId: String?,
    val childPhase: String?,
    val payloadSchema: String,
    val payloadJson: String,
    val payloadDigest: String,
    val eventId: String,
    val eventType: String,
    val eventBody: String,
    val evidenceSchema: String,
    val evidenceJson: String,
    val evidenceDigest: String,
    val status: String,
    val attemptCount: Int,
    val lastErrorCode: String?,
    val canonicalEventHash: String?,
    val canonicalSequence: Long?,
    val canonicalProvenanceDigest: String?,
    val localResultSchema: String?,
    val localResultJson: String?,
    val localResultDigest: String?,
    val occurredAtMillis: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val committedAtMillis: Long?
) {
    init {
        require(operationId.matches(OPERATION_ID)) { "xop_operation_id_invalid" }
        require(ownerType.isNotBlank()) { "xop_owner_type_empty" }
        require(operationType.isNotBlank()) { "xop_operation_type_empty" }
        require(operationVersion >= 1) { "xop_operation_version_invalid" }
        require(instanceId.isNotBlank()) { "xop_instance_id_empty" }
        require(writerBodyId.isNotBlank()) { "xop_writer_body_id_empty" }
        require(writerEpoch.isNotBlank()) { "xop_writer_epoch_empty" }
        require(instanceId != writerBodyId) { "xop_instance_body_identity_collision" }
        require(subjectId.isNotBlank()) { "xop_subject_id_empty" }
        require((parentOperationId == null) == (childPhase == null)) {
            "xop_parent_child_pair_incomplete"
        }
        require(payloadSchema.isNotBlank()) { "xop_payload_schema_empty" }
        require(payloadJson.isNotBlank()) { "xop_payload_json_empty" }
        require(payloadDigest.matches(SHA256_DIGEST)) { "xop_payload_digest_invalid" }
        require(eventId.matches(EVENT_ID)) { "xop_event_id_invalid" }
        require(eventType.isNotBlank()) { "xop_event_type_empty" }
        require(eventBody.isNotBlank()) { "xop_event_body_empty" }
        require(evidenceSchema.isNotBlank()) { "xop_evidence_schema_empty" }
        require(evidenceJson.isNotBlank()) { "xop_evidence_json_empty" }
        require(evidenceDigest.matches(SHA256_DIGEST)) { "xop_evidence_digest_invalid" }
        require(status in CrossDatabaseOperationStatus.ALL) { "xop_status_invalid" }
        require(attemptCount >= 0) { "xop_attempt_count_invalid" }
        require(lastErrorCode == null || lastErrorCode.matches(ERROR_CODE)) {
            "xop_error_code_invalid"
        }
        require(occurredAtMillis >= 0L) { "xop_occurred_at_invalid" }
        require(createdAtMillis >= 0L) { "xop_created_at_invalid" }
        require(updatedAtMillis >= createdAtMillis) { "xop_updated_at_invalid" }

        val receiptValues = listOf(
            canonicalEventHash,
            canonicalSequence,
            canonicalProvenanceDigest
        )
        require(receiptValues.all { it == null } || receiptValues.all { it != null }) {
            "xop_receipt_partial"
        }
        canonicalEventHash?.let { require(it.matches(EVENT_HASH)) { "xop_event_hash_invalid" } }
        canonicalSequence?.let { require(it >= 1L) { "xop_sequence_invalid" } }
        canonicalProvenanceDigest?.let {
            require(it.matches(SHA256_DIGEST)) { "xop_provenance_digest_invalid" }
        }

        val localResultValues = listOf(
            localResultSchema,
            localResultJson,
            localResultDigest
        )
        require(localResultValues.all { it == null } || localResultValues.all { it != null }) {
            "xop_local_result_partial"
        }
        localResultDigest?.let {
            require(it.matches(SHA256_DIGEST)) { "xop_local_result_digest_invalid" }
        }
        if (status == CrossDatabaseOperationStatus.COMMITTED) {
            require(receiptValues.all { it != null }) { "xop_committed_receipt_missing" }
            require(localResultValues.all { it != null }) { "xop_committed_result_missing" }
            require(committedAtMillis != null && committedAtMillis >= createdAtMillis) {
                "xop_committed_at_missing"
            }
        } else {
            require(committedAtMillis == null) { "xop_non_committed_timestamp_present" }
        }
    }

    internal companion object {
        val OPERATION_ID = Regex("^xop_[a-f0-9]{64}$")
        val EVENT_ID = Regex("^xevt_[a-f0-9]{64}$")
        val SHA256_DIGEST = Regex("^sha256:[a-f0-9]{64}$")
        val EVENT_HASH = Regex("^evsha256:[a-f0-9]{64}$")
        val ERROR_CODE = Regex("^XOP_[A-Z0-9_]{3,96}$")
    }
}

internal object CrossDatabaseOperationStatus {
    const val STAGED = "STAGED"
    const val PENDING_CANONICAL = "PENDING_CANONICAL"
    const val CANONICAL_COMMITTED = "CANONICAL_COMMITTED"
    const val PENDING_LOCAL_COMMIT = "PENDING_LOCAL_COMMIT"
    const val COMMITTED = "COMMITTED"
    const val BLOCKED = "BLOCKED"

    val ALL = setOf(
        STAGED,
        PENDING_CANONICAL,
        CANONICAL_COMMITTED,
        PENDING_LOCAL_COMMIT,
        COMMITTED,
        BLOCKED
    )
}
