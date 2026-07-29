package com.morimil.app.data.genesis.ultra

internal interface CanonicalConsumerReadPort {
    suspend fun readVerifiedSnapshot():
        CanonicalReadResult<CanonicalConsumerSnapshot>

    suspend fun readRecallCandidates(
        limit: Int = 60
    ): CanonicalReadResult<CanonicalRecallCandidateBatch>

    suspend fun readRestCyclePlanningInput(
        limit: Int = 80
    ): CanonicalReadResult<CanonicalRestCyclePlanningInput>

    suspend fun readHealthInput(
        recentLimit: Int = 20
    ): CanonicalReadResult<CanonicalHealthInput>
}

internal sealed interface CanonicalReadResult<out T> {
    data class Ready<T>(val value: T) : CanonicalReadResult<T>
    data class Blocked(val failure: CanonicalReadFailure) : CanonicalReadResult<Nothing>
}

internal data class CanonicalReadFailure(
    val code: CanonicalReadFailureCode,
    val disposition: CanonicalReadDisposition,
    val diagnosticCode: String
)

internal enum class CanonicalReadDisposition {
    NOT_READY,
    RETRYABLE,
    BLOCKED
}

internal enum class CanonicalReadFailureCode {
    BIRTH_NOT_COMMITTED,
    IDENTITY_INCONSISTENT,
    CANONICAL_MEMORY_ABSENT,
    CHAIN_CORRUPT,
    FOREIGN_INSTANCE,
    WRONG_BODY,
    STALE_WRITER_EPOCH,
    SNAPSHOT_INCOMPLETE,
    WRITER_BINDING_MISMATCH,
    SNAPSHOT_CHANGED_DURING_READ,
    PAYLOAD_MISSING,
    PAYLOAD_INTEGRITY_INVALID,
    PROVENANCE_UNVERIFIABLE,
    TRANSIENT_STORE_UNAVAILABLE,
    UNCLASSIFIED_VERIFICATION_FAILURE
}

internal data class CanonicalConsumerSnapshot(
    val identity: CanonicalInstanceRef,
    val writer: CanonicalWriterRef,
    val lineage: CanonicalSnapshotRef,
    val events: List<CanonicalConsumerEvent>
)

internal data class CanonicalInstanceRef(
    val instanceId: String,
    val companionName: String,
    val identityDigest: String
)

internal data class CanonicalWriterRef(
    val writerBodyId: String,
    val writerEpochId: String,
    val writerEpochDigest: String,
    val writerPublicKeyRef: String,
    val registryEpoch: Long,
    val registryDigest: String
)

internal data class CanonicalSnapshotRef(
    val instanceId: String,
    val birthRootEventHash: String,
    val birthRootSequence: Long,
    val lastEventHash: String,
    val lastSequence: Long,
    val postBirthEventCount: Int,
    val snapshotDigest: String
)

internal data class CanonicalEventRef(
    val eventId: String,
    val eventHash: String,
    val sequence: Long,
    val previousEventHash: String,
    val instanceId: String,
    val bodyId: String,
    val signerId: String,
    val signerEpochId: String,
    val signerPublicKeyRef: String,
    val eventType: String,
    val actor: String,
    val observedAt: String,
    val contentDigest: String,
    val contentType: String,
    val provenanceDigest: String,
    val privacy: String
)

internal data class CanonicalEventProvenance(
    val schema: String,
    val instanceId: String,
    val bodyId: String,
    val source: String,
    val classification: String,
    val userConfirmed: Boolean,
    val sourceId: String?,
    val noteSchema: String?,
    val noteJson: String?
)

internal enum class CanonicalPayloadState {
    VERIFIED_PAYLOAD,
    ACTIVATION_METADATA_ONLY
}

internal data class CanonicalMemorySemantics(
    val memoryKind: String?,
    val importance: Int?,
    val confidence: Int?,
    val userConfirmed: Boolean
)

internal class CanonicalConsumerEvent private constructor(
    val ref: CanonicalEventRef,
    val content: String?,
    val provenance: CanonicalEventProvenance?,
    val semantics: CanonicalMemorySemantics?,
    val payloadState: CanonicalPayloadState,
    contentBytes: ByteArray?,
    provenanceBytes: ByteArray?
) {
    private val verifiedContentBytes = contentBytes?.copyOf()
    private val verifiedProvenanceBytes = provenanceBytes?.copyOf()

    fun copyContentBytes(): ByteArray? = verifiedContentBytes?.copyOf()

    fun copyProvenanceBytes(): ByteArray? = verifiedProvenanceBytes?.copyOf()

    internal companion object {
        fun verified(
            ref: CanonicalEventRef,
            content: String,
            provenance: CanonicalEventProvenance,
            semantics: CanonicalMemorySemantics?,
            contentBytes: ByteArray,
            provenanceBytes: ByteArray
        ): CanonicalConsumerEvent {
            return CanonicalConsumerEvent(
                ref = ref,
                content = content,
                provenance = provenance,
                semantics = semantics,
                payloadState = CanonicalPayloadState.VERIFIED_PAYLOAD,
                contentBytes = contentBytes,
                provenanceBytes = provenanceBytes
            )
        }

        fun activationMetadataOnly(ref: CanonicalEventRef): CanonicalConsumerEvent {
            return CanonicalConsumerEvent(
                ref = ref,
                content = null,
                provenance = null,
                semantics = null,
                payloadState = CanonicalPayloadState.ACTIVATION_METADATA_ONLY,
                contentBytes = null,
                provenanceBytes = null
            )
        }
    }
}

internal data class CanonicalRecallCandidateBatch(
    val snapshot: CanonicalSnapshotRef,
    val instanceId: String,
    val writerBodyId: String,
    val writerEpochId: String,
    val candidates: List<CanonicalRecallCandidate>
)

internal data class CanonicalRecallCandidate(
    val event: CanonicalEventRef,
    val content: String,
    val provenance: CanonicalEventProvenance,
    val memoryKind: String,
    val importance: Int,
    val confidence: Int,
    val userConfirmed: Boolean
)

internal data class CanonicalRestCyclePlanningInput(
    val identity: CanonicalInstanceRef,
    val writer: CanonicalWriterRef,
    val snapshot: CanonicalSnapshotRef,
    val sources: List<CanonicalRestCycleSource>,
    val latestRestCycle: CanonicalEventRef?,
    val sourceSetDigest: String
)

internal data class CanonicalRestCycleSource(
    val event: CanonicalEventRef,
    val content: String,
    val provenance: CanonicalEventProvenance,
    val semantics: CanonicalMemorySemantics?
)

internal data class CanonicalHealthInput(
    val instanceId: String,
    val writerBodyId: String,
    val writerEpochId: String,
    val snapshotDigest: String,
    val birthRootPresent: Boolean,
    val canonicalMemoryVerified: Boolean,
    val totalCanonicalEventCount: Int,
    val postBirthEventCount: Int,
    val recentVerifiedEventCount: Int,
    val latestRestCycle: CanonicalEventRef?,
    val quarantineEventCount: Int
)
