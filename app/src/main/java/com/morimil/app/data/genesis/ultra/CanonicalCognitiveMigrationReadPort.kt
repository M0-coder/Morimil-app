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
    private val identityRepository: GenesisUltraRuntimeIdentityRepository,
    private val consumerReadPort: CanonicalConsumerReadPort
) : CognitiveMigrationCanonicalReadPort, CognitiveMigrationCanonicalAuditPort {
    override suspend fun readVerifiedPlanningInput(): VerifiedCognitiveMigrationPlanningInput {
        val identity = identityRepository.readCommittedIdentity()
            ?: throw CrossDatabaseProtocolErrors.retryable(
                CrossDatabaseProtocolErrors.CANONICAL_READ_TEMPORARY_UNAVAILABLE
            )
        val snapshot = when (val result = consumerReadPort.readVerifiedSnapshot()) {
            is CanonicalReadResult.Ready -> result.value
            is CanonicalReadResult.Blocked -> throw mapFailure(result.failure)
        }
        requireExactIdentity(identity, snapshot)

        val sources = snapshot.events
            .asSequence()
            .filter { event ->
                event.payloadState == CanonicalPayloadState.VERIFIED_PAYLOAD &&
                    event.content != null &&
                    event.provenance != null &&
                    event.semantics?.memoryKind != "chat_noise"
            }
            .sortedWith(
                compareByDescending<CanonicalConsumerEvent> {
                    it.semantics?.userConfirmed == true
                }
                    .thenByDescending { it.semantics?.importance ?: 0 }
                    .thenByDescending { it.semantics?.confidence ?: 0 }
                    .thenByDescending { it.ref.sequence }
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

        val sourceHashes = sources.map { source -> source.eventHash }.sorted()
        val sourceSetJson = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "instance_id" to identity.instanceId,
                "schema" to SOURCE_SET_SCHEMA,
                "source_event_hashes_sorted" to sourceHashes
            )
        )
        val recordSetJson = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "canonical_last_event_hash" to snapshot.lineage.lastEventHash,
                "canonical_last_sequence" to snapshot.lineage.lastSequence,
                "event_refs" to snapshot.events.map { event ->
                    mapOf(
                        "event_hash" to event.ref.eventHash,
                        "event_id" to event.ref.eventId,
                        "sequence" to event.ref.sequence
                    )
                },
                "instance_id" to identity.instanceId,
                "schema" to RECORD_SET_SCHEMA
            )
        )
        return VerifiedCognitiveMigrationPlanningInput(
            instanceId = identity.instanceId,
            writerBodyId = identity.activeBody.bodyId,
            writerEpoch = identity.activeBody.keyEpochId,
            canonicalBirthRootHash = snapshot.lineage.birthRootEventHash,
            canonicalLastSequence = snapshot.lineage.lastSequence,
            canonicalLastEventHash = snapshot.lineage.lastEventHash,
            canonicalRecordSetDigest =
                CrossDatabaseOperationIdentity.digestCanonicalJson(recordSetJson),
            canonicalPreSnapshotHash = snapshot.lineage.snapshotDigest,
            sourceSetDigest =
                CrossDatabaseOperationIdentity.digestCanonicalJson(sourceSetJson),
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
        } catch (_: Throwable) {
            CanonicalCognitiveMigrationAudit(
                verified = false,
                snapshotDigest = null,
                notes = listOf("canonical_chain_audit_failed")
            )
        }
    }

    private fun requireExactIdentity(
        identity: GenesisUltraRuntimeIdentity,
        snapshot: CanonicalConsumerSnapshot
    ) {
        if (snapshot.identity.instanceId != identity.instanceId) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.WRONG_INSTANCE
            )
        }
        if (snapshot.writer.writerBodyId != identity.activeBody.bodyId) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.UNAUTHORIZED_WRITER_BODY
            )
        }
        if (snapshot.writer.writerEpochId != identity.activeBody.keyEpochId) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.STALE_WRITER_EPOCH
            )
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
            CanonicalReadFailureCode.PROVENANCE_UNVERIFIABLE ->
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
        private const val SOURCE_SET_SCHEMA =
            "morimil.cognitive_migration.source_set.v1"
        private const val RECORD_SET_SCHEMA =
            "morimil.cognitive_migration.canonical_record_set.v1"

        fun production(
            identityRepository: GenesisUltraRuntimeIdentityRepository,
            consumerReadPort: CanonicalConsumerReadPort
        ): CanonicalCognitiveMigrationReadPort {
            return CanonicalCognitiveMigrationReadPort(
                identityRepository = identityRepository,
                consumerReadPort = consumerReadPort
            )
        }
    }
}
