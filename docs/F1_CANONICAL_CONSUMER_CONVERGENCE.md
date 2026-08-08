# Document status: CURRENT

# F1 canonical consumer convergence inventory

Inventory version: `5`

Content baseline SHA: `c6a6b0ca998d053c31c75977c5b6d4d9ae166e96`

Content baseline parent SHA: `c22920f68f8820bbec676a6cbc74b60548e43d29`

Current protected `main` is resolved externally from `refs/heads/main`; its moving SHA is not embedded as normative truth in this document.

Historical audited F3 COG source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`

Audited ORCH source head: `0348dccb561e576d17c45e7f8b1e38717332772b`

PR `#149`: closed and merged by squash.

PR `#150`: closed and merged by squash for a historical CURRENT reconciliation.

PR `#151`: closed and merged by squash for vendored Canvas runtime recovery; it does not change the F1 authority frontier.

PR `#153`: closed and merged by squash for a historical CURRENT reconciliation.

PR `#172`: closed and merged by squash for ORCH-002 through ORCH-004.

Tracking: open `#86` and completed canonical-memory dependency `#87`.

Gate truth: `STOP_S5=CLOSED`.

```text
CONTENT_BASELINE_SHA=c6a6b0ca998d053c31c75977c5b6d4d9ae166e96
CONTENT_BASELINE_PARENT_SHA=c22920f68f8820bbec676a6cbc74b60548e43d29
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
PR_153=MERGED_BY_SQUASH_HISTORICAL
PR_172=MERGED_BY_SQUASH_HISTORICAL
```

This document does not close `#86`. F1-A is integrated. COG-001 through COG-004 consume the verified F1-A projection, and ORCH-002 through ORCH-004 now use committed Genesis Ultra runtime identity plus the canonical orchestration commit boundary. Downstream recalls, RestCycle, health, ORCH-001, and final legacy retirement remain incomplete.

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

The integrated ORCH-002/003/004 path does not reopen legacy identity authority. It resolves committed runtime identity through `GenesisUltraRuntimeIdentityRepository` and writes canonical orchestration evidence through `CanonicalOrchestrationCommitPort`, which is likewise a bounded canonical ensure adapter rather than an identity source.

The Canvas runtime-recovery bundle is a Body application asset. It does not read, write, project, replace, or expand canonical identity or memory authority.

## Integrated downstream consumers

`CognitiveMigrationRepository` is integrated in protected main for COG-001 through COG-004. Its planning input is a verified F1-A projection. It rejects foreign Instance data, unverified payloads, unknown semantics, and legacy compatibility input before staging.

`AgentOrchestrationRepository` is partially converged: ORCH-002 through ORCH-004 no longer perform a local owner mutation followed by a legacy memory write. Their durable transitions require committed Genesis Ultra identity, exact canonical receipt, and typed local finalization. This does not close F1-ORCH-001 because the orchestration seeding gate remains legacy-dependent.

These integrations prove bounded consumer families can converge without compatibility rows. They do not prove total F1 convergence and do not close `#86`.

## Remaining convergence work

### F1-BOOT-001 — `GenesisUltraRuntimeBootstrapCoordinator.bootstrap`

`restCycleState` and `recallState` remain `WAITING_FOR_CANONICAL_MEMORY_ADAPTER`. Readiness must not be fabricated with compatibility rows.

### F1-RECALL-001 — `RecallScheduleRepository.seedFromRecentMemoryIfNeeded`

Remaining legacy dependencies include `loadGenesisCore`, `loadLocalIdentity`, and `loadMemoryContext`. Replacement requires committed canonical identity, verified candidates, canonical event receipts, and deterministic idempotent organ keys.

### F1-REST-001 — `RestCycleRepository.runLocalRestCycleIfDue`

Remaining legacy dependencies include `loadGenesisCore`, `loadLocalIdentity`, `loadMemoryContext`, and legacy audit reads. Planning must move to verified canonical input before its protocol can be considered converged.

### F1-HEALTH-001 — `LocalNervousSystemRepository.recordHealthCheckIfDegraded`

Legacy counts remain derived from `MemoryDao`. Health is a projection and must not become an alternate identity or memory authority.

### F1-ORCH-001 — `AgentOrchestrationRepository.seedDefaultOrchestrationIfNeeded`

The remaining birth gate still uses `MemoryRepository.hasCompleteBirth()`. It must move to committed Genesis Ultra startup authority. PR #172 intentionally does not close this item.

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
3. ORCH-002 through ORCH-004 durable write transitions — integrated by squash merge of PR #172.
4. Agent lifecycle owner family — AGENT-001 through AGENT-006.
5. Bootstrap protocol.
6. Recalls and ORCH-001 convergence.
7. Rest-cycle planning/execution and health convergence as their canonical inputs are retired from legacy authority.
8. Irreversible legacy removal in F3.3 after separate authorization.

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
CONTENT_BASELINE_SHA=c6a6b0ca998d053c31c75977c5b6d4d9ae166e96
CONTENT_BASELINE_PARENT_SHA=c22920f68f8820bbec676a6cbc74b60548e43d29
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
F1_A_COMMON_READ_BOUNDARY=INTEGRATED
F3_COG_CONSUMER_OF_F1_A=INTEGRATED_IN_MAIN
ORCH_002_004_CANONICAL_WRITE_PATH=INTEGRATED_IN_MAIN
F1_ORCH_001=OPEN
PR_149=MERGED_BY_SQUASH_HISTORICAL
PR_150=MERGED_POST_MERGE_CURRENT_RECONCILIATION_HISTORICAL
PR_151=MERGED_CANVAS_RUNTIME_RECOVERY_HISTORICAL
PR_153=MERGED_BY_SQUASH_HISTORICAL
PR_172=MERGED_BY_SQUASH_HISTORICAL
ISSUE_86=OPEN
ISSUE_87=CLOSED
RECALL_CONVERGED=false
REST_PLANNING_CONVERGED=false
REST_EXECUTION_CONVERGED=false
HEALTH_CONVERGED=false
LEGACY_GATES_REMOVED=false
F3_3=OPEN
STOP_S5=CLOSED
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```
