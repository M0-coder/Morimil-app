# Document status: HISTORICAL

# F3 ORCH-002 through ORCH-004 durable protocol — historical candidate record

## Historical role

This file records the candidate that became PR #172. It is no longer a proposal and must not override CURRENT documents.

```text
BASE_MAIN=c22920f68f8820bbec676a6cbc74b60548e43d29
AUDITED_SOURCE_HEAD=0348dccb561e576d17c45e7f8b1e38717332772b
MERGE_COMMIT=c6a6b0ca998d053c31c75977c5b6d4d9ae166e96
PR_172=MERGED_BY_SQUASH
OWNER_TYPE=agent_orchestration
ORCH_001=OUT_OF_SCOPE_AND_OPEN
ORCH_002=INTEGRATED
ORCH_003=INTEGRATED
ORCH_004=INTEGRATED
F3_3=OPEN
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```

## Problem removed

The prior ORCH-002/003/004 path could mutate `delegated_tasks` and then separately attempt to record the corresponding memory event. A process death between those operations could expose owner state without a canonical receipt. Task and approval identities also depended on wall-clock time, so retry could create a different durable identity.

## Integrated protocol

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

The production Android manifest defines no secondary application process. An architecture test freezes that assumption because the process-wide decision mutex depends on single-process Body execution.

## Recovery boundary

The common coordinator is parameterized by an owner-scoped protocol registry. Startup and pre-mutation recovery query only the coordinator's own `ownerType`.

This prevents the COG coordinator from consuming ORCH rows and prevents the ORCH coordinator from consuming COG rows.

The COG-specific pre-recovery quarantine for the historical COG-001 v1 payload remains attached only to the COG registry.

## ORCH-001 exclusion

`seedDefaultOrchestrationIfNeeded` remains a separate F1/ORCH-001 convergence item and still contains the legacy `MemoryRepository.hasCompleteBirth()` gate. PR #172 did not represent ORCH-001 as converged and did not use that legacy gate as ORCH-002/003/004 identity authority.

## Validation evidence

The final reviewed head `0348dccb561e576d17c45e7f8b1e38717332772b` passed:

- Android CI #653;
- Genesis Body Preparation #645;
- Reference Checks #477;
- CodeQL #366;
- SBOM #364;
- 780 JVM tests, zero failures/errors/skips;
- QA-7 JVM ratchet;
- 118 managed tests on API30 and 118 on API35 with the pre-existing four ARM64-only skips per device;
- five ORCH instrumented tests per device;
- QA-7 instrumented ratchet;
- independent artifact digest verification.

The baseline was not lowered. Earlier candidate heads that failed contract/coverage gates were rejected and superseded.

## Explicit observability delta

The prior path emitted a second legacy `immune.approval_denied` memory event when an already immune-blocked task was later submitted for approval. The integrated path returns `false` without that second legacy memory write. The original immune block remains represented in ORCH-002 canonical evidence. This is an observability delta, not an owner-state or authority change.

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

Current executable truth is governed by the CURRENT runtime contract, F1 convergence inventory, F3 inventory, ADR-0002, and current sovereignty audit.
