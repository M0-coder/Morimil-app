package com.morimil.app.data.repository

import com.morimil.app.data.genesis.ultra.GenesisUltraHashProfile
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeActiveBody
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeAuthorization
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeAuthorizationState
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeDocument
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeGuardian
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimePolicy
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeVerifiedSeed
import com.morimil.app.data.local.CrossDatabaseOperationEntity
import com.morimil.app.data.local.CrossDatabaseOperationStatus
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossDatabaseOperationCoordinatorTest {
    @Test
    fun receiptIsDurableBeforeOwnerFinalizationAndReplayIsIdempotent() = runBlocking {
        val identity = identity()
        val command = command(identity)
        val store = FakeStore()
        var canonicalCalls = 0
        var finalizerCalls = 0
        val canonical = object : CrossDatabaseCanonicalEnsurePort {
            override suspend fun ensureCommitted(
                command: CrossDatabaseCanonicalCommand
            ): CrossDatabaseCanonicalReceipt {
                canonicalCalls += 1
                return receipt(command.eventId)
            }
        }
        val finalizer = RecordingFinalizer { operation, operationReceipt ->
            finalizerCalls += 1
            assertEquals(CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT, operation.status)
            assertEquals(operation.canonicalEventHash, operationReceipt.eventHash)
            assertNotNull(operation.canonicalProvenanceDigest)
            val json = CrossDatabaseOperationIdentity.canonicalJson(
                mapOf(
                    "owner_status" to "planned",
                    "schema" to "test.local_result.v1"
                )
            )
            CrossDatabaseLocalResult(
                schema = "test.local_result.v1",
                json = json,
                digest = CrossDatabaseOperationIdentity.digestCanonicalJson(json),
                ownerStatus = "planned"
            )
        }
        val coordinator = CrossDatabaseOperationCoordinator.forTest(
            store = store,
            canonicalEnsurePort = canonical,
            finalizers = listOf(finalizer),
            clockMillis = IncrementingClock()
        )

        val first = coordinator.execute(identity, command)
        val replay = coordinator.execute(identity, command)

        assertEquals(CrossDatabaseOperationStatus.COMMITTED, first.status)
        assertEquals(first, replay)
        assertEquals(1, canonicalCalls)
        assertEquals(1, finalizerCalls)
        assertEquals(
            listOf(
                "STAGED",
                "PENDING_CANONICAL",
                "CANONICAL_COMMITTED",
                "PENDING_LOCAL_COMMIT",
                "COMMITTED"
            ),
            store.stateLog
        )
    }

    @Test
    fun staleWriterEpochFailsBeforeStagingOrCanonicalAppend() = runBlocking {
        val identity = identity()
        val stale = command(identity, writerEpoch = "stale_epoch")
        val store = FakeStore()
        var canonicalCalls = 0
        val coordinator = CrossDatabaseOperationCoordinator.forTest(
            store = store,
            canonicalEnsurePort = object : CrossDatabaseCanonicalEnsurePort {
                override suspend fun ensureCommitted(
                    command: CrossDatabaseCanonicalCommand
                ): CrossDatabaseCanonicalReceipt {
                    canonicalCalls += 1
                    return receipt(command.eventId)
                }
            },
            finalizers = listOf(
                RecordingFinalizer { _, _ ->
                    error("finalizer_must_not_run")
                }
            ),
            clockMillis = IncrementingClock()
        )
        val failure = runCatching {
            coordinator.execute(identity, stale)
        }.exceptionOrNull()

        assertTrue(failure is CrossDatabaseProtocolFailure)
        assertEquals(
            CrossDatabaseProtocolErrors.STALE_WRITER_EPOCH,
            (failure as CrossDatabaseProtocolFailure).stableCode
        )
        assertEquals(0, canonicalCalls)
        assertTrue(store.stateLog.isEmpty())
    }

    private fun command(
        identity: GenesisUltraRuntimeIdentity,
        writerEpoch: String = identity.activeBody.keyEpochId
    ): CrossDatabaseStageCommand {
        val payload = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf("schema" to "test.payload.v1", "subject" to MIGRATION_ID)
        )
        val payloadDigest = CrossDatabaseOperationIdentity.digestCanonicalJson(payload)
        val operationId = CrossDatabaseOperationIdentity.operationId(
            operationType = CognitiveMigrationProtocolTypes.PROPOSE,
            operationVersion = 1,
            instanceId = identity.instanceId,
            writerBodyId = identity.activeBody.bodyId,
            writerEpoch = writerEpoch,
            subjectId = MIGRATION_ID,
            parentOperationId = null,
            childPhase = null,
            payloadDigest = payloadDigest
        )
        val eventId = CrossDatabaseOperationIdentity.eventId(
            operationId,
            CognitiveMigrationProtocolTypes.PROPOSED_EVENT
        )
        val evidence = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "event_id" to eventId,
                "operation_id" to operationId,
                "schema" to "test.evidence.v1"
            )
        )
        return CrossDatabaseStageCommand(
            operationId = operationId,
            ownerType = CognitiveMigrationProtocolTypes.OWNER_TYPE,
            operationType = CognitiveMigrationProtocolTypes.PROPOSE,
            operationVersion = 1,
            instanceId = identity.instanceId,
            writerBodyId = identity.activeBody.bodyId,
            writerEpoch = writerEpoch,
            subjectId = MIGRATION_ID,
            parentOperationId = null,
            childPhase = null,
            payloadSchema = "test.payload.v1",
            payloadJson = payload,
            payloadDigest = payloadDigest,
            eventId = eventId,
            eventType = CognitiveMigrationProtocolTypes.PROPOSED_EVENT,
            eventBody = "deterministic test body",
            evidenceSchema = "test.evidence.v1",
            evidenceJson = evidence,
            evidenceDigest = CrossDatabaseOperationIdentity.digestCanonicalJson(evidence)
        )
    }

    private fun receipt(eventId: String): CrossDatabaseCanonicalReceipt {
        return CrossDatabaseCanonicalReceipt(
            eventId = eventId,
            eventHash = "evsha256:" + "1".repeat(64),
            sequence = 7,
            provenanceDigest = "sha256:" + "2".repeat(64),
            reusedExistingEvent = false
        )
    }

    private fun identity(): GenesisUltraRuntimeIdentity {
        val doctrine = document("doctrine/test.md", "doctrine", "doctrine")
        val charter = document("policy/charter.json", "freedom_charter", "{}")
        val recovery = document("policy/recovery.json", "recovery_policy", "{}")
        return GenesisUltraRuntimeIdentity(
            instanceId = "instance_test",
            companionName = "Morimil",
            bornAt = "2026-07-29T00:00:00Z",
            identityDigest = digest("identity"),
            activeBody = GenesisUltraRuntimeActiveBody(
                bodyId = "body_test",
                status = "active_writer",
                platformProfile = "android",
                publicKeyFingerprint = digest("body_key"),
                keyEpochId = "epoch_test",
                keyEpochDigest = digest("epoch"),
                registryEpoch = 1,
                registryDigest = digest("registry")
            ),
            guardian = GenesisUltraRuntimeGuardian(
                guardianId = "guardian_test",
                keyEpochId = "guardian_epoch",
                publicKeyRef = digest("guardian_key"),
                status = "active",
                role = "custodian_without_ownership",
                anchorDigest = digest("guardian_anchor")
            ),
            seed = GenesisUltraRuntimeVerifiedSeed(
                seedId = "seed_test",
                rootHash = digest("seed"),
                protocolVersion = "genesis-ultra-v1",
                hashProfile = GenesisUltraHashProfile.FIELD_PROFILE,
                identityDigest = digest("identity"),
                doctrineDigest = doctrine.digest
            ),
            doctrine = doctrine,
            policy = GenesisUltraRuntimePolicy(
                freedomCharter = charter,
                recoveryPolicy = recovery,
                freedomCharterDigest = charter.digest,
                recoveryPolicyDigest = recovery.digest
            ),
            authorization = GenesisUltraRuntimeAuthorization(
                state = GenesisUltraRuntimeAuthorizationState.COMMITTED,
                authorizationDigest = digest("authorization"),
                candidateDigest = digest("candidate"),
                consentDigest = digest("consent"),
                authorizedAt = "2026-07-29T00:00:00Z",
                expiresAt = "2026-07-29T01:00:00Z",
                receiptDigest = digest("receipt"),
                birthStatus = "born",
                ownershipConferred = false
            )
        )
    }

    private fun document(
        path: String,
        kind: String,
        text: String
    ): GenesisUltraRuntimeDocument {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        return GenesisUltraRuntimeDocument(
            relativePath = path,
            documentKind = kind,
            digest = GenesisUltraHashProfile.sha256(bytes),
            sourceBytes = bytes
        )
    }

    private fun digest(value: String): String {
        return GenesisUltraHashProfile.sha256(value.toByteArray(StandardCharsets.UTF_8))
    }

    private class RecordingFinalizer(
        private val block: suspend (
            CrossDatabaseOperationRecord,
            CrossDatabaseCanonicalReceipt
        ) -> CrossDatabaseLocalResult
    ) : CrossDatabaseTypedFinalizer {
        override val supportedOperationTypes =
            setOf(CognitiveMigrationProtocolTypes.PROPOSE)

        override suspend fun finalizeInsideTransaction(
            operation: CrossDatabaseOperationRecord,
            receipt: CrossDatabaseCanonicalReceipt
        ): CrossDatabaseLocalResult = block(operation, receipt)
    }

    private class IncrementingClock : () -> Long {
        private var value = 100L
        override fun invoke(): Long = value++
    }

    private class FakeStore : CrossDatabaseOperationStore {
        private var record: CrossDatabaseOperationEntity? = null
        val stateLog = mutableListOf<String>()

        override suspend fun stageExact(
            command: CrossDatabaseStageCommand,
            clockMillis: Long
        ): CrossDatabaseOperationRecord {
            record?.let { existing ->
                if (
                    existing.payloadDigest != command.payloadDigest ||
                    existing.evidenceDigest != command.evidenceDigest
                ) {
                    throw CrossDatabaseProtocolErrors.permanent(
                        CrossDatabaseProtocolErrors.OPERATION_ID_PAYLOAD_CONFLICT
                    )
                }
                return existing
            }
            return command.entity(clockMillis).also {
                record = it
                stateLog += it.status
            }
        }

        override suspend fun load(operationId: String): CrossDatabaseOperationRecord? {
            return record?.takeIf { it.operationId == operationId }
        }

        override suspend fun loadRecoverableForInstance(
            instanceId: String,
            limit: Int
        ): List<CrossDatabaseOperationRecord> {
            return listOfNotNull(record).filter { it.instanceId == instanceId }.take(limit)
        }

        override suspend fun loadRecoverableForOwner(
            instanceId: String,
            ownerType: String,
            limit: Int
        ): List<CrossDatabaseOperationRecord> {
            return listOfNotNull(record).filter {
                it.instanceId == instanceId && it.ownerType == ownerType
            }.take(limit)
        }

        override suspend fun transitionStaged(operationId: String, clockMillis: Long) {
            update(CrossDatabaseOperationStatus.PENDING_CANONICAL, clockMillis)
        }

        override suspend fun persistCanonicalReceipt(
            operationId: String,
            receipt: CrossDatabaseCanonicalReceipt,
            clockMillis: Long
        ) {
            record = requireNotNull(record).copy(
                status = CrossDatabaseOperationStatus.CANONICAL_COMMITTED,
                canonicalEventHash = receipt.eventHash,
                canonicalSequence = receipt.sequence,
                canonicalProvenanceDigest = receipt.provenanceDigest,
                updatedAtMillis = clockMillis
            )
            stateLog += CrossDatabaseOperationStatus.CANONICAL_COMMITTED
        }

        override suspend fun transitionCanonicalCommitted(
            operationId: String,
            clockMillis: Long
        ) {
            update(CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT, clockMillis)
        }

        override suspend fun recordRetryableFailure(
            operationId: String,
            expectedStatus: String,
            errorCode: String,
            clockMillis: Long
        ) {
            record = requireNotNull(record).copy(
                attemptCount = requireNotNull(record).attemptCount + 1,
                lastErrorCode = errorCode,
                updatedAtMillis = clockMillis
            )
        }

        override suspend fun markBlocked(
            operationId: String,
            errorCode: String,
            clockMillis: Long
        ) {
            update(CrossDatabaseOperationStatus.BLOCKED, clockMillis, errorCode)
        }

        override suspend fun finalizeCommitted(
            operationId: String,
            identity: GenesisUltraRuntimeIdentity,
            finalizer: CrossDatabaseTypedFinalizer,
            clockMillis: Long
        ): CrossDatabaseOperationRecord {
            val pending = requireNotNull(record)
            val result = finalizer.finalizeInsideTransaction(
                pending,
                CrossDatabaseCanonicalReceipt(
                    eventId = pending.eventId,
                    eventHash = requireNotNull(pending.canonicalEventHash),
                    sequence = requireNotNull(pending.canonicalSequence),
                    provenanceDigest = requireNotNull(pending.canonicalProvenanceDigest),
                    reusedExistingEvent = false
                )
            )
            record = pending.copy(
                status = CrossDatabaseOperationStatus.COMMITTED,
                localResultSchema = result.schema,
                localResultJson = result.json,
                localResultDigest = result.digest,
                updatedAtMillis = clockMillis,
                committedAtMillis = clockMillis
            )
            stateLog += CrossDatabaseOperationStatus.COMMITTED
            return requireNotNull(record)
        }

        private fun update(status: String, clockMillis: Long, errorCode: String? = null) {
            record = requireNotNull(record).copy(
                status = status,
                lastErrorCode = errorCode,
                updatedAtMillis = clockMillis
            )
            stateLog += status
        }

        private fun CrossDatabaseStageCommand.entity(
            clockMillis: Long
        ): CrossDatabaseOperationEntity {
            return CrossDatabaseOperationEntity(
                operationId = operationId,
                ownerType = ownerType,
                operationType = operationType,
                operationVersion = operationVersion,
                instanceId = instanceId,
                writerBodyId = writerBodyId,
                writerEpoch = writerEpoch,
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
                evidenceDigest = evidenceDigest,
                status = CrossDatabaseOperationStatus.STAGED,
                attemptCount = 0,
                lastErrorCode = null,
                canonicalEventHash = null,
                canonicalSequence = null,
                canonicalProvenanceDigest = null,
                localResultSchema = null,
                localResultJson = null,
                localResultDigest = null,
                occurredAtMillis = clockMillis,
                createdAtMillis = clockMillis,
                updatedAtMillis = clockMillis,
                committedAtMillis = null
            )
        }
    }

    private companion object {
        const val MIGRATION_ID =
            "cog_migration_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
