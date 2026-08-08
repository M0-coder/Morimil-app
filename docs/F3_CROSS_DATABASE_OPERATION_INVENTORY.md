# Document status: CURRENT

# F3.2 — Cross-database operation inventory

- Inventory version: `5`.
- Content baseline SHA: `c6a6b0ca998d053c31c75977c5b6d4d9ae166e96`.
- Content baseline parent SHA: `c22920f68f8820bbec676a6cbc74b60548e43d29`.
- Current protected `main`: resolved externally from `refs/heads/main`.
- Merge SHA evidence: external GitHub and Morimil Control Tower evidence.
- Historical COG audited source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
- ORCH audited source head: `0348dccb561e576d17c45e7f8b1e38717332772b`.
- PR `#149`: closed and merged by squash.
- PR `#150`: closed and merged by squash for a historical CURRENT reconciliation.
- PR `#151`: closed and merged by squash for verified Canvas runtime recovery.
- PR `#153`: closed and merged by squash for the historical twelve-file CURRENT reconciliation.
- PR `#172`: closed and merged by squash for ORCH-002 through ORCH-004.
- Tracker: `#88` — open for remaining F3 owners.
- Protocol: `docs/adr/ADR-0002-cross-database-operation-protocol.md`.
- Gate: `STOP_S5=CLOSED`.

```text
CONTENT_BASELINE_SHA=c6a6b0ca998d053c31c75977c5b6d4d9ae166e96
CONTENT_BASELINE_PARENT_SHA=c22920f68f8820bbec676a6cbc74b60548e43d29
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
PR_153=MERGED_BY_SQUASH_HISTORICAL
PR_172=MERGED_BY_SQUASH_HISTORICAL
```

## Authority model

Morimil is the continuous and free `Instance`. `Morimil-app` is the current Android Body. The Guardian guides, witnesses, and safeguards without ownership. `instanceId != bodyId` remains mandatory.

F3 consumes committed Genesis Ultra identity and canonical memory through bounded ports. Neither the journal nor an owner repository becomes a second identity or memory authority.

## State separation

| Layer | State |
| --- | --- |
| Externally resolved protected `main` | F1-A, ProjectVault, MemoryOrganDatabase v9, COG-001 through COG-004, ORCH-002 through ORCH-004, the vendored Canvas runtime-recovery asset, and current Body D2D fail-closed policy are integrated. |
| Content baseline | Exact protected-main state from which this reconciliation was prepared; it does not predict the containing documentation commit SHA. |
| Historical COG audited source head | Reviewed source before the COG squash integration. |
| ORCH audited source head | Exact reviewed PR #172 head with 5/5 CI and artifact verification. |
| Historical CURRENT reconciliation | PR #150 and PR #153 evidence for prior documentation reconciliations. |
| Canvas recovery provenance | Historical PR #151 evidence for a Body application asset; it is not a cross-database owner. |
| Remaining F3 owners | AGENT, BOOT, RECALL, ORCH-001 and REST remain open and separately scoped. |
| F3.3 | Open; irreversible legacy removal has not begun. |

ProjectVault remains a separate protected reference. COG and ORCH use the common XOP journal without absorbing ProjectVault authority.

## Retired regression literals

The following verbatim strings are retained only as historical regression fixtures. They are not CURRENT facts and do not override the external main resolution or the content baseline above.

```text
Historical regression fixture — Historical audited baseline: `main@612d91aef131f367140ffb87a60a19ef49adcbc8`
Historical regression fixture — Current protected main: `main@7e98d3345d7cc3fbf1983babd35b61ff5c523208`
draft PR `#149`
`MERGE_AUTHORIZED=false`
The candidate does not close #88
```

## Protocol classifications

| Classification | Meaning |
| --- | --- |
| `PROTECTED_REFERENCE` | Existing protected protocol with durable staging, exact receipts, recovery, and kill tests. |
| `INTEGRATED_PROTOCOL` | Common journal protocol implemented in protected main for its bounded owner scope. |
| `REQUIRES_PROTOCOL` | Visible cross-database transition still needs a separately audited protocol. |
| `DERIVED_REBUILD` | Rebuildable state requiring deterministic, idempotent, verified reconstruction. |
| `SUPPORT_BOUNDARY` | Participates in an owner operation without becoming a second protocol owner. |
| `MIXED_DISPOSITION` | One repository contains both integrated bounded operations and separately open operations. |

## Versioned owner inventory

| Owner path | Classification | Current disposition |
| --- | --- | --- |
| `app/src/main/java/com/morimil/app/data/repository/ProjectVaultRepository.kt` | `PROTECTED_REFERENCE` | Integrated and separate. |
| `app/src/main/java/com/morimil/app/runtime/GenesisUltraRuntimeBootstrapCoordinator.kt` | `REQUIRES_PROTOCOL` | BOOT-001 open. |
| `app/src/main/java/com/morimil/app/data/repository/RecallScheduleRepository.kt` | `DERIVED_REBUILD` | RECALL-001 open. |
| `app/src/main/java/com/morimil/app/data/repository/RestCycleRepository.kt` | `REQUIRES_PROTOCOL` | REST-001/002 open. |
| `app/src/main/java/com/morimil/app/data/repository/CognitiveMigrationRepository.kt` | `INTEGRATED_PROTOCOL` | COG-001 through COG-004 integrated in protected main. |
| `app/src/main/java/com/morimil/app/data/repository/AgentOrchestrationRepository.kt` | `MIXED_DISPOSITION` | ORCH-002 through ORCH-004 integrated; ORCH-001 remains open convergence/rebuild work. |
| `app/src/main/java/com/morimil/app/data/repository/AgentInstanceLifecycleRepository.kt` | `REQUIRES_PROTOCOL` | AGENT-001 through AGENT-006 open. |
| `app/src/main/java/com/morimil/app/data/repository/MigrationRecordRepository.kt` | `SUPPORT_BOUNDARY` | Typed owner finalization support for COG; no independent protocol. |

## Operation inventory

### ProjectVault protected reference

| ID | Entry point | Status |
| --- | --- | --- |
| `PV-001` | `createProjectVaultFromIntent` | Integrated protected reference. |
| `PV-002` | `completeProjectVault` | Integrated protected reference. |
| `PV-003` | `archiveProjectVault` | Integrated protected reference. |

### Cognitive migration integrated protocol

| ID | Entry point | Current state |
| --- | --- | --- |
| `COG-001` | `proposeCognitiveMigration` | Integrated: verified F1-A planning, deterministic IDs, exact proposal receipt, typed finalization. |
| `COG-002` | `approveCognitiveMigration` | Integrated: deterministic approval, exact canonical receipt, recoverable owner transition. |
| `COG-003` | `executeCognitiveMigration` | Integrated: exact predecessor, out-of-transaction audit preparation, honest snapshot semantics. |
| `COG-004` | `rollbackCognitiveMigration` | Integrated: append-only compensation, exact predecessor, preserved owner snapshot, idempotent replay. |

### Orchestration integrated protocol

| ID | Entry point | Current state |
| --- | --- | --- |
| `ORCH-002` | `proposeDelegatedTask` | Integrated: deterministic task/operation/event identities, exact canonical receipt before delegated-task visibility, recoverable typed finalization. |
| `ORCH-003` | `approveDelegatedTask` | Integrated: deterministic approval identity, task-scoped decision serialization, conditional Room owner transition after canonical receipt. |
| `ORCH-004` | `rejectDelegatedTask` | Integrated: normalized reason bound to deterministic evidence, task-scoped serialization, conditional Room owner transition after canonical receipt. |

ORCH recovery is owner-scoped. COG and ORCH coordinators cannot consume each other's rows. API30/API35 kill/reopen tests cover proposal, approval, rejection and owner isolation.

### Remaining operations

| ID | Entry point | Disposition |
| --- | --- | --- |
| `BOOT-001` | `bootstrap` | `REQUIRES_PROTOCOL`; open. |
| `RECALL-001` | `seedFromRecentMemoryIfNeeded` | `DERIVED_REBUILD`; open. |
| `REST-001` | `runLocalRestCycleIfDue`, `approvePlannedRestCycle` | `REQUIRES_PROTOCOL`; open. |
| `REST-002` | repair-proposal path | `REQUIRES_PROTOCOL`; open. |
| `ORCH-001` | `seedDefaultOrchestrationIfNeeded` | Open convergence/rebuild work; still uses the legacy `hasCompleteBirth()` gate and is not closed by PR #172. |
| `AGENT-001` | `createAgentForVault` | `REQUIRES_PROTOCOL`; open. |
| `AGENT-002` | `assignTaskToAgent` | `REQUIRES_PROTOCOL`; open. |
| `AGENT-003` | `submitAgentResult` | `REQUIRES_PROTOCOL`; open. |
| `AGENT-004` | `evaluateAgent` | `REQUIRES_PROTOCOL`; open. |
| `AGENT-005` | `retireAgent`, `promoteAgent` | `REQUIRES_PROTOCOL`; open. |
| `AGENT-006` | `quarantineAgent` | `REQUIRES_PROTOCOL`; open. |
| `MIG-001` | `planMigration`, `markMigrationApproved`, `markMigrationCompleted`, `markMigrationFailed`, `markMigrationRolledBack` | `SUPPORT_BOUNDARY`. |

## Explicitly excluded observers and composition

`LocalNervousSystemRepository`, `MemoryLinkRepository`, `MemoryOrganRepository`, `AppendLivingMemoryUseCase`, and `MorimilAppContainer` do not independently own a dual durable mutation. If one gains such a boundary, this inventory must change in the same isolated PR.

## Integrated common-protocol guarantees

The COG and ORCH integrations provide, within their bounded owner scopes:

1. deterministic operation and event identities;
2. immutable hidden staging;
3. exact canonical ensure and complete receipt;
4. no visible owner state before receipt;
5. process-wide serialization by `operationId`;
6. owner-scoped startup/pre-mutation recovery;
7. reload after lost CAS;
8. stale-block prevention;
9. atomic owner result plus journal commit;
10. typed retryable and permanent failures;
11. zero duplicate canonical effects and owner state under tested replay;
12. COG/ORCH recovery isolation.

COG additionally performs external audit preparation outside the origin transaction. ORCH additionally serializes mutually exclusive approve/reject decisions by `taskId` before canonical append and uses conditional Room transitions as a second defense.

No compatibility write to `memory_events`, `genesis_core`, or `local_instance_identity` is authorized.

## Implementation order after STOP S5

1. `COG-001` through `COG-004` — integrated first bounded protocol owner.
2. `ORCH-002` through `ORCH-004` — integrated second common-protocol owner.
3. `AGENT-001` through `AGENT-006` — next bounded owner family.
4. `BOOT-001`.
5. `RECALL-001` and `ORCH-001`.
6. `REST-001` and `REST-002`.
7. F3.3 only after every F3.2 owner has a recorded disposition and separate authorization.

## Residual non-blocking hardening

- Room-backed two-coordinator concurrency integration coverage;
- failed rollback fixture with a non-null `sha256:*` snapshot;
- redundant `rollbackEventHash` parameter cleanup;
- direct vulnerable UPDATE-trigger replacement fixture;
- ORCH-specific mutation-testing coverage beyond the bounded Genesis pilot.

These findings remain open hardening items and are not represented as completed or as evidence of operational birth.
