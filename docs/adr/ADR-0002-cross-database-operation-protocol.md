# Document status: CURRENT

# ADR-0002 — Common recoverable cross-database operation protocol

- Status: Accepted and implemented for COG-001..004, ORCH-002..004, AGENT-001..006, BOOT-001, REST-001, and REST-002 proposal convergence.
- Original decision date: 2026-07-28.
- COG implemented amendment: 2026-07-31.
- ORCH owner amendment: integrated by PR `#172`.
- ORCH-001 convergence amendment: integrated by PR `#180`; it does not add a new XOP operation.
- AGENT owner amendment: integrated by PR `#174`.
- BOOT owner amendment: integrated by PR `#176`.
- RECALL disposition: integrated by PR `#178` as `DERIVED_REBUILD`; it does not become an ADR-0002 XOP owner.
- REST-001 owner amendment: integrated by PR `#182` under owner `rest_cycle`.
- REST-002 proposal amendment: integrated by PR `#184` under the same closed owner registry; repair execution is not implemented.
- Bootstrap-health disposition: integrated by PR `#187`; dependency-derived bootstrap health is not an XOP owner.
- REST-BOOT-001 readiness disposition: integrated by PR `#188`; the canonical read-only readiness probe is not an XOP owner and performs no REST mutation.
- Local Nervous System Health disposition: integrated by PR `#190` as a read-only derived observer of `CanonicalConsumerReadPort.readHealthInput`; it is not an XOP owner or memory writer.
- RECALL-BOOT-001 readiness disposition: integrated by PR `#191`; the canonical read-only recall probe is not an XOP owner and performs no recall projection mutation.
- Tracker: `#88` — open for full F1/F3.2 reaudit and later F3.3 work.
- Content baseline SHA: `c4b192b8f54b2422ce816dc3542d55adfd44510c`.
- Content baseline parent SHA: `9c7325e6f1a21d79b1c3fb58f0b5f81a828fc304`.
- Current protected `main`: resolved externally from `refs/heads/main`.
- Merge SHA evidence: external GitHub and Morimil Control Tower evidence.
- Historical COG audited source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
- ORCH-002..004 audited source head: `0348dccb561e576d17c45e7f8b1e38717332772b`.
- ORCH-001 audited source head: `fe188fdee8eae901434a255051b6fa4f852b929b`.
- AGENT audited source head: `74e072b911db692041d3716af9d0511b83ad70b7`.
- BOOT audited source head: `c7710635fa172108cce87b3f7a76d6e037095864`.
- RECALL audited source head: `fae8a0df3c29775317986877bce2b8eda8593d27`.
- REST-001 audited source head: `3661450325237fcadb86098ec16ee45cd039bc0b`.
- REST-002 audited source head: `2ecca3f48d5e0ef27bd927da3986292daf7f7e2c`.
- Bootstrap-health audited source head: `f1697227241459f316bd562756e15ae3ce02c90d`.
- REST-BOOT-001 audited source head: `dd7a92a011fd4c453775df6ec307638b05313ec9`.
- Health legacy-consumer convergence audited source head: `6735e2d1febccf7da560d026d6ddd88f6ad82845`.
- RECALL-BOOT-001 audited source head: `20d834e1d438fd5883a76e9b45bcf21860e7db42`.
- PR `#189`: merged by squash for post-REST-readiness/bootstrap-Health CURRENT reconciliation.
- PR `#190`: merged by squash for Local Nervous System Health legacy-consumer convergence.
- PR `#191`: merged by squash for RECALL-BOOT-001 canonical read-only startup readiness.

```text
CONTENT_BASELINE_SHA=c4b192b8f54b2422ce816dc3542d55adfd44510c
CONTENT_BASELINE_PARENT_SHA=9c7325e6f1a21d79b1c3fb58f0b5f81a828fc304
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
PR_183=MERGED_BY_SQUASH_HISTORICAL
PR_184=MERGED_BY_SQUASH_HISTORICAL
PR_186=MERGED_BY_SQUASH_HISTORICAL
PR_187=MERGED_BY_SQUASH_HISTORICAL
PR_188=MERGED_BY_SQUASH_HISTORICAL
PR_189=MERGED_BY_SQUASH_HISTORICAL
PR_190=MERGED_BY_SQUASH_HISTORICAL
PR_191=MERGED_BY_SQUASH_HISTORICAL
```

## Context

`MorimilDatabase` and `MemoryOrganDatabase` are separate encrypted Room databases. Room cannot provide one ACID transaction across both files. A visible operation spanning them therefore requires deterministic identity, durable staging, exact canonical evidence, bounded recovery, and idempotent owner finalization.

ADR-0001 remains the separate ProjectVault protected reference. ADR-0002 governs the common journal implemented for:

- COG-001 propose, COG-002 approve, COG-003 execute, COG-004 rollback;
- ORCH-002 propose delegated task, ORCH-003 approve, ORCH-004 reject;
- AGENT-001 create worker, AGENT-002 assign task, AGENT-003 submit result, AGENT-004 evaluate, AGENT-005 retire/promote, AGENT-006 quarantine/replacement;
- BOOT-001 durable runtime bootstrap/recovery across MorimilDatabase and MemoryOrganDatabase;
- REST-001 canonical rest-cycle local consolidation under owner `rest_cycle`;
- REST-002 canonical repair-proposal convergence under owner `rest_cycle`.

ORCH-001 is integrated F1 convergence but remains outside the XOP operation set: it gates rebuildable local orchestration seed projections directly on committed Genesis Ultra identity and no longer uses legacy birth-completeness authority. RECALL-001 is integrated but deliberately outside the XOP owner set because it is a verified canonical read followed by an atomic rebuildable projection inside `MemoryOrganDatabase`.

Bootstrap health derivation from PR #187, REST-BOOT-001 and RECALL-BOOT-001 are also outside the XOP owner set. PR #187 derives the bootstrap report's Health from existing dependency state and rejects inconsistent forged Health. REST-BOOT-001 performs a read-only canonical planning probe and validates the same REST Instance/Body/epoch/digest boundary before startup may report REST READY. RECALL-BOOT-001 performs a read-only canonical recall probe and validates Instance/Body/epoch/snapshot/candidate authority before startup may report RECALL READY. None creates a canonical event, journal operation, identity source, or owner finalization path.

Local Nervous System Health is likewise outside the XOP owner set. `LocalNervousSystemRepository.observeHealth` reads only `CanonicalConsumerReadPort.readHealthInput`, derives Health from verified living-memory evidence, and returns operational telemetry without persisting canonical or legacy memory. Its telemetry is explicitly non-authoritative (`memory_authority=false`, `canonical_memory_write=false`, `legacy_memory_event_write=false`).

## Authority boundary

Morimil is the continuous Instance. `Morimil-app` is the current Android Body. The Guardian guides and safeguards without ownership.

- `instanceId != bodyId`;
- `agentInstanceId != instanceId`;
- `instanceId` comes from committed Genesis Ultra identity;
- `writerBodyId` and `writerEpoch` describe writer authorization only;
- writer authorization is not ownership;
- no database, process, journal row, model, provider, agent worker, BOOT projection, ORCH projection, recall projection, REST projection, Health telemetry, or Guardian becomes identity or canonical-memory authority.

No new compatibility write to `genesis_core`, `local_instance_identity`, or `memory_events` is permitted. Local Nervous System Health and RECALL readiness do not read those stores as authority and have no memory writer.

`CanonicalCognitiveMigrationCommitPort`, `CanonicalOrchestrationCommitPort`, `CanonicalAgentLifecycleCommitPort`, `CanonicalRuntimeBootstrapCommitPort`, and `CanonicalRestCycleCommitPort` are specialized exact canonical-ensure adapters. They are not identity sources.

## Decision

Use one common recoverable operation contract for each bounded owner spanning authoritative state across database boundaries. The journal is `cross_database_operations` in MemoryOrganDatabase v9 for integrated COG, ORCH-002..004, AGENT, BOOT and REST owner states.

Owner finalizers are closed typed Kotlin. Executable SQL, reflection targets, callbacks, prompts, provider commands, or arbitrary code are forbidden journal payloads.

ProjectVault remains unchanged and separate.

A bounded derived rebuild does not need an XOP merely because it consumes canonical data. RECALL-001 therefore remains outside the journal owner set: canonical input is verified first, then schedule and graph-link projection finalize atomically in the single MemoryOrgan database. This distinction prevents protocol machinery from becoming a second memory authority.

A bounded local seed projection also does not need an XOP when it creates no cross-database authoritative transition. ORCH-001 therefore remains outside the journal operation set: committed canonical identity is checked first and agent/device seed rows remain disposable local projections.

A read-only readiness probe, derived bootstrap-health evaluation, or Local Nervous System observation likewise does not need an XOP. PR #187 consumes runtime dependency state only. REST-BOOT-001 consumes verified canonical REST planning input but performs no canonical append, recovery advancement, migration finalization or repair proposal mutation. RECALL-BOOT-001 consumes verified canonical recall candidates but performs no recall seeding, schedule/link projection mutation or canonical append. Local Nervous System consumes verified canonical Health input but performs no canonical append, legacy event write, or owner finalization. These dispositions are integrated without widening ADR-0002's owner registry.

BOOT is an integrated owner whose local completion is a saga across both Room files: after exact canonical receipt, `MorimilDatabase` projection preparation is idempotent and replayable; MemoryOrgan projection finalization and XOP `COMMITTED` occur in the owner transaction. This does not make the BOOT journal an identity authority.

REST-001 is an integrated owner because canonical memory append and local migration/link/autobiography finalization cross authority boundaries. Its journal entry freezes deterministic intent and derived projection material; it does not store executable behavior or become a second memory source.

REST-002 is an integrated proposal-only operation under the same closed owner registry. Its journal entry freezes deterministic repair-proposal intent and exact canonical evidence; local finalization records only an approval-required proposal. It does not execute the proposed repair and must retain `repair_execution=not_implemented`.

The REST owner mappings are:

```text
ownerType = rest_cycle
REST-001 operation = rest_cycle.execute
REST-001 canonical event = rest_cycle.local_consolidation
REST-002 operation = rest_cycle.propose_repair
REST-002 canonical event = memory.repair_proposed
```

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

It creates no compatibility authority rows, does not use placeholder Instance identity, and fails closed on invalid canonical evidence. `recallId` is only local projection identity.

RECALL-BOOT-001 adds a read-only readiness disposition over the same canonical read boundary. `RecallScheduleRepository.isBootstrapReady(identity)` accepts a verified empty candidate batch as READY, maps NOT_READY to waiting without mutation, fails closed on RETRYABLE/BLOCKED evidence, validates canonical Instance/Body/epoch/snapshot/candidate authority, and does not call `seedFromRecentMemoryIfNeeded` or create schedule/link projection state.

## Runtime readiness dispositions

PR #187 uses `GenesisUltraRuntimeHealthConvergence.evaluate(...)`: bootstrap Health is READY only when legacy memory is converged and both REST and RECALL subsystem states are READY. `GenesisUltraRuntimeBootstrapReport` rejects any Health state inconsistent with those dependencies. With REST-BOOT-001 and RECALL-BOOT-001 integrated, repository truth is `HEALTH_STATE=DEPENDENCY_DERIVED`: actual READY or WAITING status depends on verified runtime inputs and is not declared as a timeless constant.

Local Nervous System Health no longer contributes legacy authority to global Health. `LocalNervousSystemRepository.observeHealth` uses `CanonicalConsumerReadPort.readHealthInput`; NOT_READY/RETRYABLE cannot report healthy, BLOCKED is critical, and the resulting telemetry is operational-only. `HEALTH_CONVERGENCE=OPEN` remains required until the full F1/F3.2 protected-main closure reaudit occurs.

REST-BOOT-001 uses `RestCycleRepository.isBootstrapReady(identity)` plus `CanonicalConsumerReadPort.readRestCyclePlanningInput`. Canonical NOT_READY maps to waiting without mutation; RETRYABLE/BLOCKED evidence fails closed; a ready input must pass `requireCanonicalPlanning(identity, planning)`. The readiness path does not call `recoverBeforeMutation`, `protocol.execute`, migration-store finalization, or repair-store mutation.

RECALL-BOOT-001 uses `RecallScheduleRepository.isBootstrapReady(identity)` plus `CanonicalConsumerReadPort.readRecallCandidates`. The readiness path does not seed projection state or acquire canonical-memory write authority.

## REST-001 mapping

REST-001 consumes verified `CanonicalConsumerReadPort.readRestCyclePlanningInput` together with `GenesisUltraRuntimeIdentityRepository.readCommittedIdentity()`. Canonical NOT_READY produces no mutation; blocked or corrupt verification fails closed.

The owner mapping is:

```text
ownerType = rest_cycle
operationType = rest_cycle.execute
canonical event = rest_cycle.local_consolidation
```

`CanonicalRestCycleCommitPort` performs exact canonical ensure and verifies event/provenance identity. Only after the exact receipt is durable can local migration completion, `canonical_memory_event` links and the autobiographical snapshot finalize atomically. The autobiographical snapshot is a rebuildable local projection, not canonical memory or identity authority.

Process-death recovery reuses the durable exact receipt and can finish the local projection without replaying the canonical writer.

## REST-002 mapping

REST-002 uses the same verified canonical planning boundary and neutral `RestCycleSourceEvent` proposal inputs. `RestRepairProposalPlanner` does not consume legacy `MemoryEventEntity` authority.

The deterministic `rest_cycle.propose_repair` operation exact-ensures `memory.repair_proposed` through `CanonicalRestCycleCommitPort`. Local finalization requires the matching planned migration, keeps `approvalRequired=true`, `approvedByUser=false`, leaves `postSnapshotId=null`, and records `automatic_changes=false` plus `repair_execution=not_implemented`.

Recovery after process death may advance an already receipted proposal to XOP `COMMITTED` exactly once while the owner migration remains planned. It cannot perform repair execution, create a repaired snapshot, or silently convert approval state.

## Recovery and concurrency

Startup recovery runs after committed identity and before ordinary XOP owner mutation. Recovery is scoped by registry `ownerType`; COG, ORCH, AGENT, BOOT and REST coordinators cannot consume each other's rows.

Common protocol advancement serializes by `operationId`. ORCH additionally serializes task decisions by `taskId`; AGENT public lifecycle mutations serialize by striped `agentInstanceId` mutexes. Multiprocess Android would require durable cross-process serialization beyond those process-local mutexes.

REST-001 and REST-002 share owner `rest_cycle` but use distinct closed operation schemas and typed finalization branches. REST recovery is owner-scoped and executes before remaining legacy convergence so an already-persisted REST receipt can finish local state without turning legacy data back into authority.

## Integrated evidence

REST-001 source head `3661450325237fcadb86098ec16ee45cd039bc0b` passed all five governed PR-associated workflows before PR #182 squash integration: Android CI #717, Genesis Body Preparation #699, Reference Checks #541, CodeQL #430, and SBOM #428. Unit tests, lint, QA-7 JVM, release-signing fail-closed, ephemeral release, API30/API35 compatibility and canonical API30 instrumented coverage passed. The Room kill/recovery test demonstrated exactly-once local completion after an exact canonical receipt without canonical writer replay.

REST-002 source head `2ecca3f48d5e0ef27bd927da3986292daf7f7e2c` passed all five governed PR-associated workflows before PR #184 squash integration: Android CI #723, Genesis Body Preparation #703, Reference Checks #547, CodeQL #436, and SBOM #434. Android validation passed unit tests, lint, release-signing fail-closed, ephemeral signed release, API30/API35 managed compatibility and instrumentation. The Room kill/recovery test demonstrated exactly-once proposal recovery from an exact canonical receipt while repair execution remained absent.

PR #187 source head `f1697227241459f316bd562756e15ae3ce02c90d` passed all five governed PR-associated workflows before PR #187 squash integration: Android CI #732, Genesis Body Preparation #710, Reference Checks #556, CodeQL #445 and SBOM #443. This is evidence for bootstrap Health derivation, not for the later Local Nervous System convergence.

REST-BOOT-001 source head `dd7a92a011fd4c453775df6ec307638b05313ec9` passed all five governed PR-associated workflows before PR #188 squash integration: Android CI #738, Genesis Body Preparation #715, Reference Checks #562, CodeQL #451 and SBOM #449. Direct JVM tests cover READY, NOT_READY, RETRYABLE and BLOCKED readiness dispositions; managed API30/API35 validation passed while physical ARM64-only cases remain outside emulator proof.

Health legacy-consumer convergence source head `6735e2d1febccf7da560d026d6ddd88f6ad82845` passed Android CI #757, Genesis Body Preparation #732, Reference Checks #581, CodeQL #470 and SBOM #468. RECALL-BOOT-001 source head `20d834e1d438fd5883a76e9b45bcf21860e7db42` passed Android CI #759, Genesis Body Preparation #733, Reference Checks #583, CodeQL #472 and SBOM #470. RECALL managed-device evidence re-read canonical state after repository recreation without creating schedule/link projections.

The global mutation pilot remained report-only and does not establish Health-, REST- or RECALL-specific mutation coverage.

## Residual hardening

- full F1/F3.2 protected-main reaudit remains required before global Health convergence can be closed;
- Health-specific mutation testing is not established;
- `CanonicalHealthInput.recentVerifiedEventCount` is intentionally excluded from Health decisions until its metadata-only semantics are separately hardened;
- REST-specific mutation testing is not established;
- RECALL-specific mutation testing is not established; existing bounded PIT remains report-only and Genesis-scoped;
- BOOT/AGENT-specific mutation testing is not established;
- ORCH-specific mutation testing remains unestablished;
- REST repair execution remains deliberately unimplemented by REST-002;
- physical ARM64 inference remains outside emulator CI.

## Current acceptance state

```text
ADR_0002=ACCEPTED_AND_IMPLEMENTED_FOR_COG_ORCH_AGENT_BOOT_REST001_AND_REST002_PROPOSAL_BOUNDED_SCOPES
RECALL_DISPOSITION=INTEGRATED_DERIVED_REBUILD_NOT_XOP_OWNER
CONTENT_BASELINE_SHA=c4b192b8f54b2422ce816dc3542d55adfd44510c
CONTENT_BASELINE_PARENT_SHA=9c7325e6f1a21d79b1c3fb58f0b5f81a828fc304
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
REST_002=INTEGRATED
REST_REPAIR_PROPOSAL_CONVERGED=true
REST_REPAIR_EXECUTION_IMPLEMENTED=false
REST_BOOT_READINESS=INTEGRATED
RECALL_BOOT_READINESS=INTEGRATED
BOOTSTRAP_HEALTH_DERIVATION=INTEGRATED
HEALTH_LEGACY_CONSUMER_CONVERGENCE=INTEGRATED
HEALTH_CAN_READ_CANONICAL_MEMORY=true
HEALTH_CAN_WRITE_CANONICAL_MEMORY=false
HEALTH_CAN_WRITE_LEGACY_MEMORY_EVENTS=false
HEALTH_CONVERGENCE=OPEN
HEALTH_CONVERGED=false
HEALTH_STATE=DEPENDENCY_DERIVED
F1_F3_2_FULL_REAUDIT=REQUIRED
F3_3=OPEN
TRACKER_88=OPEN_FOR_REAUDIT_AND_LEGACY_RETIREMENT
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```