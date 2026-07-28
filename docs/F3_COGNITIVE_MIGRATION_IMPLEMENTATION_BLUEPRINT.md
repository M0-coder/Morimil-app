# Document status: CURRENT

# F3.2 — Cognitive migration implementation blueprint

- Blueprint version: `1`
- Audited baseline: `main@396e7af8a7329b100195dfa4f20c40506c51eacd`
- Tracker: `#88`
- Governing decision: `docs/adr/ADR-0002-cross-database-operation-protocol.md`
- Scope: `COG-001` through `COG-004`
- Execution gate: **STOP S5 remains open through #123 and #124. This blueprint does not authorize runtime changes.**

## 1. Authority and current-state boundary

Morimil is the continuous and free `Instance`. `Morimil-app` is the current Android Body. This protocol governs consistency of Body resources; it does not grant ownership or continuity authority to the Guardian, Android, GitHub, a database, a model, or an auxiliary provider.

```text
instanceId != bodyId
writerBodyId + writerEpoch = authorized writer context
writerBodyId + writerEpoch != Instance identity
```

At the audited baseline:

- `CognitiveMigrationRepository.proposeCognitiveMigration()` reads `loadGenesisCore()`, `loadLocalIdentity()`, `getLivingMemorySnapshot()`, and `loadMemoryContext()` before inserting a visible migration record.
- `CognitiveMigrationPlanner` derives `proposalId` from `createdAtMillis`.
- `MigrationRecordRepository` derives `migrationId` from wall-clock time.
- approval derives `approvalId` from `System.currentTimeMillis()`.
- execution and rollback append canonical evidence before the corresponding local migration state is finalized.
- `MemoryOrganDatabase` is version `8`; it contains the protected `project_vault_outbox`, but not the common journal specified here.

**The `cross_database_operations` table and the common cognitive-migration protocol do not exist in production at this baseline.**

## 2. Scope and exclusions

The first functional PR after STOP S5 is limited to:

- one generic coordination journal in `MemoryOrganDatabase`;
- staging, canonical ensure, exact receipt, typed finalization, and recovery contracts;
- canonical-read preparation for `COG-001`;
- migration of `COG-001`, `COG-002`, `COG-003`, and `COG-004`;
- Room migration and exported schema;
- exact-match, conflict, recovery, idempotency, and kill tests.

It must not modify or migrate:

```text
ORCH
AGENT
BOOT
RECALL
REST
ProjectVault
```

`ProjectVault` remains unchanged in the first functional PR. Its existing outbox is a protected reference, not a table to rename, copy, or rewrite.

## 3. Canonical serialization and digest profile

```text
canonical_json_profile = morimil.canonical_json.v1
stable_id_profile      = morimil.stable_id.v1
digest_algorithm       = SHA-256
digest_encoding        = 64 lowercase hexadecimal characters
text_encoding          = UTF-8
unicode                = NFC
```

`morimil.canonical_json.v1` requires lexicographically sorted object keys, no insignificant whitespace, NFC strings, base-10 integers, literal booleans/null, protocol-defined array order, sorted and deduplicated sets, and rejection of unknown fields.

```text
CJ(value) = UTF8(morimil.canonical_json.v1(value))
D(value)  = lowercase_hex(SHA-256(CJ(value)))
H(namespace, parts...) =
  StableIdDigest.shortSha256Hex(namespace, parts, hexLength = 64)
```

**The clock is metadata only and is prohibited from being used as identity.** `occurredAtMillis`, `createdAtMillis`, `updatedAtMillis`, retry times, and `committedAtMillis` never participate directly or indirectly in `operationId`, `eventId`, `migrationId`, `proposalId`, `approvalId`, `payloadDigest`, or owner-record digests.

## 4. Deterministic identities

### 4.1 Canonical snapshot and plan

`CanonicalMemorySnapshot` does not currently expose one aggregate snapshot hash. The future canonical planning adapter derives it without substituting a clock value.

```json
{
  "schema": "morimil.cognitive_migration.snapshot_descriptor.v1",
  "instance_id": "<canonical instanceId>",
  "canonical_birth_root_hash": "<verified birth-root hash>",
  "canonical_last_sequence": 42,
  "canonical_last_event_hash": "<verified tail or birth root>",
  "canonical_record_set_digest": "<digest of every verified record descriptor>"
}
```

```text
canonicalRecordDescriptor = {
  sequence, event_id, event_hash, event_type, actor,
  observed_at, content_digest, provenance_digest
}
canonicalRecordSetDigest = D(canonicalRecordDescriptors ordered by sequence)
canonicalPreSnapshotHash = D(snapshotDescriptorJson)
```

The descriptor covers the complete verified snapshot, not merely selected planner records. Selection is bound separately by `source_event_hashes` and `source_set_digest`.

The deterministic plan is encoded as `planCoreJson`, with no IDs or timestamps:

```json
{
  "schema": "morimil.cognitive_migration.plan_core.v1",
  "planner_schema": "<planner schema>",
  "migration_type": "cognitive.memory_refinement",
  "from_version": "living_memory_current",
  "to_version": "living_memory_refined_v2",
  "canonical_birth_root_hash": "<verified root>",
  "canonical_pre_snapshot_hash": "<canonicalPreSnapshotHash>",
  "source_event_hashes": ["<sorted unique verified hashes>"],
  "affected_artifacts": ["<sorted unique deterministic artifacts>"],
  "steps": ["<ordered deterministic steps>"],
  "expected_effect": "<bounded deterministic plan>",
  "risk_level": "<low|medium|high|critical>",
  "rollback_strategy": "<append-only strategy>",
  "backup_required": true,
  "approval_required": true,
  "rollback_available": true
}
```

```text
planCoreDigest = D(planCoreJson)
planIdentityJson = {
  schema: morimil.cognitive_migration.plan_identity.v1,
  instance_id,
  migration_type,
  planner_schema,
  from_version,
  to_version,
  canonical_birth_root_hash,
  canonical_pre_snapshot_hash,
  source_event_hashes,
  source_set_digest,
  plan_core_digest
}
planIntentDigest = D(planIdentityJson)

proposalId =
  "cog_proposal_" + H(
    "morimil.cognitive_migration.proposal_id.v1",
    instanceId,
    planIntentDigest
  )

migrationId =
  "cog_migration_" + H(
    "morimil.cognitive_migration.migration_id.v1",
    instanceId,
    proposalId,
    "cognitive.memory_refinement"
  )
```

`migrationId` is the `subjectId` for all four operations.

### 4.2 Common operation and event IDs

After constructing the owner payload without timestamps:

```text
payloadDigest = D(payloadJson)

operationId =
  "xop_" + H(
    "morimil.cross_database.operation_id.v1",
    operationType,
    operationVersion,
    instanceId,
    writerBodyId,
    writerEpoch,
    subjectId,
    parentOperationId_or_empty,
    childPhase_or_empty,
    payloadDigest
  )

eventId =
  "xevt_" + H(
    "morimil.cross_database.event_id.v1",
    operationId,
    eventType
  )
```

The five deterministic identities are `operationId`, `eventId`, `migrationId`, `proposalId`, and `approvalId`.

For `COG-002`:

```text
approvalId = operationId
```

The COG-002 payload excludes `approvalId` to avoid a digest cycle. Its finalizer writes the derived `operationId` into `MigrationRecordEntity.approvalId`.

## 5. Proposed Room schema

```text
MemoryOrganDatabase 8 -> 9
table: cross_database_operations
```

### 5.1 Columns

| Column | SQLite type | Nullable | Key/index | Constraint and meaning |
| --- | --- | ---: | --- | --- |
| `operationId` | `TEXT` | no | primary key | Deterministic `xop_` ID; immutable. |
| `ownerType` | `TEXT` | no | owner/subject index | Initially `cognitive_migration`. |
| `operationType` | `TEXT` | no | owner/subject index | Closed registry: propose, approve, execute, rollback. |
| `operationVersion` | `INTEGER` | no | — | `>= 1`; first version `1`. |
| `instanceId` | `TEXT` | no | recovery index | Committed Genesis Ultra Instance. |
| `writerBodyId` | `TEXT` | no | — | Active writer Body at staging. |
| `writerEpoch` | `TEXT` | no | writer/recovery index | Active Body key epoch at staging. |
| `subjectId` | `TEXT` | no | owner/subject index | Deterministic `migrationId`. |
| `parentOperationId` | `TEXT` | yes | parent/child index | Null for COG v1. |
| `childPhase` | `TEXT` | yes | parent/child index | Null iff parent is null. |
| `payloadSchema` | `TEXT` | no | — | Exact payload schema. |
| `payloadJson` | `TEXT` | no | — | Immutable canonical JSON; no executable content. |
| `payloadDigest` | `TEXT` | no | — | 64 lowercase SHA-256 hex. |
| `eventId` | `TEXT` | no | unique | Deterministic canonical event ID. |
| `eventType` | `TEXT` | no | — | One event type from section 7. |
| `eventBody` | `TEXT` | no | — | Deterministic bounded text; no timestamps. |
| `evidenceSchema` | `TEXT` | no | — | Exact evidence schema. |
| `evidenceJson` | `TEXT` | no | — | Immutable canonical JSON. |
| `evidenceDigest` | `TEXT` | no | — | 64 lowercase SHA-256 hex. |
| `status` | `TEXT` | no | status/recovery indices | One of six protocol states. |
| `attemptCount` | `INTEGER` | no | — | Default `0`; never negative. |
| `lastErrorCode` | `TEXT` | yes | — | Stable bounded code, no raw exception or secret. |
| `canonicalEventHash` | `TEXT` | yes | — | Complete receipt field. |
| `canonicalSequence` | `INTEGER` | yes | — | Complete receipt field, `>= 1`. |
| `canonicalProvenanceDigest` | `TEXT` | yes | — | Complete receipt field. |
| `localResultSchema` | `TEXT` | yes | — | All local-result fields null or non-null together. |
| `localResultJson` | `TEXT` | yes | — | Exact finalizer result, including migration outcome. |
| `localResultDigest` | `TEXT` | yes | — | Required for `COMMITTED`. |
| `occurredAtMillis` | `INTEGER` | no | — | First staged observation metadata, reused on retry. |
| `createdAtMillis` | `INTEGER` | no | recovery ordering | First durable stage time. |
| `updatedAtMillis` | `INTEGER` | no | status ordering | Last metadata update. |
| `committedAtMillis` | `INTEGER` | yes | — | Non-null only for `COMMITTED`. |

### 5.2 Indices

```text
UNIQUE index_cross_database_operations_eventId(eventId)
index_cross_database_operations_instance_status_created(instanceId, status, createdAtMillis, operationId)
index_cross_database_operations_owner_subject_status(ownerType, subjectId, operationType, status)
index_cross_database_operations_status_updated(status, updatedAtMillis, operationId)
index_cross_database_operations_writer_epoch_status(instanceId, writerEpoch, status)
index_cross_database_operations_parent_child(parentOperationId, childPhase)
```

### 5.3 Constraints

```text
operationVersion >= 1
attemptCount >= 0
occurredAtMillis >= 0
createdAtMillis >= 0
updatedAtMillis >= createdAtMillis

status IN (
  STAGED,
  PENDING_CANONICAL,
  CANONICAL_COMMITTED,
  PENDING_LOCAL_COMMIT,
  COMMITTED,
  BLOCKED
)

(parentOperationId IS NULL) == (childPhase IS NULL)
payloadDigest/evidenceDigest and optional receipt/result digests are lowercase SHA-256
receipt hash/sequence/provenance digest are all null or all non-null
COMMITTED requires complete receipt, complete local result, and committedAtMillis
non-COMMITTED requires committedAtMillis IS NULL
```

`BLOCKED` may have no receipt or a complete receipt depending on the conflict point. Partial receipts are forbidden.

## 6. Proposed Kotlin contracts

```kotlin
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
)

internal interface CrossDatabaseOperationStagingPort {
    suspend fun stageExact(command: CrossDatabaseStageCommand): CrossDatabaseOperationRecord
    suspend fun load(operationId: String): CrossDatabaseOperationRecord?
}

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
    val sources: List<VerifiedCognitiveMigrationSource>
)

internal interface CognitiveMigrationCanonicalReadPort {
    suspend fun readVerifiedPlanningInput(): VerifiedCognitiveMigrationPlanningInput
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
)

internal interface CrossDatabaseCanonicalEnsurePort {
    suspend fun ensureCommitted(command: CrossDatabaseCanonicalCommand): CrossDatabaseCanonicalReceipt
}

internal data class CrossDatabaseLocalResult(
    val schema: String,
    val json: String,
    val digest: String,
    val ownerStatus: String
)

internal interface CrossDatabaseTypedFinalizer {
    val supportedOperationTypes: Set<String>
    suspend fun finalizeInsideTransaction(
        operation: CrossDatabaseOperationRecord,
        receipt: CrossDatabaseCanonicalReceipt
    ): CrossDatabaseLocalResult
}

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
```

The staging implementation owns `clockMillis`, but consults it only when inserting a previously absent row. `stageExact()` first loads by `operationId`; an exact retry compares every immutable identity, payload, event, and evidence field and reuses persisted timestamps. A new caller timestamp cannot make an identical logical action conflict or acquire another identity.

No staged payload may contain SQL, a reflection target, callback, model prompt, provider command, or executable instruction. Finalizers are selected from a closed Kotlin registry keyed by `(operationType, operationVersion)`.

## 7. COG operation contracts

```text
COG-001 -> cognitive_migration.proposed
COG-002 -> cognitive_migration.approved
COG-003 -> cognitive_migration.executed
COG-004 -> cognitive_migration.rollback
```

```text
COG-001 -> cognitive_migration.propose / version 1
COG-002 -> cognitive_migration.approve / version 1
COG-003 -> cognitive_migration.execute / version 1
COG-004 -> cognitive_migration.rollback / version 1
```

### 7.1 COG-001 — propose

Prerequisite: committed identity from `GenesisUltraRuntimeIdentityRepository` and a verified snapshot/read model from `CanonicalMemoryRepository` through `CognitiveMigrationCanonicalReadPort`. The adapter maps verified records into `VerifiedCognitiveMigrationPlanningInput`; it never exposes `MemoryEventEntity` or a legacy DAO. If unavailable, inconsistent, foreign to the active `instanceId`, or unable to prove the writer Body/epoch, COG-001 fails closed before staging. `COG-001` must not read or create compatibility rows in `memory_events`, `genesis_core`, or `local_instance_identity`.

Payload schema:

```text
morimil.cognitive_migration.cog_001.payload.v1
```

Required fields:

```text
schema
migration_id
proposal_id
migration_type
from_version
to_version
canonical_birth_root_hash
canonical_pre_snapshot_hash
canonical_last_sequence
source_event_hashes_sorted
source_set_digest
plan_schema
plan_core
plan_core_digest
planned_record
planned_record_digest
```

`plan_core` is the exact `planCoreJson`; `planned_record` is its deterministic owner projection without timestamps. The existing `genesisCoreHash` field receives the verified canonical birth-root hash and is never loaded from legacy `genesis_core`.

Evidence schema `morimil.cognitive_migration.cog_001.evidence.v1` binds operation, Instance, writer Body/epoch, migration/proposal IDs, payload/event IDs, source count/digest, `chain_verified = true`, and `legacy_input_used = false`.

Finalization transaction: reload journal and receipt, revalidate identity and writer epoch, recompute `planned_record_digest`, insert `MigrationRecordEntity(status = planned)` if absent or require exact equivalence, persist local result, and mark the journal `COMMITTED`. No visible migration record exists before canonical proposal evidence.

### 7.2 COG-002 — approve

Payload schema `morimil.cognitive_migration.cog_002.payload.v1` binds:

```text
migration_id
planned_record_digest
expected_owner_status = planned
decision = approve
approval_scope = cognitive_migration_execution
approved_by_user = true
```

The payload excludes `approvalId`; the derived `operationId` is the stable approval ID. Evidence binds `approval_id = operation_id`, `decision_source = interactive_local_user`, and `ownership_conferred = false`.

Finalization requires an exact planned record digest, writes `approvedByUser = true`, `approvalId = operationId`, and `status = approved`, and commits the journal in the same transaction. Replay returns the original result.

### 7.3 COG-003 — execute

Payload schema `morimil.cognitive_migration.cog_003.payload.v1` binds:

```text
migration_id
planned_record_digest
approval_operation_id
approval_event_hash
approval_sequence
approval_provenance_digest
expected_owner_status = approved
post_append_audit_policy = full_verified_canonical_chain
```

Evidence binds the exact approval receipt, `original_memory_rewritten = false`, and `post_append_audit_required = true`.

After the exact execution receipt is durable, audit the canonical chain. The local result records migration outcome `completed` or `failed`. The protocol outcome is `COMMITTED` in both cases when the canonical event and exact local outcome were reconciled. Migration outcome and protocol outcome are distinct.

### 7.4 COG-004 — rollback

Payload schema `morimil.cognitive_migration.cog_004.payload.v1` binds:

```text
migration_id
planned_record_digest
expected_owner_status_one_of = [approved, completed, failed]
predecessor_operation_id
predecessor_event_hash
predecessor_sequence
predecessor_provenance_digest
rollback_strategy_digest
compensation_mode = append_only
```

Evidence states `original_events_deleted = false`, `original_events_rewritten = false`, and `second_rollback_event_allowed = false`.

The finalizer verifies the allowed predecessor, marks the owner `rolled_back`, stores the rollback receipt reference, persists the deterministic local result, and commits atomically. Recovery never appends a second rollback event for the same operation.

## 8. Canonical event and provenance

The deterministic body contains operation ID/type/version, migration/proposal/approval IDs, payload digest and transition, but no timestamp.

Exact envelope values:

```text
actor = cognitive_migration_protocol
source = cross_database_operations
classification = durable_cognitive_migration_transition
source_id = operationId
```

`user_confirmed` is `false` for COG-001 and `true` for COG-002, COG-003 and COG-004 because those transitions require the corresponding explicit local action. It records a bounded action and never ownership over Morimil.

Canonical provenance note schema `morimil.cross_database_operation.canonical_commit.v1` contains operation, owner, type/version, Instance, writer Body/epoch, subject, payload digest and evidence digest.

The ensure adapter must read the verified same-Instance snapshot, locate exactly one event by `eventId`, append when absent, recover after interrupted append, verify event ID/type/actor/observed time/body/source/classification/source operation/payload/evidence/Instance/Body/epoch, reject duplicates or mismatches, and return hash, sequence and provenance digest before local finalization.

## 9. DAO and transactions

Required generic DAO methods:

```text
insertOperationAbort
loadOperation
loadByEventId
loadRecoverableForInstance
loadActiveForOwnerSubject
loadAnyForOwnerSubjectAndOperationType
countByInstanceAndStatus
transitionStagedToPendingCanonical
persistCanonicalReceipt
transitionCanonicalCommittedToPendingLocalCommit
recordRetryableFailure
markBlocked
markCommittedWithLocalResult
```

Owner-side methods or equivalent conditional updates:

```text
insertMigrationRecordAbort
loadMigrationRecord
approveMigrationRecordIfPlanned
finishMigrationRecordIfApproved
rollbackMigrationRecordIfAllowed
```

Every state update requires `operationId` and exact expected status. Before insert, staging queries the closed owner/subject/operation-type key. Exact intent is reused; different payload or evidence is blocked before canonical append. A second COG-001 proposal for the same deterministic migration subject cannot create another canonical proposal event.

Exact transaction sequence:

1. stage immutable journal intent only;
2. `STAGED -> PENDING_CANONICAL`;
3. persist complete receipt and set `CANONICAL_COMMITTED`;
4. `CANONICAL_COMMITTED -> PENDING_LOCAL_COMMIT`;
5. revalidate journal, receipt, identity, writer epoch and owner digest; apply owner transition idempotently; persist local result; set `COMMITTED` in one transaction.

A crash leaves one durable state with one recovery action. A process mutex is only an optimization.

## 10. State machine

The only normal forward order is:

```text
STAGED
PENDING_CANONICAL
CANONICAL_COMMITTED
PENDING_LOCAL_COMMIT
COMMITTED
BLOCKED
```

Interpretation:

1. `STAGED`: immutable intent; no append and no visible owner change.
2. `PENDING_CANONICAL`: exact ensure is running or retryable.
3. `CANONICAL_COMMITTED`: complete exact receipt is durable.
4. `PENDING_LOCAL_COMMIT`: typed finalization is running or retryable.
5. `COMMITTED`: owner result and protocol result are durable.
6. `BLOCKED`: terminal permanent conflict pending a future audited repair.

`BLOCKED` is listed last as the terminal fail-closed state reachable from a noncommitted state; it is not a normal successor after `COMMITTED`. No transition skips receipt persistence and no staged payload is edited.

## 11. Stable errors

Retryable:

```text
XOP_DATABASE_TEMPORARY_UNAVAILABLE
XOP_CANONICAL_READ_TEMPORARY_UNAVAILABLE
XOP_CANONICAL_APPEND_INTERRUPTED
XOP_LOCAL_FINALIZATION_INTERRUPTED
XOP_RECOVERY_BATCH_EXHAUSTED
```

Permanent `BLOCKED`:

```text
XOP_OPERATION_ID_PAYLOAD_CONFLICT
XOP_OPERATION_ID_EVIDENCE_CONFLICT
XOP_OWNER_TRANSITION_CONFLICT
XOP_EVENT_ID_CONFLICT
XOP_CANONICAL_EVENT_MISMATCH
XOP_CANONICAL_PROVENANCE_MISMATCH
XOP_CANONICAL_RECEIPT_CONFLICT
XOP_WRONG_INSTANCE
XOP_UNAUTHORIZED_WRITER_BODY
XOP_STALE_WRITER_EPOCH
XOP_OWNER_STATE_CONFLICT
XOP_PREDECESSOR_RECEIPT_MISSING
XOP_UNSUPPORTED_OPERATION_VERSION
XOP_UNSUPPORTED_PAYLOAD_SCHEMA
XOP_LEGACY_CANONICAL_INPUT_FORBIDDEN
```

Raw exception text is diagnostic input, not stable protocol state and not persisted into issues or logs.

## 12. Recovery

Startup order after the canonical F1 read path exists:

```text
committed Genesis Ultra identity
-> verified canonical memory snapshot
-> recover cognitive cross-database operations
-> existing ProjectVault recovery unchanged
-> runtime bootstrap
-> normal mutation paths
```

Before any new COG mutation, run bounded recovery for `ownerType = cognitive_migration` and the same `instanceId`.

Recovery order is `createdAtMillis ASC, operationId ASC`. Foreign Instance, stale epoch or unauthorized Body blocks. `STAGED` resumes dispatch; `PENDING_CANONICAL` exact-ensures and recovers an already-appended event; `CANONICAL_COMMITTED` advances locally; `PENDING_LOCAL_COMMIT` reruns the typed finalizer; `COMMITTED` returns the original receipt/result; any relevant `BLOCKED` row stops new cognitive mutations. Reports include counts for all six states, recovered rows, retryable failures and blocked rows.

## 13. Kill-test and conflict matrix

Every applicable cut runs on managed or physical **API 30 and API 35**.

| Cut | Required assertion |
| --- | --- |
| Before staging | No journal row, event or owner transition. |
| After `STAGED`, before dispatch | Recovery appends once and finalizes once. |
| During `PENDING_CANONICAL`, before append | Retry produces one exact event. |
| After append, before persisting receipt | Recovery finds the exact event; zero duplicate canonical events. |
| After receipt, before local dispatch | No second event; recovery advances locally. |
| During local finalization | Transaction rollback leaves no partial owner state; retry commits once. |
| After `COMMITTED`, full replay | Same receipt/result; zero duplicates. |
| Same `operationId`, different payload | Permanent payload conflict. |
| Same operation and payload, different evidence | Permanent evidence conflict. |
| Same `eventId`, different content | Permanent event conflict. |
| Same `eventId`, different provenance | Permanent provenance conflict. |
| Stale writer epoch | Block; never append under a replacement epoch. |
| Repeated same user action | Same five deterministic identities and one owner transition. |
| COG-003 audit false | Migration outcome `failed`, protocol outcome `COMMITTED`. |
| COG-004 replay | One rollback event and one `rolled_back` owner result. |

Global closure assertions:

```text
zero duplicate canonical events
zero duplicate visible MigrationRecord rows
zero visible owner state without an exact canonical receipt
zero committed journal rows with partial receipts
zero local finalizations from another Instance or stale writer epoch
```

## 14. Room migration and tests

```text
MemoryOrganDatabase version 8 -> 9
new entity: CrossDatabaseOperationEntity
new DAO: CrossDatabaseOperationDao
new migration: MemoryOrganDatabaseMigrationV9.MIGRATION_8_9
new schema: app/schemas/com.morimil.app.data.local.MemoryOrganDatabase/9.json
```

Room migration tests must create a real version-8 encrypted database, preserve representative migration and ProjectVault outbox rows, migrate with `MIGRATION_8_9`, validate schema/indices/constraints, prove the new journal empty, reject malformed digests/partial receipts/illegal status/invalid parent-child pairs, and extend the full-chain migration through version 9.

Required functional evidence:

```text
deterministic ID tests
canonical exact-match reuse tests
payload conflict tests
provenance conflict tests
writer Body/epoch tests
typed-finalizer idempotency tests
startup recovery tests
bounded pre-mutation recovery tests
COG-001 canonical-read-only tests
COG-001 through COG-004 kill-tests API 30/API 35
zero-duplicate assertions
```

## 15. Closed file list for the future functional PR

```text
app/src/main/java/com/morimil/app/data/local/CrossDatabaseOperationEntity.kt
app/src/main/java/com/morimil/app/data/local/CrossDatabaseOperationDao.kt
app/src/main/java/com/morimil/app/data/local/MemoryOrganDatabase.kt
app/src/main/java/com/morimil/app/data/local/MemoryOrganDatabaseEncryption.kt
app/src/main/java/com/morimil/app/data/local/MemoryOrganDatabaseMigrationV9.kt
app/src/main/java/com/morimil/app/data/local/MemoryOrganDao.kt
app/src/main/java/com/morimil/app/data/local/MigrationRecordEntity.kt
app/src/main/java/com/morimil/app/data/repository/CrossDatabaseOperationContracts.kt
app/src/main/java/com/morimil/app/data/repository/CrossDatabaseOperationCoordinator.kt
app/src/main/java/com/morimil/app/data/repository/CognitiveMigrationProtocolFinalizer.kt
app/src/main/java/com/morimil/app/data/repository/CognitiveMigrationRepository.kt
app/src/main/java/com/morimil/app/data/repository/MigrationRecordRepository.kt
app/src/main/java/com/morimil/app/core/memory/CognitiveMigrationPlanner.kt
app/src/main/java/com/morimil/app/data/genesis/ultra/CanonicalCognitiveMigrationReadPort.kt
app/src/main/java/com/morimil/app/data/genesis/ultra/CanonicalCognitiveMigrationCommitPort.kt
app/src/main/java/com/morimil/app/MorimilAppContainer.kt
app/src/main/java/com/morimil/app/MorimilAppContainerCognitiveMigrationProtocol.kt
app/src/main/java/com/morimil/app/MorimilAppContainerRuntimeGate.kt
app/schemas/com.morimil.app.data.local.MemoryOrganDatabase/9.json
app/src/test/java/com/morimil/app/data/repository/CrossDatabaseOperationIdentityTest.kt
app/src/test/java/com/morimil/app/data/repository/CrossDatabaseOperationCoordinatorTest.kt
app/src/test/java/com/morimil/app/data/repository/CognitiveMigrationRepositoryTest.kt
app/src/test/java/com/morimil/app/data/genesis/ultra/CanonicalCognitiveMigrationCommitPortTest.kt
app/src/test/java/com/morimil/app/architecture/CognitiveMigrationProtocolContractTest.kt
app/src/test/java/com/morimil/app/MorimilAppContainerContractTest.kt
app/src/androidTest/java/com/morimil/app/data/local/MemoryOrganDatabaseV8ToV9MigrationTest.kt
app/src/androidTest/java/com/morimil/app/data/local/FullChainDatabaseMigrationTest.kt
app/src/androidTest/java/com/morimil/app/data/repository/CognitiveMigrationProtocolKillTest.kt
```

The future PR must not touch any path or responsibility belonging to ORCH, AGENT, BOOT, RECALL, REST, or ProjectVault. It must not weaken ADR-0002 or `docs/F3_CROSS_DATABASE_OPERATION_INVENTORY.md`.

## 16. Rollback and rejection

Reject or revert the functional PR before merge if a timestamp reaches identity/payload digest, COG-001 retains a legacy read, owner state becomes visible before receipt, same-ID mismatch succeeds, writer Body/epoch is not revalidated, ProjectVault is rewritten, another F3 owner is touched, an API 30/API 35 kill test fails, Room loses existing rows, or CI/SBOM is not green on the exact head.

After a shipped database migration, rollback is forward-only: disable entry points, preserve journal and canonical evidence, and issue another audited migration. Never drop the journal, delete canonical events, edit staged payloads, or recreate legacy compatibility rows.

## 17. Acceptance boundary

A developer must be able to implement the first functional PR without inventing schema, IDs, payload/evidence versions, ports, DAO transactions, states, receipts, finalization, errors, recovery, kill cuts, migration tests, file scope or rollback criteria.

It remains preparation only. `#88` stays open, STOP S5 stays open, no runtime was implemented, and no merge authority is delegated to Agente Chat 3.
