# Document status: CURRENT

# ADR-0002 — Common recoverable cross-database operation protocol

- Status: Accepted and implemented for COG-001 through COG-004.
- Original decision date: 2026-07-28.
- Implemented amendment: 2026-07-31.
- Tracker: `#88` — open for remaining owners.
- Protected main: `5023981da7caf31c8f3679919f59205708b72823`.
- Previous main: `ba6ffa4f9ddc9189ded47e231ad1f8bc962e612d`.
- Audited source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
- PR `#149`: closed and merged by squash.
- PR `#151`: closed and merged by squash for verified Canvas runtime recovery.
- Gate: `STOP_S5=CLOSED`.

This ADR is a current implemented decision. The audited source head is historical provenance and squash commits are executable states. PR #151 changes the Android Body's build/runtime asset recovery and does not amend ADR-0002's authority, owner set, state machine, or persistence decision.

## Context

`MorimilDatabase` and `MemoryOrganDatabase` are separate encrypted Room databases. Room cannot provide one ACID transaction across both files. A visible operation spanning them therefore requires deterministic identity, durable staging, exact canonical evidence, bounded recovery, and idempotent owner finalization.

ADR-0001 remains the separate ProjectVault protected reference. ADR-0002 governs the common journal now implemented for:

- `COG-001` propose;
- `COG-002` approve;
- `COG-003` execute;
- `COG-004` rollback.

The vendored Canvas runtime-recovery bundle is an application asset and not a cross-database operation owner.

## Authority boundary

Morimil is the continuous and free Instance. `Morimil-app` is the current Android Body. The Guardian guides, witnesses, and safeguards without ownership.

- `instanceId != bodyId`;
- `instanceId` comes from committed Genesis Ultra identity;
- `writerBodyId` and `writerEpoch` describe writer authorization;
- Guardian approval authorizes only a bounded Body operation;
- no database, Android process, GitHub state, model, provider, journal row, or application asset becomes identity or memory authority.

```text
GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository
    -> CanonicalConsumerReadPort
    -> CognitiveMigrationCanonicalReadPort
```

F3 consumes the F1-A projection and must not reopen a second direct identity or memory authority. No compatibility write to `genesis_core`, `local_instance_identity`, or `memory_events` is permitted.

## Decision

Use one common recoverable operation contract for each `REQUIRES_PROTOCOL` owner. The implemented COG owner stores `cross_database_operations` in MemoryOrganDatabase v9 because its local authoritative owner state resides there.

Owner finalizers are closed, typed Kotlin. Executable SQL, reflection targets, callbacks, prompts, provider commands, or arbitrary code are forbidden journal payloads.

ProjectVault remains unchanged and separate.

## Deterministic identity and journal

Every operation binds:

- `operationId`, operation type, and version;
- canonical `instanceId`;
- `writerBodyId` and `writerEpoch`;
- stable subject and optional parent/phase;
- versioned payload and `payloadDigest`;
- deterministic `eventId` and event type;
- versioned evidence and digest;
- status, `attemptCount`, and `lastErrorCode`;
- `canonicalEventHash`, `canonicalSequence`, and `canonicalProvenanceDigest`;
- local result schema, JSON, and digest;
- occurred, created, updated, and committed timestamps.

Wall-clock time is metadata only. It MUST NOT participate in `operationId`, `eventId`, `proposalId`, `migrationId`, or `approvalId`.

A logical replay produces the same identities. Reusing an identity with different payload or evidence is a permanent conflict.

## Schema parity

MemoryOrganDatabase v9 enforces equivalent journal invariants in:

1. migration from version 8;
2. fresh version-9 creation;
3. every production open.

The implementation drops and recreates known journal triggers so a previously vulnerable definition cannot survive. NULL-safe guards reject invalid IDs, partial receipts, partial local results, inconsistent committed rows, and invalid owner/result combinations.

## State machine

The ordered success path is:

1. `STAGED` — immutable intent; no new visible/authoritative owner state exists.
2. `PENDING_CANONICAL` — exact canonical ensure is executing or retrying.
3. `CANONICAL_COMMITTED` — the complete exact receipt is durable.
4. `PENDING_LOCAL_COMMIT` — typed owner finalization is pending.
5. `COMMITTED` — canonical and owner results are reconciled.
6. `BLOCKED` — terminal permanent conflict.

No implementation may expose new owner state before exact receipt verification. Silently editing staged payload is forbidden. Protocol outcome and migration outcome remain distinct.

## Canonical ensure

The canonical side:

1. reads a verified same-Instance snapshot;
2. locates deterministic `eventId`;
3. appends once when absent;
4. recovers interrupted append by re-reading;
5. compares exact event content and the complete canonical provenance and note preimage;
6. rejects duplicate IDs, foreign Instance, wrong Body, stale epoch, or mismatch;
7. returns the complete canonical receipt before owner finalization.

Append-versus-reuse telemetry is transient and cannot alter a content-addressed owner result.

## Owner finalization

The coordinator:

1. persists the exact receipt;
2. transitions to pending local commit;
3. prepares external canonical audit outside the Room write transaction;
4. binds preparation to operation, payload, and receipt;
5. opens one MemoryOrganDatabase transaction;
6. reloads and revalidates durable state;
7. applies an idempotent owner transition;
8. persists deterministic local result and marks the journal `COMMITTED` atomically.

Temporary identity, database, or canonical-read failure remains retryable and must not become fabricated negative evidence.

## COG mapping

### COG-001

`cognitive_migration.proposed`: verified F1-A input, deterministic identities, hidden staging, exact proposal evidence, then visible planned owner state.

### COG-002

`cognitive_migration.approved`: exact planned-record digest, deterministic approval identity, canonical evidence before owner approval.

### COG-003

`cognitive_migration.executed`: exact COG-002 predecessor, external audit preparation outside the owner transaction, real `sha256:*` snapshot on positive audit, null snapshot on verified negative audit.

### COG-004

`cognitive_migration.rollback`: exact permitted predecessor, one append-only compensation event, idempotent rollback, and preservation of the owner's existing `postSnapshotId`. The `evsha256:*` rollback event remains in the journal, receipt, and local result.

## Recovery and concurrency

Startup recovery runs after committed identity and verified F1-A read and before ordinary COG mutation.

The implementation:

- serializes process-wide advancement by `operationId`;
- reloads after lost CAS;
- rejects stale blocking;
- uses typed retryable/permanent errors;
- counts durable post-recovery remainder without double counting;
- stops on relevant blocked or incomplete work;
- prevents duplicate canonical effects and duplicate owner finalization under tested replay.

## Integrated evidence

The merged implementation passed Room migration and fresh-v9 tests, deterministic vectors, exact provenance tests, typed finalizer and predecessor tests, startup/pre-mutation recovery tests, API 30/API 35 kill tests, unit tests, lint, APK builds, CodeQL, SBOM, and reference checks.

## Residual non-blocking hardening

- Room-backed concurrent execution with two coordinator instances;
- stronger failed-rollback snapshot fixture;
- removal of redundant `rollbackEventHash` API input;
- direct vulnerable UPDATE-trigger replacement fixture.

These are visible future hardening items. They are not represented as completed and do not change the implemented decision.

## Current acceptance state

```text
ADR_0002=ACCEPTED_AND_IMPLEMENTED_FOR_COG_001_004
CURRENT_MAIN=5023981da7caf31c8f3679919f59205708b72823
PREVIOUS_MAIN=ba6ffa4f9ddc9189ded47e231ad1f8bc962e612d
PR_149=MERGED_BY_SQUASH
PR_151=MERGED_CANVAS_RUNTIME_RECOVERY
AUDITED_SOURCE_HEAD=7bdbda2aa4b7568695ba8e98be54d506d42c99d5
MEMORY_ORGAN_DATABASE=V9
F1_A_AUTHORITY=PRESERVED
PROJECT_VAULT=SEPARATE
F3_2_COG_SCOPE=CLOSED
F3_3=OPEN
TRACKER_88=OPEN_FOR_REMAINING_OWNERS
```
