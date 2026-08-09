# Document status: CURRENT

# F1 canonical consumer convergence inventory

Inventory version: `10`

Content baseline SHA: `2d16c5c3197d492d5daed3707e97a68caa0011a6`

Content baseline parent SHA: `d7e679b9f8e0b34d44a5e702c02c436f21e4eaee`

Current protected `main` is resolved externally from `refs/heads/main`; its moving SHA is not embedded as normative truth in this document.

Historical audited F3 COG source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`

Audited ORCH-002..004 source head: `0348dccb561e576d17c45e7f8b1e38717332772b`

Audited ORCH-001 source head: `fe188fdee8eae901434a255051b6fa4f852b929b`

Audited AGENT source head: `74e072b911db692041d3716af9d0511b83ad70b7`

Audited BOOT source head: `c7710635fa172108cce87b3f7a76d6e037095864`

Audited RECALL source head: `fae8a0df3c29775317986877bce2b8eda8593d27`

Audited REST-001 source head: `3661450325237fcadb86098ec16ee45cd039bc0b`

PR `#176`: merged by squash for BOOT-001.

PR `#177`: merged by squash for post-BOOT CURRENT reconciliation.

PR `#178`: merged by squash for RECALL-001.

PR `#179`: merged by squash for post-RECALL CURRENT reconciliation.

PR `#180`: merged by squash for ORCH-001.

PR `#181`: merged by squash for post-ORCH CURRENT reconciliation.

PR `#182`: merged by squash for REST-001 canonical planning and durable execution.

Tracking: open `#86` and completed canonical-memory dependency `#87`.

Gate truth: `STOP_S5=CLOSED`.

```text
CONTENT_BASELINE_SHA=2d16c5c3197d492d5daed3707e97a68caa0011a6
CONTENT_BASELINE_PARENT_SHA=d7e679b9f8e0b34d44a5e702c02c436f21e4eaee
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
PR_172=MERGED_BY_SQUASH_HISTORICAL
PR_173=MERGED_BY_SQUASH_HISTORICAL
PR_174=MERGED_BY_SQUASH_HISTORICAL
PR_175=MERGED_BY_SQUASH_HISTORICAL
PR_176=MERGED_BY_SQUASH_HISTORICAL
PR_177=MERGED_BY_SQUASH_HISTORICAL
PR_178=MERGED_BY_SQUASH_HISTORICAL
PR_179=MERGED_BY_SQUASH_HISTORICAL
PR_180=MERGED_BY_SQUASH_HISTORICAL
PR_181=MERGED_BY_SQUASH_HISTORICAL
PR_182=MERGED_BY_SQUASH_HISTORICAL
```

This document does not close `#86`. F1-A is integrated. COG-001..004, ORCH-001..004, AGENT-001..006, BOOT-001, RECALL-001 and REST-001 now consume committed Genesis Ultra identity and/or verified canonical memory without reopening legacy identity authority. REST-002, health convergence, startup-level recall readiness and final legacy retirement remain incomplete.

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

Bounded F3 canonical adapters — `CanonicalCognitiveMigrationCommitPort`, `CanonicalOrchestrationCommitPort`, `CanonicalAgentLifecycleCommitPort`, `CanonicalRuntimeBootstrapCommitPort`, and `CanonicalRestCycleCommitPort` — are specialized exact-ensure writers, not identity sources.

## Integrated downstream consumers

`CognitiveMigrationRepository` is integrated for COG-001..004 using verified F1-A planning input.

`AgentOrchestrationRepository` is integrated for ORCH-001..004. ORCH-002..004 use committed Genesis Ultra identity and exact canonical receipts. ORCH-001 no longer receives or consults `MemoryRepository`; `seedDefaultOrchestrationIfNeeded` reads `GenesisUltraRuntimeIdentityRepository.readCommittedIdentity()` before any local seed and returns without mutation when canonical identity is absent. Agent/device rows remain rebuildable local projections, not identity authority.

`AgentInstanceLifecycleRepository` is integrated for AGENT-001..006. It no longer writes lifecycle evidence through `MemoryRepository.recordSystemMemoryEvent`; deterministic XOP commands bind committed runtime identity and canonical receipts before local owner visibility. Agent workers remain bounded ProjectVault workers and do not become identity or canonical-memory authority.

`GenesisUltraRuntimeBootstrapCoordinator.bootstrap` is integrated as BOOT-001. It stages deterministic `runtime_bootstrap.initialize`, obtains an exact canonical receipt, prepares Instance-stable workspace/project projections idempotently, finalizes MemoryOrgan seed-if-empty state behind the common XOP journal, and recovers after process death between the two databases. It creates no compatibility authority rows.

`RecallScheduleRepository.seedFromRecentMemoryIfNeeded` is integrated as RECALL-001 canonical derived rebuild. It uses `CanonicalConsumerReadPort.readRecallCandidates`, refuses legacy `genesis_core`, `local_instance_identity` and `memory_events` as recall authority, rejects placeholder Instance identity, binds projections to verified canonical event hashes, and commits schedule plus local graph link atomically in `MemoryOrganDatabase`.

RECALL remains a rebuildable projection, not memory authority. `targetEventHash` is the canonical idempotency key; `recallId` is only local projection/topology identity. Canonical NOT_READY produces no mutation; blocked verification fails closed.

`RestCycleRepository` is integrated for REST-001. Planning now uses committed `GenesisUltraRuntimeIdentityRepository` plus `CanonicalConsumerReadPort.readRestCyclePlanningInput`; it no longer receives or reads `MorimilDatabase`, `MemoryRepository`, `MemoryIntegrityCore`, `MemoryDao`, `genesis_core`, `local_instance_identity`, `memory_events`, or the legacy memory audit chain as authority.

REST-001 executes under owner-scoped `rest_cycle` XOP. The deterministic `rest_cycle.execute` operation exact-ensures a single canonical `rest_cycle.local_consolidation` event through `CanonicalRestCycleCommitPort`. Only after the exact receipt is verified are migration completion, `canonical_memory_event` links, and the autobiographical snapshot finalized atomically in `MemoryOrganDatabase`. The autobiographical snapshot is a rebuildable local projection, not canonical memory or identity authority. Process-death recovery is owner-scoped and replay-safe.

## Remaining convergence work

### F1-REST-002 — repair proposal convergence

REST-002 remains separate from the integrated REST-001 execution path. Repair proposals must not regain legacy identity/memory authority, become hidden canonical writers, or bypass the deterministic owner protocol.

### F1-HEALTH-001 — `LocalNervousSystemRepository.recordHealthCheckIfDegraded`

Legacy counts remain derived from `MemoryDao`. Health is a projection and must not become alternate identity or memory authority.

### Recall startup readiness

RECALL-001's repository boundary is integrated, but BOOT still reports `recallState=WAITING_FOR_CANONICAL_MEMORY_ADAPTER` and startup does not automatically seed/declare recall ready. That residual must remain visible; integration of the repository does not justify claiming end-to-end recall startup readiness.

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
6. RECALL-001 canonical derived read/rebuild — integrated.
7. ORCH-001 canonical identity-gated seed — integrated.
8. REST-001 canonical planning and owner-scoped durable XOP — integrated.
9. REST-002, health convergence and recall startup-readiness.
10. F3.3 irreversible legacy removal only after separate authorization.

## Current closure state

```text
CONTENT_BASELINE_SHA=2d16c5c3197d492d5daed3707e97a68caa0011a6
CONTENT_BASELINE_PARENT_SHA=d7e679b9f8e0b34d44a5e702c02c436f21e4eaee
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
F1_A_COMMON_READ_BOUNDARY=INTEGRATED
F3_COG_CONSUMER_OF_F1_A=INTEGRATED_IN_MAIN
ORCH_002_004_CANONICAL_WRITE_PATH=INTEGRATED_IN_MAIN
F1_ORCH_001=INTEGRATED_IN_MAIN
ORCH_001_CANONICAL_IDENTITY_GATE=INTEGRATED_IN_MAIN
AGENT_001_006_CANONICAL_WRITE_PATH=INTEGRATED_IN_MAIN
BOOT_001_CANONICAL_WRITE_PATH=INTEGRATED_IN_MAIN
F1_RECALL_001=INTEGRATED_IN_MAIN
RECALL_CANONICAL_READ_PATH=INTEGRATED_IN_MAIN
F1_REST_001=INTEGRATED_IN_MAIN
REST_001_CANONICAL_XOP=INTEGRATED_IN_MAIN
REST_PLANNING_CONVERGED=true
REST_EXECUTION_CONVERGED=true
REST_002=OPEN
ISSUE_86=OPEN
ISSUE_87=CLOSED
BOOT_CONVERGED=true
RECALL_CONVERGED=false
RECALL_BOOT_READINESS=OPEN
HEALTH_CONVERGED=false
HEALTH_CONVERGENCE=OPEN
LEGACY_GATES_REMOVED=false
F3_3=OPEN
STOP_S5=CLOSED
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```
