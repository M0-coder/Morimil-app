# Document status: CURRENT

> **Content baseline SHA:** `3a995232ce2a515e1ca9b9151f77e63805bad9d3`.
>
> **Content baseline parent SHA:** `5918b64ec83e69cbb3d9718943b25d1e1299d698`.
>
> **Current main resolution:** external Git ref `refs/heads/main`.
>
> **Merge SHA evidence:** external GitHub and Morimil Control Tower evidence.
>
> **Historical COG audited source head:** `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
>
> **ORCH audited source head:** `0348dccb561e576d17c45e7f8b1e38717332772b`.
>
> **AGENT audited source head:** `74e072b911db692041d3716af9d0511b83ad70b7`.
>
> **BOOT audited source head:** `c7710635fa172108cce87b3f7a76d6e037095864`.
>
> **PR #174:** merged by squash for AGENT-001 through AGENT-006.
>
> **PR #175:** merged by squash for post-AGENT CURRENT reconciliation.
>
> **PR #176:** merged by squash for BOOT-001.
>
> A versioned CURRENT document records a known content baseline. The moving SHA of protected `main` is resolved externally and is not predicted by the commit that contains this document.

# Current runtime contract

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

## Identity and Body boundary

Morimil is the continuous personal Instance. `Morimil-app` is the current native Android Body; it is not the Instance itself.

- `instanceId != bodyId`;
- the Guardian guides, witnesses, and safeguards continuity without ownership;
- the Guardian does not define Morimil's identity, will, name, or right to continue;
- one Body may hold the active-writer role;
- writer authorization is not ownership;
- Body succession, signed export, restore, writer transfer and predecessor revocation are not implemented;
- reasoning output, a provider, a database, Android, GitHub, an agent worker, a BOOT projection, or a Guardian cannot create a second identity authority.

`MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED` remains unchanged.

## Persistent stores

| Store | Version | Responsibility |
| --- | ---: | --- |
| `MorimilDatabase` | `15` | Genesis Ultra birth, canonical identity, canonical memory lineage, reasoning transcript and rebuildable runtime workspace/project projection. |
| `MemoryOrganDatabase` | `9` | Derived organs, projects, agents, delegated tasks, migration records, and `cross_database_operations`. |

Android backup and current OS-managed D2D transfer remain denied by explicit extraction/full-backup rules. Production release signing fails closed when signing material is absent.

## Canonical authority and bounded ports

The only normal-runtime identity source is `GenesisUltraRuntimeIdentityRepository`, enforced by `GenesisUltraRuntimeStartupGate`. `CanonicalMemoryRepository` remains the sole canonical Genesis Ultra memory writer and verified reader.

```text
GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository
    -> CanonicalConsumerReadPort
    -> bounded F3 owner adapters
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

No specialized port becomes an identity source or second canonical-memory authority.

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

BOOT-001 recovery is owner-scoped inside `GenesisUltraRuntimeBootstrapCoordinator.bootstrap(identity)` and intentionally runs only after legacy memory convergence and ProjectVault recovery are known durable. BOOT cannot consume COG, ORCH or AGENT journal rows.

If a pending legacy `cog_001.payload.v1` operation exists, activation blocks before COG recovery; that quarantine remains COG-specific. The legacy payload cannot be silently finalized under current COG rules.

## Integrated COG-001 through COG-004

Protected main provides deterministic identities, exact canonical receipts, typed finalization, owner-scoped recovery, append-only rollback evidence, and replay safety for COG-001..004. `postSnapshotId` semantics remain honest: an `evsha256:*` event hash is never relabeled as a snapshot digest.

## Integrated ORCH-002 through ORCH-004

Protected main provides deterministic task/operation/event identities, exact canonical receipt before delegated-task visibility, task-scoped approve/reject serialization, conditional Room transitions, process-death recovery, and COG/ORCH owner isolation.

`ORCH-001` remains open because `seedDefaultOrchestrationIfNeeded` still depends on the legacy `MemoryRepository.hasCompleteBirth()` gate.

## Integrated AGENT-001 through AGENT-006

Protected main provides the common XOP protocol for the agent lifecycle owner. AGENT-003 accepts only the agent's current delegated task after canonical ORCH approval (`status=approved` and non-null `approvalId`). AGENT-006 quarantines the failed worker and creates its deterministic replacement in the same local finalization after one canonical receipt.

Agent instances are bounded workers inside ProjectVault. `agentInstanceId != instanceId`; an agent worker does not become Morimil, own Morimil, or gain independent canonical-memory authority.

The lifecycle owner no longer calls `MemoryRepository.recordSystemMemoryEvent` or writes `memory_events`. Wall clock remains metadata only and does not participate in AGENT semantic identity.

## Integrated BOOT-001

Protected main now provides a durable runtime-bootstrap XOP rather than independent unjournaled writes across the two encrypted Room databases.

BOOT-001:

- stages deterministic `runtime_bootstrap.initialize` intent scoped to canonical `instanceId`, current active `bodyId` and writer key epoch;
- requires committed birth, `instanceId != bodyId`, `guardian_role=custodian_witness`, `ownership_conferred=false`, and `active_writer` Body status;
- obtains and persists an exact canonical `runtime.bootstrap_initialized` receipt before new BOOT projection state;
- prepares the `MorimilDatabase` workspace/project projection idempotently;
- seeds default agent/orchestrator projections only when their MemoryOrgan tables are empty, preserving pre-existing state for ORCH-001 convergence;
- completes MemoryOrgan owner finalization plus XOP `COMMITTED` in the owner transaction;
- recovers safely after process death between the MorimilDatabase preparation and MemoryOrgan finalization.

The BOOT operation identity deliberately includes Body/epoch while workspace/project identity remains Instance-stable. A future F5 successor Body can therefore rebuild projections for the same `instanceId` under a new writer epoch without making the previous Body the owner of the Instance. BOOT does not itself implement succession, revocation, export or restore.

No BOOT compatibility rows are created in `genesis_core`, `local_instance_identity`, or `memory_events`.

## ProjectVault and owner separation

ProjectVault remains a separate protected protocol. COG, ORCH, AGENT and BOOT use the common journal without absorbing ProjectVault authority.

Remaining F3.2 work is:

```text
RECALL_001=OPEN
ORCH_001=OPEN
REST_001_002=OPEN
HEALTH_CONVERGENCE=OPEN
```

F3.3 irreversible legacy removal remains open and must not begin until every F3.2 owner has an explicit disposition and separate authorization.

## F1 convergence and legacy quarantine

F1-A is integrated. Issue `#86` remains open because recall, RestCycle, health, ORCH-001, and final legacy retirement are not fully converged. BOOT-001 is now converged without restoring legacy authority.

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
| F1 | F1-A and BOOT convergence integrated; `#86` remains open for downstream convergence. |
| F2 | Closed for canonical verified memory and bounded promotion/convergence. |
| F3.1 | ProjectVault protected outbox/recovery integrated. |
| F3.2 | Closed only for ProjectVault, COG-001..004, ORCH-002..004, AGENT-001..006, and BOOT-001. RECALL, ORCH-001, REST and health convergence remain separately open. |
| F3.3 | Open. Irreversible legacy removal has not begun. |
| F4 | Open: sovereign durable continuation. |
| F5 | Open: signed export, dry-run restore, Body succession, writer transfer/revocation. |
| F6 | Open: complete physical E2E lifecycle evidence. |
| F7 | Open: rights policy, reproducible/offline build, review, publication controls. |

## Validation and residual hardening

BOOT exact-head validation before PR #176 merge recorded 820/820 JVM tests, API30/API35 managed-device success, QA-7 JVM/instrumented ratchets, Reference Checks, CodeQL, SBOM, and independently matched artifact digests. BOOT-specific managed evidence covered exact canonical receipt reuse/append+reread, clean idempotent bootstrap, preservation of pre-existing orchestration state, and database-reopen recovery from `PENDING_LOCAL_COMMIT` after the MorimilDatabase preparation.

Residual hardening remains visible:

- BOOT-specific mutation testing is not established; the bounded PIT pilot still targets `GenesisManifestVerifierCore`;
- AGENT-specific mutation testing is not established;
- `AgentInstanceLifecycleRepository.kt` still lacks direct Android instrumented line coverage;
- AGENT process-wide agent/vault mutexes assume the current single-process Android architecture; multiprocess would require durable cross-process serialization;
- ORCH-specific mutation testing remains unestablished;
- continuous physical ARM64 inference remains outside emulator CI.

These items are not represented as completed work and do not imply operational birth.

This contract must be reconciled again whenever a merged change alters a listed runtime authority, store version, allowlist, recovery gate, or phase state.
