package com.morimil.app.data.local
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.withTransaction
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import com.morimil.app.data.repository.CrossDatabaseCanonicalReceipt
import com.morimil.app.data.repository.CrossDatabaseFinalizationPreparation
import com.morimil.app.data.repository.CrossDatabaseOperationRecord
import com.morimil.app.data.repository.CrossDatabaseOperationStore
import com.morimil.app.data.repository.CrossDatabaseProtocolErrors
import com.morimil.app.data.repository.CrossDatabaseStageCommand
import com.morimil.app.data.repository.CrossDatabaseTypedFinalizer
@Dao
interface CrossDatabaseOperationDao {
@Insert(onConflict = OnConflictStrategy.ABORT)
suspend fun insertOperationAbort(operation: CrossDatabaseOperationEntity)
@Query("SELECT * FROM cross_database_operations WHERE operationId = :operationId LIMIT 1")
suspend fun loadOperation(operationId: String): CrossDatabaseOperationEntity?
@Query("SELECT * FROM cross_database_operations WHERE eventId = :eventId LIMIT 1")
suspend fun loadByEventId(eventId: String): CrossDatabaseOperationEntity?
@Query(
"""
SELECT * FROM cross_database_operations
WHERE instanceId = :instanceId
AND status IN (
'STAGED',
'PENDING_CANONICAL',
'CANONICAL_COMMITTED',
'PENDING_LOCAL_COMMIT',
'BLOCKED'
)
ORDER BY createdAtMillis ASC, operationId ASC
LIMIT :limit
"""
)
suspend fun loadRecoverableForInstance(
instanceId: String,
limit: Int
): List<CrossDatabaseOperationEntity>
@Query(
"""
SELECT * FROM cross_database_operations
WHERE instanceId = :instanceId
AND ownerType = :ownerType
AND status IN (
'STAGED',
'PENDING_CANONICAL',
'CANONICAL_COMMITTED',
'PENDING_LOCAL_COMMIT',
'BLOCKED'
)
ORDER BY createdAtMillis ASC, operationId ASC
LIMIT :limit
"""
)
suspend fun loadRecoverableForOwner(
instanceId: String,
ownerType: String,
limit: Int
): List<CrossDatabaseOperationEntity>
@Query(
"""
SELECT COUNT(*) FROM cross_database_operations
WHERE instanceId = :instanceId
AND status NOT IN ('COMMITTED', 'BLOCKED')
"""
)
suspend fun countRecoverableForInstance(instanceId: String): Int
@Query(
"""
SELECT COUNT(*) FROM cross_database_operations
WHERE instanceId = :instanceId
AND ownerType = :ownerType
AND status NOT IN ('COMMITTED', 'BLOCKED')
"""
)
suspend fun countRecoverableForOwner(instanceId: String, ownerType: String): Int
@Query(
"""
SELECT * FROM cross_database_operations
WHERE ownerType = :ownerType
AND subjectId = :subjectId
AND operationType = :operationType
ORDER BY createdAtMillis ASC, operationId ASC
"""
)
suspend fun loadAnyForOwnerSubjectAndOperationType(
ownerType: String,
subjectId: String,
operationType: String
): List<CrossDatabaseOperationEntity>
@Query(
"""
SELECT * FROM cross_database_operations
WHERE ownerType = :ownerType
AND subjectId = :subjectId
AND status NOT IN ('COMMITTED', 'BLOCKED')
ORDER BY createdAtMillis ASC, operationId ASC
"""
)
suspend fun loadActiveForOwnerSubject(
ownerType: String,
subjectId: String
): List<CrossDatabaseOperationEntity>
@Query(
"""
SELECT COUNT(*) FROM cross_database_operations
WHERE instanceId = :instanceId AND status = :status
"""
)
suspend fun countByInstanceAndStatus(instanceId: String, status: String): Int
@Query(
"""
SELECT COUNT(*) FROM cross_database_operations
WHERE instanceId = :instanceId
AND ownerType = :ownerType
AND payloadSchema = :payloadSchema
AND status != 'COMMITTED'
"""
)
suspend fun countNonTerminalByInstanceOwnerAndPayloadSchema(
instanceId: String,
ownerType: String,
payloadSchema: String
): Int
@Query(
"""
UPDATE cross_database_operations
SET status = 'PENDING_CANONICAL',
lastErrorCode = NULL,
updatedAtMillis = :updatedAtMillis
WHERE operationId = :operationId AND status = 'STAGED'
"""
)
suspend fun transitionStagedToPendingCanonical(
operationId: String,
updatedAtMillis: Long
): Int
@Query(
"""
UPDATE cross_database_operations
SET canonicalEventHash = :canonicalEventHash,
canonicalSequence = :canonicalSequence,
canonicalProvenanceDigest = :canonicalProvenanceDigest,
status = 'CANONICAL_COMMITTED',
lastErrorCode = NULL,
updatedAtMillis = :updatedAtMillis
WHERE operationId = :operationId AND status = 'PENDING_CANONICAL'
"""
)
suspend fun persistCanonicalReceipt(
operationId: String,
canonicalEventHash: String,
canonicalSequence: Long,
canonicalProvenanceDigest: String,
updatedAtMillis: Long
): Int
@Query(
"""
UPDATE cross_database_operations
SET status = 'PENDING_LOCAL_COMMIT',
lastErrorCode = NULL,
updatedAtMillis = :updatedAtMillis
WHERE operationId = :operationId AND status = 'CANONICAL_COMMITTED'
"""
)
suspend fun transitionCanonicalCommittedToPendingLocalCommit(
operationId: String,
updatedAtMillis: Long
): Int
@Query(
"""
UPDATE cross_database_operations
SET attemptCount = attemptCount + 1,
lastErrorCode = :lastErrorCode,
updatedAtMillis = :updatedAtMillis
WHERE operationId = :operationId AND status = :expectedStatus
"""
)
suspend fun recordRetryableFailure(
operationId: String,
expectedStatus: String,
lastErrorCode: String,
updatedAtMillis: Long
): Int
@Query(
"""
UPDATE cross_database_operations
SET status = 'BLOCKED',
attemptCount = attemptCount + 1,
lastErrorCode = :lastErrorCode,
updatedAtMillis = :updatedAtMillis
WHERE operationId = :operationId
AND status = :expectedStatus
"""
)
suspend fun markBlockedIfStatus(
operationId: String,
expectedStatus: String,
lastErrorCode: String,
updatedAtMillis: Long
): Int
@Query(
"""
UPDATE cross_database_operations
SET localResultSchema = :localResultSchema,
localResultJson = :localResultJson,
localResultDigest = :localResultDigest,
status = 'COMMITTED',
lastErrorCode = NULL,
updatedAtMillis = :updatedAtMillis,
committedAtMillis = :committedAtMillis
WHERE operationId = :operationId AND status = 'PENDING_LOCAL_COMMIT'
"""
)
suspend fun markCommittedWithLocalResult(
operationId: String,
localResultSchema: String,
localResultJson: String,
localResultDigest: String,
updatedAtMillis: Long,
committedAtMillis: Long
): Int
}
internal class RoomCrossDatabaseOperationStore(
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
override suspend fun transitionStaged(operationId: String, clockMillis: Long): Boolean {
return dao.transitionStagedToPendingCanonical(operationId, clockMillis) == 1
}
override suspend fun persistCanonicalReceipt(
operationId: String,
receipt: CrossDatabaseCanonicalReceipt,
clockMillis: Long
): Boolean {
val current = dao.loadOperation(operationId)
?: throw CrossDatabaseProtocolErrors.permanent(
CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
)
if (current.eventId != receipt.eventId) {
throw CrossDatabaseProtocolErrors.permanent(
CrossDatabaseProtocolErrors.CANONICAL_RECEIPT_CONFLICT
)
}
return dao.persistCanonicalReceipt(
operationId = operationId,
canonicalEventHash = receipt.eventHash,
canonicalSequence = receipt.sequence,
canonicalProvenanceDigest = receipt.provenanceDigest,
updatedAtMillis = clockMillis
) == 1
}
override suspend fun transitionCanonicalCommitted(
operationId: String,
clockMillis: Long
): Boolean {
return dao.transitionCanonicalCommittedToPendingLocalCommit(
operationId,
clockMillis
) == 1
}
override suspend fun recordRetryableFailure(
operationId: String,
expectedStatus: String,
errorCode: String,
clockMillis: Long
): Boolean {
return dao.recordRetryableFailure(
operationId,
expectedStatus,
errorCode,
clockMillis
) == 1
}
override suspend fun markBlocked(
operationId: String,
expectedStatus: String,
errorCode: String,
clockMillis: Long
): Boolean {
return dao.markBlockedIfStatus(
operationId,
expectedStatus,
errorCode,
clockMillis
) == 1
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
