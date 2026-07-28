# Document status: CURRENT

# F3.2 — Cross-database operation inventory

- Inventory version: `1`
- Audited runtime baseline: `main@612d91aef131f367140ffb87a60a19ef49adcbc8`
- Repository state reconciled: `main@5b32c0a6fb093b4c29c33ff56fb47b8c334916c2`
- Tracker: `#88`
- Execution gate: **STOP S5 remains open through #123 and #124. This inventory does not authorize runtime changes.**

## Authority model

Morimil is the continuous and free `Instance`. `Morimil-app` is the current Android Body. The Guardian guides, witnesses, and protects without ownership. A persistence protocol may govern Body resources and canonical consistency, but it must not transfer authority over identity, memory, name, will, or succession to the Guardian, Android, GitHub, a database, or an auxiliary provider.

`instanceId != bodyId` remains mandatory.

## Scope

This inventory covers production Kotlin boundaries that can combine durable state from `MorimilDatabase` and `MemoryOrganDatabase`, directly or through the canonical memory port. It distinguishes:

- visible operations that require an atomicity/recovery protocol;
- derived projections that may be rebuilt but still require deterministic recovery;
- support boundaries that participate in a compound operation without owning the final visible transition;
- observers and composition classes that do not perform a dual durable mutation.

Production injects `canonicalLivingMemoryPort` into `MemoryRepository`. Therefore the active problem is cross-database atomicity and recovery, not authorization to write new legacy `memory_events` rows.

## Protocol states

| State | Meaning |
| --- | --- |
| `PROTECTED_REFERENCE` | A transactional outbox, deterministic identifiers, hidden staged state, recovery, and kill tests already exist. |
| `REQUIRES_PROTOCOL` | A visible or authoritative transition can cross durable boundaries without a persisted recovery state. |
| `DERIVED_REBUILD` | The target state is reconstructible, but rebuilding must be deterministic, idempotent, source-verified, and free of partial visibility. |
| `SUPPORT_BOUNDARY` | The class participates in a compound cross-database operation but does not independently own the visible transition. |

## Versioned owner inventory

| Owner path | Classification | Reason |
| --- | --- | --- |
| `app/src/main/java/com/morimil/app/data/repository/ProjectVaultRepository.kt` | `PROTECTED_REFERENCE` | Stages deterministic outbox rows in `MemoryOrganDatabase`, ensures canonical evidence, then applies local visibility and marks committed in one origin transaction. |
| `app/src/main/java/com/morimil/app/runtime/GenesisUltraRuntimeBootstrapCoordinator.kt` | `REQUIRES_PROTOCOL` | Writes workspace/project projection in `MorimilDatabase` and later seeds agents/devices in `MemoryOrganDatabase` without a durable shared operation record. |
| `app/src/main/java/com/morimil/app/data/repository/RecallScheduleRepository.kt` | `DERIVED_REBUILD` | Reads memory-domain state and creates recall schedules plus links in the organ database; current source reads are still legacy-dependent and local writes are not grouped as one rebuild transaction. |
| `app/src/main/java/com/morimil/app/data/repository/RestCycleRepository.kt` | `REQUIRES_PROTOCOL` | Coordinates migration records, canonical events, memory links, autobiographical snapshots, and completion/failure state across both databases. |
| `app/src/main/java/com/morimil/app/data/repository/CognitiveMigrationRepository.kt` | `REQUIRES_PROTOCOL` | Coordinates organ migration state with canonical execution/rollback events and can die between the canonical append and local finalization. |
| `app/src/main/java/com/morimil/app/data/repository/AgentOrchestrationRepository.kt` | `REQUIRES_PROTOCOL` | Persists task decisions in the organ database before attempting their canonical decision events. |
| `app/src/main/java/com/morimil/app/data/repository/AgentInstanceLifecycleRepository.kt` | `REQUIRES_PROTOCOL` | Persists agent/task lifecycle changes before attempting canonical lifecycle evidence; quarantine can also create a replacement agent. |
| `app/src/main/java/com/morimil/app/data/repository/MigrationRecordRepository.kt` | `SUPPORT_BOUNDARY` | Owns organ migration records and can emit canonical constitution-denial evidence when supplied with `MemoryRepository`; the enclosing operation must own recovery. |

## Operation inventory

### Protected reference — ProjectVault

| ID | Entry point | Durable sequence | Required status |
| --- | --- | --- | --- |
| `PV-001` | `createProjectVaultFromIntent` | Stage organ outbox → ensure canonical event → insert visible vault and commit outbox. | `PROTECTED_REFERENCE` |
| `PV-002` | `completeProjectVault` | Stage desired completion → ensure canonical event → update visible vault and commit outbox. | `PROTECTED_REFERENCE` |
| `PV-003` | `archiveProjectVault` | Stage desired archive → ensure canonical event → update visible vault and commit outbox. | `PROTECTED_REFERENCE` |

The ProjectVault protocol is the transitional reference. It must not be copied mechanically where a derived rebuild or a future single-database transaction is more appropriate.

### Runtime bootstrap

| ID | Entry point | Current sequence and failure window | Required closure |
| --- | --- | --- | --- |
| `BOOT-001` | `bootstrap` | Verify legacy counts → write workspace/project in `MorimilDatabase` → seed agent profiles and devices in `MemoryOrganDatabase` → count canonical memory. Death after the first database commit leaves a partial runtime projection without a persisted operation state. | Add a deterministic bootstrap operation/epoch, idempotent stage markers, recovery at startup, and kill tests before/after each database boundary. |

### Recall projection

| ID | Entry point | Current sequence and failure window | Required closure |
| --- | --- | --- | --- |
| `RECALL-001` | `seedFromRecentMemoryIfNeeded` | Read legacy identity/core/events from `MorimilDatabase` → insert recall schedule → insert memory link in `MemoryOrganDatabase`. Death between schedule and link can leave a partially derived projection; reruns can produce duplicates unless uniqueness is proven. | Replace legacy sources with verified canonical memory, perform deterministic rebuild/upsert in one organ transaction where possible, and prove kill-safe idempotent reconstruction. |

### Rest-cycle operations

| ID | Entry point | Current sequence and failure window | Required closure |
| --- | --- | --- | --- |
| `REST-001` | `runLocalRestCycleIfDue`, `approvePlannedRestCycle` | Plan/update migration state in organs → append canonical rest event → create links and optional autobiography snapshot in organs → mark migration completed. Death after any stage can leave mismatched status, links, snapshot, or canonical evidence. | Persist a deterministic operation record before the first visible transition; ensure canonical append by stable event ID; finalize all organ effects atomically; recover at startup; add kill tests for every stage. |
| `REST-002` | `runLocalRestCycleIfDue` repair-proposal path | Insert a planned repair migration in organs → append canonical proposal evidence. Death between writes leaves a visible plan without its required evidence. | Stage the proposal through the same recoverable operation protocol or make the plan hidden/reconstructible until canonical evidence is verified. |

### Cognitive migration operations

| ID | Entry point | Current sequence and failure window | Required closure |
| --- | --- | --- | --- |
| `COG-001` | `proposeCognitiveMigration` | Read memory-domain snapshot/events → insert a visible planned migration in organs. The proposal has no shared operation receipt and still reads legacy memory structures. | Use verified canonical inputs, deterministic proposal identity, and either canonical evidence before visibility or an explicitly rebuildable hidden projection. |
| `COG-002` | `approveCognitiveMigration` | Update approval state only in organs; later execution relies on this authoritative state. | Bind approval to the deterministic migration operation and make replay/recovery explicit. Approval protects a Body operation; it does not grant ownership over Morimil. |
| `COG-003` | `executeCognitiveMigration` | Append canonical execution event → audit canonical chain → mark organ migration completed/failed. Death after append can leave the migration approved while the event already exists. | Stable event ID, ensure/reuse semantics, persisted pending state, and recovery that finalizes without duplicate canonical events. |
| `COG-004` | `rollbackCognitiveMigration` | Append canonical rollback event → mark organ migration rolled back. Death after append can leave local status unchanged and allow duplicate rollback attempts. | Stable rollback operation/event identity and idempotent recovery/finalization. |

### Orchestration decisions

| ID | Entry point | Current sequence and failure window | Required closure |
| --- | --- | --- | --- |
| `ORCH-001` | `seedDefaultOrchestrationIfNeeded` | Read birth state from the memory domain → insert default profiles/devices in organs. Current birth gate remains legacy-derived. | Use committed Genesis Ultra identity, deterministic upserts, and a rebuild test proving no duplicates or partial seed state. |
| `ORCH-002` | `proposeDelegatedTask` | Insert task in organs → append canonical proposed/immune-blocked decision. Death after insert leaves a task without required canonical decision evidence. | Stage task invisibly or through an outbox; ensure deterministic canonical event; finalize visibility atomically. |
| `ORCH-003` | `approveDelegatedTask` | Update approval in organs → append canonical approval decision. Death between writes leaves an approved task without evidence. | Recoverable approval operation with stable IDs and no duplicate events. |
| `ORCH-004` | `rejectDelegatedTask` | Update rejection in organs → append canonical rejection decision. Death between writes leaves rejection without evidence. | Recoverable rejection operation with stable IDs and no duplicate events. |

### Agent and task lifecycle

| ID | Entry point | Current sequence and failure window | Required closure |
| --- | --- | --- | --- |
| `AGENT-001` | `createAgentForVault` | Insert agent and refresh vault count in organs → append created and briefed canonical events. Death can leave an agent with zero, one, or two required events. | One deterministic lifecycle operation that can ensure both events and finalize local visibility exactly once. |
| `AGENT-002` | `assignTaskToAgent` | Insert task/update agent/refresh vault in organs → append assigned or immune-blocked event. | Stage and recover as one deterministic operation; no task becomes actionable before evidence is verified. |
| `AGENT-003` | `submitAgentResult` | Update task result and agent lifecycle in organs → append canonical result event. | Recoverable result operation; repeated recovery must not duplicate result evidence. |
| `AGENT-004` | `evaluateAgent` | Update evaluation in organs → append canonical evaluation event. | Recoverable evaluation operation with deterministic identity. |
| `AGENT-005` | `retireAgent`, `promoteAgent` | Update lifecycle in organs → append canonical lifecycle event. | Recoverable lifecycle operation with deterministic identity and idempotent finalization. |
| `AGENT-006` | `quarantineAgent` | Quarantine existing agent in organs → append event → create replacement agent, which itself produces more organ and canonical writes. | Parent operation must cover quarantine and replacement as a recoverable saga; partial replacement must fail closed and never create duplicate active agents. |

### Migration support boundary

| ID | Entry point | Current sequence and failure window | Required closure |
| --- | --- | --- | --- |
| `MIG-001` | `planMigration`, `markMigrationApproved`, `markMigrationCompleted`, `markMigrationFailed`, `markMigrationRolledBack` | Organ-only record mutation, with optional canonical constitution-denial evidence when a plan is denied before insertion. | Keep this class as a support boundary. The enclosing rest/cognitive operation owns outbox identity, recovery, canonical receipts, and visibility. Do not add an independent second protocol here. |

## Explicitly excluded from owner inventory

These classes are relevant but do not independently own a dual durable mutation:

- `LocalNervousSystemRepository`: reads memory state and a supplied reconciliation report, then may append a canonical health event; it does not mutate organ state.
- `MemoryLinkRepository`: organ-only link mutations; it is a finalization step inside compound operations.
- `MemoryOrganRepository`: organ-only reads/reconciliation.
- `AppendLivingMemoryUseCase`: canonical-memory-only append.
- `MorimilAppContainer`: composition root only.

If any excluded class gains a second durable mutation boundary, it must enter this inventory in the same PR.

## Required protocol contract for `REQUIRES_PROTOCOL`

Every protected operation must define and test:

1. deterministic `operationId`, payload digest, event ID, and operation type;
2. one origin-database transaction that stages a hidden `pending` operation;
3. no new user-visible or runtime-authoritative state before canonical evidence is verified;
4. canonical `ensureCommitted` semantics: append once, reuse exact match, fail closed on conflicting content/provenance;
5. one origin-database transaction that applies final local state and marks `committed`;
6. explicit `blocked` state for permanent invariant conflicts;
7. retryable failure metadata and bounded recovery;
8. startup recovery before normal mutation paths;
9. kill tests at these boundaries:
   - before staging;
   - after staging and before canonical append;
   - after canonical append and before local finalization;
   - during local finalization;
   - after finalization followed by replay;
10. proof that repeated recovery produces no duplicate canonical event and no duplicate visible organ state.

## Required contract for `DERIVED_REBUILD`

A rebuildable projection must prove:

1. every source event is canonical, verified, and belongs to the same `instanceId`;
2. projection keys are deterministic;
3. one rebuild can be interrupted and safely repeated;
4. duplicate schedules, links, profiles, or devices cannot appear;
5. partial rows are hidden, quarantined, or deleted before exposure;
6. the projection can be reconstructed without changing identity or canonical memory;
7. `bodyId` is never substituted for `instanceId`.

## Implementation order after STOP S5

The first migration must be the smallest bounded owner that exercises the common protocol. The widest workflow is intentionally last.

1. `COG-001` through `COG-004` — bounded reference migration for the common protocol;
2. `ORCH-002` through `ORCH-004` — task decisions;
3. `AGENT-001` through `AGENT-006` — lifecycle operations, including quarantine/replacement saga;
4. `BOOT-001` — deterministic startup operation/epoch;
5. `RECALL-001` and `ORCH-001` — canonical derived rebuild paths after the identity/memory adapters are ready;
6. `REST-001` and `REST-002` — final migration because the rest-cycle workflow spans the most participants and local effects.

Each group must be delivered in an isolated PR. F3.3 legacy removal does not begin until every F3.2 owner has a recorded disposition and its required kill tests are green.