package com.morimil.app.data.repository

data class ProjectVaultCommitCommand(
    val operationId: String,
    val vaultId: String,
    val operationType: String,
    val eventId: String,
    val eventType: String,
    val eventBody: String,
    val evidenceJson: String,
    val payloadDigest: String,
    val occurredAtMillis: Long
) {
    init {
        require(operationId.isNotBlank()) { "project_vault_commit_operation_id_empty" }
        require(vaultId.isNotBlank()) { "project_vault_commit_vault_id_empty" }
        require(operationType.isNotBlank()) { "project_vault_commit_operation_type_empty" }
        require(eventId.isNotBlank()) { "project_vault_commit_event_id_empty" }
        require(eventType.isNotBlank()) { "project_vault_commit_event_type_empty" }
        require(eventBody.isNotBlank()) { "project_vault_commit_event_body_empty" }
        require(payloadDigest.matches(SHA256_HEX)) { "project_vault_commit_digest_invalid" }
        require(occurredAtMillis >= 0L) { "project_vault_commit_timestamp_invalid" }
    }

    private companion object {
        val SHA256_HEX = Regex("^[a-f0-9]{64}$")
    }
}

data class ProjectVaultCommitReceipt(
    val eventId: String,
    val eventHash: String,
    val sequence: Long,
    val reusedExistingEvent: Boolean
) {
    init {
        require(eventId.isNotBlank()) { "project_vault_commit_receipt_event_id_empty" }
        require(eventHash.isNotBlank()) { "project_vault_commit_receipt_hash_empty" }
        require(sequence >= 1L) { "project_vault_commit_receipt_sequence_invalid" }
    }
}

interface ProjectVaultCommitPort {
    suspend fun ensureCommitted(command: ProjectVaultCommitCommand): ProjectVaultCommitReceipt
}
