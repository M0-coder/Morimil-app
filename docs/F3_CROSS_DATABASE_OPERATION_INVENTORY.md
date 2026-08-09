# Document status: CURRENT

# F3.2 — Cross-database operation inventory

- Inventory version: `10`.
- Content baseline SHA: `2d16c5c3197d492d5daed3707e97a68caa0011a6`.
- Content baseline parent SHA: `d7e679b9f8e0b34d44a5e702c02c436f21e4eaee`.
- Current protected `main`: resolved externally from `refs/heads/main`.
- Merge SHA evidence: external GitHub and Morimil Control Tower evidence.
- Historical COG audited source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
- ORCH-002..004 audited source head: `0348dccb561e576d17c45e7f8b1e38717332772b`.
- ORCH-001 audited source head: `fe188fdee8eae901434a255051b6fa4f852b929b`.
- AGENT audited source head: `74e072b911db692041d3716af9d0511b83ad70b7`.
- BOOT audited source head: `c7710635fa172108cce87b3f7a76d6e037095864`.
- RECALL audited source head: `fae8a0df3c29775317986877bce2b8eda8593d27`.
- REST-001 audited source head: `3661450325237fcadb86098ec16ee45cd039bc0b`.
- PR `#176`: merged by squash for BOOT-001.
- PR `#177`: merged by squash for post-BOOT CURRENT reconciliation.
- PR `#178`: merged by squash for RECALL-001.
- PR `#179`: merged by squash for post-RECALL CURRENT reconciliation.
- PR `#180`: merged by squash for ORCH-001.
- PR `#181`: merged by squash for post-ORCH CURRENT reconciliation.
- PR `#182`: merged by squash for REST-001.
- Tracker: `#88` — open for remaining F3 owners.
- Protocol: `docs/adr/ADR-0002-cross-database-operation-protocol.md`.
- Gate: `STOP_S5=CLOSED`.

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
REST_001=INTEGRATED
REST_002=OPEN
HEALTH_CONVERGENCE=OPEN
RECALL_BOOT_READINESS=OPEN
```

## Authority model

Morimil is the continuous Instance; `Morimil-app` is the current Android Body. The Guardian safeguards without ownership. `instanceId != bodyId` and `agentInstanceId != instanceId` remain mandatory.

Neither the XOP journal nor an owner repository becomes a second identity or canonical-memory authority. Writer Body/epoch authorization is not ownership. BOOT, ORCH seed, recall runtime projections and REST local projections remain rebuildable/bounded structures and do not become identity authority.

## Protocol classifications

| Classification | Meaning |
| --- | --- |
| `PROTECTED_REFERENCE` | Separate protected protocol with durable staging/recovery. |
| `INTEGRATED_PROTOCOL` | Common XOP journal implemented in protected main for this owner scope. |
| `REQUIRES_PROTOCOL` | Cross-database transition still awaiting isolated audited protocol. |
| `DERIVED_REBUILD` | Rebuildable state requiring deterministic verified reconstruction. |
| `SUPPORT_BOUNDARY` | Participates in another owner finalization without independent protocol ownership. |
| `MIXED_DISPOSITION` | Repository contains integrated operations plus separately open convergence work. |

## Versioned owner inventory

| Owner path | Classification | Current disposition |
| --- | --- | --- |
| `app/src/main/java/com/morimil/app/data/repository/ProjectVaultRepository.kt` | `PROTECTED_REFERENCE` | Integrated and separate. |
| `app/src/main/java/com/morimil/app/runtime/GenesisUltraRuntimeBootstrapCoordinator.kt` | `INTEGRATED_PROTOCOL` | BOOT-001 integrated; deterministic runtime bootstrap and owner-scoped recovery use the common XOP journal. |
| `app/src/main/java/com/morimil/app/data/repository/RuntimeBootstrapProtocolFinalizer.kt` | `SUPPORT_BOUNDARY` | Integrated BOOT-001 finalization support; not independently authoritative. |
| `app/src/main/java/com/morimil/app/data/repository/RecallScheduleRepository.kt` | `DERIVED_REBUILD` | RECALL-001 canonical derived rebuild integrated; startup-level recall readiness remains separately open. |
| `app/src/main/java/com/morimil/app/data/repository/RestCycleRepository.kt` | `INTEGRATED_PROTOCOL` | REST-001 integrated under owner-scoped `rest_cycle` XOP; REST-002 is separate open work and is not executed by this path. |
| `app/src/main/java/com/morimil/app/data/repository/CognitiveMigrationRepository.kt` | `INTEGRATED_PROTOCOL` | COG-001 through COG-004 integrated. |
| `app/src/main/java/com/morimil/app/data/repository/AgentOrchestrationRepository.kt` | `INTEGRATED_PROTOCOL` | ORCH-002 through ORCH-004 use common XOP; ORCH-001 canonical identity-gated seed convergence is integrated and remains a local projection path rather than a new XOP operation. |
| `app/src/main/java/com/morimil/app/data/repository/AgentInstanceLifecycleRepository.kt` | `INTEGRATED_PROTOCOL` | AGENT-001 through AGENT-006 integrated. |
| `app/src/main/java/com/morimil/app/data/repository/MigrationRecordRepository.kt` | `SUPPORT_BOUNDARY` | Typed COG finalization support. |

## Integrated operation inventory

### ProjectVault protected reference

| ID | Entry point | Status |
| --- | --- | --- |
| `PV-001` | `createProjectVaultFromIntent` | Integrated protected reference. |
| `PV-002` | `completeProjectVault` | Integrated protected reference. |
| `PV-003` | `archiveProjectVault` | Integrated protected reference. |

### Cognitive migration

| ID | Entry point | Current state |
| --- | --- | --- |
| `COG-001` | `proposeCognitiveMigration` | Integrated. |
| `COG-002` | `approveCognitiveMigration` | Integrated. |
| `COG-003` | `executeCognitiveMigration` | Integrated. |
| `COG-004` | `rollbackCognitiveMigration` | Integrated. |

### Orchestration

| ID | Entry point | Current state |
| --- | --- | --- |
| `ORCH-001` | `seedDefaultOrchestrationIfNeeded` | Integrated F1 convergence: committed Genesis Ultra identity is checked before any local seed; no legacy birth-completeness authority. |
| `ORCH-002` | `proposeDelegatedTask` | Integrated. |
| `ORCH-003` | `approveDelegatedTask` | Integrated. |
| `ORCH-004` | `rejectDelegatedTask` | Integrated. |

ORCH-001 does not add an XOP event. Its profiles/devices are local rebuildable projections gated by committed canonical identity. ORCH-002..004 remain the journaled cross-database owner operations.

### Agent lifecycle

| ID | Entry point | Current state |
| --- | --- | --- |
| `AGENT-001` | `createAgentForVault` | Integrated. |
| `AGENT-002` | `assignTaskToAgent` | Integrated. |
| `AGENT-003` | `submitAgentResult` | Integrated; requires canonical ORCH approval. |
| `AGENT-004` | `evaluateAgent` | Integrated. |
| `AGENT-005` | `retireAgent`, `promoteAgent` | Integrated as separate durable operation types. |
| `AGENT-006` | `quarantineAgent` | Integrated; failed worker quarantine and deterministic replacement finalize after one canonical receipt. |

### Runtime bootstrap

| ID | Entry point | Current state |
| --- | --- | --- |
| `BOOT-001` | `bootstrap` | Integrated: deterministic Instance/Body/epoch-scoped XOP, exact canonical receipt before new BOOT projection state, idempotent preparation and owner-scoped recovery. |

### Recall derived rebuild

| ID | Entry point | Current state |
| --- | --- | --- |
| `RECALL-001` | `seedFromRecentMemoryIfNeeded` | Integrated derived rebuild: verified `CanonicalConsumerReadPort` candidates, canonical event-hash idempotency, fail-closed verification, atomic local schedule+link projection, no legacy identity/memory authority. |

RECALL does not own a cross-database XOP because its local schedule is a rebuildable projection derived from verified canonical memory. Its remaining startup-readiness wiring is not reclassified as a second authority.

### REST cycle

| ID | Entry point | Current state |
| --- | --- | --- |
| `REST-001` | `runLocalRestCycleIfDue`, `approvePlannedRestCycle` | Integrated canonical protocol: verified canonical planning input, deterministic owner `rest_cycle`, exact `rest_cycle.local_consolidation` receipt, atomic local migration/link/autobiography finalization and replay-safe recovery. |

REST-001 has one canonical effect. The autobiographical snapshot is a rebuildable local projection bound to the exact canonical receipt. REST-002 repair proposal work is deliberately outside this execution path.

## Remaining operations

| ID | Entry point | Disposition |
| --- | --- | --- |
| `REST-002` | repair-proposal path | `REQUIRES_PROTOCOL`; open. |
| `MIG-001` | `planMigration`, `markMigrationApproved`, `markMigrationCompleted`, `markMigrationFailed`, `markMigrationRolledBack` | `SUPPORT_BOUNDARY`. |

## Integrated guarantees

Within COG, ORCH-002..004, AGENT, BOOT and REST-001 bounded scopes, common XOP guarantees remain deterministic identities, hidden staging, exact canonical ensure/receipt, owner-scoped recovery, stale-block prevention, atomic owner finalization, typed failures and replay safety.

ORCH-001 separately guarantees:

1. canonical committed identity is consulted before any local seed mutation;
2. absent identity produces no seed mutation;
3. inconsistent committed identity fails closed through the canonical identity repository;
4. no `MemoryRepository.hasCompleteBirth()` dependency remains in orchestration seeding;
5. agent/device rows remain rebuildable local projection state rather than identity authority.

RECALL-001 separately guarantees:

1. canonical verified read input only;
2. no `genesis_core`, `local_instance_identity` or `memory_events` authority read for seeding;
3. no placeholder Instance identity;
4. deterministic canonical candidate ordering;
5. `targetEventHash` as idempotency key;
6. local `recallId` only as projection/topology identity;
7. schedule + local graph link in one `MemoryOrganDatabase` transaction;
8. no mutation on canonical NOT_READY and fail-closed behavior on blocked verification;
9. legacy reconciliation cannot orphan a canonical-derived recall merely because its target is absent from legacy `memory_events`.

REST-001 separately guarantees:

1. committed Genesis Ultra identity plus verified `CanonicalConsumerReadPort.readRestCyclePlanningInput` are the only planning authority;
2. legacy `genesis_core`, `local_instance_identity`, `memory_events`, `MemoryRepository` and audit-chain planning reads do not authorize the cycle;
3. canonical NOT_READY produces no REST mutation and blocked verification fails closed;
4. deterministic migration/operation/event identity and owner-scoped `rest_cycle` recovery;
5. one exact canonical `rest_cycle.local_consolidation` event through `CanonicalRestCycleCommitPort`;
6. migration completion, `canonical_memory_event` links and autobiography finalize atomically after the receipt;
7. the autobiographical snapshot remains a rebuildable local projection;
8. process-death recovery reuses the exact receipt and does not replay the canonical writer;
9. REST-002 remains outside this path.

No compatibility write to `memory_events`, `genesis_core`, or `local_instance_identity` is authorized.

## Implementation order after STOP S5

1. `COG-001` through `COG-004` — integrated.
2. `ORCH-002` through `ORCH-004` — integrated.
3. `AGENT-001` through `AGENT-006` — integrated.
4. `BOOT-001` — integrated.
5. `RECALL-001` — integrated canonical derived rebuild.
6. `ORCH-001` — integrated canonical identity-gated seed convergence.
7. `REST-001` — integrated canonical planning and owner-scoped durable XOP.
8. `REST-002`, health convergence and recall startup-readiness.
9. F3.3 only after every F3.2 owner has a recorded disposition and separate authorization.

## Residual hardening

- REST-specific mutation testing is not established; the successful global mutation pilot remains report-only;
- RECALL-specific mutation testing is not established; the existing report-only PIT pilot remains Genesis-scoped;
- BOOT-specific mutation testing is not established;
- AGENT-specific mutation testing is not established;
- ORCH-specific mutation testing remains unestablished;
- BOOT still reports recall as `WAITING_FOR_CANONICAL_MEMORY_ADAPTER`; repository convergence does not equal startup-level recall readiness;
- `REST_002=OPEN`;
- `HEALTH_CONVERGENCE=OPEN`;
- continuous physical ARM64 inference remains outside emulator CI.

These findings are not evidence of operational birth.
