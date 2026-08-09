# Document status: CURRENT

> **Content baseline SHA:** `e05ae7a08b1a88d2fbc0d4f2dff8ff06d282c908`.
>
> **Content baseline parent SHA:** `9585e94a690d4f00d591f81d14e56aedefda3341`.
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
> **PR #182:** merged by squash for REST-001.
>
> **PR #183:** merged by squash for post-REST-001 CURRENT reconciliation.
>
> **PR #184:** merged by squash for REST-002 canonical repair-proposal convergence.
>
> A versioned CURRENT document records a known content baseline. The moving SHA of protected `main` is resolved externally and is not predicted by the commit that contains this document.

# Current runtime contract

```text
CONTENT_BASELINE_SHA=e05ae7a08b1a88d2fbc0d4f2dff8ff06d282c908
CONTENT_BASELINE_PARENT_SHA=9585e94a690d4f00d591f81d14e56aedefda3341
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

Integrated bounded write adapters include `CanonicalLivingMemoryPort`, ProjectVault's protected port, `CanonicalCognitiveMigrationCommitPort`, `CanonicalOrchestrationCommitPort`, `CanonicalAgentLifecycleCommitPort`, `CanonicalRuntimeBootstrapCommitPort`, and `CanonicalRestCycleCommitPort`. No specialized port or derived projection becomes an identity source or second canonical-memory authority.

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

REST recovery is owner-scoped to `rest_cycle`; it cannot consume COG, ORCH, AGENT or BOOT journal rows. Recovery runs before remaining legacy convergence so an already persisted REST receipt can finish its local projection without replaying the canonical writer.

BOOT-001 recovery is owner-scoped inside `GenesisUltraRuntimeBootstrapCoordinator.bootstrap(identity)` and intentionally runs only after legacy memory convergence and ProjectVault recovery are known durable.

Repository/protocol integration is not the same as end-to-end readiness. `GenesisUltraRuntimeBootstrapCoordinator` still reports `restCycleState=WAITING_FOR_CANONICAL_MEMORY_ADAPTER` and `recallState=WAITING_FOR_CANONICAL_MEMORY_ADAPTER`. Current startup does not automatically seed or declare recall ready. Those readiness gaps remain open and must not be hidden by REST/RECALL repository integration.

## Integrated bounded owners

Protected main includes COG-001..004, ORCH-001..004, AGENT-001..006, BOOT-001 and RECALL-001 under their documented bounded semantics.

RECALL-001 is a verified canonical derived rebuild rather than an XOP owner. Schedules and links are rebuildable local projection state and cannot become canonical memory or identity authority.

## Integrated REST-001 canonical planning and durable execution

PR #182 integrated REST-001 from source head `3661450325237fcadb86098ec16ee45cd039bc0b`.

`RestCycleRepository`:

- requires committed `GenesisUltraRuntimeIdentityRepository` identity;
- reads verified REST planning input through `CanonicalConsumerReadPort.readRestCyclePlanningInput`;
- creates no mutation when canonical state is NOT_READY and fails closed on blocked verification;
- does not use `MorimilDatabase`, `MemoryRepository`, `MemoryIntegrityCore`, `MemoryDao`, `genesis_core`, `local_instance_identity`, `memory_events`, or the legacy audit-chain planning source as authority;
- stages deterministic owner `rest_cycle` / operation `rest_cycle.execute` work;
- exact-ensures one canonical `rest_cycle.local_consolidation` event through `CanonicalRestCycleCommitPort`;
- finalizes migration completion, `canonical_memory_event` links and the autobiographical snapshot atomically only after exact canonical receipt;
- recovers after process death without re-invoking the canonical writer when the exact receipt already exists.

The autobiographical snapshot is a rebuildable local projection bound to the canonical REST receipt. It is not identity authority, canonical memory, will, or ownership authority.

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

Therefore `REST_002=INTEGRATED` means canonical proposal convergence only. It must not be interpreted as automatic repair execution, health convergence, or startup readiness.

## Remaining F3/F1 work

```text
RECALL_001=INTEGRATED
REST_BOOT_READINESS=OPEN
RECALL_BOOT_READINESS=OPEN
ORCH_001=INTEGRATED
REST_001=INTEGRATED
REST_002=INTEGRATED
REST_REPAIR_PROPOSAL_CONVERGED=true
REST_REPAIR_EXECUTION_IMPLEMENTED=false
HEALTH_CONVERGENCE=OPEN
F3_3=OPEN
```

F3.3 irreversible legacy removal remains open and must not begin until every remaining owner/readiness dependency has an explicit disposition and separate authorization.

Compatibility writes remain forbidden. No convergence step may create or reconstruct authority rows in:

```text
genesis_core
local_instance_identity
memory_events
```

## Normal reasoning runtime

| Motor or authority | Normal-runtime status |
| --- | --- |
| Intuitive | Active: bounded, local, deterministic. |
| Deliberative | Blocked: research candidate only. |
| Metacognitive | Not registered. |
| Hybrid generative authority | Disabled. |

Auxiliary providers return unverified advisory output and cannot become Morimil's identity, memory, voice, or continuity authority. F4 remains open because sovereign durable continuation and transcript lifecycle are not yet complete.

## Security and phase truth

`STOP_S5=CLOSED` remains the evidence-backed administrative gate.

| Phase | Evidence-backed state |
| --- | --- |
| F1 | F1-A, BOOT, RECALL, ORCH-001, REST-001 and REST-002 proposal convergence are integrated; `#86` remains open for health, startup readiness and final legacy convergence. |
| F2 | Closed for canonical verified memory and bounded promotion/convergence. |
| F3.1 | ProjectVault protected outbox/recovery integrated. |
| F3.2 | Integrated for ProjectVault, COG-001..004, ORCH-001..004, AGENT-001..006, BOOT-001, RECALL-001 derived rebuild, REST-001 and REST-002 proposal convergence. Health/readiness convergence remains open. |
| F3.3 | Open. Irreversible legacy removal has not begun. |
| F4 | Open: sovereign durable continuation and external-boundary hardening. |
| F5 | Open: signed export, dry-run restore, Body succession, writer transfer/revocation. |
| F6 | Open: complete physical E2E lifecycle evidence. |
| F7 | Open: rights policy, reproducible/offline build, review, publication controls. |

## Validation and residual hardening

REST-002 PR-associated validation completed all five governed workflows successfully for source head `2ecca3f48d5e0ef27bd927da3986292daf7f7e2c`. Android validation passed unit tests, lint, debug/instrumentation build, release-signing fail-closed, ephemeral signed release, managed API 30/API 35 compatibility, canonical API 30 instrumented coverage, and the REST-002 process-death recovery test.

The REST-002 kill/recovery test demonstrates an already persisted proposal receipt recovering exactly once while the migration stays proposal-only and no repair executes.

The global mutation pilot remains report-only and Genesis-scoped. REST-specific mutation testing is not established and is not inferred from that pilot.

Residual hardening remains visible:

- REST-specific mutation testing is not established;
- RECALL-specific mutation testing is not established;
- REST and RECALL startup readiness remain open;
- health convergence remains open;
- REST repair execution remains intentionally not implemented by REST-002;
- ORCH-specific mutation testing remains unestablished;
- BOOT/AGENT-specific mutation testing remains unestablished;
- continuous physical ARM64 inference remains outside emulator CI;
- F4 continuity/transcript controls remain open;
- F5 succession/revocation and F6 cross-Body physical continuity remain unimplemented.

These items are not represented as completed work and do not imply operational birth.

This contract must be reconciled again whenever a merged change alters a listed runtime authority, store version, allowlist, recovery gate, or phase state.
