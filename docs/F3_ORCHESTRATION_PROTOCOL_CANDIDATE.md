# Document status: PROPOSAL

# F3 ORCH-002 through ORCH-004 durable protocol candidate

## Candidate boundary

This document records a branch candidate. It does not describe protected `main` as already integrated and does not close issue #88.

```text
BASE_MAIN=c22920f68f8820bbec676a6cbc74b60548e43d29
BRANCH=executor/f3-orch-002-004-v1
OWNER_TYPE=agent_orchestration
ORCH_001=OUT_OF_SCOPE
ORCH_002=CANDIDATE
ORCH_003=CANDIDATE
ORCH_004=CANDIDATE
F3_3=OPEN
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```

## Problem being removed

The prior ORCH-002/003/004 path could mutate `delegated_tasks` and then separately attempt to record the corresponding memory event. A process death between those operations could expose an owner state without a canonical receipt. Task and approval identities also depended on wall-clock time, so a retry could create a different durable identity.

## Candidate protocol

ORCH-002, ORCH-003 and ORCH-004 use the existing `cross_database_operations` journal and common XOP state machine:

```text
STAGED
-> PENDING_CANONICAL
-> CANONICAL_COMMITTED
-> PENDING_LOCAL_COMMIT
-> COMMITTED
```

The owner-local transition occurs only after an exact canonical receipt is durable. Final owner mutation and journal `COMMITTED` finalization occur inside the same Room transaction.

## Deterministic identity

- `taskId` is derived from normalized planning inputs, Instance and writer epoch; it does not use the clock.
- `operationId` is derived through `CrossDatabaseOperationIdentity.operationId`.
- `eventId` is derived through `CrossDatabaseOperationIdentity.eventId`.
- approval identity is the committed ORCH-003 operation ID.
- rejection identity includes the normalized reason digest.
- wall-clock values are execution metadata only.

## Authority boundary

```text
IDENTITY_AUTHORITY=GenesisUltraRuntimeIdentityRepository
CANONICAL_MEMORY_AUTHORITY=CanonicalMemoryRepository
ORCHESTRATION_OWNER=MemoryOrganDatabase/delegated_tasks
JOURNAL_AUTHORITY=cross_database_operations protocol state only
GUARDIAN_OWNERSHIP=false
INSTANCE_ID_NE_BODY_ID=true
```

`CanonicalOrchestrationCommitPort` is a specialized canonical writer. It is not an identity source and does not create a second memory authority.

## Conflict and concurrency boundary

Approval and rejection are mutually exclusive decisions for one task. They are serialized by a process-wide `taskId` mutex before owner state is re-read and before canonical append. Room conditional updates additionally require:

```text
status='awaiting_approval'
approvalId IS NULL
```

The production Android manifest currently defines no secondary application process. An architecture test freezes that assumption because the process-wide decision mutex depends on single-process Body execution.

## Recovery boundary

The common coordinator is now explicitly parameterized by an owner-scoped protocol registry. Startup and pre-mutation recovery query only the coordinator's own `ownerType`.

This prevents the COG coordinator from consuming ORCH rows and prevents the ORCH coordinator from consuming COG rows.

The existing COG-specific pre-recovery quarantine for the historical COG-001 v1 payload remains attached only to the COG registry.

## ORCH-001 exclusion

`seedDefaultOrchestrationIfNeeded` remains a separate F1/ORCH-001 convergence item and still contains the legacy `MemoryRepository.hasCompleteBirth()` gate. This candidate does not represent ORCH-001 as converged and does not use that legacy gate as ORCH-002/003/004 identity authority.

## Residual API note

The DAO still contains the pre-existing broad delegated-task decision update methods for compatibility while the candidate is under validation. ORCH-002/003/004 no longer call them; the architecture contract forbids direct calls from `AgentOrchestrationRepository`. Removing unused legacy DAO methods is allowed only after exact caller verification and is not required to claim that the active ORCH path is journal-governed.

## Required evidence before technical acceptance

- JVM tests for deterministic task/operation/event identities;
- architecture non-regression tests preventing direct legacy two-commit ORCH writes;
- Room instrumentation tests for process-death recovery after canonical receipt and before local finalization;
- owner-isolation recovery test;
- API 30 and API 35 managed-device suite;
- QA-7 JVM and instrumented ratchets;
- Android Lint and Kotlin warning non-regression;
- mutation pilot non-regression;
- CodeQL;
- QA-6 SBOM/supply-chain checks;
- exact-head 5/5 CI;
- independent artifact digest audit.

## Explicit non-scope

```text
DATABASE_SCHEMA_VERSION_CHANGE=false
NEW_DATABASE=false
ORCH_001_CONVERGED=false
AGENT_001_006_CHANGED=false
BOOT_001_CHANGED=false
RECALL_CHANGED=false
REST_CHANGED=false
F3_3_CHANGED=false
F4_CHANGED=false
F5_CHANGED=false
F6_CHANGED=false
BODY_PROVISIONING_EXECUTED=false
GUARDIAN_MODIFIED=false
SEED_IMPORTED=false
GENESIS_EXECUTED=false
ACTIVATION_EXECUTED=false
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```

Protected-main integration and CURRENT-document reconciliation require separate gates after exact-head evidence. This candidate must not be presented as merged before that occurs.
