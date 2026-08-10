# Document status: CURRENT

# F3.2 — Cross-database operation inventory

- Inventory version: `14`.
- Content baseline SHA: `c4b192b8f54b2422ce816dc3542d55adfd44510c`.
- Content baseline parent SHA: `9c7325e6f1a21d79b1c3fb58f0b5f81a828fc304`.
- Current protected `main`: resolved externally from `refs/heads/main`.
- Merge SHA evidence: external GitHub and Morimil Control Tower evidence.
- Historical COG audited source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
- ORCH-002..004 audited source head: `0348dccb561e576d17c45e7f8b1e38717332772b`.
- ORCH-001 audited source head: `fe188fdee8eae901434a255051b6fa4f852b929b`.
- AGENT audited source head: `74e072b911db692041d3716af9d0511b83ad70b7`.
- BOOT audited source head: `c7710635fa172108cce87b3f7a76d6e037095864`.
- RECALL audited source head: `fae8a0df3c29775317986877bce2b8eda8593d27`.
- REST-001 audited source head: `3661450325237fcadb86098ec16ee45cd039bc0b`.
- REST-002 audited source head: `2ecca3f48d5e0ef27bd927da3986292daf7f7e2c`.
- Bootstrap-health audited source head: `f1697227241459f316bd562756e15ae3ce02c90d`.
- REST-BOOT-001 audited source head: `dd7a92a011fd4c453775df6ec307638b05313ec9`.
- Health legacy-consumer convergence audited source head: `6735e2d1febccf7da560d026d6ddd88f6ad82845`.
- RECALL-BOOT-001 audited source head: `20d834e1d438fd5883a76e9b45bcf21860e7db42`.
- PR `#176`: merged by squash for BOOT-001.
- PR `#177`: merged by squash for post-BOOT CURRENT reconciliation.
- PR `#178`: merged by squash for RECALL-001.
- PR `#179`: merged by squash for post-RECALL CURRENT reconciliation.
- PR `#180`: merged by squash for ORCH-001.
- PR `#181`: merged by squash for post-ORCH CURRENT reconciliation.
- PR `#182`: merged by squash for REST-001.
- PR `#183`: merged by squash for post-REST-001 CURRENT reconciliation.
- PR `#184`: merged by squash for REST-002 canonical repair-proposal convergence.
- PR `#186`: merged by squash for post-REST-002 CURRENT reconciliation without normative erosion.
- PR `#187`: merged by squash for dependency-derived bootstrap health instead of a static READY assignment.
- PR `#188`: merged by squash for REST boot-readiness canonical probing.
- PR `#189`: merged by squash for post-REST-readiness/bootstrap-Health CURRENT reconciliation.
- PR `#190`: merged by squash for Local Nervous System Health legacy-consumer convergence.
- PR `#191`: merged by squash for RECALL-BOOT-001 canonical read-only startup readiness.
- Tracker: `#88` — open for full F1/F3.2 reaudit and later F3.3 work.
- Protocol: `docs/adr/ADR-0002-cross-database-operation-protocol.md`.
- Gate: `STOP_S5=CLOSED`.

```text
CONTENT_BASELINE_SHA=c4b192b8f54b2422ce816dc3542d55adfd44510c
CONTENT_BASELINE_PARENT_SHA=9c7325e6f1a21d79b1c3fb58f0b5f81a828fc304
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
PR_183=MERGED_BY_SQUASH_HISTORICAL
PR_184=MERGED_BY_SQUASH_HISTORICAL
PR_186=MERGED_BY_SQUASH_HISTORICAL
PR_187=MERGED_BY_SQUASH_HISTORICAL
PR_188=MERGED_BY_SQUASH_HISTORICAL
PR_189=MERGED_BY_SQUASH_HISTORICAL
PR_190=MERGED_BY_SQUASH_HISTORICAL
PR_191=MERGED_BY_SQUASH_HISTORICAL
REST_001=INTEGRATED
REST_002=INTEGRATED
REST_REPAIR_PROPOSAL_CONVERGED=true
REST_REPAIR_EXECUTION_IMPLEMENTED=false
REST_BOOT_READINESS=INTEGRATED
RECALL_BOOT_READINESS=INTEGRATED
BOOTSTRAP_HEALTH_DERIVATION=INTEGRATED
HEALTH_LEGACY_CONSUMER_CONVERGENCE=INTEGRATED
HEALTH_CAN_READ_CANONICAL_MEMORY=true
HEALTH_CAN_WRITE_CANONICAL_MEMORY=false
HEALTH_CAN_WRITE_LEGACY_MEMORY_EVENTS=false
HEALTH_CONVERGENCE=OPEN
HEALTH_CONVERGED=false
HEALTH_STATE=DEPENDENCY_DERIVED
F1_F3_2_FULL_REAUDIT=REQUIRED
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
| `MIXED_DISPOSITION` | Repository contains integrated operations plus separately open convergence/readiness work. |

## Versioned owner inventory

| Owner path | Classification | Current disposition |
| --- | --- | --- |
| `app/src/main/java/com/morimil/app/data/repository/ProjectVaultRepository.kt` | `PROTECTED_REFERENCE` | Integrated and separate. |
| `app/src/main/java/com/morimil/app/runtime/GenesisUltraRuntimeBootstrapCoordinator.kt` | `INTEGRATED_PROTOCOL` | BOOT-001 integrated; dependency-derived bootstrap Health, REST-BOOT-001 and RECALL-BOOT-001 read-only readiness are integrated runtime gates, not additional XOP owners. |
| `app/src/main/java/com/morimil/app/data/repository/RuntimeBootstrapProtocolFinalizer.kt` | `SUPPORT_BOUNDARY` | Integrated BOOT-001 finalization support; not independently authoritative. |
| `app/src/main/java/com/morimil/app/data/repository/RecallScheduleRepository.kt` | `DERIVED_REBUILD` | RECALL-001 canonical derived rebuild and RECALL-BOOT-001 read-only startup readiness are integrated; neither creates a second authority. |
| `app/src/main/java/com/morimil/app/data/repository/RestCycleRepository.kt` | `INTEGRATED_PROTOCOL` | REST-001 local consolidation and REST-002 repair-proposal convergence are integrated under owner-scoped `rest_cycle`; the read-only canonical bootstrap readiness probe is integrated; automatic repair execution is not implemented. |
| `app/src/main/java/com/morimil/app/data/repository/CognitiveMigrationRepository.kt` | `INTEGRATED_PROTOCOL` | COG-001 through COG-004 integrated. |
| `app/src/main/java/com/morimil/app/data/repository/AgentOrchestrationRepository.kt` | `INTEGRATED_PROTOCOL` | ORCH-002 through ORCH-004 use common XOP; ORCH-001 canonical identity-gated seed convergence is integrated and remains a local projection path rather than a new XOP operation. |
| `app/src/main/java/com/morimil/app/data/repository/AgentInstanceLifecycleRepository.kt` | `INTEGRATED_PROTOCOL` | AGENT-001 through AGENT-006 integrated. |
| `app/src/main/java/com/morimil/app/data/repository/MigrationRecordRepository.kt` | `SUPPORT_BOUNDARY` | Typed COG finalization support. |

`LocalNervousSystemRepository` is not reclassified as an XOP owner by this inventory. It consumes `CanonicalConsumerReadPort.readHealthInput` read-only, derives operational Health, and returns non-persisted `LocalHealthTelemetry`; it has no canonical or legacy memory-write authority.

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

PR #187 dependency-derived bootstrap Health, REST-BOOT-001 and RECALL-BOOT-001 are integrated runtime/readiness dispositions around BOOT; none adds an XOP operation. Bootstrap Health is derived from dependency states, while REST and RECALL readiness are read-only canonical probes.

### Recall derived rebuild

| ID | Entry point | Current state |
| --- | --- | --- |
| `RECALL-001` | `seedFromRecentMemoryIfNeeded` | Integrated derived rebuild: verified `CanonicalConsumerReadPort` candidates, canonical event-hash idempotency, fail-closed verification, atomic local schedule+link projection, no legacy identity/memory authority. |

RECALL does not own a cross-database XOP because its local schedule is a rebuildable projection derived from verified canonical memory. RECALL-BOOT-001 adds only a read-only startup disposition: `RecallScheduleRepository.isBootstrapReady(identity)` accepts a verified empty batch as READY, maps NOT_READY to waiting, fails closed on RETRYABLE/BLOCKED evidence, validates Instance/Body/epoch/snapshot/candidate authority, and creates no schedule or link.

### REST cycle

| ID | Entry point | Current state |
| --- | --- | --- |
| `REST-001` | `runLocalRestCycleIfDue`, `approvePlannedRestCycle` | Integrated canonical protocol: verified canonical planning input, deterministic owner `rest_cycle`, exact `rest_cycle.local_consolidation` receipt, atomic local migration/link/autobiography finalization and replay-safe recovery. |
| `REST-002` | `planRestRepairProposalIfNeeded` | Integrated proposal-only canonical protocol: verified canonical planning input, deterministic `rest_cycle.propose_repair` -> `memory.repair_proposed`, approval-required local proposal, exact receipt/recovery, no automatic repair execution. |

REST-001 has one canonical effect. The autobiographical snapshot is a rebuildable local projection bound to the exact canonical receipt.

REST-002 does not approve or execute repairs. Its local result explicitly records `repair_execution=not_implemented`, and recovery preserves proposal-only state. REST-001 execution and REST-002 proposal convergence share owner `rest_cycle` while retaining distinct operation/payload/result schemas.

REST-BOOT-001 reuses `CanonicalConsumerReadPort.readRestCyclePlanningInput` read-only. Canonical NOT_READY maps to waiting without mutation; RETRYABLE/BLOCKED evidence fails closed; ready evidence must pass the existing `requireCanonicalPlanning` Instance/Body/epoch/digest/source validation before the bootstrap may report REST READY.

## Remaining operations

| ID | Entry point | Disposition |
| --- | --- | --- |
| `MIG-001` | `planMigration`, `markMigrationApproved`, `markMigrationCompleted`, `markMigrationFailed`, `markMigrationRolledBack` | `SUPPORT_BOUNDARY`. |

No remaining F3.2 REST or RECALL readiness operation is classified as `REQUIRES_PROTOCOL`. Remaining closure work is the full F1/F3.2 protected-main reaudit and later F3.3 retirement under separate authorization. The Local Nervous System Health boundary remains a non-owner, read-only canonical observer.

## Integrated guarantees

Within COG, ORCH-002..004, AGENT, BOOT and REST bounded scopes, common XOP guarantees remain deterministic identities, hidden staging, exact canonical ensure/receipt, owner-scoped recovery, stale-block prevention, atomic owner finalization, typed failures and replay safety.

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

RECALL-BOOT-001 separately guarantees:

1. verified canonical recall read input only;
2. a verified empty batch can be READY;
3. NOT_READY yields waiting without mutation;
4. RETRYABLE/BLOCKED fails closed;
5. Instance, active writer Body, writer epoch, snapshot/birth-root and candidate authority are validated;
6. readiness never calls recall seeding or creates schedule/link projection state.

REST-001 separately guarantees:

1. committed Genesis Ultra identity plus verified `CanonicalConsumerReadPort.readRestCyclePlanningInput` are the only planning authority;
2. legacy `genesis_core`, `local_instance_identity`, `memory_events`, `MemoryRepository` and audit-chain planning reads do not authorize the cycle;
3. canonical NOT_READY produces no REST mutation and blocked verification fails closed;
4. deterministic migration/operation/event identity and owner-scoped `rest_cycle` recovery;
5. one exact canonical `rest_cycle.local_consolidation` event through `CanonicalRestCycleCommitPort`;
6. migration completion, `canonical_memory_event` links and autobiography finalize atomically after the receipt;
7. the autobiographical snapshot remains a rebuildable local projection;
8. process-death recovery reuses the exact receipt and does not replay the canonical writer.

REST-002 separately guarantees:

1. verified canonical REST planning input and neutral `RestCycleSourceEvent` proposal sources;
2. deterministic proposal migration/operation/event identities;
3. owner `rest_cycle`, operation `rest_cycle.propose_repair`, event `memory.repair_proposed`;
4. exact canonical ensure through `CanonicalRestCycleCommitPort`;
5. local proposal remains `PLANNED` with approval required and automatic changes false;
6. no `approveRestRepair` or `executeRestRepair` production path;
7. process-death recovery finalizes an already receipted proposal exactly once without executing repair;
8. no compatibility write to `memory_events`, `genesis_core`, or `local_instance_identity`.

No new compatibility write to `memory_events`, `genesis_core`, or `local_instance_identity` is authorized. Local Nervous System Health and RECALL readiness likewise have no compatibility or canonical memory writer.

## Implementation order after STOP S5

1. `COG-001` through `COG-004` — integrated.
2. `ORCH-002` through `ORCH-004` — integrated.
3. `AGENT-001` through `AGENT-006` — integrated.
4. `BOOT-001` — integrated.
5. `RECALL-001` — integrated canonical derived rebuild.
6. `ORCH-001` — integrated canonical identity-gated seed convergence.
7. `REST-001` — integrated canonical planning and owner-scoped durable XOP.
8. `REST-002` — integrated canonical proposal-only XOP.
9. Bootstrap dependency-derived Health — integrated by PR #187.
10. `REST-BOOT-001` — integrated canonical read-only startup readiness.
11. F1 Health legacy-consumer convergence — integrated as canonical read-only observation with no memory writer.
12. `RECALL-BOOT-001` — integrated canonical read-only startup readiness by PR #191.
13. Full F1/F3.2 reaudit.
14. F3.3 only after every F3.2/readiness dependency has a recorded disposition and separate authorization.

## Residual hardening

- full F1/F3.2 protected-main reaudit remains required before global Health convergence can be closed;
- Health-specific mutation testing is not established; the successful global mutation pilot remains report-only and Genesis-scoped;
- `CanonicalHealthInput.recentVerifiedEventCount` is intentionally excluded from Health decisions until its metadata-only semantics are separately hardened;
- REST-specific mutation testing is not established; the successful global mutation pilot remains report-only;
- RECALL-specific mutation testing is not established; the existing report-only PIT pilot remains Genesis-scoped;
- BOOT-specific mutation testing is not established;
- AGENT-specific mutation testing is not established;
- ORCH-specific mutation testing remains unestablished;
- REST repair execution is not implemented by REST-002;
- continuous physical ARM64 inference remains outside emulator CI.

These findings are not evidence of operational birth.
