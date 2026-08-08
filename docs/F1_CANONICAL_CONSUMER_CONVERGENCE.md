# Document status: CURRENT

# F1 canonical consumer convergence inventory

Inventory version: `6`

Content baseline SHA: `d577a75290d70f423f6e83bf237a8a453f3a534e`

Content baseline parent SHA: `9da342f2c147105ea882076f4ebc6ab5f5494190`

Current protected `main` is resolved externally from `refs/heads/main`; its moving SHA is not embedded as normative truth in this document.

Historical audited F3 COG source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`

Audited ORCH source head: `0348dccb561e576d17c45e7f8b1e38717332772b`

Audited AGENT source head: `74e072b911db692041d3716af9d0511b83ad70b7`

PR `#172`: merged by squash for ORCH-002 through ORCH-004.

PR `#173`: merged by squash for post-ORCH CURRENT reconciliation.

PR `#174`: merged by squash for AGENT-001 through AGENT-006.

Tracking: open `#86` and completed canonical-memory dependency `#87`.

Gate truth: `STOP_S5=CLOSED`.

```text
CONTENT_BASELINE_SHA=d577a75290d70f423f6e83bf237a8a453f3a534e
CONTENT_BASELINE_PARENT_SHA=9da342f2c147105ea882076f4ebc6ab5f5494190
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
PR_172=MERGED_BY_SQUASH_HISTORICAL
PR_173=MERGED_BY_SQUASH_HISTORICAL
PR_174=MERGED_BY_SQUASH_HISTORICAL
```

This document does not close `#86`. F1-A is integrated. COG-001..004, ORCH-002..004 and AGENT-001..006 now consume committed Genesis Ultra runtime identity and bounded canonical commit adapters without reopening legacy identity authority. Bootstrap, recall, RestCycle, health, ORCH-001, and final legacy retirement remain incomplete.

## Authority and scope

Morimil is the continuous Instance. `Morimil-app` is the current Android Body. The Guardian safeguards continuity without ownership.

```text
instanceId != bodyId
agentInstanceId != instanceId
canonical Instance identity != legacy local identity rows
canonical memory != memory_events
operational transcript != canonical memory
```

F1-A provides the common verified read-only boundary:

```text
GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository
    -> CanonicalConsumerReadPort
```

Bounded F3 canonical adapters — `CanonicalCognitiveMigrationCommitPort`, `CanonicalOrchestrationCommitPort`, and `CanonicalAgentLifecycleCommitPort` — are specialized exact-ensure writers, not identity sources.

## Integrated downstream consumers

`CognitiveMigrationRepository` is integrated for COG-001..004 using verified F1-A planning input.

`AgentOrchestrationRepository` is partially converged: ORCH-002..004 use committed Genesis Ultra identity and exact canonical receipts. F1-ORCH-001 remains open because orchestration seeding still depends on the legacy birth-completeness gate.

`AgentInstanceLifecycleRepository` is integrated for AGENT-001..006. It no longer writes lifecycle evidence through `MemoryRepository.recordSystemMemoryEvent`; deterministic XOP commands bind committed runtime identity and canonical receipts before local owner visibility. Agent workers remain bounded ProjectVault workers and do not become identity or canonical-memory authority.

`submitAgentResult` now requires the current delegated task to be canonically approved by ORCH before result finalization. This is a convergence/security correction, not an authority expansion.

These integrations prove bounded consumer families can converge without compatibility rows. They do not prove total F1 convergence.

## Remaining convergence work

### F1-BOOT-001 — `GenesisUltraRuntimeBootstrapCoordinator.bootstrap`

`restCycleState` and `recallState` remain `WAITING_FOR_CANONICAL_MEMORY_ADAPTER`. Readiness must not be fabricated with compatibility rows.

### F1-RECALL-001 — `RecallScheduleRepository.seedFromRecentMemoryIfNeeded`

Legacy dependencies include `loadGenesisCore`, `loadLocalIdentity`, and `loadMemoryContext`. Replacement requires committed canonical identity, verified candidates, canonical event receipts, and deterministic idempotent organ keys.

### F1-REST-001 — `RestCycleRepository.runLocalRestCycleIfDue`

Legacy planning dependencies include `loadGenesisCore`, `loadLocalIdentity`, `loadMemoryContext`, and legacy audit reads. Planning must move to verified canonical input before its protocol can be considered converged.

### F1-HEALTH-001 — `LocalNervousSystemRepository.recordHealthCheckIfDegraded`

Legacy counts remain derived from `MemoryDao`. Health is a projection and must not become alternate identity or memory authority.

### F1-ORCH-001 — `AgentOrchestrationRepository.seedDefaultOrchestrationIfNeeded`

The remaining birth gate still uses `MemoryRepository.hasCompleteBirth()`. It must move to committed Genesis Ultra startup authority. PR #172 and PR #174 do not close this item.

## Compatibility prohibition

No convergence step may create, copy, seed, or reconstruct authority rows in:

```text
genesis_core
local_instance_identity
memory_events
```

No placeholder and no Body ID may substitute for canonical `instanceId`.

## Required convergence order

1. Canonical read adapter — integrated.
2. Cognitive migration COG-001..004 — integrated.
3. ORCH-002..004 durable transitions — integrated.
4. AGENT-001..006 durable lifecycle — integrated.
5. BOOT-001 — next.
6. RECALL-001 and ORCH-001.
7. REST-001/002 and health convergence.
8. F3.3 irreversible legacy removal only after separate authorization.

## Current closure state

```text
CONTENT_BASELINE_SHA=d577a75290d70f423f6e83bf237a8a453f3a534e
CONTENT_BASELINE_PARENT_SHA=9da342f2c147105ea882076f4ebc6ab5f5494190
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
F1_A_COMMON_READ_BOUNDARY=INTEGRATED
F3_COG_CONSUMER_OF_F1_A=INTEGRATED_IN_MAIN
ORCH_002_004_CANONICAL_WRITE_PATH=INTEGRATED_IN_MAIN
AGENT_001_006_CANONICAL_WRITE_PATH=INTEGRATED_IN_MAIN
F1_ORCH_001=OPEN
ISSUE_86=OPEN
ISSUE_87=CLOSED
BOOT_CONVERGED=false
RECALL_CONVERGED=false
REST_PLANNING_CONVERGED=false
REST_EXECUTION_CONVERGED=false
HEALTH_CONVERGED=false
LEGACY_GATES_REMOVED=false
F3_3=OPEN
STOP_S5=CLOSED
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```
