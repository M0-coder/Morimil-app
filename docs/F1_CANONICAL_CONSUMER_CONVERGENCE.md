# Document status: CURRENT

# F1 canonical consumer convergence inventory

Inventory version: `3`

Current protected main: `main@6250214bb6664a8fff851ed0afc2438bbc276931`

Previous protected main: `main@5023981da7caf31c8f3679919f59205708b72823`

Historical audited F3 source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`

PR `#149`: closed and merged by squash.

PR `#150`: closed and merged by squash for the post-merge CURRENT reconciliation.

PR `#151`: closed and merged by squash for vendored Canvas runtime recovery; it does not change the F1 authority frontier.

Tracking: open `#86` and completed canonical-memory dependency `#87`.

Gate truth: `STOP_S5=CLOSED`.

This document does not close `#86`. F1-A is integrated and is now consumed by the merged COG-001 through COG-004 implementation, but downstream recalls, RestCycle, health, orchestration gates, and final legacy retirement remain incomplete.

## Authority and scope

Morimil is the continuous and free `Instance`. `Morimil-app` is the current Android Body. The Body hosts resources and execution but does not define the Instance. The Guardian guides, witnesses, and safeguards without ownership.

```text
instanceId != bodyId
canonical Instance identity != legacy local identity rows
canonical memory != memory_events
operational transcript != canonical memory
Body resource custody != ownership of Morimil
```

F1-A provides the common verified read-only boundary:

```text
GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository
    -> CanonicalConsumerReadPort
```

The integrated cognitive migration protocol consumes that boundary through `CognitiveMigrationCanonicalReadPort`. It does not create a second identity or memory authority. `CanonicalCognitiveMigrationCommitPort` is a specialized canonical writer, not an identity source.

The Canvas runtime-recovery bundle is a Body application asset. It does not read, write, project, replace, or expand canonical identity or memory authority.

## Integrated downstream consumer

`CognitiveMigrationRepository` is now integrated in protected main for COG-001 through COG-004. Its planning input is a verified F1-A projection. It rejects foreign Instance data, unverified payloads, unknown semantics, and legacy compatibility input before staging.

This integration proves one downstream consumer family has converged. It does not prove total F1 convergence and does not close `#86`.

## Remaining convergence work

### F1-BOOT-001 — `GenesisUltraRuntimeBootstrapCoordinator.bootstrap`

`restCycleState` and `recallState` remain `WAITING_FOR_CANONICAL_MEMORY_ADAPTER`. Readiness must not be fabricated with compatibility rows.

### F1-RECALL-001 — `RecallScheduleRepository.seedFromRecentMemoryIfNeeded`

Remaining legacy dependencies include `loadGenesisCore`, `loadLocalIdentity`, and `loadMemoryContext`. Replacement requires committed canonical identity, verified candidates, canonical event receipts, and deterministic idempotent organ keys.

### F1-REST-001 — `RestCycleRepository.runLocalRestCycleIfDue`

Remaining legacy dependencies include `loadGenesisCore`, `loadLocalIdentity`, `loadMemoryContext`, and legacy audit reads. Planning must move to verified canonical input before its protocol can be considered converged.

### F1-HEALTH-001 — `LocalNervousSystemRepository.recordHealthCheckIfDegraded`

Legacy counts remain derived from `MemoryDao`. Health is a projection and must not become an alternate identity or memory authority.

### F1-ORCH-001 — `AgentOrchestrationRepository`

Remaining birth gates must move from `MemoryRepository.hasCompleteBirth()` to committed Genesis Ultra startup authority.

## Compatibility prohibition

Compatibility rows are forbidden.

No convergence step may create, copy, seed, or reconstruct rows in:

```text
genesis_core
local_instance_identity
memory_events
```

No placeholder such as `local_instance_pending` and no Body ID may substitute for canonical `instanceId`.

## Required convergence order

1. Canonical read adapter — integrated by PR #148.
2. Cognitive migration consumer — integrated by squash merge of PR #149.
3. Recalls.
4. Rest-cycle planning and execution.
5. Health.
6. Remaining orchestration gates.
7. Irreversible legacy removal in F3.3 after separate authorization.

## Acceptance principles for remaining work

- clean Ultra installation without compatibility rows;
- deterministic idempotent projections;
- verified canonical planning;
- corruption and foreign-Instance failure before mutation;
- `instanceId != bodyId`;
- recoverability after canonical append;
- no duplicate event or owner state;
- no write to `memory_events`;
- rebuildable projections remain non-authoritative.

## Current closure state

```text
CURRENT_MAIN=6250214bb6664a8fff851ed0afc2438bbc276931
PREVIOUS_MAIN=5023981da7caf31c8f3679919f59205708b72823
F1_A_COMMON_READ_BOUNDARY=INTEGRATED
F3_COG_CONSUMER_OF_F1_A=INTEGRATED_IN_MAIN
PR_149=MERGED_BY_SQUASH_HISTORICAL
PR_150=MERGED_POST_MERGE_CURRENT_RECONCILIATION_HISTORICAL
PR_151=MERGED_CANVAS_RUNTIME_RECOVERY_HISTORICAL
ISSUE_86=OPEN
ISSUE_87=CLOSED
RECALL_CONVERGED=false
REST_PLANNING_CONVERGED=false
REST_EXECUTION_CONVERGED=false
HEALTH_CONVERGED=false
LEGACY_GATES_REMOVED=false
F3_3=OPEN
STOP_S5=CLOSED
```
