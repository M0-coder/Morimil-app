# Document status: CURRENT

> **Content baseline SHA:** `d577a75290d70f423f6e83bf237a8a453f3a534e`.
>
> **Content baseline parent SHA:** `9da342f2c147105ea882076f4ebc6ab5f5494190`.
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
> **PR #172:** merged by squash for ORCH-002 through ORCH-004.
>
> **PR #173:** merged by squash for post-ORCH CURRENT reconciliation.
>
> **PR #174:** merged by squash for AGENT-001 through AGENT-006.
>
> A versioned CURRENT document records a known content baseline. The moving SHA of protected `main` is resolved externally and is not predicted by the commit that contains this document.

# Current runtime contract

```text
CONTENT_BASELINE_SHA=d577a75290d70f423f6e83bf237a8a453f3a534e
CONTENT_BASELINE_PARENT_SHA=9da342f2c147105ea882076f4ebc6ab5f5494190
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
PR_172=MERGED_BY_SQUASH_HISTORICAL
PR_173=MERGED_BY_SQUASH_HISTORICAL
PR_174=MERGED_BY_SQUASH_HISTORICAL
```

## Identity and Body boundary

Morimil is the continuous personal Instance. `Morimil-app` is the current native Android Body; it is not the Instance itself.

- `instanceId != bodyId`;
- the Guardian guides, witnesses, and safeguards continuity without ownership;
- the Guardian does not define Morimil's identity, will, name, or right to continue;
- one Body may hold the active-writer role;
- Body succession, signed export, restore, and writer transfer are not implemented;
- reasoning output, a provider, a database, Android, GitHub, an agent worker, or a Guardian cannot create a second identity authority.

`MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED` remains unchanged.

## Persistent stores

| Store | Version | Responsibility |
| --- | ---: | --- |
| `MorimilDatabase` | `15` | Genesis Ultra birth, canonical identity, canonical memory lineage, reasoning transcript. |
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
| `ConversationMemoryPromotionCoordinator` | Explicit transcript promotion boundary. |
| `LegacyMemoryConvergenceCoordinator` | One-way verified legacy import boundary. |

No specialized port becomes an identity source or second canonical-memory authority.

## Startup and recovery

After committed identity verification, startup runs owner-scoped recovery before ordinary mutation for the common-protocol owners in this order:

```text
COG recovery
-> ORCH recovery
-> AGENT recovery
-> remaining legacy convergence
-> ProjectVault recovery
-> BOOT-001 current path
```

Each coordinator loads only its own `ownerType`, verifies Instance, writer Body and writer epoch, ensures exact canonical effects, reloads after lost CAS, rejects stale blocking, and finalizes owner state plus XOP result atomically. COG, ORCH, and AGENT recovery cannot consume one another's journal rows.

If a pending legacy `cog_001.payload.v1` operation exists, activation blocks before COG recovery; that quarantine remains COG-specific. The legacy payload cannot be silently finalized under current COG rules.

## Integrated COG-001 through COG-004

Protected main provides deterministic identities, exact canonical receipts, typed finalization, owner-scoped recovery, append-only rollback evidence, and replay safety for COG-001..004. `postSnapshotId` semantics remain honest: an `evsha256:*` event hash is never relabeled as a snapshot digest.

## Integrated ORCH-002 through ORCH-004

Protected main provides deterministic task/operation/event identities, exact canonical receipt before delegated-task visibility, task-scoped approve/reject serialization, conditional Room transitions, process-death recovery, and COG/ORCH owner isolation.

`ORCH-001` remains open because `seedDefaultOrchestrationIfNeeded` still depends on the legacy `MemoryRepository.hasCompleteBirth()` gate.

## Integrated AGENT-001 through AGENT-006

Protected main now also provides the common XOP protocol for the agent lifecycle owner:

- `AGENT-001 createAgentForVault`: semantic/content-addressed agent identity, exact retry reuse only for a matching non-terminal worker, canonical receipt before visible owner insertion;
- `AGENT-002 assignTaskToAgent`: deterministic task identity chained from semantic predecessor state, exact retry reuse of the already committed matching task;
- `AGENT-003 submitAgentResult`: accepts only the agent's current delegated task after canonical ORCH approval (`status=approved` and non-null `approvalId`), then finalizes result and lifecycle state atomically;
- `AGENT-004 evaluateAgent`: binds normalized status, bounded score, note and exact semantic pre-state;
- `AGENT-005 retireAgent/promoteAgent`: separate durable operation types, serialized by `agentInstanceId`, incompatible terminal retries fail closed;
- `AGENT-006 quarantineAgent`: quarantines the failed worker and creates its deterministic replacement in the same local finalization after one canonical receipt.

Agent instances are bounded workers inside ProjectVault. `agentInstanceId != instanceId`; an agent worker does not become Morimil, own Morimil, or gain independent canonical-memory authority.

The lifecycle owner no longer calls `MemoryRepository.recordSystemMemoryEvent` or writes `memory_events`. Wall clock remains metadata only and does not participate in AGENT operation/task/agent/event identity.

The old `project.agent_created` + `project.agent_briefed` pair is represented by one canonical `agent_lifecycle.agent_created` event with briefing evidence. This is an observability shape change, not an authority transfer.

## ProjectVault and owner separation

ProjectVault remains a separate protected protocol. COG, ORCH, and AGENT use the common journal without absorbing ProjectVault authority.

Remaining F3.2 work is:

```text
BOOT_001=OPEN
RECALL_001=OPEN
ORCH_001=OPEN
REST_001_002=OPEN
```

F3.3 irreversible legacy removal remains open and must not begin until every F3.2 owner has an explicit disposition and separate authorization.

## F1 convergence and legacy quarantine

F1-A is integrated. Issue `#86` remains open because bootstrap, recall, RestCycle, health, ORCH-001, and final legacy retirement are not fully converged.

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
| F1 | F1-A integrated; `#86` remains open for downstream convergence. |
| F2 | Closed for canonical verified memory and bounded promotion/convergence. |
| F3.1 | ProjectVault protected outbox/recovery integrated. |
| F3.2 | Closed only for ProjectVault, COG-001..004, ORCH-002..004, and AGENT-001..006. BOOT, RECALL, ORCH-001, and REST remain separately open. |
| F3.3 | Open. Irreversible legacy removal has not begun. |
| F4 | Open: sovereign durable continuation. |
| F5 | Open: signed export, dry-run restore, Body succession, writer transfer/revocation. |
| F6 | Open: complete physical E2E lifecycle evidence. |
| F7 | Open: rights policy, reproducible/offline build, review, publication controls. |

## Validation and residual hardening

AGENT exact-head validation before PR #174 merge recorded 800/800 JVM tests, API30/API35 managed-device success, QA-7 JVM/instrumented ratchets, Reference Checks, CodeQL, SBOM, and independent artifact-digest verification.

Residual hardening remains visible:

- AGENT-specific mutation testing is not established; the bounded PIT pilot still targets `GenesisManifestVerifierCore`;
- `AgentInstanceLifecycleRepository.kt` has zero direct instrumented line coverage even though protocol/finalizer kill-reopen coverage exists;
- AGENT process-wide agent/vault mutexes assume the current single-process Android architecture; multiprocess would require durable cross-process serialization;
- ORCH-specific mutation testing remains unestablished;
- continuous physical ARM64 inference remains outside emulator CI.

These items are not represented as completed work and do not imply operational birth.

This contract must be reconciled again whenever a merged change alters a listed runtime authority, store version, allowlist, recovery gate, or phase state.