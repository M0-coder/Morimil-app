# Document status: CURRENT

# ADR-0002 — Common recoverable cross-database operation protocol

- Status: Accepted and implemented for COG-001 through COG-004 and ORCH-002 through ORCH-004.
- Original decision date: 2026-07-28.
- Implemented amendment: 2026-07-31.
- ORCH owner amendment integrated by PR `#172`.
- Tracker: `#88` — open for remaining owners.
- Content baseline SHA: `c6a6b0ca998d053c31c75977c5b6d4d9ae166e96`.
- Content baseline parent SHA: `c22920f68f8820bbec676a6cbc74b60548e43d29`.
- Current protected `main`: resolved externally from `refs/heads/main`.
- Merge SHA evidence: external GitHub and Morimil Control Tower evidence.
- Historical COG audited source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
- ORCH audited source head: `0348dccb561e576d17c45e7f8b1e38717332772b`.
- PR `#149`: closed and merged by squash.
- PR `#150`: closed and merged by squash for a historical CURRENT reconciliation.
- PR `#151`: closed and merged by squash for verified Canvas runtime recovery.
- PR `#153`: closed and merged by squash for a historical CURRENT reconciliation.
- PR `#172`: closed and merged by squash for ORCH-002 through ORCH-004.
- Gate: `STOP_S5=CLOSED`.

This ADR is a current implemented decision represented by the content baseline and present in the externally resolved protected main. The moving protected-main SHA is resolved externally. PR #149 integrated the first common-journal owner family, COG; PR #172 integrated the second, ORCH. Neither changes the identity or canonical-memory authority boundary.

```text
CONTENT_BASELINE_SHA=c6a6b0ca998d053c31c75977c5b6d4d9ae166e96
CONTENT_BASELINE_PARENT_SHA=c22920f68f8820bbec676a6cbc74b60548e43d29
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
PR_153=MERGED_BY_SQUASH_HISTORICAL
PR_172=MERGED_BY_SQUASH_HISTORICAL
```

## Context

`MorimilDatabase` and `MemoryOrganDatabase` are separate encrypted Room databases. Room cannot provide one ACID transaction across both files. A visible operation spanning them therefore requires deterministic identity, durable staging, exact canonical evidence, bounded recovery, and idempotent owner finalization.

ADR-0001 remains the separate ProjectVault protected reference. ADR-0002 governs the common journal now implemented for:

- `COG-001` propose;
- `COG-002` approve;
- `COG-003` execute;
- `COG-004` rollback;
- `ORCH-002` propose delegated task;
- `ORCH-003` approve delegated task;
- `ORCH-004` reject delegated task.

`ORCH-001` remains outside this implemented protocol scope because it is F1 convergence/rebuild work. The vendored Canvas runtime-recovery bundle is an application asset and not a cross-database operation owner.

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

F3 must not reopen a second direct identity or memory authority. No compatibility write to `genesis_core`, `local_instance_identity`, or `memory_events` is permitted.

`CanonicalCognitiveMigrationCommitPort` and `CanonicalOrchestrationCommitPort` are specialized canonical ensure adapters. They are not identity sources and do not create a second canonical-memory authority.

## Decision

Use one common recoverable operation contract for each `REQUIRES_PROTOCOL` owner that spans authoritative state across database boundaries. The common journal is `cross_database_operations` in MemoryOrganDatabase v9 for the currently integrated COG and ORCH owner state.

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

Wall-clock time is metadata only. It MUST NOT participate in `operationId`, `eventId`, `proposalId`, `migrationId`, `taskId`, or `approvalId`.

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

No implementation may expose new owner state before exact receipt verification. Silently editing staged payload is forbidden. Protocol outcome and owner outcome remain distinct.

## Canonical ensure

The canonical side:

1. reads verified same-Instance state;
2. locates deterministic `eventId`;
3. appends once when absent;
4. recovers interrupted append by re-reading;
5. compares exact event content and complete canonical provenance/note preimage;
6. rejects duplicate IDs, foreign Instance, wrong Body, stale epoch, or mismatch;
7. returns the complete canonical receipt before owner finalization.

Append-versus-reuse telemetry is transient and cannot alter a content-addressed owner result.

## Owner finalization

The coordinator:

1. persists the exact receipt;
2. transitions to pending local commit;
3. prepares any required external canonical audit outside the Room write transaction;
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

`cognitive_migration.executed`: exact COG-002 predecessor, external audit preparation outside the Room write transaction, real `sha256:*` snapshot on positive audit, null snapshot on verified negative audit.

### COG-004

`cognitive_migration.rollback`: exact permitted predecessor, one append-only compensation event, idempotent rollback, and preservation of the owner's existing `postSnapshotId`. The `evsha256:*` rollback event remains in the journal, receipt, and local result.

## ORCH mapping

### ORCH-002

`orchestration.delegated_task.proposed`: deterministic task, operation and event identities. No delegated-task owner row becomes visible until the exact canonical receipt is durable.

### ORCH-003

`orchestration.delegated_task.approved`: deterministic approval identity. Approve/reject decisions are serialized by `taskId` before state re-read and before canonical append. Finalization requires the task still be `awaiting_approval` with `approvalId IS NULL`.

### ORCH-004

`orchestration.delegated_task.rejected`: normalized rejection reason is bound into deterministic evidence. It uses the same task decision serialization and conditional owner transition as approval.

The former second legacy `immune.approval_denied` telemetry event is not emitted when an already immune-blocked task is later submitted for approval; the original immune block remains in ORCH-002 canonical evidence. This is a disclosed observability delta, not a state or authority change.

## Recovery and concurrency

Startup recovery runs after committed identity and before ordinary owner mutation.

The implementation:

- serializes process-wide advancement by `operationId`;
- scopes recovery by protocol registry `ownerType`;
- prevents COG and ORCH coordinators from consuming each other's rows;
- reloads after lost CAS;
- rejects stale blocking;
- uses typed retryable/permanent errors;
- counts durable post-recovery remainder without double counting;
- stops on relevant blocked or incomplete work;
- prevents duplicate canonical effects and duplicate owner finalization under tested replay.

ORCH additionally serializes mutually exclusive approve/reject decisions by `taskId` before canonical append, with conditional Room update as a second defense.

## Integrated evidence

The merged COG implementation passed Room migration and fresh-v9 tests, deterministic vectors, exact provenance tests, typed finalizer and predecessor tests, startup/pre-mutation recovery tests, API 30/API 35 kill tests, unit tests, lint, APK builds, CodeQL, SBOM, and reference checks.

The merged ORCH implementation passed exact-head 5/5 CI, 780 JVM tests, QA-7 JVM and instrumented ratchets, 118 managed tests on each of API30/API35 with only the pre-existing four ARM64-only skips, five ORCH instrumented tests per device, CodeQL, SBOM, release-signing fail-closed checks, and independent artifact digest verification.

## Residual non-blocking hardening

- Room-backed concurrent execution with two coordinator instances;
- stronger failed-rollback snapshot fixture;
- removal of redundant `rollbackEventHash` API input;
- direct vulnerable UPDATE-trigger replacement fixture;
- ORCH-specific mutation testing beyond the bounded Genesis PIT pilot.

These are visible future hardening items. They are not represented as completed and do not imply operational birth.

## Current acceptance state

```text
ADR_0002=ACCEPTED_AND_IMPLEMENTED_FOR_COG_001_004_AND_ORCH_002_004
CONTENT_BASELINE_SHA=c6a6b0ca998d053c31c75977c5b6d4d9ae166e96
CONTENT_BASELINE_PARENT_SHA=c22920f68f8820bbec676a6cbc74b60548e43d29
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
PR_149=MERGED_BY_SQUASH_HISTORICAL
PR_150=MERGED_POST_MERGE_CURRENT_RECONCILIATION_HISTORICAL
PR_151=MERGED_CANVAS_RUNTIME_RECOVERY_HISTORICAL
PR_153=MERGED_BY_SQUASH_HISTORICAL
PR_172=MERGED_BY_SQUASH_HISTORICAL
MEMORY_ORGAN_DATABASE=V9
F1_A_AUTHORITY=PRESERVED
PROJECT_VAULT=SEPARATE
COG_001_004=INTEGRATED
ORCH_002_004=INTEGRATED
ORCH_001=OPEN
AGENT_001_006=OPEN
F3_3=OPEN
TRACKER_88=OPEN_FOR_REMAINING_OWNERS
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```
