package com.morimil.app.data.repository

import com.morimil.app.core.identity.StableIdDigest
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import com.morimil.app.data.local.CrossDatabaseOperationEntity
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

internal typealias CrossDatabaseOperationRecord = CrossDatabaseOperationEntity

internal data class CrossDatabaseStageCommand(
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
    val evidenceDigest: String
) {
    init {
        require(operationId.matches(CrossDatabaseOperationEntity.OPERATION_ID)) {
            "xop_stage_operation_id_invalid"
        }
        require(eventId.matches(CrossDatabaseOperationEntity.EVENT_ID)) {
            "xop_stage_event_id_invalid"
        }
        require(operationVersion >= 1) { "xop_stage_version_invalid" }
        require(instanceId.isNotBlank() && instanceId != writerBodyId) {
            "xop_stage_identity_invalid"
        }
        require(writerBodyId.isNotBlank() && writerEpoch.isNotBlank()) {
            "xop_stage_writer_invalid"
        }
        require(subjectId.isNotBlank()) { "xop_stage_subject_invalid" }
        require((parentOperationId == null) == (childPhase == null)) {
            "xop_stage_parent_child_invalid"
        }
        require(payloadDigest == CrossDatabaseOperationIdentity.digestCanonicalJson(payloadJson)) {
            "xop_stage_payload_digest_mismatch"
        }
        require(evidenceDigest == CrossDatabaseOperationIdentity.digestCanonicalJson(evidenceJson)) {
            "xop_stage_evidence_digest_mismatch"
        }
        require(
            operationId == CrossDatabaseOperationIdentity.operationId(
                operationType = operationType,
                operationVersion = operationVersion,
                instanceId = instanceId,
                writerBodyId = writerBodyId,
                writerEpoch = writerEpoch,
                subjectId = subjectId,
                parentOperationId = parentOperationId,
                childPhase = childPhase,
                payloadDigest = payloadDigest
            )
        ) { "xop_stage_operation_identity_mismatch" }
        require(eventId == CrossDatabaseOperationIdentity.eventId(operationId, eventType)) {
            "xop_stage_event_identity_mismatch"
        }
        require(eventBody.isNotBlank()) { "xop_stage_event_body_empty" }
    }
}

internal interface CrossDatabaseOperationStagingPort {
    suspend fun stageExact(command: CrossDatabaseStageCommand): CrossDatabaseOperationRecord
    suspend fun load(operationId: String): CrossDatabaseOperationRecord?
}

internal data class CrossDatabaseCanonicalCommand(
    val operationId: String,
    val operationType: String,
    val operationVersion: Int,
    val instanceId: String,
    val writerBodyId: String,
    val writerEpoch: String,
    val subjectId: String,
    val payloadDigest: String,
    val evidenceDigest: String,
    val eventId: String,
    val eventType: String,
    val eventBody: String,
    val evidenceJson: String,
    val occurredAtMillis: Long
)

internal data class CrossDatabaseCanonicalReceipt(
    val eventId: String,
    val eventHash: String,
    val sequence: Long,
    val provenanceDigest: String,
    val reusedExistingEvent: Boolean
) {
    init {
        require(eventId.matches(CrossDatabaseOperationEntity.EVENT_ID)) {
            "xop_receipt_event_id_invalid"
        }
        require(eventHash.matches(CrossDatabaseOperationEntity.EVENT_HASH)) {
            "xop_receipt_event_hash_invalid"
        }
        require(sequence >= 1L) { "xop_receipt_sequence_invalid" }
        require(provenanceDigest.matches(CrossDatabaseOperationEntity.SHA256_DIGEST)) {
            "xop_receipt_provenance_invalid"
        }
    }
}

internal interface CrossDatabaseCanonicalEnsurePort {
    suspend fun ensureCommitted(
        command: CrossDatabaseCanonicalCommand
    ): CrossDatabaseCanonicalReceipt
}

internal data class CrossDatabaseLocalResult(
    val schema: String,
    val json: String,
    val digest: String,
    val ownerStatus: String
) {
    init {
        require(schema.isNotBlank()) { "xop_local_result_schema_empty" }
        require(digest == CrossDatabaseOperationIdentity.digestCanonicalJson(json)) {
            "xop_local_result_digest_mismatch"
        }
        require(ownerStatus.isNotBlank()) { "xop_local_result_owner_status_empty" }
    }
}

internal interface CrossDatabaseTypedFinalizer {
    val supportedOperationTypes: Set<String>

    suspend fun finalizeInsideTransaction(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult
}

internal data class CrossDatabaseRecoveryReport(
    val stagedCount: Int,
    val pendingCanonicalCount: Int,
    val canonicalCommittedCount: Int,
    val pendingLocalCount: Int,
    val committedCount: Int,
    val blockedCount: Int,
    val recoveredCount: Int,
    val retryableFailureCount: Int
)

internal interface CrossDatabaseOperationRecovery {
    suspend fun recoverAtStartup(
        identity: GenesisUltraRuntimeIdentity,
        limit: Int
    ): CrossDatabaseRecoveryReport

    suspend fun recoverBeforeMutation(
        identity: GenesisUltraRuntimeIdentity,
        ownerType: String,
        limit: Int
    ): CrossDatabaseRecoveryReport
}

internal class CrossDatabaseProtocolFailure(
    val stableCode: String,
    val permanent: Boolean,
    cause: Throwable? = null
) : IllegalStateException(stableCode, cause) {
    init {
        require(stableCode.matches(Regex("^XOP_[A-Z0-9_]{3,96}$"))) {
            "xop_failure_code_invalid"
        }
    }
}

internal object CrossDatabaseProtocolErrors {
    const val DATABASE_TEMPORARY_UNAVAILABLE = "XOP_DATABASE_TEMPORARY_UNAVAILABLE"
    const val CANONICAL_READ_TEMPORARY_UNAVAILABLE =
        "XOP_CANONICAL_READ_TEMPORARY_UNAVAILABLE"
    const val CANONICAL_APPEND_INTERRUPTED = "XOP_CANONICAL_APPEND_INTERRUPTED"
    const val LOCAL_FINALIZATION_INTERRUPTED = "XOP_LOCAL_FINALIZATION_INTERRUPTED"
    const val RECOVERY_BATCH_EXHAUSTED = "XOP_RECOVERY_BATCH_EXHAUSTED"
    const val OPERATION_ID_PAYLOAD_CONFLICT = "XOP_OPERATION_ID_PAYLOAD_CONFLICT"
    const val OPERATION_ID_EVIDENCE_CONFLICT = "XOP_OPERATION_ID_EVIDENCE_CONFLICT"
    const val OWNER_TRANSITION_CONFLICT = "XOP_OWNER_TRANSITION_CONFLICT"
    const val EVENT_ID_CONFLICT = "XOP_EVENT_ID_CONFLICT"
    const val CANONICAL_EVENT_MISMATCH = "XOP_CANONICAL_EVENT_MISMATCH"
    const val CANONICAL_PROVENANCE_MISMATCH = "XOP_CANONICAL_PROVENANCE_MISMATCH"
    const val CANONICAL_RECEIPT_CONFLICT = "XOP_CANONICAL_RECEIPT_CONFLICT"
    const val WRONG_INSTANCE = "XOP_WRONG_INSTANCE"
    const val UNAUTHORIZED_WRITER_BODY = "XOP_UNAUTHORIZED_WRITER_BODY"
    const val STALE_WRITER_EPOCH = "XOP_STALE_WRITER_EPOCH"
    const val OWNER_STATE_CONFLICT = "XOP_OWNER_STATE_CONFLICT"
    const val PREDECESSOR_RECEIPT_MISSING = "XOP_PREDECESSOR_RECEIPT_MISSING"
    const val UNSUPPORTED_OPERATION_VERSION = "XOP_UNSUPPORTED_OPERATION_VERSION"
    const val UNSUPPORTED_PAYLOAD_SCHEMA = "XOP_UNSUPPORTED_PAYLOAD_SCHEMA"
    const val LEGACY_CANONICAL_INPUT_FORBIDDEN = "XOP_LEGACY_CANONICAL_INPUT_FORBIDDEN"

    fun permanent(code: String, cause: Throwable? = null): CrossDatabaseProtocolFailure {
        return CrossDatabaseProtocolFailure(code, permanent = true, cause = cause)
    }

    fun retryable(code: String, cause: Throwable? = null): CrossDatabaseProtocolFailure {
        return CrossDatabaseProtocolFailure(code, permanent = false, cause = cause)
    }

    fun rethrowCancellation(failure: Throwable) {
        if (failure is CancellationException) throw failure
    }
}

internal object CognitiveMigrationProtocolTypes {
    const val OWNER_TYPE = "cognitive_migration"
    const val PROPOSE = "cognitive_migration.propose"
    const val APPROVE = "cognitive_migration.approve"
    const val EXECUTE = "cognitive_migration.execute"
    const val ROLLBACK = "cognitive_migration.rollback"

    const val PROPOSED_EVENT = "cognitive_migration.proposed"
    const val APPROVED_EVENT = "cognitive_migration.approved"
    const val EXECUTED_EVENT = "cognitive_migration.executed"
    const val ROLLBACK_EVENT = "cognitive_migration.rollback"

    const val VERSION = 1

    val CLOSED_REGISTRY = mapOf(
        PROPOSE to PROPOSED_EVENT,
        APPROVE to APPROVED_EVENT,
        EXECUTE to EXECUTED_EVENT,
        ROLLBACK to ROLLBACK_EVENT
    )
}

internal object CrossDatabaseOperationIdentity {
    fun operationId(
        operationType: String,
        operationVersion: Int,
        instanceId: String,
        writerBodyId: String,
        writerEpoch: String,
        subjectId: String,
        parentOperationId: String?,
        childPhase: String?,
        payloadDigest: String
    ): String {
        require(payloadDigest.matches(CrossDatabaseOperationEntity.SHA256_DIGEST)) {
            "xop_identity_payload_digest_invalid"
        }
        val suffix = StableIdDigest.shortSha256Hex(
            namespace = "morimil.cross_database.operation_id.v1",
            parts = listOf(
                operationType,
                operationVersion.toString(),
                instanceId,
                writerBodyId,
                writerEpoch,
                subjectId,
                parentOperationId.orEmpty(),
                childPhase.orEmpty(),
                payloadDigest
            ),
            hexLength = 64
        )
        return "xop_$suffix"
    }

    fun eventId(operationId: String, eventType: String): String {
        require(operationId.matches(CrossDatabaseOperationEntity.OPERATION_ID)) {
            "xop_event_operation_id_invalid"
        }
        val suffix = StableIdDigest.shortSha256Hex(
            namespace = "morimil.cross_database.event_id.v1",
            parts = listOf(operationId, eventType),
            hexLength = 64
        )
        return "xevt_$suffix"
    }

    fun digestCanonicalJson(canonicalJson: String): String {
        require(canonicalJson == canonicalizeParsedJson(canonicalJson)) {
            "xop_json_not_canonical"
        }
        return digestUtf8(canonicalJson)
    }

    fun digestUtf8(value: String): String {
        require(Normalizer.isNormalized(value, Normalizer.Form.NFC)) {
            "xop_text_not_nfc"
        }
        val raw = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte ->
                "%02x".format(Locale.US, byte.toInt() and 0xff)
            }
        return "sha256:$raw"
    }

    fun canonicalJson(value: Any?): String = canonicalize(value)

    private fun canonicalizeParsedJson(value: String): String {
        val clean = value.trim()
        require(clean.isNotEmpty()) { "xop_json_empty" }
        val parsed = when {
            clean.startsWith("{") -> JSONObject(clean)
            clean.startsWith("[") -> JSONArray(clean)
            else -> error("xop_json_root_invalid")
        }
        return canonicalize(parsed)
    }

    private fun canonicalize(value: Any?): String {
        return when (value) {
            null, JSONObject.NULL -> "null"
            is String -> JSONObject.quote(requireNfc(value))
            is Boolean -> value.toString()
            is Byte, is Short, is Int, is Long -> value.toString()
            is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(
                prefix = "{",
                postfix = "}",
                separator = ","
            ) { key -> "${JSONObject.quote(requireNfc(key))}:${canonicalize(value.get(key))}" }
            is JSONArray -> (0 until value.length()).joinToString(
                prefix = "[",
                postfix = "]",
                separator = ","
            ) { index -> canonicalize(value.get(index)) }
            is Map<*, *> -> value.entries
                .map { entry ->
                    require(entry.key is String) { "xop_json_map_key_not_string" }
                    entry.key as String to entry.value
                }
                .sortedBy { entry -> entry.first }
                .joinToString(prefix = "{", postfix = "}", separator = ",") { entry ->
                    "${JSONObject.quote(requireNfc(entry.first))}:${canonicalize(entry.second)}"
                }
            is Set<*> -> value.map(::canonicalize).sorted()
                .joinToString(prefix = "[", postfix = "]", separator = ",")
            is Iterable<*> -> value.joinToString(
                prefix = "[",
                postfix = "]",
                separator = ",",
                transform = ::canonicalize
            )
            is Array<*> -> value.joinToString(
                prefix = "[",
                postfix = "]",
                separator = ",",
                transform = ::canonicalize
            )
            else -> error("xop_json_type_unsupported:${value::class.java.simpleName}")
        }
    }

    private fun requireNfc(value: String): String {
        require(Normalizer.isNormalized(value, Normalizer.Form.NFC)) {
            "xop_json_string_not_nfc"
        }
        return value
    }
}
