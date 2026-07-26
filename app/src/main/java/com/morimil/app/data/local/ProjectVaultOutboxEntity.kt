package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "project_vault_outbox",
    indices = [
        Index(value = ["vaultId"], name = "index_project_vault_outbox_vaultId"),
        Index(value = ["status"], name = "index_project_vault_outbox_status"),
        Index(
            value = ["vaultId", "status"],
            name = "index_project_vault_outbox_vaultId_status"
        ),
        Index(value = ["updatedAtMillis"], name = "index_project_vault_outbox_updatedAtMillis")
    ]
)
data class ProjectVaultOutboxEntity(
    @PrimaryKey
    val operationId: String,
    val vaultId: String,
    val operationType: String,
    val eventId: String,
    val eventType: String,
    val eventBody: String,
    val evidenceJson: String,
    val payloadJson: String,
    val payloadDigest: String,
    val status: String,
    val attemptCount: Int,
    val lastError: String?,
    val canonicalEventHash: String?,
    val canonicalSequence: Long?,
    val occurredAtMillis: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val committedAtMillis: Long?
) {
    init {
        require(operationId.isNotBlank()) { "project_vault_outbox_operation_id_empty" }
        require(vaultId.isNotBlank()) { "project_vault_outbox_vault_id_empty" }
        require(operationType in OPERATION_TYPES) { "project_vault_outbox_operation_type_invalid" }
        require(eventId.isNotBlank()) { "project_vault_outbox_event_id_empty" }
        require(eventType.isNotBlank()) { "project_vault_outbox_event_type_empty" }
        require(eventBody.isNotBlank()) { "project_vault_outbox_event_body_empty" }
        require(payloadDigest.matches(SHA256_HEX)) { "project_vault_outbox_digest_invalid" }
        require(status in STATUSES) { "project_vault_outbox_status_invalid" }
        require(attemptCount >= 0) { "project_vault_outbox_attempt_count_invalid" }
        require(createdAtMillis >= 0L && updatedAtMillis >= 0L && occurredAtMillis >= 0L) {
            "project_vault_outbox_timestamp_invalid"
        }
        if (status == STATUS_COMMITTED) {
            require(!canonicalEventHash.isNullOrBlank()) {
                "project_vault_outbox_committed_hash_missing"
            }
            require(canonicalSequence != null && canonicalSequence >= 1L) {
                "project_vault_outbox_committed_sequence_missing"
            }
            require(committedAtMillis != null) {
                "project_vault_outbox_committed_at_missing"
            }
        }
    }

    companion object {
        const val OPERATION_CREATE = "create"
        const val OPERATION_COMPLETE = "complete"
        const val OPERATION_ARCHIVE = "archive"

        const val STATUS_PENDING = "pending"
        const val STATUS_COMMITTED = "committed"
        const val STATUS_BLOCKED = "blocked"

        private val OPERATION_TYPES = setOf(
            OPERATION_CREATE,
            OPERATION_COMPLETE,
            OPERATION_ARCHIVE
        )
        private val STATUSES = setOf(
            STATUS_PENDING,
            STATUS_COMMITTED,
            STATUS_BLOCKED
        )
        private val SHA256_HEX = Regex("^[a-f0-9]{64}$")
    }
}
