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
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
assertFalse(operationReceipt.reusedExistingEvent)
localResult()
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
@Test
fun pendingCog001V1BlocksRecoveryBeforeCanonicalAppend() = runBlocking {
val identity = identity()
val legacy = command(
identity = identity,
payloadSchema = "morimil.cognitive_migration.cog_001.payload.v1"
)
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
error("finalizer_must_not_run_for_pending_v1")
}
),
clockMillis = IncrementingClock()
)
coordinator.stageExact(legacy)
val failure = runCatching {
coordinator.recoverAtStartup(identity, 20)
}.exceptionOrNull() as CrossDatabaseProtocolFailure
assertEquals(
CrossDatabaseProtocolErrors.UNSUPPORTED_PAYLOAD_SCHEMA,
failure.stableCode
)
assertEquals(0, canonicalCalls)
assertEquals(
CrossDatabaseOperationStatus.STAGED,
coordinator.load(legacy.operationId)?.status
)
}
@Test
fun exactlyFullRecoveryBatchDoesNotReportFalseRetryableRemainder() = runBlocking {
val identity = identity()
val command = command(identity)
val store = FakeStore()
val coordinator = CrossDatabaseOperationCoordinator.forTest(
store = store,
canonicalEnsurePort = object : CrossDatabaseCanonicalEnsurePort {
override suspend fun ensureCommitted(
command: CrossDatabaseCanonicalCommand
): CrossDatabaseCanonicalReceipt = receipt(command.eventId)
},
finalizers = listOf(RecordingFinalizer { _, _ -> localResult() }),
clockMillis = IncrementingClock()
)
coordinator.stageExact(command)
val report = coordinator.recoverAtStartup(identity, limit = 1)
assertEquals(1, report.recoveredCount)
assertEquals(0, report.retryableFailureCount)
assertEquals(0, store.countRecoverableForInstance(identity.instanceId))
}
@Test
fun concurrentExecuteSameOperationCommitsExactlyOnce() = runBlocking {
val identity = identity()
val command = command(identity)
val store = FakeStore()
val canonicalEntered = CompletableDeferred<Unit>()
val releaseCanonical = CompletableDeferred<Unit>()
val canonicalCalls = AtomicInteger()
val finalizerCalls = AtomicInteger()
val canonical = object : CrossDatabaseCanonicalEnsurePort {
override suspend fun ensureCommitted(
command: CrossDatabaseCanonicalCommand
): CrossDatabaseCanonicalReceipt {
canonicalCalls.incrementAndGet()
canonicalEntered.complete(Unit)
releaseCanonical.await()
return receipt(command.eventId)
}
}
val finalizer = RecordingFinalizer { _, _ ->
finalizerCalls.incrementAndGet()
localResult()
}
val firstCoordinator = coordinator(store, canonical, finalizer)
val secondCoordinator = coordinator(store, canonical, finalizer)
val first = async { firstCoordinator.execute(identity, command) }
canonicalEntered.await()
val secondStarted = CompletableDeferred<Unit>()
val second = async {
secondStarted.complete(Unit)
secondCoordinator.execute(identity, command)
}
secondStarted.await()
assertFalse(second.isCompleted)
assertEquals(1, canonicalCalls.get())
releaseCanonical.complete(Unit)
val firstResult = first.await()
val secondResult = second.await()
assertEquals(CrossDatabaseOperationStatus.COMMITTED, firstResult.status)
assertEquals(firstResult, secondResult)
assertEquals(1, canonicalCalls.get())
assertEquals(1, finalizerCalls.get())
assertEquals(0, store.blockedWrites)
assertEquals(
CrossDatabaseOperationStatus.COMMITTED,
store.load(command.operationId)?.status
)
}
@Test
fun concurrentRecoveryAndExecuteCannotBlockForwardProgress() = runBlocking {
val identity = identity()
val command = command(identity)
val store = FakeStore()
val canonicalEntered = CompletableDeferred<Unit>()
val releaseCanonical = CompletableDeferred<Unit>()
val canonicalCalls = AtomicInteger()
val finalizerCalls = AtomicInteger()
val canonical = object : CrossDatabaseCanonicalEnsurePort {
override suspend fun ensureCommitted(
command: CrossDatabaseCanonicalCommand
): CrossDatabaseCanonicalReceipt {
canonicalCalls.incrementAndGet()
canonicalEntered.complete(Unit)
releaseCanonical.await()
return receipt(command.eventId)
}
}
val finalizer = RecordingFinalizer { _, _ ->
finalizerCalls.incrementAndGet()
localResult()
}
val recoveryCoordinator = coordinator(store, canonical, finalizer)
val executeCoordinator = coordinator(store, canonical, finalizer)
recoveryCoordinator.stageExact(command)
val recovery = async { recoveryCoordinator.recoverAtStartup(identity, 20) }
canonicalEntered.await()
val executeStarted = CompletableDeferred<Unit>()
val execute = async {
executeStarted.complete(Unit)
executeCoordinator.execute(identity, command)
}
executeStarted.await()
assertFalse(execute.isCompleted)
releaseCanonical.complete(Unit)
val report = recovery.await()
val executed = execute.await()
assertEquals(1, report.recoveredCount)
assertEquals(CrossDatabaseOperationStatus.COMMITTED, executed.status)
assertEquals(1, canonicalCalls.get())
assertEquals(1, finalizerCalls.get())
assertEquals(0, store.blockedWrites)
assertEquals(null, store.load(command.operationId)?.lastErrorCode)
}
@Test
fun lostCasReloadsCompatibleAdvancedState() = runBlocking {
val identity = identity()
val command = command(identity)
val store = FakeStore().apply {
loseNextTransitionStagedTo = CrossDatabaseOperationStatus.PENDING_CANONICAL
}
val coordinator = CrossDatabaseOperationCoordinator.forTest(
store = store,
canonicalEnsurePort = object : CrossDatabaseCanonicalEnsurePort {
override suspend fun ensureCommitted(
command: CrossDatabaseCanonicalCommand
): CrossDatabaseCanonicalReceipt = receipt(command.eventId)
},
finalizers = listOf(RecordingFinalizer { _, _ -> localResult() }),
clockMillis = IncrementingClock()
)
val result = coordinator.execute(identity, command)
assertEquals(CrossDatabaseOperationStatus.COMMITTED, result.status)
assertEquals(0, store.blockedWrites)
assertTrue(store.stateLog.contains(CrossDatabaseOperationStatus.PENDING_CANONICAL))
}
@Test
fun stalePermanentFailureCannotBlockAdvancedOperation() = runBlocking {
val identity = identity()
val command = command(identity)
val store = FakeStore()
val canonicalCalls = AtomicInteger()
val finalizerCalls = AtomicInteger()
val coordinator = CrossDatabaseOperationCoordinator.forTest(
store = store,
canonicalEnsurePort = object : CrossDatabaseCanonicalEnsurePort {
override suspend fun ensureCommitted(
command: CrossDatabaseCanonicalCommand
): CrossDatabaseCanonicalReceipt {
canonicalCalls.incrementAndGet()
store.forceCanonicalCommitted(receipt(command.eventId))
throw CrossDatabaseProtocolErrors.permanent(
CrossDatabaseProtocolErrors.OWNER_TRANSITION_CONFLICT
)
}
},
finalizers = listOf(
RecordingFinalizer { _, _ ->
finalizerCalls.incrementAndGet()
localResult()
}
),
clockMillis = IncrementingClock()
)
val result = coordinator.execute(identity, command)
assertEquals(CrossDatabaseOperationStatus.COMMITTED, result.status)
assertEquals(1, canonicalCalls.get())
assertEquals(1, finalizerCalls.get())
assertEquals(1, store.markBlockedAttempts)
assertEquals(0, store.blockedWrites)
assertEquals(null, result.lastErrorCode)
}
private fun coordinator(
store: FakeStore,
canonical: CrossDatabaseCanonicalEnsurePort,
finalizer: CrossDatabaseTypedFinalizer
): CrossDatabaseOperationCoordinator {
return CrossDatabaseOperationCoordinator.forTest(
store = store,
canonicalEnsurePort = canonical,
finalizers = listOf(finalizer),
clockMillis = IncrementingClock()
)
}
private fun command(
identity: GenesisUltraRuntimeIdentity,
writerEpoch: String = identity.activeBody.keyEpochId,
payloadSchema: String = "test.payload.v1"
): CrossDatabaseStageCommand {
val payload = CrossDatabaseOperationIdentity.canonicalJson(
mapOf("schema" to payloadSchema, "subject" to MIGRATION_ID)
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
payloadSchema = payloadSchema,
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
private fun localResult(): CrossDatabaseLocalResult {
val json = CrossDatabaseOperationIdentity.canonicalJson(
mapOf(
"owner_status" to "planned",
"schema" to "test.local_result.v1"
)
)
return CrossDatabaseLocalResult(
schema = "test.local_result.v1",
json = json,
digest = CrossDatabaseOperationIdentity.digestCanonicalJson(json),
ownerStatus = "planned"
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
var loseNextTransitionStagedTo: String? = null
var markBlockedAttempts = 0
var blockedWrites = 0
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
return listOfNotNull(record).filter {
it.instanceId == instanceId &&
it.status !in setOf(
CrossDatabaseOperationStatus.COMMITTED,
CrossDatabaseOperationStatus.BLOCKED
)
}.take(limit)
}
override suspend fun loadRecoverableForOwner(
instanceId: String,
ownerType: String,
limit: Int
): List<CrossDatabaseOperationRecord> {
return loadRecoverableForInstance(instanceId, limit).filter {
it.ownerType == ownerType
}
}
override suspend fun countRecoverableForInstance(instanceId: String): Int {
return loadRecoverableForInstance(instanceId, Int.MAX_VALUE).size
}
override suspend fun countRecoverableForOwner(
instanceId: String,
ownerType: String
): Int {
return loadRecoverableForOwner(instanceId, ownerType, Int.MAX_VALUE).size
}
override suspend fun countNonTerminalByInstanceOwnerAndPayloadSchema(
instanceId: String,
ownerType: String,
payloadSchema: String
): Int {
return listOfNotNull(record).count {
it.instanceId == instanceId &&
it.ownerType == ownerType &&
it.payloadSchema == payloadSchema &&
it.status != CrossDatabaseOperationStatus.COMMITTED
}
}
override suspend fun transitionStaged(
operationId: String,
clockMillis: Long
): Boolean {
val forced = loseNextTransitionStagedTo
if (forced != null) {
loseNextTransitionStagedTo = null
update(forced, clockMillis)
return false
}
if (requireNotNull(record).status != CrossDatabaseOperationStatus.STAGED) return false
update(CrossDatabaseOperationStatus.PENDING_CANONICAL, clockMillis)
return true
}
override suspend fun persistCanonicalReceipt(
operationId: String,
receipt: CrossDatabaseCanonicalReceipt,
clockMillis: Long
): Boolean {
val current = requireNotNull(record)
if (current.status != CrossDatabaseOperationStatus.PENDING_CANONICAL) return false
record = current.copy(
status = CrossDatabaseOperationStatus.CANONICAL_COMMITTED,
canonicalEventHash = receipt.eventHash,
canonicalSequence = receipt.sequence,
canonicalProvenanceDigest = receipt.provenanceDigest,
updatedAtMillis = clockMillis
)
stateLog += CrossDatabaseOperationStatus.CANONICAL_COMMITTED
return true
}
override suspend fun transitionCanonicalCommitted(
operationId: String,
clockMillis: Long
): Boolean {
if (
requireNotNull(record).status !=
CrossDatabaseOperationStatus.CANONICAL_COMMITTED
) {
return false
}
update(CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT, clockMillis)
return true
}
override suspend fun recordRetryableFailure(
operationId: String,
expectedStatus: String,
errorCode: String,
clockMillis: Long
): Boolean {
val current = requireNotNull(record)
if (current.status != expectedStatus) return false
record = current.copy(
attemptCount = current.attemptCount + 1,
lastErrorCode = errorCode,
updatedAtMillis = clockMillis
)
return true
}
override suspend fun markBlocked(
operationId: String,
expectedStatus: String,
errorCode: String,
clockMillis: Long
): Boolean {
markBlockedAttempts += 1
if (requireNotNull(record).status != expectedStatus) return false
update(CrossDatabaseOperationStatus.BLOCKED, clockMillis, errorCode)
blockedWrites += 1
return true
}
override suspend fun finalizeCommitted(
operationId: String,
identity: GenesisUltraRuntimeIdentity,
finalizer: CrossDatabaseTypedFinalizer,
receipt: CrossDatabaseCanonicalReceipt,
preparation: CrossDatabaseFinalizationPreparation?,
clockMillis: Long
): CrossDatabaseOperationRecord {
val pending = requireNotNull(record)
if (pending.status == CrossDatabaseOperationStatus.COMMITTED) return pending
require(pending.status == CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT)
val result = finalizer.finalizePreparedInsideTransaction(
operation = pending,
receipt = receipt,
preparation = preparation
)
record = pending.copy(
status = CrossDatabaseOperationStatus.COMMITTED,
localResultSchema = result.schema,
localResultJson = result.json,
localResultDigest = result.digest,
lastErrorCode = null,
updatedAtMillis = clockMillis,
committedAtMillis = clockMillis
)
stateLog += CrossDatabaseOperationStatus.COMMITTED
return requireNotNull(record)
}
fun forceCanonicalCommitted(receipt: CrossDatabaseCanonicalReceipt) {
val current = requireNotNull(record)
record = current.copy(
status = CrossDatabaseOperationStatus.CANONICAL_COMMITTED,
canonicalEventHash = receipt.eventHash,
canonicalSequence = receipt.sequence,
canonicalProvenanceDigest = receipt.provenanceDigest,
lastErrorCode = null
)
stateLog += CrossDatabaseOperationStatus.CANONICAL_COMMITTED
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
private companion object {
const val MIGRATION_ID =
"cog_migration_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
}
}
