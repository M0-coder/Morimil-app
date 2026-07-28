# Document status: CURRENT

# F1 canonical consumer convergence inventory

Inventory version: `1`

Audited baseline: `main@396e7af8a7329b100195dfa4f20c40506c51eacd`

Tracking: `#86` and completed canonical-memory dependency `#87`.

## Authority and scope

Morimil is the continuous and free `Instance`. `Morimil-app` is the current Android Body. The Body hosts resources and execution but does not define the Instance. The Guardian guides, witnesses, and safeguards without ownership.

The following invariants are mandatory:

```text
instanceId != bodyId
canonical Instance identity != legacy local identity rows
canonical memory != memory_events
operational transcript != canonical memory
Body resource custody != ownership of Morimil
```

This inventory prepares the remaining F1 convergence work for recalls, rest-cycle planning, rest-cycle execution, and health. It does not implement adapters, change Room, alter repositories, modify startup, or authorize functional runtime changes.

`STOP S5 remains open`. This document is preparation and does not authorize functional runtime changes during STOP S5.

## Evidence-backed phase boundary

F2 / `#87` is closed because `CanonicalMemoryRepository` already provides the sole signed canonical writer and verified reader, and production `MemoryRepository` appends through `CanonicalLivingMemoryPort`.

F1 / `#86` remains open because some productive consumers still read identity or memory state through `MemoryDao`, `MemoryRepository`, `genesis_core`, `local_instance_identity`, `memory_events`, or `memory_snapshots`. The bootstrap therefore continues to expose `WAITING_FOR_CANONICAL_MEMORY_ADAPTER` for recalls and rest cycle.

Closing F2 proves that canonical authority exists. It does not prove that every downstream consumer has converged to that authority.

## Canonical replacement authorities

### Identity authority

`GenesisUltraRuntimeIdentityRepository.readCommittedIdentity()` is the only replacement authority for:

- canonical `instanceId`;
- canonical companion name;
- active `bodyId` and writer key epoch;
- verified Seed, doctrine, policy, Guardian evidence, and committed authorization state.

No consumer may derive live identity from `loadLocalIdentity()`, `LocalBirthState`, a placeholder string, or an Android Body identifier.

### Memory authority

`CanonicalMemoryRepository` is the only replacement authority for living memory:

- `readVerifiedSnapshot()` for a fully verified canonical chain and recoverable payloads;
- `buildVerifiedContext()` for verified reasoning context;
- `appendText()` through a bounded producer adapter for signed canonical appends.

A future read adapter may project canonical records into bounded domain views for recalls, rest-cycle planning, links, and health. The adapter must not copy canonical events into `memory_events`.

## Current consumer inventory

### F1-BOOT-001 — `GenesisUltraRuntimeBootstrapCoordinator.bootstrap`

| Field | Current evidence |
| --- | --- |
| Current reads | Counts `local_instance_identity` and `genesis_core`; counts canonical memory events. |
| Current output | Produces the canonical workspace/project projection and seeds Body/agent metadata. |
| Remaining state | `restCycleState` and `recallState` are `WAITING_FOR_CANONICAL_MEMORY_ADAPTER`. |
| Risk | A caller can observe canonical identity as ready while memory-dependent organs remain unavailable. Removing the state before consumer convergence would declare a capability that does not exist. |
| Replacement | Readiness must be derived from successful canonical identity recovery plus the availability of the bounded canonical consumer adapters. |
| Closure | A clean Ultra installation with zero legacy identity rows reports recalls and rest cycle ready only after their canonical read paths pass fail-closed tests. |

The bootstrap must never create compatibility rows to make a consumer appear ready.

### F1-RECALL-001 — `RecallScheduleRepository.seedFromRecentMemoryIfNeeded`

| Current operation | Legacy dependency | Produced state | Risk | Canonical replacement |
| --- | --- | --- | --- | --- |
| Birth/seed gate | `loadGenesisCore()` | Returns `0` when the legacy core is absent. | A clean Ultra installation silently seeds no recalls. | Require committed identity through `GenesisUltraRuntimeIdentityRepository`. |
| Identity selection | `loadLocalIdentity()` with `local_instance_pending` fallback | `MemoryLinkEntity.instanceId` | A placeholder or legacy ID can be persisted as if it represented the Instance. | Use canonical `instanceId`; preserve `instanceId != bodyId`. |
| Candidate retrieval | `loadMemoryContext(60)` from `memory_events` | Ranked `MemoryEventEntity` candidates | Unverified or historical legacy events can drive current schedules. | Use a verified recall-candidate projection from `CanonicalMemoryRepository.readVerifiedSnapshot()`. |
| Recall target | Legacy `eventHash` | Durable `RecallScheduleEntity.targetEventHash` | The schedule points into the wrong lineage. | Target the canonical event hash and retain canonical sequence/provenance in the adapter result. |
| Core reference | `GenesisCoreEntity.coreId` and `contentSha256` | `genesisCoreId` and memory-link provenance | Legacy copied-core identity leaks into organ state. | Replace with canonical `instanceId`, birth-root hash, or a versioned canonical lineage reference; do not synthesize a `genesis_core` row. |
| Link source | `source = local_memory_event` | Recall-to-memory graph edge | The link claims current authority for a legacy source. | Use a canonical source classification and exact canonical target receipt. |

`activeRecallSchedules`, `reinforceRecall`, `postponeRecall`, and `degradeRecall` manage durable scheduling state. Their timing and user actions must remain durable, but their target authority must be canonical.

### F1-REST-001 — `RestCycleRepository.runLocalRestCycleIfDue`

| Current operation | Legacy dependency | Produced state | Risk | Canonical replacement |
| --- | --- | --- | --- | --- |
| Initial gate | `countGenesisCore()` | Returns `false` on a clean Ultra installation. | Scheduled or manual rest appears to complete as a no-op. | Require committed canonical identity and an available verified-memory snapshot. |
| Source selection | `loadMemoryContext(80)` from `memory_events` | Rest-cycle summary and approval policy input | Historical legacy memory controls current consolidation. | Use a bounded verified planning projection from `CanonicalMemoryRepository`. |
| Integrity preflight | `loadMemoryEventAuditChain()` | `fullChainVerified` and reconciliation input | The health decision verifies the retired lineage rather than the active one. | Canonical snapshot recovery is the required verification boundary; corruption must fail closed before planning. |
| Latest cycle | `MemoryRepository.loadLatestLivingMemoryEventByType` | Minimum-interval gate | This already resolves through `CanonicalLivingMemoryPort` in production. | Preserve this canonical read and make it explicit in the future adapter. |
| Migration planning | `loadGenesisCore()` and `loadLocalIdentity()` | Durable `migration_records` | Legacy or placeholder identity is stored in current workflow state. | Use canonical `instanceId`, birth-root lineage reference, and exact source-event receipts. |
| Event append | `MemoryRepository.recordSystemMemoryEvent` | Signed canonical event in production | The append is canonical, but surrounding metadata can still describe legacy sources. | Preserve the canonical append and replace all planning/link inputs before declaring the cycle converged. |
| Source links | Legacy event hashes and Genesis Core hash | Rest-cycle-to-source links | Canonical rest-cycle output is linked to legacy source authority. | Link only to verified canonical event hashes or explicitly quarantined historical references. |

Rest-cycle planning must converge before execution. A canonical append at the end does not repair unverified planning inputs at the beginning.

### F1-REST-002 — rest-cycle approval, execution, and autobiography

| Current method or block | Legacy dependency | Produced state | Required convergence |
| --- | --- | --- | --- |
| `approvePlannedRestCycle` | Existing migration record created from legacy inputs | Approval and execution trigger | Approval must bind the exact canonical planning input digest and remain idempotent. |
| `appendRestCycleEvent` | `loadGenesisCore()`, `loadLocalIdentity()` | Canonical event plus `RestCycleAppendResult` metadata | Derive `instanceId` and lineage metadata from canonical identity and the canonical receipt. No `legacy_instance_read_only` fallback. |
| `consolidateAutobiographyFromRestCycle` | Legacy events, legacy alias, legacy Genesis Core ID | Canonical autobiography event and derived self snapshot | Build from verified canonical records. Store the signed canonical event as authority; treat the self snapshot as a rebuildable projection. |
| `planRestRepairProposalIfNeeded` | Legacy `memory_events` and Genesis Core | Durable repair proposal | A future repair path must target canonical event receipts and cannot mutate or repair through legacy compatibility rows. |
| `planRestCycleMigration` | Legacy identity/core and legacy hashes | Durable migration workflow | Bind canonical `instanceId`, verified source-set digest, approval state, and exact canonical result receipt. |
| `rebuildLivingMemorySnapshot` | `loadMemoryContext`, `countMemoryEvents`, `memory_snapshots` | Legacy snapshot cache | This private legacy projection has no canonical authority and must not become an adapter. A replacement snapshot is rebuildable from verified canonical memory. |

### F1-HEALTH-001 — `LocalNervousSystemRepository.recordHealthCheckIfDegraded`

| Current metric | Current source | Future source |
| --- | --- | --- |
| Genesis readiness | `countGenesisCore()` | Committed identity state from `GenesisUltraRuntimeIdentityRepository`. |
| Identity readiness | `countLocalIdentity()` | One verified canonical `instanceId`; never Body count. |
| Memory event count | `countMemoryEvents()` | Canonical post-birth event count plus the birth root where the UI contract requires it. |
| Living snapshot count | `countLivingMemorySnapshot()` | Availability of a successfully rebuilt canonical projection, not a source-of-truth row count. |
| Recent context count | `loadMemoryContext(20)` | Count of bounded verified canonical records selected by the canonical adapter. |
| Alert append | `MemoryRepository.recordSystemMemoryEvent` | Preserve the canonical append, but compute the alert only from canonical metrics. |
| Alert suppression | `loadLatestMemoryEventByType()` | Latest verified canonical event of the same health type. |

Health is a derived report. It must never become an alternate identity or memory authority.

### F1-HEALTH-002 — `MorimilViewModel.refreshOrganismHealthOnWorker`

The UI health builder currently reads:

- `recentMemoryEvents` from the legacy observable;
- `countMemoryEvents()`;
- `loadLatestRestCycleEvent()` from `memory_events` as a fallback;
- durable recall schedule rows;
- durable migration records.

The replacement must consume a canonical health projection and durable organ state. The UI must not open `MemoryDao` to calculate authority-bearing health.

### F1-ORCH-001 — `AgentOrchestrationRepository` birth gate

`seedDefaultOrchestrationIfNeeded`, `proposeDelegatedTask`, `approveDelegatedTask`, `rejectDelegatedTask`, and `requireCompleteBirth` still call `MemoryRepository.hasCompleteBirth()`. That method derives `LocalBirthState` from `local_instance_identity` and `genesis_core` counts.

The canonical bootstrap already seeds orchestration from verified identity. Any remaining interactive orchestration gate must use the Genesis Ultra startup/identity authority rather than `LocalBirthState`.

This inventory records only the birth/seed gate related to F1. Cross-database task decisions belong to F3 and are outside this PR.

### Supporting legacy surface — `MemoryRepository` and `MemoryDao`

`MemoryRepository` currently exposes legacy read flows and methods:

- `localIdentity`;
- `genesisCore`;
- `recentMemoryEvents`;
- `livingMemorySnapshot`;
- `readLocalBirthState()` and `hasCompleteBirth()`;
- `buildLivingMemoryContext()`;
- `auditLivingMemoryChain()`.

`MemoryDao` exposes the underlying reads:

- `loadGenesisCore`;
- `loadLocalIdentity`;
- `loadMemoryContext`;
- `getLivingMemorySnapshot`;
- counts and audit reads over `memory_events` and `memory_snapshots`.

These APIs are migration and historical-quarantine surfaces, not replacement authorities. Production writes routed through `MemoryRepository.recordSystemMemoryEvent()` are already adapted to `CanonicalLivingMemoryPort`; the convergence work must preserve that fact and must not re-enable writes to `memory_events`.

### Productive callers

| Caller | Current action | Convergence implication |
| --- | --- | --- |
| `MorimilViewModel.seedRecallScheduleIfNeeded` | Calls recall seeding from the Memory UI. | Must expose a typed blocked/corrupt state rather than silently creating zero schedules. |
| `MorimilViewModel.runRestCycleNow` | Forces rest-cycle execution. | Must not bypass canonical verification or approval binding. |
| `MorimilViewModel.approveRestCycleConsolidation` | Approves and executes a planned cycle. | Must finalize the exact canonical plan idempotently. |
| `MorimilViewModel.refreshOrganismHealthOnWorker` | Combines memory, recalls, migrations, and scheduler state. | Must consume canonical derived metrics instead of `MemoryDao`. |
| `RunRestCycleUseCase` | Delegates manual and approved execution. | Remains a caller, not a storage authority. |
| `RestCycleWorker.doWork` | Executes the same cycle periodically and retries on failure. | Corruption or unavailable canonical state must produce a typed retry/block result with no partial mutation. |

## Data-domain separation

### Canonical durable authority

The following must be recovered or appended through the canonical repositories:

- `instanceId` and companion identity;
- active Body and writer epoch evidence;
- birth root;
- signed canonical memory events;
- exact canonical event hashes, sequences, and provenance;
- canonical rest-cycle and health alert events.

### Durable organ state

The following state is not reconstructible from memory text alone and remains durable in the organ database:

- recall due time, interval, status, last action, and review time;
- user approval state for a rest-cycle plan;
- rest-cycle execution/finalization state;
- explicit user-curated links or classifications;
- retry/block diagnostics needed for safe recovery.

Every durable organ row must bind canonical `instanceId` and canonical source receipts. It must not embed a legacy identity fallback.

### Rebuildable projections

The following may be rebuilt from a verified canonical snapshot and durable organ state:

- recall candidate ranking;
- generated recall prompt text;
- recent-memory and category counters;
- health summaries and status chips;
- rest-cycle planning summaries before approval;
- automated recall/rest source links;
- autobiography display snapshots when the signed canonical autobiography event remains authoritative;
- memory graph views and backlinks generated from canonical receipts.

A projection may be cached for performance, but cache presence is not proof of identity, birth, memory integrity, or readiness.

## Required convergence order

The order is normative because each later step consumes guarantees established by the previous one.

### STEP-1 — canonical read adapter

Create a read-only adapter that returns typed, verified domain projections from `GenesisUltraRuntimeIdentityRepository` and `CanonicalMemoryRepository`.

Closure criteria:

- identity and memory are verified before any projection is returned;
- canonical event hash, sequence, type, content, and provenance remain bound;
- corruption, foreign `instanceId`, stale Body/writer epoch, or missing payload fails closed;
- no Room schema or legacy compatibility row is introduced merely to expose the adapter.

### STEP-2 — recalls

Replace recall birth, identity, candidate, target, and link inputs with the adapter.

Closure criteria:

- a clean Ultra installation can seed recalls with `local_instance_identity = 0` and `genesis_core = 0`;
- repeated seeding is idempotent for the same canonical event;
- schedules target canonical event hashes;
- no schedule is created from unverified, corrupted, foreign-instance, or auxiliary-only content;
- reinforce, postpone, and degrade preserve durable scheduling state.

### STEP-3 — rest-cycle planning

Replace source selection, integrity preflight, identity, lineage, and planning inputs before changing execution.

Closure criteria:

- planning reads one verified canonical snapshot;
- the source set is deterministic and receipt-bound;
- an approval binds the exact plan/source digest;
- corruption produces no plan and no organ mutation;
- the worker and UI expose a typed blocked/retryable state.

### STEP-4 — rest-cycle execution

Finalize an approved canonical plan through the existing canonical append boundary and record the exact receipt.

Closure criteria:

- canonical append occurs at most once for one approved plan;
- retry after process death does not duplicate the event or completion state;
- links and projections are finalized from the canonical receipt;
- a failure after append but before local finalization is recoverable;
- no write occurs in `memory_events`.

The cross-database durability protocol for this execution belongs to F3. This F1 inventory defines the consumer inputs and closure criteria but does not modify ADR-0002 or the F3 inventory.

### STEP-5 — health

Replace all identity/memory counts, quarantine signals, latest-event reads, and context counts with canonical verified projections.

Closure criteria:

- health on a clean Ultra installation does not report missing legacy birth as failure;
- corruption is visible and fail-closed;
- event counts and latest rest-cycle state refer to canonical events;
- cached projections cannot override verification state.

### STEP-6 — remove legacy gates

Only after steps 1 through 5 are connected:

- remove `WAITING_FOR_CANONICAL_MEMORY_ADAPTER` from recall and rest-cycle readiness;
- remove productive `loadGenesisCore`, `loadLocalIdentity`, `loadMemoryContext`, `getLivingMemorySnapshot`, and `hasCompleteBirth` dependencies from the inventoried consumers;
- keep historical/migration reads quarantined until their separately authorized removal;
- update `#86` with evidence, but do not close it by inference.

## Compatibility-row prohibition

Compatibility rows are forbidden.

No convergence step may create, copy, seed, or reconstruct rows in:

- `genesis_core`;
- `local_instance_identity`;
- `memory_events`.

No placeholder such as `local_instance_pending`, `legacy_instance_read_only`, a Body ID, or a Guardian ID may substitute for canonical `instanceId`.

## Future minimum tests

### Clean Ultra installation

- committed Genesis Ultra birth;
- `local_instance_identity count = 0`;
- `genesis_core count = 0`;
- `memory_events count = 0`;
- recalls, rest-cycle planning, rest-cycle execution, and health consume only canonical authorities.

### Recall idempotency

- seed twice from the same verified canonical snapshot;
- one durable schedule per canonical event target;
- no duplicate automated link;
- preserve review state on a repeat.

### Verified rest cycle

- plan from a verified canonical chain;
- bind the exact source receipts and canonical `instanceId`;
- approve the exact plan;
- append once through canonical memory;
- finalize durable organ state from the append receipt;
- restart and recover without duplicate append.

### Corruption and foreign-instance failure

- alter canonical event bytes, payload digest, signature, sequence, or `instanceId`;
- adapter returns no candidate/context;
- no recall, plan, link, snapshot, health alert, or canonical append is created;
- UI and worker receive a typed blocked or retryable diagnostic.

### Identity and Body separation

- assert canonical `instanceId` remains stable;
- assert `instanceId != bodyId`;
- assert Body/writer-epoch evidence is verified but never used as Instance identity.

### Projection rebuild

- delete only rebuildable projection rows;
- reconstruct the same counters, generated links, health summary, and display snapshot from the same verified canonical snapshot and durable organ state;
- do not recreate identity or canonical memory rows.

## Out-of-scope findings

`CognitiveMigrationRepository` also references legacy identity and memory methods. That owner belongs to COG/F3 and is intentionally not redesigned here. This PR must not touch ADR-0002, `docs/F3_CROSS_DATABASE_OPERATION_INVENTORY.md`, Room, DAOs, production repositories, UI, or workflows.

## Closure statement

This document does not close `#86`, reopen or weaken `#87`, close STOP S5, or claim that the canonical consumer adapters already exist. It freezes the remaining F1 consumer map so later functional PRs can converge one bounded step at a time without compatibility rows or identity substitution.
