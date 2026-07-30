package com.morimil.app.data.genesis.ultra

import com.morimil.app.data.repository.CrossDatabaseOperationIdentity
import com.morimil.app.data.repository.CrossDatabaseProtocolErrors
import com.morimil.app.data.repository.CrossDatabaseProtocolFailure
import kotlinx.coroutines.CancellationException

internal data class VerifiedCognitiveMigrationSource(
    val eventId: String,
    val eventHash: String,
    val sequence: Long,
    val eventType: String,
    val actor: String,
    val content: String,
    val observedAt: String,
    val provenanceDigest: String
)

internal data class VerifiedCognitiveMigrationPlanningInput(
    val instanceId: String,
    val writerBodyId: String,
    val writerEpoch: String,
    val canonicalBirthRootHash: String,
    val canonicalLastSequence: Long,
    val canonicalLastEventHash: String,
    val canonicalRecordSetDigest: String,
    val canonicalPreSnapshotHash: String,
    val sourceSetDigest: String,
    val sources: List<VerifiedCognitiveMigrationSource>
)

internal interface CognitiveMigrationCanonicalReadPort {
    suspend fun readVerifiedPlanningInput(): VerifiedCognitiveMigrationPlanningInput
}

internal interface CognitiveMigrationCanonicalAuditPort {
    suspend fun auditVerifiedCanonicalChain(): CanonicalCognitiveMigrationAudit
}

internal data class CanonicalCognitiveMigrationAudit(
    val verified: Boolean,
    val snapshotDigest: String?,
    val notes: List<String>
)

internal class CanonicalCognitiveMigrationReadPort private constructor(
    private val consumerReadPort: CanonicalConsumerReadPort
) : CognitiveMigrationCanonicalReadPort, CognitiveMigrationCanonicalAuditPort {
    override suspend fun readVerifiedPlanningInput(): VerifiedCognitiveMigrationPlanningInput {
        val snapshot = when (val result = consumerReadPort.readVerifiedSnapshot()) {
            is CanonicalReadResult.Ready -> result.value
            is CanonicalReadResult.Blocked -> throw mapFailure(result.failure)
        }
        requireSnapshotBindings(snapshot)

        val sources = selectPlanningSources(snapshot.events)
        val recordSetDigest = canonicalRecordSetDigest(snapshot.events)
        val preSnapshotHash = canonicalPreSnapshotHash(snapshot, recordSetDigest)
        return VerifiedCognitiveMigrationPlanningInput(
            instanceId = snapshot.identity.instanceId,
            writerBodyId = snapshot.writer.writerBodyId,
            writerEpoch = snapshot.writer.writerEpochId,
            canonicalBirthRootHash = snapshot.lineage.birthRootEventHash,
            canonicalLastSequence = snapshot.lineage.lastSequence,
            canonicalLastEventHash = snapshot.lineage.lastEventHash,
            canonicalRecordSetDigest = recordSetDigest,
            canonicalPreSnapshotHash = preSnapshotHash,
            sourceSetDigest = sourceSetDigest(snapshot.identity.instanceId, sources),
            sources = sources
        )
    }

    override suspend fun auditVerifiedCanonicalChain(): CanonicalCognitiveMigrationAudit {
        return try {
            val input = readVerifiedPlanningInput()
            CanonicalCognitiveMigrationAudit(
                verified = true,
                snapshotDigest = input.canonicalPreSnapshotHash,
                notes = listOf(
                    "canonical_chain_verified",
                    "append_only_refinement_committed"
                )
            )
        } catch (failure: CrossDatabaseProtocolFailure) {
            if (!failure.permanent) throw failure
            CanonicalCognitiveMigrationAudit(
                verified = false,
                snapshotDigest = null,
                notes = listOf("canonical_chain_audit_failed")
            )
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            throw CrossDatabaseProtocolErrors.retryable(
                CrossDatabaseProtocolErrors.CANONICAL_READ_TEMPORARY_UNAVAILABLE,
                failure
            )
        }
    }

    private fun requireSnapshotBindings(snapshot: CanonicalConsumerSnapshot) {
        if (snapshot.lineage.instanceId != snapshot.identity.instanceId) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.WRONG_INSTANCE
            )
        }
        if (snapshot.writer.writerBodyId == snapshot.identity.instanceId) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.UNAUTHORIZED_WRITER_BODY
            )
        }
        snapshot.events.forEach { event ->
            if (event.ref.instanceId != snapshot.identity.instanceId) {
                throw CrossDatabaseProtocolErrors.permanent(
                    CrossDatabaseProtocolErrors.WRONG_INSTANCE
                )
            }
        }
    }

    private fun mapFailure(failure: CanonicalReadFailure): CrossDatabaseProtocolFailure {
        return when (failure.code) {
            CanonicalReadFailureCode.FOREIGN_INSTANCE ->
                CrossDatabaseProtocolErrors.permanent(
                    CrossDatabaseProtocolErrors.WRONG_INSTANCE
                )
            CanonicalReadFailureCode.WRONG_BODY,
            CanonicalReadFailureCode.WRITER_BINDING_MISMATCH ->
                CrossDatabaseProtocolErrors.permanent(
                    CrossDatabaseProtocolErrors.UNAUTHORIZED_WRITER_BODY
                )
            CanonicalReadFailureCode.STALE_WRITER_EPOCH ->
                CrossDatabaseProtocolErrors.permanent(
                    CrossDatabaseProtocolErrors.STALE_WRITER_EPOCH
                )
            CanonicalReadFailureCode.PAYLOAD_MISSING,
            CanonicalReadFailureCode.PAYLOAD_INTEGRITY_INVALID,
            CanonicalReadFailureCode.PROVENANCE_UNVERIFIABLE,
            CanonicalReadFailureCode.CHAIN_CORRUPT,
            CanonicalReadFailureCode.SNAPSHOT_INCOMPLETE,
            CanonicalReadFailureCode.IDENTITY_INCONSISTENT ->
                CrossDatabaseProtocolErrors.permanent(
                    CrossDatabaseProtocolErrors.LEGACY_CANONICAL_INPUT_FORBIDDEN
                )
            else -> if (
                failure.disposition == CanonicalReadDisposition.BLOCKED
            ) {
                CrossDatabaseProtocolErrors.permanent(
                    CrossDatabaseProtocolErrors.LEGACY_CANONICAL_INPUT_FORBIDDEN
                )
            } else {
                CrossDatabaseProtocolErrors.retryable(
                    CrossDatabaseProtocolErrors.CANONICAL_READ_TEMPORARY_UNAVAILABLE
                )
            }
        }
    }

    internal companion object {
        private const val MAX_PLANNING_SOURCES = 16
        private const val COGNITIVE_PROTOCOL_ACTOR = "cognitive_migration_protocol"
        private const val COGNITIVE_PROTOCOL_CLASSIFICATION =
            "durable_cognitive_migration_transition"
        private const val COGNITIVE_PROTOCOL_EVENT_PREFIX = "cognitive_migration."
        private const val COGNITIVE_PROTOCOL_NOTE_SCHEMA =
            "morimil.cross_database_operation.canonical_commit.v1"
        private const val COGNITIVE_PROTOCOL_SOURCE = "cross_database_operations"
        private const val LIVING_MEMORY_NOTE_SCHEMA = "morimil.living_memory_write.v1"
        private const val LEGACY_MEMORY_NOTE_SCHEMA = "morimil.legacy_memory_import.v1"
        private const val SOURCE_SET_SCHEMA =
            "morimil.cognitive_migration.source_set.v2"
        private const val RECORD_SET_SCHEMA =
            "morimil.cognitive_migration.canonical_record_set.v2"
        private const val PRE_SNAPSHOT_SCHEMA =
            "morimil.cognitive_migration.pre_snapshot.v2"
        private val ALLOWED_PLANNING_NOTE_SCHEMAS = setOf(
            LIVING_MEMORY_NOTE_SCHEMA,
            LEGACY_MEMORY_NOTE_SCHEMA
        )

        internal fun isAllowedPlanningSource(event: CanonicalConsumerEvent): Boolean {
            val provenance = event.provenance ?: return false
            val memoryKind = event.semantics?.memoryKind ?: return false
            return event.payloadState == CanonicalPayloadState.VERIFIED_PAYLOAD &&
                event.content != null &&
                provenance.noteSchema in ALLOWED_PLANNING_NOTE_SCHEMAS &&
                memoryKind != "chat_noise" &&
                provenance.source != COGNITIVE_PROTOCOL_SOURCE &&
                provenance.classification != COGNITIVE_PROTOCOL_CLASSIFICATION &&
                provenance.noteSchema != COGNITIVE_PROTOCOL_NOTE_SCHEMA &&
                event.ref.actor != COGNITIVE_PROTOCOL_ACTOR &&
                !event.ref.eventType.startsWith(COGNITIVE_PROTOCOL_EVENT_PREFIX)
        }

        internal fun selectPlanningSources(
            events: List<CanonicalConsumerEvent>
        ): List<VerifiedCognitiveMigrationSource> {
            return events
                .asSequence()
                .filter(::isAllowedPlanningSource)
                .sortedWith(
                    compareByDescending<CanonicalConsumerEvent> {
                        it.semantics?.userConfirmed == true
                    }
                        .thenByDescending { it.semantics?.importance ?: 0 }
                        .thenByDescending { it.semantics?.confidence ?: 0 }
                        .thenByDescending { it.ref.sequence }
                        .thenBy { it.ref.eventHash }
                )
                .take(MAX_PLANNING_SOURCES)
                .map { event ->
                    VerifiedCognitiveMigrationSource(
                        eventId = event.ref.eventId,
                        eventHash = event.ref.eventHash,
                        sequence = event.ref.sequence,
                        eventType = event.ref.eventType,
                        actor = event.ref.actor,
                        content = requireNotNull(event.content),
                        observedAt = event.ref.observedAt,
                        provenanceDigest = event.ref.provenanceDigest
                    )
                }
                .toList()
        }

        internal fun sourceSetDigest(
            instanceId: String,
            sources: List<VerifiedCognitiveMigrationSource>
        ): String {
            val sourceSetJson = CrossDatabaseOperationIdentity.canonicalJson(
                mapOf(
                    "instance_id" to instanceId,
                    "schema" to SOURCE_SET_SCHEMA,
                    "sources_sorted" to sources
                        .sortedWith(compareBy({ it.eventHash }, { it.eventId }))
                        .map { source ->
                            mapOf(
                                "actor" to source.actor,
                                "event_hash" to source.eventHash,
                                "event_id" to source.eventId,
                                "event_type" to source.eventType,
                                "observed_at" to source.observedAt,
                                "provenance_digest" to source.provenanceDigest,
                                "sequence" to source.sequence
                            )
                        }
                )
            )
            return CrossDatabaseOperationIdentity.digestCanonicalJson(sourceSetJson)
        }

        internal fun canonicalRecordSetDigest(
            events: List<CanonicalConsumerEvent>
        ): String {
            val recordSetJson = CrossDatabaseOperationIdentity.canonicalJson(
                mapOf(
                    "events" to events.sortedBy { it.ref.sequence }.map { event ->
                        mapOf(
                            "actor" to event.ref.actor,
                            "body_id" to event.ref.bodyId,
                            "content_digest" to event.ref.contentDigest,
                            "content_type" to event.ref.contentType,
                            "event_hash" to event.ref.eventHash,
                            "event_id" to event.ref.eventId,
                            "event_type" to event.ref.eventType,
                            "instance_id" to event.ref.instanceId,
                            "observed_at" to event.ref.observedAt,
                            "payload_state" to event.payloadState.name,
                            "previous_event_hash" to event.ref.previousEventHash,
                            "privacy" to event.ref.privacy,
                            "provenance_digest" to event.ref.provenanceDigest,
                            "sequence" to event.ref.sequence,
                            "signer_epoch_id" to event.ref.signerEpochId,
                            "signer_id" to event.ref.signerId,
                            "signer_public_key_ref" to event.ref.signerPublicKeyRef
                        )
                    },
                    "schema" to RECORD_SET_SCHEMA
                )
            )
            return CrossDatabaseOperationIdentity.digestCanonicalJson(recordSetJson)
        }

        internal fun canonicalPreSnapshotHash(
            snapshot: CanonicalConsumerSnapshot,
            recordSetDigest: String = canonicalRecordSetDigest(snapshot.events)
        ): String {
            val preSnapshotJson = CrossDatabaseOperationIdentity.canonicalJson(
                mapOf(
                    "identity" to mapOf(
                        "companion_name" to snapshot.identity.companionName,
                        "identity_digest" to snapshot.identity.identityDigest,
                        "instance_id" to snapshot.identity.instanceId
                    ),
                    "lineage" to mapOf(
                        "birth_root_event_hash" to snapshot.lineage.birthRootEventHash,
                        "birth_root_sequence" to snapshot.lineage.birthRootSequence,
                        "last_event_hash" to snapshot.lineage.lastEventHash,
                        "last_sequence" to snapshot.lineage.lastSequence,
                        "post_birth_event_count" to snapshot.lineage.postBirthEventCount,
                        "source_snapshot_digest" to snapshot.lineage.snapshotDigest
                    ),
                    "record_set_digest" to recordSetDigest,
                    "schema" to PRE_SNAPSHOT_SCHEMA,
                    "writer" to mapOf(
                        "registry_digest" to snapshot.writer.registryDigest,
                        "registry_epoch" to snapshot.writer.registryEpoch,
                        "writer_body_id" to snapshot.writer.writerBodyId,
                        "writer_epoch_digest" to snapshot.writer.writerEpochDigest,
                        "writer_epoch_id" to snapshot.writer.writerEpochId,
                        "writer_public_key_ref" to snapshot.writer.writerPublicKeyRef
                    )
                )
            )
            return CrossDatabaseOperationIdentity.digestCanonicalJson(preSnapshotJson)
        }

        fun production(
            consumerReadPort: CanonicalConsumerReadPort
        ): CanonicalCognitiveMigrationReadPort {
            return CanonicalCognitiveMigrationReadPort(
                consumerReadPort = consumerReadPort
            )
        }
    }
}
