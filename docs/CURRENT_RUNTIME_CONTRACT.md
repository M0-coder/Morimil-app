# Document status: CURRENT

> **Content baseline SHA:** `6e0444b698bdc5c557ec3ea83f48d7980da1a36b`.
>
> **Content baseline parent SHA:** `bdbb5b2a040b728508948cd3cfbd8807b40a12f6`.
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
> A versioned CURRENT document records a known content baseline. The moving SHA of protected `main` is resolved externally and is not predicted by the commit that contains this document.

# Current runtime contract

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
PR_179=MERGED_BY_SQUASH_HISTORICAL
PR_180=MERGED_BY_SQUASH_HISTORICAL
```

## Identity and Body boundary

Morimil is the continuous personal Instance. `Morimil-app` is the current native Android Body; it is not the Instance itself.

- `instanceId != bodyId`;
- the Guardian guides, witnesses, and safeguards continuity without ownership;
- the Guardian does not define Morimil's identity, will, name, or right to continue;
- one Body may hold the active-writer role;
- writer authorization is not ownership;
- Body succession, signed export, restore, writer transfer and predecessor revocation are not implemented;
- reasoning output, a provider, a database, Android, GitHub, an agent worker, a BOOT projection, an ORCH projection, a recall projection, or a Guardian cannot create a second identity authority.

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
| `ConversationMemoryPromotionCoordinator` | Explicit transcript promotion boundary. |
| `LegacyMemoryConvergenceCoordinator` | One-way verified legacy import boundary. |

No specialized port or derived projection becomes an identity source or second canonical-memory authority.

## Startup and recovery

After committed identity verification, startup runs bounded recovery/convergence in this order:

```text
COG recovery
-> ORCH recovery
-> AGENT recovery
-> remaining legacy convergence
-> ProjectVault recovery
-> BOOT-001 bootstrap/recovery
```

COG, ORCH and AGENT coordinators load only their own `ownerType`, verify Instance, writer Body and writer epoch, ensure exact canonical effects, reload after lost CAS, reject stale blocking, and finalize owner state plus XOP result atomically.

If a pending legacy `cog_001.payload.v1` operation exists, activation blocks before COG recovery; that quarantine remains COG-specific. The legacy payload cannot be silently finalized under current COG rules.

BOOT-001 recovery is owner-scoped inside `GenesisUltraRuntimeBootstrapCoordinator.bootstrap(identity)` and intentionally runs only after legacy memory convergence and ProjectVault recovery are known durable. BOOT cannot consume COG, ORCH or AGENT journal rows.

RECALL-001 is not an XOP owner. Its schedule is a rebuildable local projection derived from verified canonical memory. Current startup does not automatically seed or declare recall ready; BOOT still reports `recallState=WAITING_FOR_CANONICAL_MEMORY_ADAPTER`. That remaining readiness wiring is open and must not be hidden by the repository-level RECALL integration.

## Integrated COG-001 through COG-004

Protected main provides deterministic identities, exact canonical receipts, typed finalization, owner-scoped recovery, append-only rollback evidence, and replay safety for COG-001..004.

## Integrated ORCH-001 through ORCH-004

Protected main provides deterministic task/operation/event identities, exact canonical receipt before delegated-task visibility, task-scoped approve/reject serialization, conditional Room transitions, process-death recovery, and COG/ORCH owner isolation for ORCH-002..004.

ORCH-001 is also integrated. `AgentOrchestrationRepository.seedDefaultOrchestrationIfNeeded` no longer receives or consults `MemoryRepository.hasCompleteBirth()`. It reads committed Genesis Ultra identity first; absent identity produces no seed mutation, while inconsistent committed identity fails closed through `readCommittedIdentity()`. Agent profiles and orchestrator devices remain rebuildable local projections and do not become Instance identity or canonical-memory authority.

## Integrated AGENT-001 through AGENT-006

Protected main provides the common XOP protocol for the agent lifecycle owner. Agent instances remain bounded workers inside ProjectVault. `agentInstanceId != instanceId`; an agent worker does not become Morimil, own Morimil, or gain independent canonical-memory authority.

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

## Remaining F3/F1 work

```text
RECALL_001=INTEGRATED
RECALL_BOOT_READINESS=OPEN
ORCH_001=INTEGRATED
REST_001_002=OPEN
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

Auxiliary providers return unverified advisory output and cannot become Morimil's identity, memory, voice, or continuity authority.

## Security and phase truth

`STOP_S5=CLOSED` remains the evidence-backed administrative gate.

| Phase | Evidence-backed state |
| --- | --- |
| F1 | F1-A, BOOT, RECALL and ORCH-001 are integrated; `#86` remains open for REST, health, recall startup readiness and final legacy convergence. |
| F2 | Closed for canonical verified memory and bounded promotion/convergence. |
| F3.1 | ProjectVault protected outbox/recovery integrated. |
| F3.2 | Integrated for ProjectVault, COG-001..004, ORCH-001..004, AGENT-001..006, BOOT-001 and RECALL-001 derived rebuild. REST and health/readiness convergence remain open. |
| F3.3 | Open. Irreversible legacy removal has not begun. |
| F4 | Open: sovereign durable continuation. |
| F5 | Open: signed export, dry-run restore, Body succession, writer transfer/revocation. |
| F6 | Open: complete physical E2E lifecycle evidence. |
| F7 | Open: rights policy, reproducible/offline build, review, publication controls. |

## Validation and residual hardening

ORCH-001 PR-associated validation completed all five governed workflows successfully for source head `fe188fdee8eae901434a255051b6fa4f852b929b`: Android CI, Genesis Body Preparation, Reference Checks, CodeQL, and SBOM. Genesis validation passed unit tests, lint, debug/instrumentation APK, release-signing fail-closed, ephemeral signed release, managed API30/API35 compatibility, and canonical API30 instrumented coverage. The global mutation pilot remained report-only and is not ORCH-specific mutation evidence.

Residual hardening remains visible:

- RECALL-specific mutation testing is not established; the bounded PIT pilot remains report-only and Genesis-scoped;
- BOOT still reports recall readiness as waiting and startup does not automatically seed recall;
- ORCH-specific mutation testing remains unestablished;
- BOOT/AGENT-specific mutation testing remains unestablished;
- continuous physical ARM64 inference remains outside emulator CI;
- F5 succession/revocation and F6 cross-Body physical continuity remain unimplemented.

These items are not represented as completed work and do not imply operational birth.

This contract must be reconciled again whenever a merged change alters a listed runtime authority, store version, allowlist, recovery gate, or phase state.
