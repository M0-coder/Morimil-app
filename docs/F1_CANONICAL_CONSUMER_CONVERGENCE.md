# Document status: CURRENT

# F1 canonical consumer convergence inventory

Inventory version: `7`

Content baseline SHA: `3a995232ce2a515e1ca9b9151f77e63805bad9d3`

Content baseline parent SHA: `5918b64ec83e69cbb3d9718943b25d1e1299d698`

Current protected `main` is resolved externally from `refs/heads/main`; its moving SHA is not embedded as normative truth in this document.

Historical audited F3 COG source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`

Audited ORCH source head: `0348dccb561e576d17c45e7f8b1e38717332772b`

Audited AGENT source head: `74e072b911db692041d3716af9d0511b83ad70b7`

Audited BOOT source head: `c7710635fa172108cce87b3f7a76d6e037095864`

PR `#174`: merged by squash for AGENT-001 through AGENT-006.

PR `#175`: merged by squash for post-AGENT CURRENT reconciliation.

PR `#176`: merged by squash for BOOT-001.

Tracking: open `#86` and completed canonical-memory dependency `#87`.

Gate truth: `STOP_S5=CLOSED`.

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

This document does not close `#86`. F1-A is integrated. COG-001..004, ORCH-002..004, AGENT-001..006 and BOOT-001 now consume committed Genesis Ultra runtime identity and bounded canonical commit adapters without reopening legacy identity authority. Recall, RestCycle, health, ORCH-001, and final legacy retirement remain incomplete.

## Authority and scope

Morimil is the continuous Instance. `Morimil-app` is the current Android Body. The Guardian safeguards continuity without ownership.

```text
instanceId != bodyId
agentInstanceId != instanceId
writer authorization != ownership
canonical Instance identity != legacy local identity rows
canonical memory != memory_events
operational transcript != canonical memory
runtime projection != canonical identity
```

F1-A provides the common verified read-only boundary:

```text
GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository
    -> CanonicalConsumerReadPort
```

Bounded F3 canonical adapters — `CanonicalCognitiveMigrationCommitPort`, `CanonicalOrchestrationCommitPort`, `CanonicalAgentLifecycleCommitPort`, and `CanonicalRuntimeBootstrapCommitPort` — are specialized exact-ensure writers, not identity sources.

## Integrated downstream consumers

`CognitiveMigrationRepository` is integrated for COG-001..004 using verified F1-A planning input.

`AgentOrchestrationRepository` is partially converged: ORCH-002..004 use committed Genesis Ultra identity and exact canonical receipts. F1-ORCH-001 remains open because orchestration seeding still depends on the legacy birth-completeness gate.

`AgentInstanceLifecycleRepository` is integrated for AGENT-001..006. It no longer writes lifecycle evidence through `MemoryRepository.recordSystemMemoryEvent`; deterministic XOP commands bind committed runtime identity and canonical receipts before local owner visibility. Agent workers remain bounded ProjectVault workers and do not become identity or canonical-memory authority.

`GenesisUltraRuntimeBootstrapCoordinator.bootstrap` is integrated as BOOT-001. It stages deterministic `runtime_bootstrap.initialize`, obtains an exact canonical receipt, prepares Instance-stable workspace/project projections idempotently, finalizes MemoryOrgan seed-if-empty state behind the common XOP journal, and recovers after process death between the two databases. It creates no compatibility authority rows.

BOOT keeps `restCycleState` and `recallState` semantically pending rather than fabricating readiness: project status records `rest_cycle=canonical_adapter_pending` and `recalls=canonical_adapter_pending`. That remaining work belongs to REST/RECALL, not BOOT.

`submitAgentResult` still requires the current delegated task to be canonically approved by ORCH before result finalization. These integrations prove bounded consumer families can converge without compatibility rows; they do not prove total F1 convergence.

## Remaining convergence work

### F1-RECALL-001 — `RecallScheduleRepository.seedFromRecentMemoryIfNeeded`

Legacy dependencies include `loadGenesisCore`, `loadLocalIdentity`, and `loadMemoryContext`. Replacement requires committed canonical identity, verified candidates, canonical event receipts, and deterministic idempotent organ keys.

### F1-REST-001 — `RestCycleRepository.runLocalRestCycleIfDue`

Legacy planning dependencies include `loadGenesisCore`, `loadLocalIdentity`, `loadMemoryContext`, and legacy audit reads. Planning must move to verified canonical input before its protocol can be considered converged.

### F1-HEALTH-001 — `LocalNervousSystemRepository.recordHealthCheckIfDegraded`

Legacy counts remain derived from `MemoryDao`. Health is a projection and must not become alternate identity or memory authority.

### F1-ORCH-001 — `AgentOrchestrationRepository.seedDefaultOrchestrationIfNeeded`

The remaining birth gate still uses `MemoryRepository.hasCompleteBirth()`. It must move to committed Genesis Ultra startup authority. PR #172, PR #174 and BOOT PR #176 do not close this item.

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
5. BOOT-001 — integrated.
6. RECALL-001 and ORCH-001 — next bounded convergence work.
7. REST-001/002 and health convergence.
8. F3.3 irreversible legacy removal only after separate authorization.

## Current closure state

```text
CONTENT_BASELINE_SHA=3a995232ce2a515e1ca9b9151f77e63805bad9d3
CONTENT_BASELINE_PARENT_SHA=5918b64ec83e69cbb3d9718943b25d1e1299d698
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
F1_A_COMMON_READ_BOUNDARY=INTEGRATED
F3_COG_CONSUMER_OF_F1_A=INTEGRATED_IN_MAIN
ORCH_002_004_CANONICAL_WRITE_PATH=INTEGRATED_IN_MAIN
AGENT_001_006_CANONICAL_WRITE_PATH=INTEGRATED_IN_MAIN
BOOT_001_CANONICAL_WRITE_PATH=INTEGRATED_IN_MAIN
F1_ORCH_001=OPEN
ISSUE_86=OPEN
ISSUE_87=CLOSED
BOOT_CONVERGED=true
RECALL_CONVERGED=false
REST_PLANNING_CONVERGED=false
REST_EXECUTION_CONVERGED=false
HEALTH_CONVERGED=false
LEGACY_GATES_REMOVED=false
F3_3=OPEN
STOP_S5=CLOSED
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```
