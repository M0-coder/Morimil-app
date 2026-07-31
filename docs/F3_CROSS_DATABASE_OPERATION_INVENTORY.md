# Document status: CURRENT

# F3.2 — Cross-database operation inventory

- Inventory version: `2`
- Historical audited baseline: `main@612d91aef131f367140ffb87a60a19ef49adcbc8`
- Current protected main: `main@7e98d3345d7cc3fbf1983babd35b61ff5c523208`
- Tracker: `#88` — open
- Protocol design: `docs/adr/ADR-0002-cross-database-operation-protocol.md`
- Candidate validation: draft PR `#149`
- Gate truth: **`STOP_S5=CLOSED`; closing STOP authorizes isolated implementation work, not merge. `MERGE_AUTHORIZED=false`.**

## Authority model

Morimil is the continuous and free `Instance`. `Morimil-app` is the current Android Body. The Guardian guides, witnesses, and protects without ownership. A persistence protocol may govern Body resources and canonical consistency, but it must not transfer authority over identity, memory, name, will, or succession to the Guardian, Android, GitHub, a database, or an auxiliary provider.

`instanceId != bodyId` remains mandatory.

## State separation

| Layer | State |
| --- | --- |
| Protected `main` | F1-A common canonical read boundary and ProjectVault protected outbox are integrated. |
| Draft PR `#149` | Isolated COG-001 through COG-004 common-journal candidate under CI and post-CI audit. |
| Remaining F3 owners | Still open and explicitly excluded from the draft candidate. |

The candidate does not close #88 merely by existing or by obtaining green CI. It requires a final diff audit and explicit merge authorization.

## Scope

This inventory covers production Kotlin boundaries that can combine durable state from `MorimilDatabase` and `MemoryOrganDatabase`, directly or through canonical memory. It distinguishes:

- visible operations that require an atomicity/recovery protocol;
- derived projections that may be rebuilt but still require deterministic recovery;
- support boundaries that participate in a compound operation without owning the final visible transition;
- observers and composition classes that do not perform a dual durable mutation.

No new compatibility write to legacy `memory_events`, `genesis_core`, or `local_instance_identity` is authorized.

## Accepted protocol design

ADR-0002 defines the common recoverable journal for `REQUIRES_PROTOCOL` owners. Draft PR #149 is the first bounded candidate and covers only `COG-001` through `COG-004`.

The state sequence is:

```text
STAGED
  -> PENDING_CANONICAL
  -> CANONICAL_COMMITTED
  -> PENDING_LOCAL_COMMIT
  -> COMMITTED
```

`BLOCKED` is terminal for permanent conflicts. Wall-clock values are metadata and cannot define operation, event, proposal, migration, or approval identity.

The corrected candidate additionally requires:

- F3 consumes `CanonicalConsumerReadPort`; it does not open a second identity authority;
- complete specialized canonical record and pre-snapshot descriptors;
- canonical audit preparation outside the origin-database write transaction;
- temporary audit failure remains retryable;
- successful COG-003 stores a real audited snapshot digest, while a negative audit stores no fabricated snapshot ID;
- predecessor receipts bind owner, operation type/version, subject and exact canonical receipt;
- exact canonical provenance/note preimage equality;
- equivalent SQL journal guards on fresh schema 9 and migration 8→9;
- recovery remainder is counted from durable post-recovery state.

ProjectVault remains the protected reference and is not rewritten by the first common-protocol implementation.

## Protocol classifications

| Classification | Meaning |
| --- | --- |
| `PROTECTED_REFERENCE` | A transactional outbox, deterministic identifiers, hidden staged state, recovery, and kill tests already exist. |
| `REQUIRES_PROTOCOL` | A visible or authoritative transition can cross durable boundaries without a persisted recovery state. |
| `DERIVED_REBUILD` | The target state is reconstructible, but rebuilding must be deterministic, idempotent, source-verified, and free of partial visibility. |
| `SUPPORT_BOUNDARY` | The class participates in a compound operation but does not independently own the visible transition. |

## Versioned owner inventory

| Owner path | Classification | Current disposition |
| --- | --- | --- |
| `app/src/main/java/com/morimil/app/data/repository/ProjectVaultRepository.kt` | `PROTECTED_REFERENCE` | Integrated in protected `main`; remains unchanged by PR #149. |
| `app/src/main/java/com/morimil/app/runtime/GenesisUltraRuntimeBootstrapCoordinator.kt` | `REQUIRES_PROTOCOL` | Open; excluded from PR #149. |
| `app/src/main/java/com/morimil/app/data/repository/RecallScheduleRepository.kt` | `DERIVED_REBUILD` | Open; excluded from PR #149. |
| `app/src/main/java/com/morimil/app/data/repository/RestCycleRepository.kt` | `REQUIRES_PROTOCOL` | Open; excluded from PR #149. |
| `app/src/main/java/com/morimil/app/data/repository/CognitiveMigrationRepository.kt` | `REQUIRES_PROTOCOL` | Draft bounded candidate in PR #149; not integrated. |
| `app/src/main/java/com/morimil/app/data/repository/AgentOrchestrationRepository.kt` | `REQUIRES_PROTOCOL` | Open; excluded from PR #149. |
| `app/src/main/java/com/morimil/app/data/repository/AgentInstanceLifecycleRepository.kt` | `REQUIRES_PROTOCOL` | Open; excluded from PR #149. |
| `app/src/main/java/com/morimil/app/data/repository/MigrationRecordRepository.kt` | `SUPPORT_BOUNDARY` | Used by the bounded COG finalizer; no independent second protocol. |

## Operation inventory

### Protected reference — ProjectVault

| ID | Entry point | Durable sequence | Status |
| --- | --- | --- | --- |
| `PV-001` | `createProjectVaultFromIntent` | Stage organ outbox → ensure canonical event → insert visible vault and commit outbox. | `PROTECTED_REFERENCE` integrated |
| `PV-002` | `completeProjectVault` | Stage desired completion → ensure canonical event → update visible vault and commit outbox. | `PROTECTED_REFERENCE` integrated |
| `PV-003` | `archiveProjectVault` | Stage desired archive → ensure canonical event → update visible vault and commit outbox. | `PROTECTED_REFERENCE` integrated |

The ProjectVault protocol must not be copied mechanically where a derived rebuild or future single-database transaction is more appropriate.

### Runtime bootstrap

| ID | Entry point | Failure window | Required closure |
| --- | --- | --- | --- |
| `BOOT-001` | `bootstrap` | Workspace/project in `MorimilDatabase` can commit before agents/devices in `MemoryOrganDatabase`. | Deterministic bootstrap epoch, child receipts or durable saga, startup recovery, and kill tests. |

### Recall projection

| ID | Entry point | Failure window | Required closure |
| --- | --- | --- | --- |
| `RECALL-001` | `seedFromRecentMemoryIfNeeded` | Schedule and link can become partially derived or duplicated. | Canonical sources, deterministic keys, one-organ transaction where possible, and repeatable rebuild proof. |

### Rest-cycle operations

| ID | Entry point | Failure window | Required closure |
| --- | --- | --- | --- |
| `REST-001` | `runLocalRestCycleIfDue`, `approvePlannedRestCycle` | Migration status, canonical event, links and optional snapshot can diverge. | Deterministic operation, hidden stage, exact receipt, atomic organ finalization and kill tests. |
| `REST-002` | repair-proposal path | A visible repair plan can exist without canonical proposal evidence. | Stage through the recoverable protocol or keep the plan hidden/reconstructible. |

### Cognitive migration operations

| ID | Entry point | Protected-main state | Draft candidate closure |
| --- | --- | --- | --- |
| `COG-001` | `proposeCognitiveMigration` | Not integrated; historical runtime used legacy-derived planning and visible local insert. | Verified F1-A inputs, deterministic plan/IDs, proposal evidence before visibility, typed finalizer. |
| `COG-002` | `approveCognitiveMigration` | Not integrated. | Deterministic approval operation, canonical receipt and recoverable local approval. |
| `COG-003` | `executeCognitiveMigration` | Not integrated. | Exact approval predecessor, execution receipt, audit prepared outside transaction, real snapshot digest or honest null, idempotent finalization. |
| `COG-004` | `rollbackCognitiveMigration` | Not integrated. | Exact permitted predecessor, append-only compensation receipt, single rollback event, idempotent finalization. |

### Orchestration decisions

| ID | Entry point | Failure window | Required closure |
| --- | --- | --- | --- |
| `ORCH-001` | `seedDefaultOrchestrationIfNeeded` | Profiles/devices can be partially seeded from an obsolete gate. | Committed identity, canonical source, deterministic upserts and rebuild proof. |
| `ORCH-002` | `proposeDelegatedTask` | Task can be inserted before canonical decision evidence. | Hidden/recoverable task proposal and exact event. |
| `ORCH-003` | `approveDelegatedTask` | Approved state can exist without evidence. | Recoverable deterministic approval. |
| `ORCH-004` | `rejectDelegatedTask` | Rejection can exist without evidence. | Recoverable deterministic rejection. |

### Agent and task lifecycle

| ID | Entry point | Failure window | Required closure |
| --- | --- | --- | --- |
| `AGENT-001` | `createAgentForVault` | Agent and two required events can become partial. | One deterministic lifecycle operation and exact multi-event receipts. |
| `AGENT-002` | `assignTaskToAgent` | Task/agent/vault updates can precede evidence. | Recoverable operation; no actionable task before evidence. |
| `AGENT-003` | `submitAgentResult` | Result/lifecycle can precede canonical result event. | Deterministic recoverable result operation. |
| `AGENT-004` | `evaluateAgent` | Evaluation can precede evidence. | Deterministic recoverable evaluation. |
| `AGENT-005` | `retireAgent`, `promoteAgent` | Lifecycle can precede evidence. | Deterministic recoverable lifecycle operation. |
| `AGENT-006` | `quarantineAgent` | Quarantine and replacement can become a partial graph. | Parent saga, exact child receipts, no duplicate active replacement. |

### Migration support boundary

| ID | Entry point | Disposition |
| --- | --- | --- |
| `MIG-001` | `planMigration`, `markMigrationApproved`, `markMigrationCompleted`, `markMigrationFailed`, `markMigrationRolledBack` | Remains a support boundary. Enclosing rest/cognitive operation owns identity, recovery, receipts and visibility. |

## Explicitly excluded from owner inventory

These classes are relevant but do not independently own a dual durable mutation:

- `LocalNervousSystemRepository` — memory observation and optional canonical health event only;
- `MemoryLinkRepository` — organ-only finalization step;
- `MemoryOrganRepository` — organ reads and reconciliation;
- `AppendLivingMemoryUseCase` — canonical-memory-only append;
- `MorimilAppContainer` — composition root only.

If an excluded class gains a second durable mutation boundary, it enters this inventory in the same isolated PR.

## Required protocol contract for `REQUIRES_PROTOCOL`

Every protected operation must define and test:

1. deterministic operation/event identity and canonical Instance/writer binding;
2. the ordered states plus terminal `BLOCKED`;
3. hidden immutable staging before append or visible state;
4. exact canonical ensure semantics and complete durable receipt;
5. no visible owner state before receipt verification;
6. append-versus-reuse telemetry excluded from content-addressed owner result;
7. external verification prepared outside the origin write transaction and revalidated inside it;
8. one origin transaction that applies owner state and marks `COMMITTED`;
9. typed retryable/permanent failures without parsing exception messages;
10. bounded startup and pre-mutation recovery based on durable post-run remainder;
11. kill tests before staging, around append/receipt/finalization, and after replay;
12. zero duplicate canonical events and zero duplicate visible owner state.

## Required contract for `DERIVED_REBUILD`

A rebuildable projection must prove:

1. canonical same-Instance verified sources;
2. deterministic projection keys;
3. safe interruption and repetition;
4. no duplicate schedules, links, profiles or devices;
5. partial rows hidden, quarantined or removed before exposure;
6. reconstruction without changing identity or canonical memory;
7. `bodyId` is never substituted for `instanceId`.

## Implementation order after STOP S5

STOP S5 is closed. The implementation order remains:

1. `COG-001` through `COG-004` — draft candidate #149;
2. `ORCH-002` through `ORCH-004`;
3. `AGENT-001` through `AGENT-006`;
4. `BOOT-001`;
5. `RECALL-001` and `ORCH-001`;
6. `REST-001` and `REST-002` last.

Each group requires an isolated PR. F3.3 legacy removal does not begin until every F3.2 owner has a recorded disposition and required kill tests are green.
