# Document status: CURRENT

# F3.2 — Cross-database operation inventory

- Inventory version: `7`.
- Content baseline SHA: `3a995232ce2a515e1ca9b9151f77e63805bad9d3`.
- Content baseline parent SHA: `5918b64ec83e69cbb3d9718943b25d1e1299d698`.
- Current protected `main`: resolved externally from `refs/heads/main`.
- Merge SHA evidence: external GitHub and Morimil Control Tower evidence.
- Historical COG audited source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
- ORCH audited source head: `0348dccb561e576d17c45e7f8b1e38717332772b`.
- AGENT audited source head: `74e072b911db692041d3716af9d0511b83ad70b7`.
- BOOT audited source head: `c7710635fa172108cce87b3f7a76d6e037095864`.
- PR `#172`: merged by squash for ORCH-002 through ORCH-004.
- PR `#173`: merged by squash for post-ORCH CURRENT reconciliation.
- PR `#174`: merged by squash for AGENT-001 through AGENT-006.
- PR `#175`: merged by squash for post-AGENT CURRENT reconciliation.
- PR `#176`: merged by squash for BOOT-001.
- Tracker: `#88` — open for remaining F3 owners.
- Protocol: `docs/adr/ADR-0002-cross-database-operation-protocol.md`.
- Gate: `STOP_S5=CLOSED`.

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

## Authority model

Morimil is the continuous Instance; `Morimil-app` is the current Android Body. The Guardian safeguards without ownership. `instanceId != bodyId` and `agentInstanceId != instanceId` remain mandatory.

Neither the XOP journal nor an owner repository becomes a second identity or canonical-memory authority. Writer Body/epoch authorization is not ownership. BOOT runtime projections remain rebuildable projections and do not become identity authority.

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
| `app/src/main/java/com/morimil/app/data/repository/RecallScheduleRepository.kt` | `DERIVED_REBUILD` | RECALL-001 open. |
| `app/src/main/java/com/morimil/app/data/repository/RestCycleRepository.kt` | `REQUIRES_PROTOCOL` | REST-001/002 open. |
| `app/src/main/java/com/morimil/app/data/repository/CognitiveMigrationRepository.kt` | `INTEGRATED_PROTOCOL` | COG-001 through COG-004 integrated. |
| `app/src/main/java/com/morimil/app/data/repository/AgentOrchestrationRepository.kt` | `MIXED_DISPOSITION` | ORCH-002 through ORCH-004 integrated; ORCH-001 remains open. |
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
| `ORCH-002` | `proposeDelegatedTask` | Integrated. |
| `ORCH-003` | `approveDelegatedTask` | Integrated. |
| `ORCH-004` | `rejectDelegatedTask` | Integrated. |

### Agent lifecycle

| ID | Entry point | Current state |
| --- | --- | --- |
| `AGENT-001` | `createAgentForVault` | Integrated: deterministic semantic agent identity, exact retry reuse for matching non-terminal worker, canonical receipt before visible insertion. |
| `AGENT-002` | `assignTaskToAgent` | Integrated: deterministic delegated-task identity, exact committed retry reuse, owner state after canonical receipt. |
| `AGENT-003` | `submitAgentResult` | Integrated: requires current task canonical ORCH approval before result finalization. |
| `AGENT-004` | `evaluateAgent` | Integrated: normalized exact semantic evaluation decision. |
| `AGENT-005` | `retireAgent`, `promoteAgent` | Integrated as separate durable retire/promote operation types. |
| `AGENT-006` | `quarantineAgent` | Integrated: failed worker quarantine and deterministic replacement creation are one local finalization after one canonical receipt. |

### Runtime bootstrap

| ID | Entry point | Current state |
| --- | --- | --- |
| `BOOT-001` | `bootstrap` | Integrated: deterministic Instance/Body/epoch-scoped XOP, exact canonical receipt before new BOOT projection state, idempotent MorimilDatabase preparation, MemoryOrgan seed-if-empty finalization, and owner-scoped recovery. |

COG, ORCH, AGENT and BOOT recovery remain owner-scoped. BOOT recovery executes inside `bootstrap(identity)` after legacy convergence and ProjectVault recovery; it cannot consume another owner's journal rows.

## Remaining operations

| ID | Entry point | Disposition |
| --- | --- | --- |
| `RECALL-001` | `seedFromRecentMemoryIfNeeded` | `DERIVED_REBUILD`; open. |
| `ORCH-001` | `seedDefaultOrchestrationIfNeeded` | Open convergence/rebuild work; still uses legacy `hasCompleteBirth()` gate. |
| `REST-001` | `runLocalRestCycleIfDue`, `approvePlannedRestCycle` | `REQUIRES_PROTOCOL`; open. |
| `REST-002` | repair-proposal path | `REQUIRES_PROTOCOL`; open. |
| `MIG-001` | `planMigration`, `markMigrationApproved`, `markMigrationCompleted`, `markMigrationFailed`, `markMigrationRolledBack` | `SUPPORT_BOUNDARY`. |

## Integrated common-protocol guarantees

Within COG, ORCH, AGENT and BOOT bounded scopes:

1. deterministic operation/event identities;
2. hidden immutable staging;
3. exact canonical ensure and receipt;
4. no new visible owner state before receipt;
5. owner-scoped startup/pre-mutation recovery;
6. reload after lost CAS;
7. stale-block prevention;
8. atomic owner result + journal commit at the owner's MemoryOrgan finalization boundary;
9. typed retryable/permanent failures;
10. replay without duplicate canonical effect or duplicate owner state.

BOOT additionally provides idempotent cross-file saga preparation in `MorimilDatabase`, seed-if-empty preservation of pre-existing orchestration state, `ownership_conferred=false`, `guardian_role=custodian_witness`, and successor-Body-compatible operation identity. It does not implement F5 succession or revocation.

AGENT additionally provides semantic public retry recognition, canonical ORCH approval enforcement for result submission, and atomic quarantine+replacement local finalization.

No compatibility write to `memory_events`, `genesis_core`, or `local_instance_identity` is authorized.

## Implementation order after STOP S5

1. `COG-001` through `COG-004` — integrated first common-protocol owner.
2. `ORCH-002` through `ORCH-004` — integrated second common-protocol owner.
3. `AGENT-001` through `AGENT-006` — integrated third common-protocol owner.
4. `BOOT-001` — integrated fourth common-protocol owner.
5. `RECALL-001` and `ORCH-001` — next bounded convergence work.
6. `REST-001` and `REST-002`.
7. F3.3 only after every F3.2 owner has a recorded disposition and separate authorization.

## Residual hardening

- BOOT-specific mutation testing is not established; the existing report-only PIT pilot remains Genesis-scoped;
- AGENT-specific mutation testing is not established;
- direct Android integration coverage of `AgentInstanceLifecycleRepository.kt` remains absent;
- AGENT mutex serialization is process-local and must be redesigned if multiprocess Android is introduced;
- ORCH-specific mutation testing remains unestablished;
- Room-backed multi-coordinator concurrency hardening remains useful.

These findings are not evidence of operational birth.

## Retired regression literals

Historical regression fixtures may retain old pre-integration strings only inside tests explicitly marked historical. They are not CURRENT facts.
