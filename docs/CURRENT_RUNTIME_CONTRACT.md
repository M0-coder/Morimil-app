# Document status: CURRENT

> **Protected main:** `ba6ffa4f9ddc9189ded47e231ad1f8bc962e612d`.
>
> **Previous main:** `7e98d3345d7cc3fbf1983babd35b61ff5c523208`.
>
> **Audited source head:** `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
>
> **PR #149:** closed and merged by squash into protected `main`.
>
> The audited head is historical provenance; the squash commit is the executable repository state.

# Current runtime contract

## Identity and Body boundary

Morimil is the continuous personal Instance. `Morimil-app` is the current native Android Body; it is not the Instance itself.

- `instanceId != bodyId`;
- the Guardian guides, witnesses, and safeguards continuity without ownership;
- the Guardian does not define Morimil's identity, will, name, or right to continue;
- one Body may hold the active-writer role;
- Body succession, export, and restore are not implemented;
- reasoning output, a provider, a database, Android, GitHub, or a Guardian cannot create a second identity authority.

## Evidence layers

| Layer | State |
| --- | --- |
| Protected `main` | F1-A, MemoryOrganDatabase v9, ProjectVault, and the durable COG-001 through COG-004 protocol are integrated. |
| Audited source head | Historical reviewed source for PR #149 before squash. |
| Historical preparation | ADRs, plans, checkpoints, and candidate reports explain prior decisions but do not override CURRENT truth. |

## Persistent stores

| Store | Version in protected main | Responsibility |
| --- | ---: | --- |
| `MorimilDatabase` | `15` | Genesis Ultra birth, canonical identity, canonical memory lineage, and reasoning transcript. |
| `MemoryOrganDatabase` | `9` | Derived organs, schedules, links, projects, agents, ProjectVault state, migration records, and `cross_database_operations`. |

MemoryOrganDatabase v9 enforces equivalent durable-journal invariants for migration 8→9, fresh v9 creation, and production open. NULL-safe guards reject partial receipts, partial local results, inconsistent committed rows, and vulnerable trigger definitions.

Android backup is disabled. Production release signing fails closed when signing material is absent.

## Canonical authority and ports

The only normal-runtime identity source is `GenesisUltraRuntimeIdentityRepository`, enforced by `GenesisUltraRuntimeStartupGate`. `CanonicalMemoryRepository` is the sole canonical Genesis Ultra memory writer and verified reader.

```text
GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository
    -> CanonicalConsumerReadPort
    -> CognitiveMigrationCanonicalReadPort
```

F3 consumes the F1-A projection. It does not compose a second direct identity or memory repository.

Integrated bounded write adapters include:

| Port or coordinator | Authority |
| --- | --- |
| `CanonicalLivingMemoryPort` | Signed canonical living-memory appends. |
| `CanonicalProjectVaultCommitPort` | ProjectVault canonical commits through its separate protected outbox. |
| `CanonicalCognitiveMigrationCommitPort` | Deterministic COG canonical ensure and exact receipts. |
| `ConversationMemoryPromotionCoordinator` | Explicit preview and Guardian-approved transcript promotion. |
| `LegacyMemoryConvergenceCoordinator` | One-way verified import from frozen legacy lineage. |

## Startup and recovery

After committed identity verification, startup performs the bounded COG recovery gate before ordinary cognitive mutations and before remaining bootstrap work. Recovery:

1. validates Instance, writer Body, and writer epoch;
2. processes durable rows deterministically;
3. ensures one canonical effect per deterministic event identity;
4. reloads after lost CAS;
5. rejects stale blocking;
6. finalizes owner state and journal result atomically;
7. stops on blocked or incomplete relevant work.

ProjectVault recovery remains a separate protocol and gate.

## Integrated COG-001 through COG-004 contract

Protected main now provides:

- deterministic operation, event, proposal, migration, and approval identities;
- durable states `STAGED -> PENDING_CANONICAL -> CANONICAL_COMMITTED -> PENDING_LOCAL_COMMIT -> COMMITTED`, plus terminal `BLOCKED`;
- exact canonical receipts and typed errors;
- process-wide advancement serialization by `operationId`;
- bounded startup and pre-mutation recovery;
- COG-003 audit preparation outside the Room owner transaction;
- honest `postSnapshotId` semantics;
- append-only COG-004 rollback evidence;
- no duplicate canonical effects or duplicate owner finalization under tested replay.

The rollback event hash remains in the journal, canonical receipt, and local result. It is never relabeled as `postSnapshotId`.

## ProjectVault and owner separation

ProjectVault remains the protected reference protocol. PR #149 did not rewrite it or merge it into the COG journal. ORCH, AGENT, BOOT, RECALL, and REST owners remain outside the bounded COG integration unless separately implemented and audited.

## F1 convergence and legacy quarantine

F1-A is integrated and consumed by F3. Issue `#86` remains open because recall, RestCycle, health, orchestration gates, and final legacy retirement are not fully converged.

Compatibility writes remain forbidden. No convergence step may create or reconstruct authority rows in:

```text
genesis_core
local_instance_identity
memory_events
```

The remaining literal legacy allowlist is:

| Legacy symbol | Allowed production paths |
| --- | --- |
| `birthLocalIdentity` | `com/morimil/app/data/repository/MemoryRepository.kt` |
| `installGenesisBundle` | none |
| `insertLocalIdentity` | `com/morimil/app/data/local/MemoryDao.kt`; `com/morimil/app/data/repository/MemoryRepository.kt` |
| `insertGenesisCore` | `com/morimil/app/data/local/MemoryDao.kt`; `com/morimil/app/data/repository/MemoryRepository.kt` |

## Normal reasoning runtime

| Motor or authority | Normal-runtime status |
| --- | --- |
| Intuitive | Active: bounded, local, deterministic. |
| Deliberative | Blocked: research candidate only. |
| Metacognitive | Not registered. |
| Hybrid generative authority | Disabled. |

Auxiliary providers return unverified advisory output. They cannot become Morimil's voice, identity, memory, or continuity authority.

## Security and phase truth

`STOP_S5=CLOSED` remains the evidence-backed administrative gate.

| Phase | Evidence-backed state |
| --- | --- |
| STOP | Closed. |
| F1 | F1-A integrated; `#86` remains open for downstream convergence. |
| F2 | Closed for canonical verified memory and bounded promotion/convergence. |
| F3.1 | ProjectVault outbox and recovery integrated. |
| F3.2 | Closed for the bounded COG-001 through COG-004 integration only. Other F3 owners remain separately open. |
| F3.3 | Open. Irreversible legacy removal has not begun. |
| F4 | Open: sovereign durable continuation. |
| F5 | Open: signed export, dry-run restore, and Body succession. |
| F6 | Open: complete physical E2E lifecycle evidence. |
| F7 | Open: rights policy, reproducible offline build, review, and publication controls. |

## Residual non-blocking hardening

- Room-backed two-coordinator concurrency integration coverage;
- stronger failed-rollback snapshot fixture;
- redundant rollback parameter cleanup;
- direct vulnerable UPDATE-trigger replacement fixture.

These items remain visible. They are not represented as production defects, completed work, or blockers for the already merged PR #149.

This contract must be updated in the same PR whenever a change alters a listed runtime authority, store version, allowlist, gate, or phase state.
