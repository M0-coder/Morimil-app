package com.morimil.app.data.repository
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import com.morimil.app.data.local.CrossDatabaseOperationStatus
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.RoomCrossDatabaseOperationStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
return withOperationLock(command.operationId) {
val staged = stageExact(command)
advanceToTerminalLocked(staged.operationId, identity)
}
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
if (countRemaining() > retryable) retryable += 1
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
return withOperationLock(operationId) {
advanceToTerminalLocked(operationId, identity)
}
}
private suspend fun advanceToTerminalLocked(
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
if (!store.transitionStaged(operationId, clockMillis())) {
val durable = loadCompatibleAfterLostCas(
operation = operation,
conflictCode = CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT
)
if (durable.status == CrossDatabaseOperationStatus.COMMITTED) {
return durable
}
}
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
if (!store.persistCanonicalReceipt(operationId, receipt, clockMillis())) {
val durable = loadCompatibleAfterLostCas(
operation = operation,
conflictCode = CrossDatabaseProtocolErrors.CANONICAL_RECEIPT_CONFLICT,
receipt = receipt
)
if (durable.status == CrossDatabaseOperationStatus.COMMITTED) {
return durable
}
}
}
CrossDatabaseOperationStatus.CANONICAL_COMMITTED -> {
if (!store.transitionCanonicalCommitted(operationId, clockMillis())) {
val durable = loadCompatibleAfterLostCas(
operation = operation,
conflictCode = CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT
)
if (durable.status == CrossDatabaseOperationStatus.COMMITTED) {
return durable
}
}
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
val durable = persistFailure(operation, failure)
if (durable.status == CrossDatabaseOperationStatus.COMMITTED) return durable
if (isCompatibleAdvance(operation.status, durable.status)) return@repeat
throw failure
} catch (failure: Throwable) {
val mapped = mapFailure(operation.status, failure)
val durable = persistFailure(operation, mapped)
if (durable.status == CrossDatabaseOperationStatus.COMMITTED) return durable
if (isCompatibleAdvance(operation.status, durable.status)) return@repeat
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
val durable = persistFailure(current, exhausted)
if (durable.status == CrossDatabaseOperationStatus.COMMITTED) return durable
throw exhausted
}
private suspend fun persistFailure(
operation: CrossDatabaseOperationRecord,
failure: CrossDatabaseProtocolFailure
): CrossDatabaseOperationRecord {
val updated = if (failure.permanent) {
store.markBlocked(
operationId = operation.operationId,
expectedStatus = operation.status,
errorCode = failure.stableCode,
clockMillis = clockMillis()
)
} else {
store.recordRetryableFailure(
operationId = operation.operationId,
expectedStatus = operation.status,
errorCode = failure.stableCode,
clockMillis = clockMillis()
)
}
if (updated) {
return store.load(operation.operationId)
?: throw CrossDatabaseProtocolErrors.permanent(
CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
)
}
return loadCompatibleAfterLostCas(
operation = operation,
conflictCode = CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT
)
}
private suspend fun loadCompatibleAfterLostCas(
operation: CrossDatabaseOperationRecord,
conflictCode: String,
receipt: CrossDatabaseCanonicalReceipt? = null
): CrossDatabaseOperationRecord {
val current = store.load(operation.operationId)
?: throw CrossDatabaseProtocolErrors.permanent(
CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
)
if (current.status == CrossDatabaseOperationStatus.BLOCKED) {
throw CrossDatabaseProtocolErrors.permanent(
current.lastErrorCode ?: CrossDatabaseProtocolErrors.OWNER_STATE_CONFLICT
)
}
if (!isCompatibleAdvance(operation.status, current.status)) {
throw CrossDatabaseProtocolErrors.permanent(conflictCode)
}
receipt?.let { current.resolveReceipt(it) }
return current
}
private fun isCompatibleAdvance(expectedStatus: String, actualStatus: String): Boolean {
val expectedRank = protocolStatusRank(expectedStatus) ?: return false
val actualRank = protocolStatusRank(actualStatus) ?: return false
return actualRank > expectedRank
}
private fun protocolStatusRank(status: String): Int? {
return when (status) {
CrossDatabaseOperationStatus.STAGED -> 0
CrossDatabaseOperationStatus.PENDING_CANONICAL -> 1
CrossDatabaseOperationStatus.CANONICAL_COMMITTED -> 2
CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT -> 3
CrossDatabaseOperationStatus.COMMITTED -> 4
else -> null
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
private const val OPERATION_MUTEX_STRIPES = 64
private const val COG_001_PAYLOAD_SCHEMA_V1 =
"morimil.cognitive_migration.cog_001.payload.v1"
private val OPERATION_MUTEXES = Array(OPERATION_MUTEX_STRIPES) { Mutex() }
private suspend fun <T> withOperationLock(
operationId: String,
block: suspend () -> T
): T {
val index = (operationId.hashCode() and Int.MAX_VALUE) % OPERATION_MUTEX_STRIPES
return OPERATION_MUTEXES[index].withLock { block() }
}
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
suspend fun transitionStaged(operationId: String, clockMillis: Long): Boolean
suspend fun persistCanonicalReceipt(
operationId: String,
receipt: CrossDatabaseCanonicalReceipt,
clockMillis: Long
): Boolean
suspend fun transitionCanonicalCommitted(
operationId: String,
clockMillis: Long
): Boolean
suspend fun recordRetryableFailure(
operationId: String,
expectedStatus: String,
errorCode: String,
clockMillis: Long
): Boolean
suspend fun markBlocked(
operationId: String,
expectedStatus: String,
errorCode: String,
clockMillis: Long
): Boolean
suspend fun finalizeCommitted(
operationId: String,
identity: GenesisUltraRuntimeIdentity,
finalizer: CrossDatabaseTypedFinalizer,
receipt: CrossDatabaseCanonicalReceipt,
preparation: CrossDatabaseFinalizationPreparation?,
clockMillis: Long
): CrossDatabaseOperationRecord
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
