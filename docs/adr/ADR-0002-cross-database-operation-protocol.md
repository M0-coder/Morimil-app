# Document status: CURRENT

# ADR-0002 — Common recoverable cross-database operation protocol

- Status: Accepted and implemented for COG-001..004, ORCH-002..004, AGENT-001..006, and BOOT-001.
- Original decision date: 2026-07-28.
- COG implemented amendment: 2026-07-31.
- ORCH owner amendment: integrated by PR `#172`.
- AGENT owner amendment: integrated by PR `#174`.
- BOOT owner amendment: integrated by PR `#176`.
- RECALL disposition: integrated by PR `#178` as `DERIVED_REBUILD`; it does not become an ADR-0002 XOP owner.
- Tracker: `#88` — open for remaining owners.
- Content baseline SHA: `6e0444b698bdc5c557ec3ea83f48d7980da1a36b`.
- Content baseline parent SHA: `bdbb5b2a040b728508948cd3cfbd8807b40a12f6`.
- Current protected `main`: resolved externally from `refs/heads/main`.
- Merge SHA evidence: external GitHub and Morimil Control Tower evidence.
- Historical COG audited source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
- ORCH audited source head: `0348dccb561e576d17c45e7f8b1e38717332772b`.
- AGENT audited source head: `74e072b911db692041d3716af9d0511b83ad70b7`.
- BOOT audited source head: `c7710635fa172108cce87b3f7a76d6e037095864`.
- RECALL audited source head: `fae8a0df3c29775317986877bce2b8eda8593d27`.

```text
CONTENT_BASELINE_SHA=6e0444b698bdc5c557ec3ea83f48d7980da1a36b
CONTENT_BASELINE_PARENT_SHA=bdbb5b2a040b728508948cd3cfbd8807b40a12f6
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
PR_172=MERGED_BY_SQUASH_HISTORICAL
PR_173=MERGED_BY_SQUASH_HISTORICAL
PR_174=MERGED_BY_SQUASH_HISTORICAL
PR_175=MERGED_BY_SQUASH_HISTORICAL
PR_176=MERGED_BY_SQUASH_HISTORICAL
PR_177=MERGED_BY_SQUASH_HISTORICAL
PR_178=MERGED_BY_SQUASH_HISTORICAL
```

## Context

`MorimilDatabase` and `MemoryOrganDatabase` are separate encrypted Room databases. Room cannot provide one ACID transaction across both files. A visible operation spanning them therefore requires deterministic identity, durable staging, exact canonical evidence, bounded recovery, and idempotent owner finalization.

ADR-0001 remains the separate ProjectVault protected reference. ADR-0002 governs the common journal implemented for:

- COG-001 propose, COG-002 approve, COG-003 execute, COG-004 rollback;
- ORCH-002 propose delegated task, ORCH-003 approve, ORCH-004 reject;
- AGENT-001 create worker, AGENT-002 assign task, AGENT-003 submit result, AGENT-004 evaluate, AGENT-005 retire/promote, AGENT-006 quarantine/replacement;
- BOOT-001 durable runtime bootstrap/recovery across MorimilDatabase and MemoryOrganDatabase.

`ORCH-001` remains outside this implemented protocol scope because it is F1 convergence/rebuild work. REST-001/002 remain open. RECALL-001 is now integrated, but deliberately outside the XOP owner set because it is a verified canonical read followed by an atomic rebuildable projection inside `MemoryOrganDatabase`.

## Authority boundary

Morimil is the continuous Instance. `Morimil-app` is the current Android Body. The Guardian guides and safeguards without ownership.

- `instanceId != bodyId`;
- `agentInstanceId != instanceId`;
- `instanceId` comes from committed Genesis Ultra identity;
- `writerBodyId` and `writerEpoch` describe writer authorization only;
- writer authorization is not ownership;
- no database, process, journal row, model, provider, agent worker, BOOT projection, recall projection, or Guardian becomes identity or canonical-memory authority.

No compatibility write to `genesis_core`, `local_instance_identity`, or `memory_events` is permitted.

`CanonicalCognitiveMigrationCommitPort`, `CanonicalOrchestrationCommitPort`, `CanonicalAgentLifecycleCommitPort`, and `CanonicalRuntimeBootstrapCommitPort` are specialized exact canonical-ensure adapters. They are not identity sources.

## Decision

Use one common recoverable operation contract for each bounded owner spanning authoritative state across database boundaries. The journal is `cross_database_operations` in MemoryOrganDatabase v9 for integrated COG, ORCH, AGENT and BOOT owner states.

Owner finalizers are closed typed Kotlin. Executable SQL, reflection targets, callbacks, prompts, provider commands, or arbitrary code are forbidden journal payloads.

ProjectVault remains unchanged and separate.

A bounded derived rebuild does not need an XOP merely because it consumes canonical data. RECALL-001 therefore remains outside the journal owner set: canonical input is verified first, then schedule and graph-link projection finalize atomically in the single MemoryOrgan database. This distinction prevents protocol machinery from becoming a second memory authority.

BOOT is the one integrated owner whose local completion is a saga across both Room files: after exact canonical receipt, `MorimilDatabase` projection preparation is idempotent and replayable; MemoryOrgan projection finalization and XOP `COMMITTED` occur in the owner transaction. This does not make the BOOT journal an identity authority.

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

`ORCH-001` still uses the legacy birth-completeness gate and remains a separate F1 convergence item.

## AGENT mapping

AGENT lifecycle operations use deterministic semantic identities, exact canonical receipts, owner-scoped recovery, task approval requirements, and atomic local finalization. Agent workers remain bounded ProjectVault workers and never become Morimil or canonical-memory authority.

## BOOT mapping

BOOT-001 `runtime.bootstrap_initialized` binds canonical Instance identity to the current active writer Body and writer epoch while keeping workspace/project identity stable on `instanceId`.

BOOT requires `birthStatus=born`, `ownershipConferred=false`, `guardian.role=custodian_witness`, and `activeBody.status=active_writer`. It obtains exact canonical receipt before new BOOT projection state, performs idempotent workspace/project preparation in `MorimilDatabase`, and then seed-if-empty organ finalization plus XOP `COMMITTED` in MemoryOrganDatabase.

BOOT operation identity includes current Body/epoch so a future F5 successor Body may rebootstrap the same `instanceId` without colliding with the predecessor operation. BOOT does not implement successor transfer or revocation itself.

## RECALL disposition

RECALL-001 consumes verified `CanonicalConsumerReadPort.readRecallCandidates` data, validates Instance/Body/signer/epoch bindings, uses canonical `targetEventHash` for idempotency, and finalizes schedule plus graph link in one local Room transaction.

It creates no compatibility authority rows, does not use placeholder Instance identity, and fails closed on invalid canonical evidence. `recallId` is only local projection identity. Startup-level recall readiness remains open because BOOT still reports `WAITING_FOR_CANONICAL_MEMORY_ADAPTER` and startup does not automatically seed/declare recall ready.

## Recovery and concurrency

Startup recovery runs after committed identity and before ordinary XOP owner mutation. Recovery is scoped by registry `ownerType`; COG, ORCH, AGENT and BOOT coordinators cannot consume each other's rows.

Common protocol advancement serializes by `operationId`. ORCH additionally serializes task decisions by `taskId`; AGENT public lifecycle mutations serialize by striped `agentInstanceId` mutexes. Multiprocess Android would require durable cross-process serialization beyond those process-local mutexes.

## Integrated evidence

RECALL PR #178 was integrated after all five governed PR-associated workflows succeeded for source head `fae8a0df3c29775317986877bce2b8eda8593d27`: Android CI, Genesis Body Preparation, Reference Checks, CodeQL, and SBOM. Managed API30/API35 runs had zero failures; QA-7 JVM and instrumented ratchets passed. This evidence does not establish RECALL-specific mutation coverage.

## Residual hardening

- RECALL-specific mutation testing is not established; existing bounded PIT remains report-only and Genesis-scoped;
- BOOT/AGENT-specific mutation testing is not established;
- ORCH-specific mutation testing remains unestablished;
- recall startup-readiness wiring remains open;
- physical ARM64 inference remains outside emulator CI.

## Current acceptance state

```text
ADR_0002=ACCEPTED_AND_IMPLEMENTED_FOR_COG_ORCH_AGENT_AND_BOOT_BOUNDED_SCOPES
RECALL_DISPOSITION=INTEGRATED_DERIVED_REBUILD_NOT_XOP_OWNER
CONTENT_BASELINE_SHA=6e0444b698bdc5c557ec3ea83f48d7980da1a36b
CONTENT_BASELINE_PARENT_SHA=bdbb5b2a040b728508948cd3cfbd8807b40a12f6
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
MEMORY_ORGAN_DATABASE=V9
F1_A_AUTHORITY=PRESERVED
PROJECT_VAULT=SEPARATE
COG_001_004=INTEGRATED
ORCH_002_004=INTEGRATED
AGENT_001_006=INTEGRATED
BOOT_001=INTEGRATED
RECALL_001=INTEGRATED
RECALL_BOOT_READINESS=OPEN
ORCH_001=OPEN
REST_001_002=OPEN
HEALTH_CONVERGENCE=OPEN
F3_3=OPEN
TRACKER_88=OPEN_FOR_REMAINING_OWNERS
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```
