# Document status: CURRENT

# F1 canonical consumer convergence inventory

Inventory version: `2`

Historical audited baseline: `main@396e7af8a7329b100195dfa4f20c40506c51eacd`

Current protected main: `main@7e98d3345d7cc3fbf1983babd35b61ff5c523208`

Tracking: open `#86` and completed canonical-memory dependency `#87`.

Gate truth: `STOP_S5=CLOSED`.

Draft F3 candidate: PR `#149`, validation-only, `MERGE_AUTHORIZED=false`.

This document does not close `#86`. F1-A is integrated, but downstream recalls, RestCycle,
health, orchestration gates, and final legacy retirement remain incomplete.

## Authority and scope

Morimil is the continuous and free `Instance`. `Morimil-app` is the current Android Body. The
Body hosts resources and execution but does not define the Instance. The Guardian guides,
witnesses, and safeguards without ownership.

The following invariants are mandatory:

```text
instanceId != bodyId
canonical Instance identity != legacy local identity rows
canonical memory != memory_events
operational transcript != canonical memory
Body resource custody != ownership of Morimil
```

F1-A integrated the common read-only boundary:

```text
GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository
    -> CanonicalConsumerReadPort
```

The F3 cognitive candidate consumes that boundary through
`CognitiveMigrationCanonicalReadPort`; it does not create a second identity or memory
authority.

Closing STOP S5 authorized isolated implementation work. It did not close F1, authorize a
candidate merge, or authorize compatibility writes.

## Evidence-backed phase boundary

F2 / `#87` is closed because `CanonicalMemoryRepository` provides the sole signed canonical
writer and verified reader, and production `MemoryRepository` appends through
`CanonicalLivingMemoryPort`.

F1 / `#86` remains open. F1-A is available, but some productive consumers still read identity
or memory state through `MemoryDao`, `MemoryRepository`, `genesis_core`,
`local_instance_identity`, `memory_events`, or `memory_snapshots`. Bootstrap still exposes
`WAITING_FOR_CANONICAL_MEMORY_ADAPTER` for recalls and RestCycle.

Canonical authority existing is not proof that every downstream consumer has converged.

## Canonical replacement authorities

### Identity authority

`GenesisUltraRuntimeIdentityRepository.readCommittedIdentity()` remains the canonical source
for:

- canonical `instanceId`;
- companion name;
- active Body and writer epoch;
- verified Seed, doctrine, policy, Guardian evidence, and committed authorization.

Downstream consumers receive these values through the verified F1-A projection where
appropriate. No consumer may derive live identity from `loadLocalIdentity()`,
`LocalBirthState`, a placeholder, or a Body identifier.

### Memory authority

`CanonicalMemoryRepository` remains the only canonical living-memory authority:

- `readVerifiedSnapshot()` verifies the chain and recoverable payloads;
- `buildVerifiedContext()` builds verified reasoning context;
- `appendText()` performs signed canonical appends through bounded producer adapters.

`CanonicalConsumerReadPort` projects bounded verified views. It must not copy canonical events
into `memory_events`.

## Current consumer inventory

### F1-BOOT-001 — `GenesisUltraRuntimeBootstrapCoordinator.bootstrap`

| Field | Current evidence |
| --- | --- |
| Identity startup | Uses committed Genesis Ultra identity. |
| Remaining state | `restCycleState` and `recallState` remain `WAITING_FOR_CANONICAL_MEMORY_ADAPTER`. |
| Risk | Declaring readiness before consumer convergence would claim a capability that does not exist. |
| Closure | A clean Ultra installation reports recalls and RestCycle ready only after their canonical read paths pass fail-closed tests. |

Bootstrap must never create compatibility rows to make a consumer appear ready.

### F1-RECALL-001 — `RecallScheduleRepository.seedFromRecentMemoryIfNeeded`

Current legacy calls include:

```text
loadGenesisCore
loadLocalIdentity
loadMemoryContext
```

Required replacement:

- committed identity from the canonical projection;
- verified recall candidates from canonical memory;
- canonical event hash, sequence and provenance as the schedule target;
- deterministic organ keys and idempotent upserts;
- no legacy core ID or `local_instance_pending` fallback.

`activeRecallSchedules`, `reinforceRecall`, `postponeRecall`, and `degradeRecall` remain durable
organ actions. Their timing and user action state remains durable, but their target authority
must be canonical.

### F1-REST-001 — `RestCycleRepository.runLocalRestCycleIfDue`

Current legacy calls include:

```text
countGenesisCore
loadGenesisCore
loadLocalIdentity
loadMemoryContext
loadMemoryEventAuditChain
```

Required replacement:

- committed canonical identity;
- verified bounded planning projection;
- exact source-set digest and canonical receipts;
- corruption failure before planning;
- no plan or link derived from unverified legacy memory.

A canonical append at the end does not repair unverified planning input at the beginning.

### F1-REST-002 — approval, execution, and autobiography

`approvePlannedRestCycle`, `appendRestCycleEvent`,
`consolidateAutobiographyFromRestCycle`, `planRestRepairProposalIfNeeded`,
`planRestCycleMigration`, and `rebuildLivingMemorySnapshot` require convergence.

Required behavior:

- approval binds the exact canonical plan digest;
- execution is recoverable through the F3 common protocol;
- autobiography is built from verified canonical records;
- signed canonical event remains authority;
- any display snapshot is a rebuildable projection;
- repair targets canonical event receipts;
- legacy `memory_snapshots` never become a replacement authority.

### F1-HEALTH-001 — `LocalNervousSystemRepository.recordHealthCheckIfDegraded`

Current legacy metrics include:

```text
countGenesisCore
countLocalIdentity
countMemoryEvents
countLivingMemorySnapshot
loadMemoryContext
```

Required replacement:

- committed identity readiness;
- canonical post-birth event count;
- verified projection availability;
- bounded canonical recent-context count;
- latest verified canonical health event for suppression.

Health is a derived report. It must never become an alternate identity or memory authority.

### F1-HEALTH-002 — `MorimilViewModel.refreshOrganismHealthOnWorker`

Current UI assembly combines legacy memory observables/counts with durable recall and migration
state. The replacement must consume a canonical health projection and durable organ state. The
UI must not open `MemoryDao` to calculate authority-bearing health.

### F1-ORCH-001 — `AgentOrchestrationRepository` birth gate

`seedDefaultOrchestrationIfNeeded`, `proposeDelegatedTask`, `approveDelegatedTask`,
`rejectDelegatedTask`, and `requireCompleteBirth` still depend on
`MemoryRepository.hasCompleteBirth()` in remaining paths.

The replacement uses committed Genesis Ultra startup/identity authority, not `LocalBirthState`.
Cross-database task decisions remain F3 work and are outside the COG candidate.

### Supporting legacy surface — `MemoryRepository` and `MemoryDao`

Remaining historical or migration APIs include:

```text
localIdentity
genesisCore
recentMemoryEvents
livingMemorySnapshot
readLocalBirthState
hasCompleteBirth
buildLivingMemoryContext
auditLivingMemoryChain
loadGenesisCore
loadLocalIdentity
loadMemoryContext
getLivingMemorySnapshot
memory_events
memory_snapshots
```

These are quarantine and migration surfaces, not replacement authorities. Existing production
appends routed through `MemoryRepository.recordSystemMemoryEvent()` already use
`CanonicalLivingMemoryPort`; convergence must not re-enable writes to `memory_events`.

### Productive callers

| Caller | Required convergence |
| --- | --- |
| `MorimilViewModel.seedRecallScheduleIfNeeded` | Expose typed blocked/corrupt state rather than silently creating zero schedules. |
| `MorimilViewModel.runRestCycleNow` | Never bypass canonical verification or approval binding. |
| `MorimilViewModel.approveRestCycleConsolidation` | Finalize the exact canonical plan idempotently. |
| `MorimilViewModel.refreshOrganismHealthOnWorker` | Use canonical derived metrics instead of `MemoryDao`. |
| `RunRestCycleUseCase` | Remain a caller, not a storage authority. |
| `RestCycleWorker.doWork` | Retry typed temporary failures and create no partial mutation on corruption. |

## Data-domain separation

### Canonical durable authority

The following must be recovered or appended through canonical repositories:

- Instance identity and active writer evidence;
- birth root and lineage;
- signed canonical memory events;
- exact event hashes, sequences and provenance;
- canonical RestCycle and health alert events.

### Durable organ state

The following is durable organ state:

- recall due time, interval, status, last action, and review time;
- user approval state for a RestCycle plan;
- RestCycle execution/recovery state;
- explicit user-curated links or classifications;
- retry/block diagnostics needed for safe recovery.

Every durable organ row binds canonical `instanceId` and canonical source receipts. It must not
embed a legacy identity fallback.

### Rebuildable projections

The following may be rebuilt from verified canonical snapshot plus durable organ state:

- recall candidate ranking and generated prompts;
- recent-memory/category counters;
- health summaries and status chips;
- RestCycle planning summaries before approval;
- automated links;
- autobiography display snapshots;
- graph views and backlinks.

Cache presence is not proof of identity, birth, memory integrity, or readiness.

## Required convergence order

The order is normative because each later step consumes guarantees established by the previous
one.

### STEP-1 — canonical read adapter

Status: **integrated by PR #148**.

`CanonicalConsumerReadPort` returns typed verified projections from
`GenesisUltraRuntimeIdentityRepository` and `CanonicalMemoryRepository`. It preserves event
hash, sequence, content/provenance binding, writer epoch, defensive byte ownership, and
fail-closed failure types.

This step being integrated does not close later consumer work.

### STEP-2 — recalls

Replace recall birth, identity, candidate and target reads. Prove deterministic idempotent
schedule/link creation and canonical targets.

### STEP-3 — rest-cycle planning

Replace planning gates and source selection with verified canonical input. Corruption produces
no plan and no organ mutation.

### STEP-4 — rest-cycle execution

Use the F3 recoverable protocol for approval, append, receipt persistence, atomic local
finalization and replay. Failure after append but before local finalization is recoverable.

### STEP-5 — health

Replace health identity/memory metrics with canonical derived projections while preserving
organ scheduler/migration state.

### STEP-6 — remove legacy gates

After consumers are converged and tested:

- remove `WAITING_FOR_CANONICAL_MEMORY_ADAPTER` only when readiness is real;
- remove productive `LocalBirthState` gates;
- retire legacy read callers;
- proceed to F3.3 schema removal in a separate migration.

## Compatibility prohibition

Compatibility rows are forbidden.

No convergence step may create, copy, seed, or reconstruct rows in:

```text
genesis_core
local_instance_identity
memory_events
```

No placeholder such as `local_instance_pending` may substitute for canonical `instanceId`.
No Body ID may substitute for the Instance.

## Required acceptance tests

### Clean Ultra installation

A clean Ultra installation with zero legacy identity/core rows obtains canonical identity and
verified memory through F1-A. Pending consumers remain explicitly blocked rather than silently
no-op or fabricating legacy rows.

### Recall idempotency

The same verified canonical snapshot produces deterministic recall/link keys; repeated seeding
is idempotent and creates no duplicates.

### Verified rest cycle

Planning uses only verified canonical sources. Approval binds the exact plan. Execution persists
an exact receipt and finalizes atomically.

### Corruption and foreign-instance failure

Corruption produces no plan and no organ mutation. Foreign Instance, wrong Body, stale epoch,
missing payload, or invalid provenance fails closed.

### Identity and Body separation

Every resulting row preserves `instanceId != bodyId`; no placeholder or Body identifier is
persisted as the Instance.

### Projection rebuild

Deleting rebuildable projection rows and rerunning reconstruction produces the same bounded
projection without changing canonical memory or identity.

Additional acceptance conditions:

- failure after append but before local finalization is recoverable;
- repeated recovery creates no duplicate event or owner row;
- no write occurs in `memory_events`;
- all required checks are green on the exact head.

## Current closure state

```text
F1_A_COMMON_READ_BOUNDARY=INTEGRATED
ISSUE_86=OPEN
ISSUE_87=CLOSED
RECALL_CONVERGED=false
REST_PLANNING_CONVERGED=false
REST_EXECUTION_CONVERGED=false
HEALTH_CONVERGED=false
LEGACY_GATES_REMOVED=false
STOP_S5=CLOSED
PR_149=DRAFT_VALIDATION_ONLY
MERGE_AUTHORIZED=false
```
