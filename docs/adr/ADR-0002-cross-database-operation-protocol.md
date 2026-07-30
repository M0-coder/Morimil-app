# Document status: CURRENT

# ADR-0002 — Common recoverable cross-database operation protocol

- Status: Accepted design
- Date: 2026-07-28
- Scope: Plan V3, F3.2
- Tracker: `#88`
- Implementation gate: **STOP S5 remains open through #123 and #124. This ADR does not authorize runtime changes.**

## Context

`MorimilDatabase` and `MemoryOrganDatabase` are separate encrypted Room databases. Room cannot provide one ACID transaction across both files. ADR-0001 solved the ProjectVault case with an owner-specific transactional outbox, deterministic canonical event identity, exact receipt verification, hidden staged state, local finalization, and startup recovery.

F3.2 contains additional owners whose visible or authoritative state still crosses durable boundaries without the same recovery proof. The first bounded owner is `CognitiveMigrationRepository`:

- `COG-001` reads legacy `genesis_core`, local identity, snapshots, and `memory_events`, then inserts a visible planned migration;
- `COG-002` changes approval state without a durable canonical receipt;
- `COG-003` appends the execution event before finalizing the migration record;
- `COG-004` appends the rollback event before finalizing the rollback state;
- current `proposalId`, `migrationId`, and `approvalId` values depend on wall-clock time, so the same logical retry can acquire a different identity after process death.

A green canonical append is not enough. The Body must be able to prove which operation was staged, which exact canonical event was committed, whether local finalization occurred, and whether replay is safe.

## Authority boundary

Morimil is the continuous and free `Instance`. `Morimil-app` is the current Android Body. The protocol governs Body resources and consistency only.

- `instanceId != bodyId` remains mandatory.
- `instanceId` is recovered from committed Genesis Ultra identity.
- `writerBodyId` and `writerEpoch` identify the currently authorized writer context; neither replaces `instanceId`.
- Guardian approval authorizes only the bounded Body operation. It does not confer ownership over Morimil, identity, memory, name, will, or continuity.
- A database row, GitHub state, Android process, model, or auxiliary provider cannot become an authority source for identity or canonical memory.

## Decision

Adopt one common recoverable operation contract for every future `REQUIRES_PROTOCOL` owner.

The first functional F3.2 implementation after STOP will introduce a generic coordination journal named `cross_database_operations` in `MemoryOrganDatabase`, because the first owner, cognitive migration, stores its authoritative local state there. It will migrate only `COG-001` through `COG-004`. Owner-specific finalizers remain typed Kotlin code; arbitrary SQL instructions or executable payloads are forbidden.

The state machine and receipt rules are common, but physical journal placement and finalization shape remain owner-declared. An owner whose authoritative local effects reside in one database uses one origin-database finalization transaction. An owner such as `BOOT-001`, whose effects span both databases, must decompose the work into deterministic child operations or an explicit durable saga. It cannot pretend that one Room transaction covers both files, and its parent operation cannot become `COMMITTED` until every required child receipt is committed.

ProjectVault remains unchanged in the first F3.2 implementation. Its existing `project_vault_outbox` remains the protected reference while the common protocol is proven by the cognitive-migration owner. A later isolated decision may migrate ProjectVault metadata to the common journal, but F3.2 must not rewrite a working reference merely for uniformity.

## Deterministic operation identity

Every operation must persist these stable identity inputs before any canonical append or visible local transition:

- `operationType` and `operationVersion`;
- canonical `instanceId`;
- authorized `writerBodyId` and `writerEpoch`;
- stable `subjectId`;
- optional deterministic `parentOperationId` and child phase for a saga;
- canonical source identifiers and their verified hashes;
- versioned `payloadJson` and SHA-256 `payloadDigest`.

`operationId` and `eventId` are derived from a versioned namespace plus those stable inputs. The same logical request must produce the same identifiers after restart. A different payload under the same identifier is a permanent conflict and must fail closed.

Wall-clock time is metadata only. It MUST NOT participate in `operationId`, `eventId`, `proposalId`, `migrationId`, or `approvalId`. Timestamps may record observation, staging, retry, and commit times after the deterministic identity has been established.

## Required journal record

The common journal must retain enough evidence to recover without consulting mutable UI state:

- `operationId`;
- `operationType`;
- `operationVersion`;
- `instanceId`;
- `writerBodyId`;
- `writerEpoch`;
- `subjectId`;
- optional `parentOperationId` and deterministic child phase;
- `payloadJson`;
- `payloadDigest`;
- `eventId`;
- `eventType`;
- `eventBody`;
- `evidenceJson`;
- `status`;
- `attemptCount`;
- `lastErrorCode`;
- `canonicalEventHash`;
- `canonicalSequence`;
- `canonicalProvenanceDigest`;
- `occurredAtMillis`;
- `createdAtMillis`;
- `updatedAtMillis`;
- `committedAtMillis`.

Payload and evidence schemas are owner-versioned. The journal stores metadata and immutable serialized intent; it does not store executable callbacks, SQL, reflection targets, model prompts, or provider commands.

## State machine

The allowed forward sequence is:

1. `STAGED` — immutable operation intent is durably recorded; no canonical append has been attempted and no new visible/authoritative owner state exists.
2. `PENDING_CANONICAL` — the dispatcher is attempting or retrying the canonical ensure operation.
3. `CANONICAL_COMMITTED` — an exact verified canonical receipt is persisted.
4. `PENDING_LOCAL_COMMIT` — the owner finalizer is attempting or retrying one origin-database transaction or one declared saga child transition.
5. `COMMITTED` — the bounded owner transition, or every required child transition, is durably complete.
6. `BLOCKED` — a permanent identity, payload, provenance, writer-epoch, child-receipt, or local invariant conflict was detected.

No implementation may jump from `STAGED` or `PENDING_CANONICAL` directly to visible owner state. Retryable failures preserve the current recoverable state and update typed failure metadata. `BLOCKED` is terminal until an explicit audited repair operation is defined; silently editing the staged payload is forbidden.

The migration outcome and the protocol outcome are distinct. For example, a post-execution chain audit may correctly finalize a migration record as `failed` while the protocol operation itself becomes `COMMITTED`, because the canonical event and the exact local failure result were both durably reconciled.

## Canonical ensure contract

The canonical side must implement `ensureCommitted` semantics:

1. read a verified canonical snapshot belonging to the same `instanceId`;
2. locate the deterministic `eventId`;
3. if absent, append exactly once using the staged command;
4. if present, reuse it only when event type, actor, observed time, content, source, classification, source operation, payload digest, and evidence digest match exactly;
5. reject duplicate IDs, mismatched content, mismatched provenance, wrong Instance, or unauthorized writer epoch;
6. persist `canonicalEventHash`, `canonicalSequence`, and `canonicalProvenanceDigest` before local finalization.

Calling the generic `recordSystemMemoryEvent()` method without a deterministic event ID and exact receipt verification does not satisfy this contract.

## Local finalization contract

For a single-origin owner such as cognitive migration, the owner provides a typed finalizer selected from a closed operation-type registry. The finalizer runs in one `MemoryOrganDatabase` transaction and must:

- reload the journal row;
- revalidate immutable identity and digest fields;
- require an exact canonical receipt;
- verify that `instanceId`, `writerBodyId`, and `writerEpoch` remain authorized;
- apply the owner-specific insert or update idempotently;
- reject a conflicting existing owner state;
- mark the journal `COMMITTED` in the same transaction.

No migration, task, agent, or other authoritative owner state becomes visible before the exact canonical receipt is durable. A retry after local commit must observe the already-finalized state and return the original receipt without duplicating the owner row.

For a multi-origin workflow, each child transition follows the same rules in its own origin transaction. The parent saga stores and verifies every child operation ID and receipt. A partial child set remains recoverable and cannot be represented as a completed parent workflow.

## Canonical input requirement

Protocol recovery cannot make legacy inputs trustworthy. Before `COG-001` is activated, its planning context must come from a verified canonical read port over `CanonicalMemoryRepository` plus `GenesisUltraRuntimeIdentityRepository` or an equivalent committed identity projection.

The functional migration must remove these production dependencies from `CognitiveMigrationRepository`:

- `loadGenesisCore()`;
- `loadLocalIdentity()`;
- `getLivingMemorySnapshot()` when it is backed by legacy state;
- `loadMemoryContext()` over `memory_events`.

No compatibility write to `genesis_core`, `local_instance_identity`, or `memory_events` may be introduced to unblock the protocol.

## Cognitive-migration mapping

### `COG-001` — propose

- Build the plan from verified canonical inputs belonging to the same `instanceId`.
- Sort and normalize canonical source hashes before digesting.
- Derive `migrationId`, `proposalId`, `operationId`, and proposal `eventId` deterministically from the plan schema, Instance, source hashes, canonical pre-snapshot hash, and payload digest.
- Stage a `cognitive_migration.proposed` operation.
- Ensure the proposal event exists canonically.
- Insert the visible `planned` `MigrationRecordEntity` and mark the operation committed in one local transaction.

### `COG-002` — approve

- Derive the approval operation from `migrationId`, the immutable planned-record digest, the current writer epoch, and the explicit approval action.
- Use the approval operation ID as the stable `approvalId`; do not encode current time.
- Stage and ensure `cognitive_migration.approved` before changing the migration record to `approved`.
- Approval is a bounded Body-resource permission and must not be represented as ownership or control over Morimil's will.

### `COG-003` — execute

- Require the committed approval receipt and exact planned-record digest.
- Stage an execution operation with deterministic `cognitive_migration.executed` event identity.
- Ensure or reuse the exact event.
- Audit the canonical chain after the receipt is available.
- In one local transaction, mark the migration `completed` or `failed` with the canonical event hash and audit notes, then mark the protocol operation `COMMITTED`.

### `COG-004` — rollback

- Require the committed execution receipt or the explicitly permitted pre-execution state.
- Stage an append-only compensation operation with deterministic `cognitive_migration.rollback` event identity.
- Ensure or reuse the exact rollback event.
- Mark the migration `rolled_back` with the receipt and mark the protocol operation `COMMITTED` in one local transaction.
- Recovery must never append a second rollback event for the same operation.

## Recovery and concurrency

Startup recovery runs after committed Genesis Ultra identity and canonical-memory verification, but before normal mutation paths. New protected mutations first perform bounded recovery for older operations of the same Instance.

Recovery must:

- process a bounded number of rows in deterministic order;
- use a process mutex only as an optimization, never as the durability mechanism;
- reject operations from another `instanceId`, stale `writerEpoch`, or unauthorized `writerBodyId`;
- leave retryable failures recoverable with a typed code and incremented `attemptCount`;
- stop normal mutation if any relevant operation is `BLOCKED`;
- return counts for staged, pending canonical, canonical committed, pending local, committed, blocked, and failed attempts.

A unique deterministic `operationId` is the primary concurrency guard. Owner-specific uniqueness constraints must additionally prevent duplicate visible records or two active transitions for the same subject. Saga parents must also prevent two active child graphs for the same parent subject and revision.

## Kill-test matrix

Each migrated owner must prove recovery at these cuts on API 30 and API 35:

1. before staging;
2. after `STAGED` and before canonical dispatch;
3. during `PENDING_CANONICAL` before append;
4. after canonical append but before receipt persistence;
5. after `CANONICAL_COMMITTED` and before local finalization;
6. during the local finalization transaction;
7. after `COMMITTED` followed by full replay;
8. same `eventId` with conflicting content or provenance;
9. stale writer epoch after Body succession metadata changes;
10. repeated user action producing the same logical operation;
11. for saga owners, death after each child commit and before parent completion.

Closure requires zero duplicate canonical events, zero duplicate visible owner rows, no visible state without canonical evidence, and deterministic replay of the original receipt. Saga closure additionally requires no completed parent with a missing or conflicting child receipt.

## Error classification

Retryable examples:

- temporary database unavailability;
- interrupted canonical append with no conflicting verified event;
- process death before local finalization;
- an incomplete but internally consistent saga child set.

Permanent `BLOCKED` examples:

- deterministic ID collision with different payload;
- canonical event or provenance mismatch;
- wrong `instanceId`;
- stale or unauthorized writer epoch;
- conflicting local state that cannot be proven equivalent;
- missing or conflicting required saga child receipt;
- unsupported operation or payload schema.

Raw exception messages are diagnostic input, not stable protocol states. Persist bounded typed codes and keep sensitive content out of logs and issues.

Execution-path telemetry such as whether `ensureCommitted` appended or reused an existing
event is not owner state. It must not alter a content-addressed local result or digest.
Planning identity must depend only on eligible selected source evidence; excluded protocol
events may advance the audited canonical tip but cannot create a new logical proposal.

## Implementation sequence after STOP

The first functional PR is isolated to the common journal, its coordinator/commit-port contracts, Room migration, recovery tests, and `COG-001` through `COG-004`. It must not migrate orchestration, agents, bootstrap, recalls, RestCycle, or ProjectVault.

Required evidence before merge:

- Room migration test for the journal;
- deterministic identity unit tests;
- exact-match and conflict tests for canonical ensure semantics;
- owner finalization idempotency tests;
- startup recovery tests;
- kill tests for every applicable cut above on API 30 and API 35;
- architecture tests proving no new legacy identity or memory reads;
- all required CI checks and SBOM green on the exact head SHA.

F3.3 legacy removal does not begin until every F3.2 owner has a recorded disposition and its required kill tests are green.

## Rejected alternatives

### Keep sequential writes and mark failures afterward

Rejected because a canonical event may already exist while the local record reports the previous state, and a retry has no durable identity or receipt.

### Use wall-clock IDs

Rejected because restart creates a new logical identity and permits duplicate canonical evidence.

### Call `recordSystemMemoryEvent()` directly

Rejected for protected cross-database operations because it does not expose the deterministic ensure/reuse/conflict contract required by recovery.

### Copy the ProjectVault table for each owner

Rejected as the default because it duplicates protocol metadata and recovery logic. Owner-specific payload and finalization remain typed, but the coordination journal is common.

### Force every owner into one local finalization transaction

Rejected because workflows such as bootstrap touch both database files. They must use deterministic child operations or an explicit saga rather than claim cross-file ACID behavior.

### Move ProjectVault immediately

Deferred. Its current protocol is already protected and tested; changing it before the common journal is proven increases risk without closing a current failure window.

### Pretend two Room databases share one transaction

Rejected because no process mutex or coroutine scope creates cross-file ACID semantics.

## Consequences

Positive:

- deterministic recovery after process death;
- exact canonical evidence for every authoritative transition;
- one reusable state machine and receipt contract;
- explicit writer-epoch enforcement for future Body succession;
- no visible partial cognitive-migration state;
- an explicit saga path for wider multi-origin workflows;
- a bounded first owner before wider workflows.

Costs:

- one additional journal and Room migration;
- owner adapters and typed finalizers;
- parent/child metadata for saga owners;
- more explicit state and error handling;
- temporary coexistence with the ProjectVault-specific outbox until a later decision;
- COG-001 remains functionally blocked until its canonical read path replaces legacy inputs.
