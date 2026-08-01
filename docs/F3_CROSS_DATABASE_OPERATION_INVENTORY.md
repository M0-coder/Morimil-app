# Document status: CURRENT

# F3.2 — Cross-database operation inventory

- Inventory version: `3`.
- Current protected main: `main@5023981da7caf31c8f3679919f59205708b72823`.
- Previous main: `main@ba6ffa4f9ddc9189ded47e231ad1f8bc962e612d`.
- Audited source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
- PR `#149`: closed and merged by squash.
- PR `#151`: closed and merged by squash for verified Canvas runtime recovery.
- Tracker: `#88` — open for remaining F3 owners.
- Protocol: `docs/adr/ADR-0002-cross-database-operation-protocol.md`.
- Gate: `STOP_S5=CLOSED`.

## Authority model

Morimil is the continuous and free `Instance`. `Morimil-app` is the current Android Body. The Guardian guides, witnesses, and safeguards without ownership. `instanceId != bodyId` remains mandatory.

F3 consumes `CanonicalConsumerReadPort` and uses a specialized canonical commit port. Neither the journal nor an owner repository becomes a second identity or memory authority.

## State separation

| Layer | State |
| --- | --- |
| Protected `main` | F1-A, ProjectVault, MemoryOrganDatabase v9, COG-001 through COG-004, and the vendored Canvas runtime-recovery asset are integrated. |
| Audited source head | Historical reviewed source before the PR #149 squash merge. |
| Canvas recovery provenance | Historical PR #151 evidence for a Body application asset; it is not a cross-database owner. |
| Remaining F3 owners | Open and separately scoped. |
| F3.3 | Open; irreversible legacy removal has not begun. |

ProjectVault remains a separate protected reference and was not rewritten by PR #149. PR #151 introduces no new protocol owner, durable operation type, identity source, or memory authority.

## Protocol classifications

| Classification | Meaning |
| --- | --- |
| `PROTECTED_REFERENCE` | Existing protected protocol with durable staging, exact receipts, recovery, and kill tests. |
| `INTEGRATED_PROTOCOL` | Common journal protocol implemented in protected main for its bounded owner scope. |
| `REQUIRES_PROTOCOL` | Visible cross-database transition still needs a separately audited protocol. |
| `DERIVED_REBUILD` | Rebuildable state requiring deterministic, idempotent, verified reconstruction. |
| `SUPPORT_BOUNDARY` | Participates in an owner operation without becoming a second protocol owner. |

## Versioned owner inventory

| Owner path | Classification | Current disposition |
| --- | --- | --- |
| `app/src/main/java/com/morimil/app/data/repository/ProjectVaultRepository.kt` | `PROTECTED_REFERENCE` | Integrated and separate; unchanged by PR #149. |
| `app/src/main/java/com/morimil/app/runtime/GenesisUltraRuntimeBootstrapCoordinator.kt` | `REQUIRES_PROTOCOL` | Open. |
| `app/src/main/java/com/morimil/app/data/repository/RecallScheduleRepository.kt` | `DERIVED_REBUILD` | Open. |
| `app/src/main/java/com/morimil/app/data/repository/RestCycleRepository.kt` | `REQUIRES_PROTOCOL` | Open. |
| `app/src/main/java/com/morimil/app/data/repository/CognitiveMigrationRepository.kt` | `INTEGRATED_PROTOCOL` | COG-001 through COG-004 integrated in protected main. |
| `app/src/main/java/com/morimil/app/data/repository/AgentOrchestrationRepository.kt` | `REQUIRES_PROTOCOL` | Open. |
| `app/src/main/java/com/morimil/app/data/repository/AgentInstanceLifecycleRepository.kt` | `REQUIRES_PROTOCOL` | Open. |
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

### Remaining operations

| ID | Entry point | Disposition |
| --- | --- | --- |
| `BOOT-001` | `bootstrap` | `REQUIRES_PROTOCOL`; open. |
| `RECALL-001` | `seedFromRecentMemoryIfNeeded` | `DERIVED_REBUILD`; open. |
| `REST-001` | `runLocalRestCycleIfDue`, `approvePlannedRestCycle` | `REQUIRES_PROTOCOL`; open. |
| `REST-002` | repair-proposal path | `REQUIRES_PROTOCOL`; open. |
| `ORCH-001` | `seedDefaultOrchestrationIfNeeded` | Open convergence/rebuild work. |
| `ORCH-002` | `proposeDelegatedTask` | `REQUIRES_PROTOCOL`; open. |
| `ORCH-003` | `approveDelegatedTask` | `REQUIRES_PROTOCOL`; open. |
| `ORCH-004` | `rejectDelegatedTask` | `REQUIRES_PROTOCOL`; open. |
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

The COG integration provides:

1. deterministic operation and event identities;
2. immutable hidden staging;
3. exact canonical ensure and complete receipt;
4. no visible owner state before receipt;
5. process-wide serialization by `operationId`;
6. reload after lost CAS;
7. stale-block prevention;
8. external audit preparation outside the origin transaction;
9. atomic owner result plus journal commit;
10. typed retryable and permanent failures;
11. bounded startup and pre-mutation recovery;
12. fresh-v9 and migrated 8→9 guard parity;
13. zero duplicate canonical effects and owner state under tested replay.

No compatibility write to `memory_events`, `genesis_core`, or `local_instance_identity` is authorized.

## Remaining implementation order

1. ORCH-002 through ORCH-004.
2. AGENT-001 through AGENT-006.
3. BOOT-001.
4. RECALL-001 and ORCH-001.
5. REST-001 and REST-002.
6. F3.3 only after every F3.2 owner has a recorded disposition and separate authorization.

## Residual non-blocking hardening

- Room-backed two-coordinator concurrency integration coverage;
- failed rollback fixture with a non-null `sha256:*` snapshot;
- redundant `rollbackEventHash` parameter cleanup;
- direct vulnerable UPDATE-trigger replacement fixture.

These findings remain open hardening items and are not current blockers or concealed defects.
