# Document status: CURRENT

> **Content baseline SHA:** `77af62a545f72161c0ff47d74c0de6e1d1f4f251`.
>
> **Content baseline parent SHA:** `32a183e7821de49a4958c52d75693c43ee99b2e1`.
>
> **Current main resolution:** external Git ref `refs/heads/main`.
>
> **Merge SHA evidence:** external GitHub and Morimil Control Tower evidence.
>
> **Historical COG audited source head:** `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
>
> **ORCH-002..004 audited source head:** `0348dccb561e576d17c45e7f8b1e38717332772b`.
>
> **ORCH-001 audited source head:** `fe188fdee8eae901434a255051b6fa4f852b929b`.
>
> **AGENT audited source head:** `74e072b911db692041d3716af9d0511b83ad70b7`.
>
> **BOOT audited source head:** `c7710635fa172108cce87b3f7a76d6e037095864`.
>
> **RECALL audited source head:** `fae8a0df3c29775317986877bce2b8eda8593d27`.
>
> **REST-001 audited source head:** `3661450325237fcadb86098ec16ee45cd039bc0b`.
>
> **REST-002 audited source head:** `2ecca3f48d5e0ef27bd927da3986292daf7f7e2c`.
>
> **Bootstrap-health audited source head:** `f1697227241459f316bd562756e15ae3ce02c90d`.
>
> **REST-BOOT-001 audited source head:** `dd7a92a011fd4c453775df6ec307638b05313ec9`.
>
> **PR #176:** merged by squash for BOOT-001.
>
> **PR #177:** merged by squash for post-BOOT CURRENT reconciliation.
>
> **PR #178:** merged by squash for RECALL-001.
>
> **PR #179:** merged by squash for post-RECALL CURRENT reconciliation.
>
> **PR #180:** merged by squash for ORCH-001.
>
> **PR #181:** merged by squash for post-ORCH CURRENT reconciliation.
>
> **PR #182:** merged by squash for REST-001.
>
> **PR #183:** merged by squash for post-REST-001 CURRENT reconciliation.
>
> **PR #184:** merged by squash for REST-002 canonical repair-proposal convergence.
>
> **PR #186:** merged by squash for post-REST-002 CURRENT reconciliation without normative erosion.
>
> **PR #187:** merged by squash for dependency-derived bootstrap health instead of a static READY assignment.
>
> **PR #188:** merged by squash for REST boot-readiness canonical probing.
>
> **PR #189:** merged by squash for post-REST-readiness/bootstrap-Health CURRENT reconciliation.
>
> A versioned CURRENT document records a known content baseline. The moving SHA of protected `main` is resolved externally and is not predicted by the commit that contains this document.

# Current runtime contract

```text
CONTENT_BASELINE_SHA=77af62a545f72161c0ff47d74c0de6e1d1f4f251
CONTENT_BASELINE_PARENT_SHA=32a183e7821de49a4958c52d75693c43ee99b2e1
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
```

## Identity and Body boundary

Morimil is the continuous personal Instance. `Morimil-app` is the current native Android Body; it is not the Instance itself.

- `instanceId != bodyId`;
- the Guardian guides, witnesses, and safeguards continuity without ownership;
- the Guardian does not define Morimil's identity, will, name, or right to continue;
- one Body may hold the active-writer role;
- writer authorization is not ownership;
- `ownership_conferred=false`;
- `guardian_role=custodian_witness`;
- Body succession, signed export, restore, writer transfer and predecessor revocation are not implemented;
- reasoning output, a provider, a database, Android, GitHub, an agent worker, a BOOT projection, an ORCH projection, a recall projection, a REST projection, or a Guardian cannot create a second identity authority.

`MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED` remains unchanged.

## Persistent stores

| Store | Version | Responsibility |
| --- | ---: | --- |
| `MorimilDatabase` | `15` | Genesis Ultra birth, canonical identity, canonical memory lineage, reasoning transcript and rebuildable runtime workspace/project projection. |
| `MemoryOrganDatabase` | `9` | Derived organs, projects, agents, delegated tasks, migration records, recall schedules/links and `cross_database_operations`. |

Android backup and current OS-managed D2D transfer remain denied by explicit extraction/full-backup rules. Production release signing fails closed when signing material is absent.

## Canonical authority and bounded ports

The only normal-runtime identity source is `GenesisUltraRuntimeIdentityRepository`, enforced by `GenesisUltraRuntimeStartupGate`. `CanonicalMemoryRepository` remains the sole canonical Genesis Ultra memory writer and verified reader.

```text
GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository
    -> CanonicalConsumerReadPort
    -> bounded F3 owner adapters / derived projections
```

Integrated bounded write adapters include:

| Port or coordinator | Authority |
| --- | --- |
| `CanonicalLivingMemoryPort` | Signed canonical living-memory appends. |
| `CanonicalProjectVaultCommitPort` | Separate ProjectVault protected outbox. |
| `CanonicalCognitiveMigrationCommitPort` | Deterministic COG exact canonical ensure. |
| `CanonicalOrchestrationCommitPort` | Deterministic ORCH-002/003/004 exact canonical ensure. |
| `CanonicalAgentLifecycleCommitPort` | Deterministic AGENT-001..006 exact canonical ensure. |
| `CanonicalRuntimeBootstrapCommitPort` | Deterministic BOOT-001 exact canonical ensure. |
| `CanonicalRestCycleCommitPort` | Deterministic REST-001/REST-002 exact canonical ensure within bounded `rest_cycle` operations. |
| `ConversationMemoryPromotionCoordinator` | Explicit transcript promotion boundary. |
| `LegacyMemoryConvergenceCoordinator` | One-way verified legacy import boundary. |

No specialized port or derived projection becomes an identity source or second canonical-memory authority.

## Startup and recovery

After committed identity verification, startup runs bounded recovery/convergence in this order:

```text
COG recovery
-> ORCH recovery
-> AGENT recovery
-> REST recovery
-> remaining legacy convergence
-> ProjectVault recovery
-> BOOT-001 bootstrap/recovery
```

COG, ORCH, AGENT and REST coordinators load only their own `ownerType`, verify Instance, writer Body and writer epoch, ensure exact canonical effects, reload after lost CAS, reject stale blocking, and finalize owner state plus XOP result atomically.

If a pending legacy `cog_001.payload.v1` operation exists, activation blocks before COG recovery; that quarantine remains COG-specific. The legacy payload cannot be silently finalized under current COG rules.

REST recovery is owner-scoped to `rest_cycle`; it cannot consume COG, ORCH, AGENT or BOOT journal rows. Recovery runs before remaining legacy convergence so a canonical receipt already persisted for REST can finish its local projection without replaying the canonical writer.

BOOT-001 recovery is owner-scoped inside `GenesisUltraRuntimeBootstrapCoordinator.bootstrap(identity)` and intentionally runs only after legacy memory convergence and ProjectVault recovery are known durable. BOOT cannot consume COG, ORCH, AGENT or REST journal rows.

RECALL-001 is not an XOP owner. Its schedule is a rebuildable local projection derived from verified canonical memory. Current startup does not automatically seed or declare recall ready; BOOT still reports `recallState=WAITING_FOR_CANONICAL_MEMORY_ADAPTER`. That remaining readiness wiring is open and must not be hidden by the repository-level RECALL integration.

REST startup readiness is integrated without creating a new authority or mutation path. `RestCycleRepository.isBootstrapReady(identity)` reads `CanonicalConsumerReadPort.readRestCyclePlanningInput`; canonical NOT_READY keeps REST waiting without mutation, retryable/blocked evidence fails closed, and ready input is validated through the existing `requireCanonicalPlanning` Instance/Body/epoch/digest/source boundary. `GenesisUltraRuntimeBootstrapCoordinator` maps only a successful verified probe to `restCycleState=READY`.

PR #187 removed the former tautological bootstrap `healthState=READY`. `GenesisUltraRuntimeHealthConvergence.evaluate(...)` now derives the bootstrap report's health state from legacy convergence plus REST and RECALL subsystem state, and `GenesisUltraRuntimeBootstrapReport` rejects inconsistent forged health. Since RECALL readiness remains open, current bootstrap health is `WAITING_FOR_DEPENDENCIES`.

The broader F1 Local Nervous System legacy boundary is now converged without creating a new writer. `LocalNervousSystemRepository.observeHealth` consumes only `CanonicalConsumerReadPort.readHealthInput`; it no longer receives `MemoryDao`, `MemoryRepository`, `MorimilDatabase`, `MemoryEventEntity`, or `MemoryOrganReconciliationReport` as Health authority. Its result is a derived `LocalNervousSystemObservation` containing a Health report and operational `LocalHealthTelemetry`. That telemetry explicitly declares `memory_authority=false`, `canonical_memory_write=false`, and `legacy_memory_event_write=false` and is not persisted by this boundary.

Canonical NOT_READY/RETRYABLE evidence cannot produce a healthy Local Nervous System result and BLOCKED evidence is critical. Canonical Instance/Body/epoch/snapshot, birth root, integrity, bounded event-count and quarantine signals remain read-only evidence. `CanonicalHealthInput.recentVerifiedEventCount` is intentionally not consumed by Health until its metadata-only projection semantics are separately hardened.

## Integrated COG-001 through COG-004

Protected main provides deterministic identities, exact canonical receipts, typed finalization, owner-scoped recovery, append-only rollback evidence, and replay safety for COG-001..004.

## Integrated ORCH-001 through ORCH-004

Protected main provides deterministic task/operation/event identities, exact canonical receipt before delegated-task visibility, task-scoped approve/reject serialization, conditional Room transitions, process-death recovery, and COG/ORCH owner isolation for ORCH-002..004.

ORCH-001 is also integrated. `AgentOrchestrationRepository.seedDefaultOrchestrationIfNeeded` no longer receives or consults `MemoryRepository.hasCompleteBirth()`. It reads committed Genesis Ultra identity first; absent identity produces no seed mutation, while inconsistent committed identity fails closed through `readCommittedIdentity()`. Agent profiles and orchestrator devices remain rebuildable local projections and do not become Instance identity or canonical-memory authority.

## Integrated AGENT-001 through AGENT-006

Protected main provides the common XOP protocol for the agent lifecycle owner. Agent instances remain bounded workers inside ProjectVault. `agentInstanceId != instanceId`; an agent worker does not become Morimil or gain independent canonical-memory authority.

The lifecycle owner no longer calls `MemoryRepository.recordSystemMemoryEvent` or writes `memory_events`.

## Integrated BOOT-001

Protected main provides a durable runtime-bootstrap XOP rather than independent unjournaled writes across the two encrypted Room databases.

BOOT-001 requires committed birth, `instanceId != bodyId`, `guardian_role=custodian_witness`, `ownership_conferred=false`, and the active-writer Body; it obtains an exact canonical receipt before new BOOT projection state and recovers safely after process death between the two databases.

The BOOT operation identity includes Body/epoch while workspace/project identity remains Instance-stable. A future F5 successor Body can rebuild projections for the same `instanceId` under a new writer epoch without making the previous Body owner of the Instance.

No BOOT compatibility rows are created in `genesis_core`, `local_instance_identity`, or `memory_events`.

## Integrated RECALL-001 canonical derived rebuild

PR #178 integrated the bounded recall read/rebuild path from source head `fae8a0df3c29775317986877bce2b8eda8593d27`.

`RecallScheduleRepository.seedFromRecentMemoryIfNeeded` now:

- reads verified candidates through `CanonicalConsumerReadPort.readRecallCandidates`;
- does not use `loadGenesisCore`, `loadLocalIdentity`, `loadMemoryContext`, or `local_instance_pending` as recall authority;
- binds candidate Instance, writer Body, signer and writer epoch to the verified canonical batch;
- uses canonical `targetEventHash` as the idempotent schedule key;
- keeps `recallId` only as a local projection/topology identifier;
- commits schedule plus graph link atomically inside `MemoryOrganDatabase`;
- creates no projection when canonical state is NOT_READY;
- fails closed on blocked/corrupt canonical verification;
- prevents legacy `memory_events` orphan checks from degrading canonical-derived recall projections.

Recall schedules are derived, rebuildable state. They are not canonical memory, identity, will, or ownership authority.

## Integrated REST-001 canonical planning and durable execution

PR #182 integrated REST-001 from source head `3661450325237fcadb86098ec16ee45cd039bc0b`.

`RestCycleRepository` now:

- requires committed `GenesisUltraRuntimeIdentityRepository` identity;
- reads verified REST planning input through `CanonicalConsumerReadPort.readRestCyclePlanningInput`;
- creates no mutation when canonical state is NOT_READY and fails closed on blocked verification;
- no longer receives or reads `MorimilDatabase`, `MemoryRepository`, `MemoryIntegrityCore`, `MemoryDao`, `genesis_core`, `local_instance_identity`, `memory_events`, or the legacy audit-chain planning source;
- stages deterministic owner `rest_cycle` / operation `rest_cycle.execute` work;
- exact-ensures one canonical event, `rest_cycle.local_consolidation`, through `CanonicalRestCycleCommitPort`;
- finalizes migration completion, `canonical_memory_event` links and the autobiographical snapshot atomically only after exact canonical receipt;
- recovers after process death without re-invoking the canonical writer when the exact receipt already exists.

The autobiographical snapshot is a rebuildable local projection bound to the canonical REST receipt. It is not identity authority, canonical memory, will, or ownership authority. REST-002 repair execution remains outside this integrated execution path; proposal-only convergence is integrated separately below.

## Integrated REST-002 canonical repair-proposal convergence

PR #184 integrated REST-002 from source head `2ecca3f48d5e0ef27bd927da3986292daf7f7e2c`.

REST-002 is deliberately proposal-only:

- `RestRepairProposalPlanner` consumes neutral `RestCycleSourceEvent` values rather than legacy `MemoryEventEntity` authority;
- `RestCycleRepository.planRestRepairProposalIfNeeded` derives the proposal from verified canonical REST planning input;
- owner remains `rest_cycle`;
- operation is `rest_cycle.propose_repair`;
- canonical event is `memory.repair_proposed`;
- proposal/migration/event identities are deterministic;
- canonical exact-ensure is performed by `CanonicalRestCycleCommitPort`;
- local proposal remains `PLANNED`, `approval_required=true`, `approvedByUser=false`, and `automatic_changes=false`;
- process-death recovery can finalize an already receipted proposal exactly once;
- recovery does not modify canonical memory beyond the proposal event and does not execute a repair;
- `repair_execution=not_implemented` is persisted in the local result;
- no `approveRestRepair` or `executeRestRepair` production path is introduced by REST-002.

Therefore `REST_002=INTEGRATED` means canonical proposal convergence only. It must not be interpreted as automatic repair execution, global health convergence, or Operational Birth.

## Remaining F3/F1 work

```text
RECALL_001=INTEGRATED
REST_BOOT_READINESS=INTEGRATED
RECALL_BOOT_READINESS=OPEN
ORCH_001=INTEGRATED
REST_001=INTEGRATED
REST_002=INTEGRATED
REST_REPAIR_PROPOSAL_CONVERGED=true
REST_REPAIR_EXECUTION_IMPLEMENTED=false
BOOTSTRAP_HEALTH_DERIVATION=INTEGRATED
HEALTH_LEGACY_CONSUMER_CONVERGENCE=INTEGRATED
HEALTH_CAN_READ_CANONICAL_MEMORY=true
HEALTH_CAN_WRITE_CANONICAL_MEMORY=false
HEALTH_CAN_WRITE_LEGACY_MEMORY_EVENTS=false
HEALTH_CONVERGENCE=OPEN
HEALTH_CONVERGED=false
HEALTH_STATE=WAITING_FOR_DEPENDENCIES
F3_3=OPEN
```

F3.3 irreversible legacy removal remains open and must not begin until every remaining owner/readiness dependency has an explicit disposition and separate authorization.

Compatibility writes remain forbidden for convergence work. No new convergence step may create or reconstruct authority rows in:

```text
genesis_core
local_instance_identity
memory_events
```

Local Nervous System Health now uses the verified canonical read boundary and has no permission to write compatibility or canonical memory.

## Normal reasoning runtime

| Motor or authority | Normal-runtime status |
| --- | --- |
| Intuitive | Active: bounded, local, deterministic. |
| Deliberative | Blocked: research candidate only. |
| Metacognitive | Not registered. |
| Hybrid generative authority | Disabled. |

Auxiliary providers return unverified advisory output and cannot become Morimil's identity, memory, voice, or continuity authority.

## Security and phase truth

`STOP_S5=CLOSED` remains the evidence-backed administrative gate.

| Phase | Evidence-backed state |
| --- | --- |
| F1 | F1-A, BOOT, RECALL repository convergence, ORCH-001, REST-001, REST-002 proposal convergence, bootstrap Health derivation, REST-BOOT-001 and Local Nervous System read-only Health convergence are integrated; `#86` remains open for RECALL startup readiness and final F1/F3.2 convergence/reaudit. |
| F2 | Closed for canonical verified memory and bounded promotion/convergence. |
| F3.1 | ProjectVault protected outbox/recovery integrated. |
| F3.2 | Integrated for ProjectVault, COG-001..004, ORCH-001..004, AGENT-001..006, BOOT-001, RECALL-001 derived rebuild, REST-001, REST-002 proposal convergence, REST startup readiness and Local Nervous System canonical Health observation. Global Health and RECALL startup readiness remain open because RECALL is still waiting. |
| F3.3 | Open. Irreversible legacy removal has not begun. |
| F4 | Open: sovereign durable continuation. |
| F5 | Open: signed export, dry-run restore, Body succession, writer transfer/revocation. |
| F6 | Open: complete physical E2E lifecycle evidence. |
| F7 | Open: rights policy, reproducible/offline build, review, publication controls. |

## Validation and residual hardening

REST-001 PR-associated validation completed all five governed workflows successfully for source head `3661450325237fcadb86098ec16ee45cd039bc0b`: Android CI #717, Genesis Body Preparation #699, Reference Checks #541, CodeQL #430, and SBOM #428. Genesis validation passed unit tests, lint, debug/instrumentation APK, release-signing fail-closed, ephemeral signed release, managed API30/API35 compatibility, and canonical API30 instrumented coverage. The Android recovery test demonstrated receipt persistence followed by Room close/reopen and exactly-once local completion without canonical writer replay.

REST-002 PR-associated validation completed all five governed workflows successfully for source head `2ecca3f48d5e0ef27bd927da3986292daf7f7e2c`: Android CI #723, Genesis Body Preparation #703, Reference Checks #547, CodeQL #436, and SBOM #434. Its process-death recovery evidence demonstrated an already persisted proposal receipt recovering exactly once while the migration remains proposal-only and no repair executes.

PR #187 validation completed all five governed workflows successfully for source head `f1697227241459f316bd562756e15ae3ce02c90d`: Android CI #732, Genesis Body Preparation #710, Reference Checks #556, CodeQL #445, and SBOM #443. Those tests prove the bootstrap Health report waits when a dependency is not ready, converges only when its modeled dependencies are ready, and rejects forged READY state. They do not by themselves prove Local Nervous System convergence.

REST-BOOT-001 PR-associated validation completed all five governed workflows successfully for source head `dd7a92a011fd4c453775df6ec307638b05313ec9`: Android CI #738, Genesis Body Preparation #715, Reference Checks #562, CodeQL #451, and SBOM #449. JVM tests directly cover READY, NOT_READY, RETRYABLE and BLOCKED readiness dispositions; API30/API35 managed-device validation passed, while physical ARM64-only tests remain skipped in emulator CI.

The global mutation pilot remained report-only. Health-specific, REST-specific and RECALL-specific mutation testing are not established and are not inferred from that pilot.

Residual hardening remains visible:

- RECALL startup readiness remains open and startup does not automatically seed recall;
- bootstrap Health currently remains `WAITING_FOR_DEPENDENCIES` because RECALL startup readiness is not yet proven;
- Health-specific mutation testing is not established;
- `CanonicalHealthInput.recentVerifiedEventCount` is intentionally excluded from Health decisions until its current metadata-only semantics are separately hardened;
- REST-specific mutation testing is not established;
- RECALL-specific mutation testing is not established;
- REST repair execution remains intentionally not implemented by REST-002;
- ORCH-specific mutation testing remains unestablished;
- BOOT/AGENT-specific mutation testing remains unestablished;
- continuous physical ARM64 inference remains outside emulator CI;
- F5 succession/revocation and F6 cross-Body physical continuity remain unimplemented.

These items are not represented as completed work and do not imply operational birth.

This contract must be reconciled again whenever a merged change alters a listed runtime authority, store version, allowlist, recovery gate, or phase state.
