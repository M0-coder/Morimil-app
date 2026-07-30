package com.morimil.app.data.repository

import androidx.room.withTransaction
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import com.morimil.app.data.local.CrossDatabaseOperationDao
import com.morimil.app.data.local.CrossDatabaseOperationEntity
import com.morimil.app.data.local.CrossDatabaseOperationStatus
import com.morimil.app.data.local.MemoryOrganDatabase
import kotlinx.coroutines.CancellationException

internal class CrossDatabaseOperationCoordinator private constructor(
    private val store: CrossDatabaseOperationStore,
    private val canonicalEnsurePort: CrossDatabaseCanonicalEnsurePort,
    finalizers: List<CrossDatabaseTypedFinalizer>,
    private val clockMillis: () -> Long
) : CrossDatabaseOperationStagingPort, CrossDatabaseOperationRecovery {
    private val finalizerByType = buildMap<String, CrossDatabaseTypedFinalizer> {
        finalizers.forEach { finalizer ->
            finalizer.supportedOperationTypes.forEach { operationType ->
                require(put(operationType, finalizer) == null) {
                    "xop_duplicate_finalizer:$operationType"
                }
            }
        }
    }

    override suspend fun stageExact(
        command: CrossDatabaseStageCommand
    ): CrossDatabaseOperationRecord {
        validateClosedRegistry(command)
        return store.stageExact(command, clockMillis())
    }

    suspend fun execute(
        identity: GenesisUltraRuntimeIdentity,
        command: CrossDatabaseStageCommand
    ): CrossDatabaseOperationRecord {
        requireCommandWriter(command, identity)
        val staged = stageExact(command)
        return advanceToTerminal(staged.operationId, identity)
    }

    override suspend fun load(operationId: String): CrossDatabaseOperationRecord? {
        return store.load(operationId)
    }

    override suspend fun recoverAtStartup(
        identity: GenesisUltraRuntimeIdentity,
        limit: Int
    ): CrossDatabaseRecoveryReport {
        require(limit in 1..MAX_RECOVERY_BATCH) { "xop_recovery_limit_invalid" }
        requireNoPendingCog001V1(identity.instanceId)
        return recover(
            identity = identity,
            operations = store.loadRecoverableForInstance(identity.instanceId, limit),
            countRemaining = {
                store.countRecoverableForInstance(identity.instanceId)
            }
        )
    }

    override suspend fun recoverBeforeMutation(
        identity: GenesisUltraRuntimeIdentity,
        ownerType: String,
        limit: Int
    ): CrossDatabaseRecoveryReport {
        require(limit in 1..MAX_RECOVERY_BATCH) { "xop_recovery_limit_invalid" }
        require(ownerType == CognitiveMigrationProtocolTypes.OWNER_TYPE) {
            "xop_recovery_owner_unsupported"
        }
        requireNoPendingCog001V1(identity.instanceId)
        return recover(
            identity = identity,
            operations = store.loadRecoverableForOwner(
                instanceId = identity.instanceId,
                ownerType = ownerType,
                limit = limit
            ),
            countRemaining = {
                store.countRecoverableForOwner(identity.instanceId, ownerType)
            }
        )
    }

    private suspend fun requireNoPendingCog001V1(instanceId: String) {
        if (
            store.countNonTerminalByInstanceOwnerAndPayloadSchema(
                instanceId = instanceId,
                ownerType = CognitiveMigrationProtocolTypes.OWNER_TYPE,
                payloadSchema = COG_001_PAYLOAD_SCHEMA_V1
            ) != 0
        ) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.UNSUPPORTED_PAYLOAD_SCHEMA
            )
        }
    }

    private suspend fun recover(
        identity: GenesisUltraRuntimeIdentity,
        operations: List<CrossDatabaseOperationRecord>,
        countRemaining: suspend () -> Int
    ): CrossDatabaseRecoveryReport {
        var recovered = 0
        var retryable = 0
        var newlyBlocked = 0
        val originalCounts = operations.groupingBy { operation -> operation.status }.eachCount()

        operations.forEach { operation ->
            if (operation.status == CrossDatabaseOperationStatus.BLOCKED) return@forEach
            try {
                val result = advanceToTerminal(operation.operationId, identity)
                if (result.status == CrossDatabaseOperationStatus.COMMITTED) recovered += 1
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: CrossDatabaseProtocolFailure) {
                if (failure.permanent) newlyBlocked += 1 else retryable += 1
            }
        }

        if (countRemaining() > 0) retryable += 1

        return CrossDatabaseRecoveryReport(
            stagedCount = originalCounts[CrossDatabaseOperationStatus.STAGED] ?: 0,
            pendingCanonicalCount =
                originalCounts[CrossDatabaseOperationStatus.PENDING_CANONICAL] ?: 0,
            canonicalCommittedCount =
                originalCounts[CrossDatabaseOperationStatus.CANONICAL_COMMITTED] ?: 0,
            pendingLocalCount =
                originalCounts[CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT] ?: 0,
            committedCount = originalCounts[CrossDatabaseOperationStatus.COMMITTED] ?: 0,
            blockedCount =
                (originalCounts[CrossDatabaseOperationStatus.BLOCKED] ?: 0) + newlyBlocked,
            recoveredCount = recovered,
            retryableFailureCount = retryable
        )
    }

    private suspend fun advanceToTerminal(
        operationId: String,
        identity: GenesisUltraRuntimeIdentity
    ): CrossDatabaseOperationRecord {
        var receiptObservedThisExecution: CrossDatabaseCanonicalReceipt? = null
        repeat(MAX_STATE_ADVANCES) {
            val operation = store.load(operationId)
                ?: throw CrossDatabaseProtocolErrors.permanent(
                    CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
                )
            try {
                requireOperationWriter(operation, identity)
                when (operation.status) {
                    CrossDatabaseOperationStatus.STAGED -> {
                        store.transitionStaged(operationId, clockMillis())
                    }
                    CrossDatabaseOperationStatus.PENDING_CANONICAL -> {
                        val receipt = canonicalEnsurePort.ensureCommitted(
                            operation.toCanonicalCommand()
                        )
                        if (receipt.eventId != operation.eventId) {
                            throw CrossDatabaseProtocolErrors.permanent(
                                CrossDatabaseProtocolErrors.CANONICAL_RECEIPT_CONFLICT
                            )
                        }
                        receiptObservedThisExecution = receipt
                        store.persistCanonicalReceipt(operationId, receipt, clockMillis())
                    }
                    CrossDatabaseOperationStatus.CANONICAL_COMMITTED -> {
                        store.transitionCanonicalCommitted(operationId, clockMillis())
                    }
                    CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT -> {
                        val finalizer = finalizerByType[operation.operationType]
                            ?: throw CrossDatabaseProtocolErrors.permanent(
                                CrossDatabaseProtocolErrors.UNSUPPORTED_OPERATION_VERSION
                            )
                        val receipt = operation.resolveReceipt(receiptObservedThisExecution)
                        val preparation = finalizer.prepareOutsideTransaction(operation, receipt)
                        return store.finalizeCommitted(
                            operationId = operationId,
                            identity = identity,
                            finalizer = finalizer,
                            receipt = receipt,
                            preparation = preparation,
                            clockMillis = clockMillis()
                        )
                    }
                    CrossDatabaseOperationStatus.COMMITTED -> return operation
                    CrossDatabaseOperationStatus.BLOCKED -> {
                        throw CrossDatabaseProtocolErrors.permanent(
                            operation.lastErrorCode
                                ?: CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
                        )
                    }
                    else -> throw CrossDatabaseProtocolErrors.permanent(
                        CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
                    )
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: CrossDatabaseProtocolFailure) {
                persistFailure(operation, failure)
                throw failure
            } catch (failure: Throwable) {
                val mapped = mapFailure(operation.status, failure)
                persistFailure(operation, mapped)
                throw mapped
            }
        }
        val exhausted = CrossDatabaseProtocolErrors.retryable(
            CrossDatabaseProtocolErrors.RECOVERY_BATCH_EXHAUSTED
        )
        val current = store.load(operationId)
            ?: throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )
        store.recordRetryableFailure(
            operationId = operationId,
            expectedStatus = current.status,
            errorCode = exhausted.stableCode,
            clockMillis = clockMillis()
        )
        throw exhausted
    }

    private suspend fun persistFailure(
        operation: CrossDatabaseOperationRecord,
        failure: CrossDatabaseProtocolFailure
    ) {
        if (failure.permanent) {
            store.markBlocked(operation.operationId, failure.stableCode, clockMillis())
        } else {
            store.recordRetryableFailure(
                operationId = operation.operationId,
                expectedStatus = operation.status,
                errorCode = failure.stableCode,
                clockMillis = clockMillis()
            )
        }
    }

    private fun mapFailure(
        status: String,
        failure: Throwable
    ): CrossDatabaseProtocolFailure {
        CrossDatabaseProtocolErrors.rethrowCancellation(failure)
        return if (status == CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT) {
            CrossDatabaseProtocolErrors.retryable(
                CrossDatabaseProtocolErrors.LOCAL_FINALIZATION_INTERRUPTED,
                failure
            )
        } else {
            CrossDatabaseProtocolErrors.retryable(
                CrossDatabaseProtocolErrors.CANONICAL_APPEND_INTERRUPTED,
                failure
            )
        }
    }

    private fun validateClosedRegistry(command: CrossDatabaseStageCommand) {
        val expectedEvent = CognitiveMigrationProtocolTypes.CLOSED_REGISTRY[
            command.operationType
        ] ?: throw CrossDatabaseProtocolErrors.permanent(
            CrossDatabaseProtocolErrors.UNSUPPORTED_OPERATION_VERSION
        )
        if (
            command.ownerType != CognitiveMigrationProtocolTypes.OWNER_TYPE ||
            command.operationVersion != CognitiveMigrationProtocolTypes.VERSION
        ) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.UNSUPPORTED_OPERATION_VERSION
            )
        }
        if (command.eventType != expectedEvent) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.EVENT_ID_CONFLICT
            )
        }
        if (finalizerByType[command.operationType] == null) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.UNSUPPORTED_OPERATION_VERSION
            )
        }
    }

    private fun requireCommandWriter(
        command: CrossDatabaseStageCommand,
        identity: GenesisUltraRuntimeIdentity
    ) {
        requireWriter(
            instanceId = command.instanceId,
            writerBodyId = command.writerBodyId,
            writerEpoch = command.writerEpoch,
            identity = identity
        )
    }

    private fun requireOperationWriter(
        operation: CrossDatabaseOperationRecord,
        identity: GenesisUltraRuntimeIdentity
    ) {
        requireWriter(
            instanceId = operation.instanceId,
            writerBodyId = operation.writerBodyId,
            writerEpoch = operation.writerEpoch,
            identity = identity
        )
    }

    private fun requireWriter(
        instanceId: String,
        writerBodyId: String,
        writerEpoch: String,
        identity: GenesisUltraRuntimeIdentity
    ) {
        if (instanceId != identity.instanceId) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.WRONG_INSTANCE
            )
        }
        if (writerBodyId != identity.activeBody.bodyId) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.UNAUTHORIZED_WRITER_BODY
            )
        }
        if (writerEpoch != identity.activeBody.keyEpochId) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.STALE_WRITER_EPOCH
            )
        }
    }

    private fun CrossDatabaseOperationRecord.toCanonicalCommand():
        CrossDatabaseCanonicalCommand {
        return CrossDatabaseCanonicalCommand(
            operationId = operationId,
            operationType = operationType,
            operationVersion = operationVersion,
            instanceId = instanceId,
            writerBodyId = writerBodyId,
            writerEpoch = writerEpoch,
            subjectId = subjectId,
            payloadDigest = payloadDigest,
            evidenceDigest = evidenceDigest,
            eventId = eventId,
            eventType = eventType,
            eventBody = eventBody,
            evidenceJson = evidenceJson,
            occurredAtMillis = occurredAtMillis
        )
    }

    internal companion object {
        private const val MAX_RECOVERY_BATCH = 200
        private const val MAX_STATE_ADVANCES = 8
        private const val COG_001_PAYLOAD_SCHEMA_V1 =
            "morimil.cognitive_migration.cog_001.payload.v1"

        fun production(
            database: MemoryOrganDatabase,
            canonicalEnsurePort: CrossDatabaseCanonicalEnsurePort,
            finalizers: List<CrossDatabaseTypedFinalizer>,
            clockMillis: () -> Long = System::currentTimeMillis
        ): CrossDatabaseOperationCoordinator {
            return CrossDatabaseOperationCoordinator(
                store = RoomCrossDatabaseOperationStore(database),
                canonicalEnsurePort = canonicalEnsurePort,
                finalizers = finalizers,
                clockMillis = clockMillis
            )
        }

        fun forTest(
            store: CrossDatabaseOperationStore,
            canonicalEnsurePort: CrossDatabaseCanonicalEnsurePort,
            finalizers: List<CrossDatabaseTypedFinalizer>,
            clockMillis: () -> Long
        ): CrossDatabaseOperationCoordinator {
            return CrossDatabaseOperationCoordinator(
                store = store,
                canonicalEnsurePort = canonicalEnsurePort,
                finalizers = finalizers,
                clockMillis = clockMillis
            )
        }
    }
}

internal interface CrossDatabaseOperationStore {
    suspend fun stageExact(
        command: CrossDatabaseStageCommand,
        clockMillis: Long
    ): CrossDatabaseOperationRecord

    suspend fun load(operationId: String): CrossDatabaseOperationRecord?

    suspend fun loadRecoverableForInstance(
        instanceId: String,
        limit: Int
    ): List<CrossDatabaseOperationRecord>

    suspend fun loadRecoverableForOwner(
        instanceId: String,
        ownerType: String,
        limit: Int
    ): List<CrossDatabaseOperationRecord>

    suspend fun countRecoverableForInstance(instanceId: String): Int

    suspend fun countRecoverableForOwner(instanceId: String, ownerType: String): Int

    suspend fun countNonTerminalByInstanceOwnerAndPayloadSchema(
        instanceId: String,
        ownerType: String,
        payloadSchema: String
    ): Int

    suspend fun transitionStaged(operationId: String, clockMillis: Long)

    suspend fun persistCanonicalReceipt(
        operationId: String,
        receipt: CrossDatabaseCanonicalReceipt,
        clockMillis: Long
    )

    suspend fun transitionCanonicalCommitted(operationId: String, clockMillis: Long)

    suspend fun recordRetryableFailure(
        operationId: String,
        expectedStatus: String,
        errorCode: String,
        clockMillis: Long
    )

    suspend fun markBlocked(operationId: String, errorCode: String, clockMillis: Long)

    suspend fun finalizeCommitted(
        operationId: String,
        identity: GenesisUltraRuntimeIdentity,
        finalizer: CrossDatabaseTypedFinalizer,
        receipt: CrossDatabaseCanonicalReceipt,
        preparation: CrossDatabaseFinalizationPreparation?,
        clockMillis: Long
    ): CrossDatabaseOperationRecord
}

private class RoomCrossDatabaseOperationStore(
    private val database: MemoryOrganDatabase
) : CrossDatabaseOperationStore {
    private val dao: CrossDatabaseOperationDao = database.crossDatabaseOperationDao()

    override suspend fun stageExact(
        command: CrossDatabaseStageCommand,
        clockMillis: Long
    ): CrossDatabaseOperationRecord {
        return database.withTransaction {
            val existing = dao.loadOperation(command.operationId)
            if (existing != null) {
                requireExact(existing, command)
                return@withTransaction existing
            }
            val sameOwnerIntent = dao.loadAnyForOwnerSubjectAndOperationType(
                ownerType = command.ownerType,
                subjectId = command.subjectId,
                operationType = command.operationType
            )
            if (sameOwnerIntent.isNotEmpty()) {
                val conflict = sameOwnerIntent.first()
                val code = if (conflict.payloadDigest != command.payloadDigest) {
                    CrossDatabaseProtocolErrors.OPERATION_ID_PAYLOAD_CONFLICT
                } else {
                    CrossDatabaseProtocolErrors.OPERATION_ID_EVIDENCE_CONFLICT
                }
                throw CrossDatabaseProtocolErrors.permanent(code)
            }
            val inserted = command.toEntity(clockMillis)
            dao.insertOperationAbort(inserted)
            dao.loadOperation(inserted.operationId)
                ?: throw CrossDatabaseProtocolErrors.permanent(
                    CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
                )
        }
    }

    override suspend fun load(operationId: String): CrossDatabaseOperationRecord? {
        return dao.loadOperation(operationId)
    }

    override suspend fun loadRecoverableForInstance(
        instanceId: String,
        limit: Int
    ): List<CrossDatabaseOperationRecord> {
        return dao.loadRecoverableForInstance(instanceId, limit)
    }

    override suspend fun loadRecoverableForOwner(
        instanceId: String,
        ownerType: String,
        limit: Int
    ): List<CrossDatabaseOperationRecord> {
        return dao.loadRecoverableForOwner(instanceId, ownerType, limit)
    }

    override suspend fun countRecoverableForInstance(instanceId: String): Int {
        return dao.countRecoverableForInstance(instanceId)
    }

    override suspend fun countRecoverableForOwner(
        instanceId: String,
        ownerType: String
    ): Int {
        return dao.countRecoverableForOwner(instanceId, ownerType)
    }

    override suspend fun countNonTerminalByInstanceOwnerAndPayloadSchema(
        instanceId: String,
        ownerType: String,
        payloadSchema: String
    ): Int {
        return dao.countNonTerminalByInstanceOwnerAndPayloadSchema(
            instanceId = instanceId,
            ownerType = ownerType,
            payloadSchema = payloadSchema
        )
    }

    override suspend fun transitionStaged(operationId: String, clockMillis: Long) {
        if (dao.transitionStagedToPendingCanonical(operationId, clockMillis) != 1) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT
            )
        }
    }

    override suspend fun persistCanonicalReceipt(
        operationId: String,
        receipt: CrossDatabaseCanonicalReceipt,
        clockMillis: Long
    ) {
        val current = dao.loadOperation(operationId)
            ?: throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
            )
        if (current.eventId != receipt.eventId) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.CANONICAL_RECEIPT_CONFLICT
            )
        }
        if (
            dao.persistCanonicalReceipt(
                operationId = operationId,
                canonicalEventHash = receipt.eventHash,
                canonicalSequence = receipt.sequence,
                canonicalProvenanceDigest = receipt.provenanceDigest,
                updatedAtMillis = clockMillis
            ) != 1
        ) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.CANONICAL_RECEIPT_CONFLICT
            )
        }
    }

    override suspend fun transitionCanonicalCommitted(
        operationId: String,
        clockMillis: Long
    ) {
        if (
            dao.transitionCanonicalCommittedToPendingLocalCommit(
                operationId,
                clockMillis
            ) != 1
        ) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT
            )
        }
    }

    override suspend fun recordRetryableFailure(
        operationId: String,
        expectedStatus: String,
        errorCode: String,
        clockMillis: Long
    ) {
        dao.recordRetryableFailure(operationId, expectedStatus, errorCode, clockMillis)
    }

    override suspend fun markBlocked(
        operationId: String,
        errorCode: String,
        clockMillis: Long
    ) {
        dao.markBlocked(operationId, errorCode, clockMillis)
    }

    override suspend fun finalizeCommitted(
        operationId: String,
        identity: GenesisUltraRuntimeIdentity,
        finalizer: CrossDatabaseTypedFinalizer,
        receipt: CrossDatabaseCanonicalReceipt,
        preparation: CrossDatabaseFinalizationPreparation?,
        clockMillis: Long
    ): CrossDatabaseOperationRecord {
        return database.withTransaction {
            val operation = dao.loadOperation(operationId)
                ?: throw CrossDatabaseProtocolErrors.permanent(
                    CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
                )
            if (operation.status == CrossDatabaseOperationStatus.COMMITTED) {
                return@withTransaction operation
            }
            if (operation.status != CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT) {
                throw CrossDatabaseProtocolErrors.permanent(
                    CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT
                )
            }
            if (
                operation.instanceId != identity.instanceId ||
                operation.writerBodyId != identity.activeBody.bodyId ||
                operation.writerEpoch != identity.activeBody.keyEpochId
            ) {
                throw CrossDatabaseProtocolErrors.permanent(
                    CrossDatabaseProtocolErrors.STALE_WRITER_EPOCH
                )
            }
            val persistedReceipt = operation.resolveReceipt(receipt)
            preparation?.let { prepared ->
                if (
                    prepared.operationId != operation.operationId ||
                    prepared.receiptEventHash != persistedReceipt.eventHash ||
                    prepared.payloadDigest != operation.payloadDigest
                ) {
                    throw CrossDatabaseProtocolErrors.permanent(
                        CrossDatabaseProtocolErrors.FINALIZATION_PREPARATION_CONFLICT
                    )
                }
            }
            val result = finalizer.finalizePreparedInsideTransaction(
                operation = operation,
                receipt = persistedReceipt,
                preparation = preparation
            )
            if (
                dao.markCommittedWithLocalResult(
                    operationId = operation.operationId,
                    localResultSchema = result.schema,
                    localResultJson = result.json,
                    localResultDigest = result.digest,
                    updatedAtMillis = clockMillis,
                    committedAtMillis = clockMillis
                ) != 1
            ) {
                throw CrossDatabaseProtocolErrors.permanent(
                    CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT
                )
            }
            dao.loadOperation(operationId)
                ?: throw CrossDatabaseProtocolErrors.permanent(
                    CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
                )
        }
    }

    private fun requireExact(
        existing: CrossDatabaseOperationRecord,
        command: CrossDatabaseStageCommand
    ) {
        if (
            existing.payloadDigest != command.payloadDigest ||
            existing.payloadJson != command.payloadJson ||
            existing.payloadSchema != command.payloadSchema
        ) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OPERATION_ID_PAYLOAD_CONFLICT
            )
        }
        if (
            existing.evidenceDigest != command.evidenceDigest ||
            existing.evidenceJson != command.evidenceJson ||
            existing.evidenceSchema != command.evidenceSchema
        ) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.OPERATION_ID_EVIDENCE_CONFLICT
            )
        }
        val exact = existing.ownerType == command.ownerType &&
            existing.operationType == command.operationType &&
            existing.operationVersion == command.operationVersion &&
            existing.instanceId == command.instanceId &&
            existing.writerBodyId == command.writerBodyId &&
            existing.writerEpoch == command.writerEpoch &&
            existing.subjectId == command.subjectId &&
            existing.parentOperationId == command.parentOperationId &&
            existing.childPhase == command.childPhase &&
            existing.eventId == command.eventId &&
            existing.eventType == command.eventType &&
            existing.eventBody == command.eventBody
        if (!exact) {
            throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.EVENT_ID_CONFLICT
            )
        }
    }

    private fun CrossDatabaseStageCommand.toEntity(
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
            parentOperationId = parentOperationId,
            childPhase = childPhase,
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

private fun CrossDatabaseOperationRecord.resolveReceipt(
    observed: CrossDatabaseCanonicalReceipt?
): CrossDatabaseCanonicalReceipt {
    val persisted = CrossDatabaseCanonicalReceipt(
        eventId = eventId,
        eventHash = canonicalEventHash
            ?: throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.CANONICAL_RECEIPT_CONFLICT
            ),
        sequence = canonicalSequence
            ?: throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.CANONICAL_RECEIPT_CONFLICT
            ),
        provenanceDigest = canonicalProvenanceDigest
            ?: throw CrossDatabaseProtocolErrors.permanent(
                CrossDatabaseProtocolErrors.CANONICAL_RECEIPT_CONFLICT
            ),
        reusedExistingEvent = true
    )
    if (observed == null) return persisted
    if (
        observed.eventId != persisted.eventId ||
        observed.eventHash != persisted.eventHash ||
        observed.sequence != persisted.sequence ||
        observed.provenanceDigest != persisted.provenanceDigest
    ) {
        throw CrossDatabaseProtocolErrors.permanent(
            CrossDatabaseProtocolErrors.CANONICAL_RECEIPT_CONFLICT
        )
    }
    return observed
}
