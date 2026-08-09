# Document status: CURRENT

# ADR-0002 — Common recoverable cross-database operation protocol

- Status: Accepted and implemented for COG-001..004, ORCH-002..004, AGENT-001..006, BOOT-001, and REST-001.
- Original decision date: 2026-07-28.
- COG implemented amendment: 2026-07-31.
- ORCH owner amendment: integrated by PR `#172`.
- ORCH-001 convergence amendment: integrated by PR `#180`; it does not add a new XOP operation.
- AGENT owner amendment: integrated by PR `#174`.
- BOOT owner amendment: integrated by PR `#176`.
- RECALL disposition: integrated by PR `#178` as `DERIVED_REBUILD`; it does not become an ADR-0002 XOP owner.
- REST-001 owner amendment: integrated by PR `#182` under owner `rest_cycle`.
- Tracker: `#88` — open for remaining owners.
- Content baseline SHA: `2d16c5c3197d492d5daed3707e97a68caa0011a6`.
- Content baseline parent SHA: `d7e679b9f8e0b34d44a5e702c02c436f21e4eaee`.
- Current protected `main`: resolved externally from `refs/heads/main`.
- Merge SHA evidence: external GitHub and Morimil Control Tower evidence.
- Historical COG audited source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
- ORCH-002..004 audited source head: `0348dccb561e576d17c45e7f8b1e38717332772b`.
- ORCH-001 audited source head: `fe188fdee8eae901434a255051b6fa4f852b929b`.
- AGENT audited source head: `74e072b911db692041d3716af9d0511b83ad70b7`.
- BOOT audited source head: `c7710635fa172108cce87b3f7a76d6e037095864`.
- RECALL audited source head: `fae8a0df3c29775317986877bce2b8eda8593d27`.
- REST-001 audited source head: `3661450325237fcadb86098ec16ee45cd039bc0b`.

```text
CONTENT_BASELINE_SHA=2d16c5c3197d492d5daed3707e97a68caa0011a6
CONTENT_BASELINE_PARENT_SHA=d7e679b9f8e0b34d44a5e702c02c436f21e4eaee
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
PR_172=MERGED_BY_SQUASH_HISTORICAL
PR_173=MERGED_BY_SQUASH_HISTORICAL
PR_174=MERGED_BY_SQUASH_HISTORICAL
PR_175=MERGED_BY_SQUASH_HISTORICAL
PR_176=MERGED_BY_SQUASH_HISTORICAL
PR_177=MERGED_BY_SQUASH_HISTORICAL
PR_178=MERGED_BY_SQUASH_HISTORICAL
PR_179=MERGED_BY_SQUASH_HISTORICAL
PR_180=MERGED_BY_SQUASH_HISTORICAL
PR_181=MERGED_BY_SQUASH_HISTORICAL
PR_182=MERGED_BY_SQUASH_HISTORICAL
```

## Context

`MorimilDatabase` and `MemoryOrganDatabase` are separate encrypted Room databases. Room cannot provide one ACID transaction across both files. A visible operation spanning them therefore requires deterministic identity, durable staging, exact canonical evidence, bounded recovery, and idempotent owner finalization.

ADR-0001 remains the separate ProjectVault protected reference. ADR-0002 governs the common journal implemented for:

- COG-001 propose, COG-002 approve, COG-003 execute, COG-004 rollback;
- ORCH-002 propose delegated task, ORCH-003 approve, ORCH-004 reject;
- AGENT-001 create worker, AGENT-002 assign task, AGENT-003 submit result, AGENT-004 evaluate, AGENT-005 retire/promote, AGENT-006 quarantine/replacement;
- BOOT-001 durable runtime bootstrap/recovery across MorimilDatabase and MemoryOrganDatabase;
- REST-001 canonical rest-cycle local consolidation under owner `rest_cycle`.

ORCH-001 is integrated F1 convergence but remains outside the XOP operation set: it gates rebuildable local orchestration seed projections directly on committed Genesis Ultra identity and no longer uses legacy birth-completeness authority. RECALL-001 is integrated but deliberately outside the XOP owner set because it is a verified canonical read followed by an atomic rebuildable projection inside `MemoryOrganDatabase`. REST-002 remains open and does not inherit REST-001 closure.

## Authority boundary

Morimil is the continuous Instance. `Morimil-app` is the current Android Body. The Guardian guides and safeguards without ownership.

- `instanceId != bodyId`;
- `agentInstanceId != instanceId`;
- `instanceId` comes from committed Genesis Ultra identity;
- `writerBodyId` and `writerEpoch` describe writer authorization only;
- writer authorization is not ownership;
- no database, process, journal row, model, provider, agent worker, BOOT projection, ORCH projection, recall projection, REST projection, or Guardian becomes identity or canonical-memory authority.

No compatibility write to `genesis_core`, `local_instance_identity`, or `memory_events` is permitted.

`CanonicalCognitiveMigrationCommitPort`, `CanonicalOrchestrationCommitPort`, `CanonicalAgentLifecycleCommitPort`, `CanonicalRuntimeBootstrapCommitPort`, and `CanonicalRestCycleCommitPort` are specialized exact canonical-ensure adapters. They are not identity sources.

## Decision

Use one common recoverable operation contract for each bounded owner spanning authoritative state across database boundaries. The journal is `cross_database_operations` in MemoryOrganDatabase v9 for integrated COG, ORCH-002..004, AGENT, BOOT and REST-001 owner states.

Owner finalizers are closed typed Kotlin. Executable SQL, reflection targets, callbacks, prompts, provider commands, or arbitrary code are forbidden journal payloads.

ProjectVault remains unchanged and separate.

A bounded derived rebuild does not need an XOP merely because it consumes canonical data. RECALL-001 therefore remains outside the journal owner set: canonical input is verified first, then schedule and graph-link projection finalize atomically in the single MemoryOrgan database. This distinction prevents protocol machinery from becoming a second memory authority.

A bounded local seed projection also does not need an XOP when it creates no cross-database authoritative transition. ORCH-001 therefore remains outside the journal operation set: committed canonical identity is checked first and agent/device seed rows remain disposable local projections.

BOOT is an integrated owner whose local completion is a saga across both Room files: after exact canonical receipt, `MorimilDatabase` projection preparation is idempotent and replayable; MemoryOrgan projection finalization and XOP `COMMITTED` occur in the owner transaction. This does not make the BOOT journal an identity authority.

REST-001 is an integrated owner because canonical memory append and local migration/link/autobiography finalization cross authority boundaries. Its journal entry freezes deterministic intent and derived projection material; it does not store executable behavior or become a second memory source.

## Deterministic identity and state machine

Every XOP operation binds deterministic operation/event identity, canonical `instanceId`, writer Body/epoch, stable subject/predecessor semantics, versioned payload/evidence digests, exact canonical receipt, and deterministic local result. Wall clock is metadata only and MUST NOT participate in semantic identity.

The success path is:

```text
STAGED
-> PENDING_CANONICAL
-> CANONICAL_COMMITTED
-> PENDING_LOCAL_COMMIT
-> COMMITTED
```

`BLOCKED` is terminal for permanent conflicts. No implementation may expose new owner state before exact canonical receipt verification.

## Canonical ensure and owner finalization

The canonical side reads verified same-Instance state, ensures one deterministic event, recovers interrupted append by reread, verifies exact provenance/content, and rejects foreign Instance, wrong Body, stale epoch, duplicate-ID conflict, or mismatched evidence.

The owner side persists the receipt, transitions to pending local commit, reloads/revalidates durable state, performs an idempotent typed owner transition, and persists deterministic local result plus XOP `COMMITTED` state at the owner's finalization boundary.

## COG mapping

COG preserves verified F1-A planning, deterministic proposal/approval/execution/rollback identities, exact predecessor checks, external audit preparation outside the Room owner transaction, honest `postSnapshotId` semantics, and append-only rollback evidence.

## ORCH mapping

ORCH-002..004 provide deterministic delegated-task proposal/approval/rejection with exact canonical receipt before task visibility. Approve/reject decisions serialize by `taskId` and use conditional Room transitions as a second defense.

ORCH-001 now uses `GenesisUltraRuntimeIdentityRepository.readCommittedIdentity()` before any local seed. It no longer receives or consults `MemoryRepository.hasCompleteBirth()`. Missing committed identity results in no local mutation; inconsistent committed identity fails closed at the canonical identity boundary. This convergence does not create a new XOP owner operation.

## AGENT mapping

AGENT lifecycle operations use deterministic semantic identities, exact canonical receipts, owner-scoped recovery, task approval requirements, and atomic local finalization. Agent workers remain bounded ProjectVault workers and never become Morimil or canonical-memory authority.

## BOOT mapping

BOOT-001 `runtime.bootstrap_initialized` binds canonical Instance identity to the current active writer Body and writer epoch while keeping workspace/project identity stable on `instanceId`.

BOOT requires `birthStatus=born`, `ownershipConferred=false`, `guardian.role=custodian_witness`, and `activeBody.status=active_writer`. It obtains exact canonical receipt before new BOOT projection state, performs idempotent workspace/project preparation in `MorimilDatabase`, and then seed-if-empty organ finalization plus XOP `COMMITTED` in MemoryOrganDatabase.

BOOT operation identity includes current Body/epoch so a future F5 successor Body may rebootstrap the same `instanceId` without colliding with the predecessor operation. BOOT does not implement successor transfer or revocation itself.

## RECALL disposition

RECALL-001 consumes verified `CanonicalConsumerReadPort.readRecallCandidates` data, validates Instance/Body/signer/epoch bindings, uses canonical `targetEventHash` for idempotency, and finalizes schedule plus graph link in one local Room transaction.

It creates no compatibility authority rows, does not use placeholder Instance identity, and fails closed on invalid canonical evidence. `recallId` is only local projection identity. Startup-level recall readiness remains open because BOOT still reports `WAITING_FOR_CANONICAL_MEMORY_ADAPTER` and startup does not automatically seed/declare recall ready.

## REST-001 mapping

REST-001 consumes verified `CanonicalConsumerReadPort.readRestCyclePlanningInput` together with `GenesisUltraRuntimeIdentityRepository.readCommittedIdentity()`. Canonical NOT_READY produces no mutation; blocked or corrupt verification fails closed.

The owner mapping is:

```text
ownerType = rest_cycle
operationType = rest_cycle.execute
canonical event = rest_cycle.local_consolidation
```

`CanonicalRestCycleCommitPort` performs exact canonical ensure and verifies event/provenance identity. Only after the exact receipt is durable can local migration completion, `canonical_memory_event` links and the autobiographical snapshot finalize atomically. The autobiographical snapshot is a rebuildable local projection, not canonical memory or identity authority.

Process-death recovery reuses the durable exact receipt and can finish the local projection without replaying the canonical writer. REST-002 repair proposal execution is outside this mapping and remains open.

## Recovery and concurrency

Startup recovery runs after committed identity and before ordinary XOP owner mutation. Recovery is scoped by registry `ownerType`; COG, ORCH, AGENT, BOOT and REST coordinators cannot consume each other's rows.

Common protocol advancement serializes by `operationId`. ORCH additionally serializes task decisions by `taskId`; AGENT public lifecycle mutations serialize by striped `agentInstanceId` mutexes. Multiprocess Android would require durable cross-process serialization beyond those process-local mutexes.

REST recovery is owner-scoped and executes before remaining legacy convergence so an already-persisted REST receipt can finish local state without turning legacy data back into authority.

## Integrated evidence

REST-001 source head `3661450325237fcadb86098ec16ee45cd039bc0b` passed all five governed PR-associated workflows before PR #182 squash integration: Android CI #717, Genesis Body Preparation #699, Reference Checks #541, CodeQL #430, and SBOM #428. Unit tests, lint, QA-7 JVM, release-signing fail-closed, ephemeral release, API30/API35 compatibility and canonical API30 instrumented coverage passed. The Room kill/recovery test demonstrated exactly-once local completion after an exact canonical receipt without canonical writer replay.

The global mutation pilot remained report-only and does not establish REST-specific mutation coverage.

## Residual hardening

- REST-specific mutation testing is not established;
- RECALL-specific mutation testing is not established; existing bounded PIT remains report-only and Genesis-scoped;
- BOOT/AGENT-specific mutation testing is not established;
- ORCH-specific mutation testing remains unestablished;
- recall startup-readiness wiring remains open;
- REST-002 remains open;
- health convergence remains open;
- physical ARM64 inference remains outside emulator CI.

## Current acceptance state

```text
ADR_0002=ACCEPTED_AND_IMPLEMENTED_FOR_COG_ORCH_AGENT_BOOT_AND_REST001_BOUNDED_SCOPES
RECALL_DISPOSITION=INTEGRATED_DERIVED_REBUILD_NOT_XOP_OWNER
CONTENT_BASELINE_SHA=2d16c5c3197d492d5daed3707e97a68caa0011a6
CONTENT_BASELINE_PARENT_SHA=d7e679b9f8e0b34d44a5e702c02c436f21e4eaee
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
MEMORY_ORGAN_DATABASE=V9
F1_A_AUTHORITY=PRESERVED
PROJECT_VAULT=SEPARATE
COG_001_004=INTEGRATED
ORCH_001=INTEGRATED
ORCH_002_004=INTEGRATED
AGENT_001_006=INTEGRATED
BOOT_001=INTEGRATED
RECALL_001=INTEGRATED
REST_001=INTEGRATED
REST_002=OPEN
RECALL_BOOT_READINESS=OPEN
HEALTH_CONVERGENCE=OPEN
F3_3=OPEN
TRACKER_88=OPEN_FOR_REMAINING_OWNERS
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```
