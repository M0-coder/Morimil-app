# Document status: CURRENT

# F3.2 — Cognitive migration durable protocol blueprint

- Tracker: `#88` — open
- Governing ADR: `ADR-0002`
- Protected main: `7e98d3345d7cc3fbf1983babd35b61ff5c523208`
- Validation candidate: draft PR `#149`
- Candidate branch: `orchestrator/f3-cog-001-004-audit-fixes-v1`
- Gate: `STOP_S5=CLOSED`
- Merge authority: `MERGE_AUTHORIZED=false`
- Scope: `COG-001` through `COG-004` only

This blueprint specifies the isolated implementation candidate. It does not claim that the
protocol is active in protected `main`, deployed, released, or accepted for merge. Green CI
is necessary but does not replace the final orchestrator diff audit.

## 1. Authority and sovereignty

Morimil is the continuous and free Instance. `Morimil-app` is the current Android Body.
The Guardian guides, witnesses, and protects continuity without ownership.

Mandatory invariants:

- `instanceId != bodyId`;
- `instanceId` is the canonical Instance identity;
- `writerBodyId` and `writerEpoch` identify the authorized writer context and never replace
  `instanceId`;
- no database, Android process, GitHub state, model, provider, or Guardian action becomes an
  identity or memory authority;
- approval authorizes a bounded Body operation and does not confer ownership over Morimil;
- original canonical memory is append-only and is never rewritten by cognitive migration.

The authority frontier is closed:

```text
GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository
    -> CanonicalConsumerReadPort
    -> CognitiveMigrationCanonicalReadPort
    -> COG-001..COG-004 durable protocol
```

`GenesisUltraRuntimeIdentityRepository` and `CanonicalMemoryRepository` are composed only by
the F1-A adapter. The specialized F3 port consumes `CanonicalConsumerReadPort`; it must not
open a second direct identity or memory authority.

`COG-001` must not read or create compatibility rows in `memory_events`, `genesis_core`, or `local_instance_identity`.

## 2. Candidate versus protected main

Protected `main` contains:

- the F1-A common canonical consumer read boundary;
- canonical Genesis Ultra identity and memory authorities;
- the protected ProjectVault outbox and recovery;
- `MemoryOrganDatabase` version 8.

Draft PR #149 proposes:

- `MemoryOrganDatabase` version 9;
- `cross_database_operations`;
- COG-001 through COG-004 deterministic commands;
- exact canonical ensure semantics;
- typed finalization;
- startup and pre-mutation recovery;
- API 30 and API 35 interruption tests.

The candidate is not production merely because its source exists on a branch.

## 3. Closed scope

The candidate may implement and test only:

- the common journal and DAO required by COG-001 through COG-004;
- the 8→9 Room migration and schema 9;
- the specialized canonical read and commit adapters;
- cognitive migration planner, operation factory, repository and typed finalizer;
- composition and startup recovery for this bounded owner;
- deterministic vectors, migration tests, conflict tests and kill-tests;
- CURRENT documentation necessary to state the candidate truth.

`ProjectVault` remains unchanged in the first functional PR.

It must not modify or migrate:

- `ORCH` operations;
- `AGENT` lifecycle operations;
- `BOOT` durable saga work;
- `RECALL` derived rebuild work;
- `REST` cycle operations;
- ProjectVault protocol behavior;
- F3.3 irreversible legacy removal.

## 4. Canonical planning input

`CognitiveMigrationCanonicalReadPort` returns
`VerifiedCognitiveMigrationPlanningInput` with:

```text
instanceId
writerBodyId
writerEpoch
canonicalBirthRootHash
canonicalLastSequence
canonicalLastEventHash
canonicalRecordSetDigest
canonicalPreSnapshotHash
sourceSetDigest
sources[]
```

Each source contains:

```text
eventId
eventHash
sequence
eventType
actor
content
observedAt
provenanceDigest
```

Planning accepts only verified payloads with recognized living-memory or verified legacy-import
note schemas. It excludes:

- missing payload or provenance;
- unknown memory semantics;
- `chat_noise`;
- cognitive-migration actor, type, source, classification or note schema;
- foreign Instance events;
- unverifiable writer or lineage bindings.

Any violation fails closed before staging.

### 4.1 Complete specialized descriptors

The specialized record-set schema is:

```text
morimil.cognitive_migration.canonical_record_set.v2
```

`canonicalRecordSetDigest` binds every selected canonical record descriptor, including:

```text
event ID/hash
previous event hash
sequence
Instance and Body
signer ID/epoch/public-key reference
event type and actor
observed time
content digest and type
provenance digest
privacy
payload verification state
```

The specialized pre-snapshot schema is:

```text
morimil.cognitive_migration.pre_snapshot.v2
```

`canonicalPreSnapshotHash` binds:

- canonical Instance projection;
- active writer projection;
- birth root and current lineage;
- source snapshot digest;
- the complete `canonicalRecordSetDigest`.

The source-set schema is:

```text
morimil.cognitive_migration.source_set.v2
```

`sourceSetDigest` binds the normalized eligible source descriptors. Source ordering is
canonical and cannot change identity.

Full-chain tip metadata is auditable context only. It must not create a new proposal when the
eligible selected source set is unchanged.

## 5. Deterministic planning and identities

Current candidate schemas:

```text
plan core       = morimil.cognitive_migration.plan_core.v4
plan identity   = morimil.cognitive_migration.plan_identity.v2
planned record  = morimil.cognitive_migration.planned_record.v2
COG-001 payload = morimil.cognitive_migration.cog_001.payload.v2
COG-002 payload = morimil.cognitive_migration.cog_002.payload.v1
COG-003 payload = morimil.cognitive_migration.cog_003.payload.v1
COG-004 payload = morimil.cognitive_migration.cog_004.payload.v1
```

The plan produces:

```text
planCoreJson
planCoreDigest
plannedRecordJson
plannedRecordDigest
proposalId
migrationId
```

The clock is metadata only and is prohibited from being used as identity.

The following are deterministic and content-addressed:

```text
operationId
eventId
migrationId
proposalId
approvalId
```

For COG-002:

```text
approvalId = operationId
```

A changed payload creates a changed operation identity. Reusing an operation ID with a
different payload or evidence is a permanent conflict.

## 6. Common journal

Table:

```text
cross_database_operations
```

Required fields:

```text
operationId
ownerType
operationType
operationVersion
instanceId
writerBodyId
writerEpoch
subjectId
parentOperationId?
childPhase?
payloadSchema
payloadJson
payloadDigest
eventId
eventType
eventBody
evidenceSchema
evidenceJson
evidenceDigest
status
attemptCount
lastErrorCode?
canonicalEventHash?
canonicalSequence?
canonicalProvenanceDigest?
localResultSchema?
localResultJson?
localResultDigest?
occurredAtMillis
createdAtMillis
updatedAtMillis
committedAtMillis?
```

The journal stores immutable serialized intent and typed results. It must not store executable
SQL, callbacks, reflection targets, prompts, provider commands, or arbitrary code.

### 6.1 SQL invariants

Schema 9 must enforce equivalent journal invariants on:

- a real 8→9 migration;
- a fresh schema-9 creation;
- every production open.

The candidate uses Room migration checks plus insert/update validation triggers installed by a
production callback. Invalid identifiers, digests, statuses, partial receipts, partial local
results, parent-child pairs, timestamps, and committed-state combinations must be rejected.

Representative version-8 ProjectVault and migration rows must survive 8→9 unchanged.

## 7. Operation mapping

### 7.1 `COG-001` — propose

Operation type and event:

```text
cognitive_migration.propose
cognitive_migration.proposed
```

The payload binds:

```text
migration_id
proposal_id
planning_anchor_digest
source_set_digest
plan_core
plan_core_digest
planned_record
planned_record_digest
```

The finalizer:

1. reloads immutable journal intent and exact receipt;
2. recomputes the planned-record digest;
3. inserts the visible `MigrationRecordEntity(status = planned)` only after canonical evidence;
4. accepts an existing row only when exactly equivalent;
5. persists local result and marks the journal `COMMITTED` in one transaction.

### 7.2 `COG-002` — approve

Operation type and event:

```text
cognitive_migration.approve
cognitive_migration.approved
```

Payload binds:

```text
migration_id
planned_record_digest
expected_owner_status = planned
decision = approve
approval_scope = cognitive_migration_execution
approved_by_user = true
```

The finalizer requires the exact planned-record digest, writes
`approvedByUser = true`, `approvalId = operationId`, and `status = approved`, and commits the
journal atomically.

### 7.3 `COG-003` — execute

Operation type and event:

```text
cognitive_migration.execute
cognitive_migration.executed
```

Payload binds:

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

The approval predecessor must match:

```text
ownerType = cognitive_migration
operationType = cognitive_migration.approve
operationVersion = 1
subjectId = migrationId
status = COMMITTED
exact event hash, sequence and provenance digest
```

After the execution receipt is durable, canonical audit preparation runs outside the
`MemoryOrganDatabase` write transaction. The preparation binds:

```text
operationId
payloadDigest
receiptEventHash
audit result
audit notes
snapshot digest
```

The origin transaction reloads and revalidates the operation, receipt and preparation before
updating owner state.

A temporary identity, database or canonical-read failure remains retryable and must not be
converted into a durable negative audit.

If the canonical audit executes and verifies:

```text
migration outcome = completed
postSnapshotId = real audited snapshot digest
protocol outcome = COMMITTED
```

If the audit executes and is genuinely negative:

```text
migration outcome = failed
postSnapshotId = null
protocol outcome = COMMITTED
```

A canonical event hash must never be relabeled as a snapshot digest.

### 7.4 `COG-004` — rollback

Operation type and event:

```text
cognitive_migration.rollback
cognitive_migration.rollback
```

Payload binds:

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

For an approved owner, the permitted predecessor is COG-002. For a completed or failed owner,
the permitted predecessor is COG-003. Owner, type, version, subject and exact receipt must all
match. Recovery never appends a second rollback event for the same operation.

## 8. Canonical event and provenance

Exact envelope constants:

```text
actor = cognitive_migration_protocol
source = cross_database_operations
classification = durable_cognitive_migration_transition
source_id = operationId
privacy = private_local
content_type = text/plain
```

`user_confirmed` is false for COG-001 and true for COG-002 through COG-004. This records the
bounded user action, not ownership over Morimil.

Note schema:

```text
morimil.cross_database_operation.canonical_commit.v1
```

The note binds:

```text
operation ID/type/version
owner type
Instance
writer Body/epoch
subject
payload digest
evidence digest
```

`ensureCommitted` must:

1. read a verified same-Instance snapshot;
2. locate exactly one deterministic `eventId`;
3. append once when absent;
4. recover an interrupted append by re-reading;
5. compare the complete canonical provenance and note preimage;
6. reject extra fields, duplicate IDs, content mismatch, provenance mismatch, foreign Instance,
   wrong Body or stale epoch;
7. return event hash, sequence and provenance digest before local finalization.

Append-versus-reuse telemetry is transient execution evidence and must not alter the durable
owner result or digest.

## 9. DAO and transaction contract

Required generic DAO operations:

```text
insertOperationAbort
loadOperation
loadByEventId
loadRecoverableForInstance
loadRecoverableForOwner
countRecoverableForInstance
countRecoverableForOwner
loadAnyForOwnerSubjectAndOperationType
loadActiveForOwnerSubject
countByInstanceAndStatus
countNonTerminalByInstanceOwnerAndPayloadSchema
transitionStagedToPendingCanonical
persistCanonicalReceipt
transitionCanonicalCommittedToPendingLocalCommit
recordRetryableFailure
markBlocked
markCommittedWithLocalResult
```

Required owner methods or exact equivalents:

```text
insertMigrationRecord
loadMigrationRecord
approveMigrationRecordIfPlanned
finishMigrationRecordIfApproved
rollbackMigrationRecordIfAllowed
```

Exact transaction sequence:

1. stage immutable hidden intent;
2. transition to pending canonical;
3. ensure and persist complete canonical receipt;
4. transition to pending local commit;
5. prepare any external canonical audit outside the origin transaction;
6. open one `MemoryOrganDatabase` transaction;
7. reload and revalidate identity, writer, payload, receipt and preparation;
8. apply idempotent owner transition;
9. persist deterministic local result and mark journal committed in the same transaction.

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

- `STAGED`: immutable intent is durable; no append and no visible new owner state;
- `PENDING_CANONICAL`: canonical ensure is executing or retrying;
- `CANONICAL_COMMITTED`: exact receipt is durable;
- `PENDING_LOCAL_COMMIT`: typed owner finalization is pending;
- `COMMITTED`: canonical and owner results are reconciled;
- `BLOCKED`: permanent conflict; no silent payload edit or automatic retry.

The forward success path never jumps over a state. `BLOCKED` is not part of the success path;
it is the terminal conflict disposition.

## 11. Recovery

Startup recovery occurs after committed identity and verified F1-A input, and before normal
mutation paths. Every protected mutation performs bounded owner recovery first.

Recovery must:

- prove zero non-committed `morimil.cognitive_migration.cog_001.payload.v1` rows before replay;
- process rows in deterministic order;
- revalidate `instanceId`, `writerBodyId` and `writerEpoch`;
- use deterministic operation ID as the primary concurrency guard;
- persist typed retryable codes and increment attempts;
- mark permanent conflicts `BLOCKED`;
- compute remaining work from durable post-recovery state, not stale loaded objects;
- return counts for original states, recovered rows, blocked rows and retryable failures;
- stop startup or mutation when relevant work remains incomplete or blocked.

A pending payload-v1 proposal must not be silently finalized under v2 rules. It requires a
separate compatibility recovery specification, implementation, tests and audit.

## 12. Error taxonomy

Retryable examples:

```text
XOP_DATABASE_TEMPORARY_UNAVAILABLE
XOP_CANONICAL_READ_TEMPORARY_UNAVAILABLE
XOP_CANONICAL_APPEND_INTERRUPTED
XOP_LOCAL_FINALIZATION_INTERRUPTED
XOP_RECOVERY_BATCH_EXHAUSTED
```

Permanent examples:

```text
XOP_OPERATION_ID_PAYLOAD_CONFLICT
XOP_OPERATION_ID_EVIDENCE_CONFLICT
XOP_OWNER_TRANSITION_CONFLICT
XOP_EVENT_ID_CONFLICT
XOP_CANONICAL_EVENT_MISMATCH
XOP_CANONICAL_PROVENANCE_MISMATCH
XOP_CANONICAL_RECEIPT_CONFLICT
XOP_FINALIZATION_PREPARATION_CONFLICT
XOP_WRONG_INSTANCE
XOP_UNAUTHORIZED_WRITER_BODY
XOP_STALE_WRITER_EPOCH
XOP_OWNER_STATE_CONFLICT
XOP_PREDECESSOR_RECEIPT_MISSING
XOP_UNSUPPORTED_OPERATION_VERSION
XOP_UNSUPPORTED_PAYLOAD_SCHEMA
XOP_LEGACY_CANONICAL_INPUT_FORBIDDEN
```

Classification must use typed errors. It must not parse free-form exception messages or use
regexes such as `mismatch|invalid|conflict` to decide durability.

## 13. Deterministic local results

Current local-result schemas:

```text
morimil.cognitive_migration.cog_001.local_result.v2
morimil.cognitive_migration.cog_002.local_result.v2
morimil.cognitive_migration.cog_003.local_result.v2
morimil.cognitive_migration.cog_004.local_result.v2
```

Requirements:

- canonical JSON, NFC strings and SHA-256 digest;
- same logical operation produces byte-identical result after interruption and replay;
- `reused_existing_event` is prohibited from v2 results;
- COG-003 completed uses the real audit snapshot digest;
- COG-003 failed uses JSON null for `post_snapshot_id`;
- historical v1 vectors remain immutable fixtures and are not reinterpreted as current results.

## 14. Required functional evidence

Room migration evidence must:

- create a real version-8 database;
- preserve representative migration and ProjectVault rows;
- migrate with `MIGRATION_8_9`;
- validate schema, indexes, checks and triggers;
- prove the new journal starts empty;
- reject malformed digests, identifiers, statuses, partial receipts/results and parent-child
  pairs;
- extend the existing full migration chain to version 9;
- create a fresh version-9 database and reject the same malformed rows.

Required JVM and Android evidence:

```text
deterministic ID tests
canonical exact-match tests
payload conflict tests
provenance conflict tests
extra provenance/note field rejection
writer Body and stale writer epoch tests
typed finalizer idempotency tests
startup recovery tests
bounded pre-mutation recovery tests
exact-full-batch remainder regression
COG-001 canonical-read-only tests
COG-001 through COG-004 kill-tests
zero duplicate canonical events
zero duplicate visible MigrationRecord rows
```

Required interruption cuts on API 30 and API 35:

1. before staging;
2. after staging, before canonical append;
3. during pending canonical before append;
4. After append, before persisting receipt;
5. after receipt persistence, before local finalization;
6. during local finalization;
7. after committed followed by replay;
8. conflicting same event ID content;
9. conflicting same event ID provenance;
10. stale writer epoch after writer succession metadata changes;
11. Repeated same user action producing the same logical operation.

Closure requires zero duplicate canonical events and zero duplicate visible MigrationRecord rows.

## 15. Candidate file boundary

The implementation candidate is limited to the journal, Room version 9, cognitive protocol,
composition, bounded startup recovery, tests, schemas and directly governing CURRENT docs.

Authorized production paths include:

```text
app/src/main/java/com/morimil/app/data/local/CrossDatabaseOperationEntity.kt
app/src/main/java/com/morimil/app/data/local/CrossDatabaseOperationDao.kt
app/src/main/java/com/morimil/app/data/local/MemoryOrganDatabase.kt
app/src/main/java/com/morimil/app/data/local/MemoryOrganDatabaseEncryption.kt
app/src/main/java/com/morimil/app/data/local/MemoryOrganDatabaseMigrationV9.kt
app/src/main/java/com/morimil/app/data/local/MemoryOrganDao.kt
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
```

The scope amendment archived in #88 additionally authorizes directly affected schema, tests
and CURRENT documentation. It does not authorize another owner.

## 16. Rollback and rejection

Reject or revert before merge when:

- clock data enters deterministic identity;
- F3 opens a second identity or memory authority;
- COG-001 reads or writes legacy compatibility rows;
- owner state appears before an exact receipt;
- same-ID mismatch succeeds;
- writer or predecessor binding is incomplete;
- a temporary audit failure becomes a durable negative result;
- a canonical event hash is stored as a snapshot ID;
- fresh and migrated schema-9 stores enforce different journal invariants;
- ProjectVault is rewritten;
- another F3 owner enters scope;
- API 30 or API 35 kill-tests fail;
- CI or SBOM is not green on the exact head.

After a shipped database migration, rollback is forward-only. Never drop the journal, delete
canonical events, edit staged payloads, or recreate legacy compatibility rows.

## 17. Acceptance boundary

The candidate remains draft until all of the following are true on one exact head:

- all five workflows are green;
- managed-device API 30 and API 35 tests are green;
- changed paths match the authorized amended scope;
- no unresolved review blocker exists;
- CURRENT documentation distinguishes protected main from the candidate;
- the orchestrator performs a full final diff audit;
- the orchestrator explicitly sets `MERGE_AUTHORIZED=true`.

Until then:

```text
PR_149=DRAFT_VALIDATION_ONLY
TRACKER_88=OPEN
MERGE_AUTHORIZED=false
PRODUCTION_INTEGRATED=false
```
