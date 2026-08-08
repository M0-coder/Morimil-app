# Document status: CURRENT

# ADR-0002 — Common recoverable cross-database operation protocol

- Status: Accepted and implemented for COG-001..004, ORCH-002..004, AGENT-001..006, and BOOT-001.
- Original decision date: 2026-07-28.
- COG implemented amendment: 2026-07-31.
- ORCH owner amendment: integrated by PR `#172`.
- AGENT owner amendment: integrated by PR `#174`.
- BOOT owner amendment: integrated by PR `#176`.
- Tracker: `#88` — open for remaining owners.
- Content baseline SHA: `3a995232ce2a515e1ca9b9151f77e63805bad9d3`.
- Content baseline parent SHA: `5918b64ec83e69cbb3d9718943b25d1e1299d698`.
- Current protected `main`: resolved externally from `refs/heads/main`.
- Merge SHA evidence: external GitHub and Morimil Control Tower evidence.
- Historical COG audited source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
- ORCH audited source head: `0348dccb561e576d17c45e7f8b1e38717332772b`.
- AGENT audited source head: `74e072b911db692041d3716af9d0511b83ad70b7`.
- BOOT audited source head: `c7710635fa172108cce87b3f7a76d6e037095864`.

```text
CONTENT_BASELINE_SHA=3a995232ce2a515e1ca9b9151f77e63805bad9d3
CONTENT_BASELINE_PARENT_SHA=5918b64ec83e69cbb3d9718943b25d1e1299d698
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
PR_172=MERGED_BY_SQUASH_HISTORICAL
PR_173=MERGED_BY_SQUASH_HISTORICAL
PR_174=MERGED_BY_SQUASH_HISTORICAL
PR_175=MERGED_BY_SQUASH_HISTORICAL
PR_176=MERGED_BY_SQUASH_HISTORICAL
```

## Context

`MorimilDatabase` and `MemoryOrganDatabase` are separate encrypted Room databases. Room cannot provide one ACID transaction across both files. A visible operation spanning them therefore requires deterministic identity, durable staging, exact canonical evidence, bounded recovery, and idempotent owner finalization.

ADR-0001 remains the separate ProjectVault protected reference. ADR-0002 governs the common journal implemented for:

- COG-001 propose, COG-002 approve, COG-003 execute, COG-004 rollback;
- ORCH-002 propose delegated task, ORCH-003 approve, ORCH-004 reject;
- AGENT-001 create worker, AGENT-002 assign task, AGENT-003 submit result, AGENT-004 evaluate, AGENT-005 retire/promote, AGENT-006 quarantine/replacement;
- BOOT-001 durable runtime bootstrap/recovery across MorimilDatabase and MemoryOrganDatabase.

`ORCH-001` remains outside this implemented protocol scope because it is F1 convergence/rebuild work. RECALL-001 and REST-001/002 also remain open.

## Authority boundary

Morimil is the continuous Instance. `Morimil-app` is the current Android Body. The Guardian guides and safeguards without ownership.

- `instanceId != bodyId`;
- `agentInstanceId != instanceId`;
- `instanceId` comes from committed Genesis Ultra identity;
- `writerBodyId` and `writerEpoch` describe writer authorization only;
- writer authorization is not ownership;
- no database, process, journal row, model, provider, agent worker, BOOT projection, or Guardian becomes identity or canonical-memory authority.

No compatibility write to `genesis_core`, `local_instance_identity`, or `memory_events` is permitted.

`CanonicalCognitiveMigrationCommitPort`, `CanonicalOrchestrationCommitPort`, `CanonicalAgentLifecycleCommitPort`, and `CanonicalRuntimeBootstrapCommitPort` are specialized exact canonical-ensure adapters. They are not identity sources.

## Decision

Use one common recoverable operation contract for each bounded owner spanning authoritative state across database boundaries. The journal is `cross_database_operations` in MemoryOrganDatabase v9 for integrated COG, ORCH, AGENT and BOOT owner states.

Owner finalizers are closed typed Kotlin. Executable SQL, reflection targets, callbacks, prompts, provider commands, or arbitrary code are forbidden journal payloads.

ProjectVault remains unchanged and separate.

BOOT is the one integrated owner whose local completion is a saga across both Room files: after exact canonical receipt, `MorimilDatabase` projection preparation is idempotent and replayable; MemoryOrgan projection finalization and XOP `COMMITTED` occur in the owner transaction. This does not make the BOOT journal an identity authority.

## Deterministic identity and state machine

Every operation binds deterministic operation/event identity, canonical `instanceId`, writer Body/epoch, stable subject/predecessor semantics, versioned payload/evidence digests, exact canonical receipt, and deterministic local result. Wall clock is metadata only and MUST NOT participate in semantic identity.

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

The legacy second `immune.approval_denied` telemetry write is not recreated after an already immune-blocked task is submitted for approval; the original immune block remains in ORCH-002 canonical evidence.

## AGENT mapping

### AGENT-001 — create

`agent_lifecycle.agent_created` binds deterministic semantic worker identity to committed Instance, writer epoch, ProjectVault, template and normalized briefing. Exact public retry reuses only a matching non-terminal worker whose XOP is already committed. Terminal workers are not resurrected.

### AGENT-002 — assign

`agent_lifecycle.task_assigned` binds deterministic project-task identity to the worker's semantic predecessor plus normalized delegation plan. Exact retry returns the already committed matching current task.

### AGENT-003 — submit result

`agent_lifecycle.result_submitted` requires the current delegated task to belong to the worker and be canonically approved by ORCH (`status=approved`, non-null `approvalId`) before canonical evidence and local result finalization.

### AGENT-004 — evaluate

`agent_lifecycle.agent_evaluated` binds exact pre-state, normalized status/note and bounded quality score.

### AGENT-005 — retire/promote

Retirement and promotion are separate durable operation types even though the inventory groups them under AGENT-005. They serialize by `agentInstanceId`; exact applied retries are idempotent and incompatible terminal retries fail closed.

### AGENT-006 — quarantine

`agent_lifecycle.agent_quarantined` binds the failed worker, reason and deterministic replacement identity. Quarantine plus replacement creation are one local finalization after one canonical receipt, avoiding a second crash window.

The former `project.agent_created` + `project.agent_briefed` legacy observability pair is collapsed into one canonical creation event containing briefing evidence. This is an observability shape change, not an authority transfer.

## BOOT mapping

BOOT-001 `runtime.bootstrap_initialized` binds canonical Instance identity to the current active writer Body and writer epoch while keeping workspace/project identity stable on `instanceId`.

BOOT requires `birthStatus=born`, `ownershipConferred=false`, `guardian.role=custodian_witness`, and `activeBody.status=active_writer`. It obtains exact canonical receipt before new BOOT projection state, performs idempotent workspace/project preparation in `MorimilDatabase`, and then seed-if-empty organ finalization plus XOP `COMMITTED` in MemoryOrganDatabase.

If the process dies after the MorimilDatabase preparation, owner-scoped startup recovery repeats the preparation safely and completes the MemoryOrgan finalization without replaying an already durable canonical effect. Pre-existing agent/device rows are preserved for ORCH-001 convergence rather than overwritten by BOOT.

BOOT operation identity includes current Body/epoch so a future F5 successor Body may rebootstrap the same `instanceId` without colliding with the predecessor operation. BOOT does not implement the successor transfer or revocation itself.

## Recovery and concurrency

Startup recovery runs after committed identity and before ordinary owner mutation. Recovery is scoped by registry `ownerType`; COG, ORCH, AGENT and BOOT coordinators cannot consume each other's rows.

Common protocol advancement serializes by `operationId`. ORCH additionally serializes task decisions by `taskId`; AGENT public lifecycle mutations serialize by striped `agentInstanceId` mutexes and AGENT-001 uses a vault-scoped stripe for semantic retry detection/allocation. These AGENT mutexes assume the current single-process app; multiprocess Android would require durable cross-process serialization.

BOOT recovery is invoked inside `bootstrap(identity)` after legacy convergence and ProjectVault recovery, preserving startup ordering while closing the former two-database crash window.

## Integrated evidence

BOOT PR #176 was integrated only after exact-head 5/5 CI on `c7710635fa172108cce87b3f7a76d6e037095864`: Android CI, Genesis Body Preparation, Reference Checks, CodeQL, and SBOM all succeeded. Evidence included 820 JVM tests, 127 managed tests on each API30/API35 with 0 failures/errors and the same four physical-ARM64-only skips, BOOT kill/reopen recovery, QA-7 JVM/instrumented ratchets, and matched artifact digests.

## Residual hardening

- BOOT-specific mutation testing is not established; existing bounded PIT still targets GenesisManifestVerifierCore;
- AGENT-specific mutation testing is not established;
- `AgentInstanceLifecycleRepository.kt` lacks direct instrumented line coverage;
- AGENT process-local mutex assumption requires redesign if multiprocess is introduced;
- ORCH-specific mutation testing remains unestablished;
- physical ARM64 inference remains outside emulator CI.

## Current acceptance state

```text
ADR_0002=ACCEPTED_AND_IMPLEMENTED_FOR_COG_ORCH_AGENT_AND_BOOT_BOUNDED_SCOPES
CONTENT_BASELINE_SHA=3a995232ce2a515e1ca9b9151f77e63805bad9d3
CONTENT_BASELINE_PARENT_SHA=5918b64ec83e69cbb3d9718943b25d1e1299d698
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
MEMORY_ORGAN_DATABASE=V9
F1_A_AUTHORITY=PRESERVED
PROJECT_VAULT=SEPARATE
COG_001_004=INTEGRATED
ORCH_002_004=INTEGRATED
AGENT_001_006=INTEGRATED
BOOT_001=INTEGRATED
ORCH_001=OPEN
RECALL_001=OPEN
REST_001_002=OPEN
HEALTH_CONVERGENCE=OPEN
F3_3=OPEN
TRACKER_88=OPEN_FOR_REMAINING_OWNERS
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```
