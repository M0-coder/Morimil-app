# Document status: HISTORICAL

# F1 / F3 — RECALL-001 canonical derived rebuild

Historical implementation baseline: `main@bdbb5b2a040b728508948cd3cfbd8807b40a12f6`.

Validated source head: `fae8a0df3c29775317986877bce2b8eda8593d27`.

Integrated by squash through PR `#178` as commit `6e0444b698bdc5c557ec3ea83f48d7980da1a36b`.

```text
RECALL_001=INTEGRATED_IN_MAIN
RECALL_001_TESTED=DEMONSTRATED_BY_PR_INTEGRATION_REF_CI
RECALL_001_MERGED=TRUE
INTEGRATION_COMMIT=6e0444b698bdc5c557ec3ea83f48d7980da1a36b
```

This file is retained as historical implementation evidence. CURRENT truth is governed by `docs/CURRENT_RUNTIME_CONTRACT.md`, `docs/F1_CANONICAL_CONSUMER_CONVERGENCE.md`, and `docs/F3_CROSS_DATABASE_OPERATION_INVENTORY.md`.

## Integrated scope

`RecallScheduleRepository.seedFromRecentMemoryIfNeeded` moved from legacy runtime reads to the shared verified canonical consumer boundary.

```text
OWNER=RECALL-001
CLASSIFICATION=DERIVED_REBUILD
CANONICAL_READ=CanonicalConsumerReadPort.readRecallCandidates
LEGACY_GENESIS_READ=false
LEGACY_LOCAL_IDENTITY_READ=false
LEGACY_MEMORY_EVENTS_READ=false
NEW_CANONICAL_WRITER=false
ROOM_SCHEMA_CHANGE=false
F3_3=false
ORCH_001=false
REST_001_002=false
OPERATIONAL_BIRTH=false
MOTOR_ACTIVATION_CHANGE=false
```

## Authority invariants

```text
instanceId != bodyId
writer authorization != ownership
Guardian = custodian/witness, not owner
canonical memory != memory_events
recall schedule = rebuildable projection, not memory authority
```

A recall schedule may be created only from a verified canonical recall candidate. The projection stores the canonical target event hash and canonical birth-root event hash. Existing legacy-named projection columns are not reinterpreted as legacy authority and remain scheduled for later F3.3 schema retirement.

`local_instance_pending` and other placeholder identities are forbidden.

## Deterministic rebuild semantics

- candidate ordering is deterministic by recall priority, confirmation, importance, confidence, canonical sequence, then event hash;
- the unique canonical `targetEventHash` is the idempotent schedule key;
- `recallId` remains only the local projection/node identifier and confers no identity authority;
- schedule insertion and its local graph link commit in one `MemoryOrganDatabase` Room transaction;
- repeated seeding, including after repository/process reconstruction, cannot create a second schedule or graph link for the same canonical event;
- canonical NOT_READY returns without mutating the organ;
- retryable or blocked canonical verification failures fail closed and create no projection.

## Legacy reconciliation boundary

`MemoryOrganReconciliation` does not invalidate canonical recall projections by comparing their target hashes against the legacy `memory_events` hash set. Canonical recall sources use `source=canonical_memory_event`; legacy recalls retain legacy orphan checking until their own retirement.

This integration does not converge RestCycle, health, ORCH-001, or F3.3.

## Validation evidence at integration

The PR-associated integration-ref validation for source head `fae8a0df3c29775317986877bce2b8eda8593d27` completed successfully across Android CI, Genesis Body Preparation, Reference Checks, CodeQL, and SBOM. Managed-device tests passed on API 30 and API 35 with zero failures; QA-7 JVM and instrumented ratchets passed. Mutation testing remained truthfully report-only and did not establish RECALL-specific mutation coverage.

Operational birth remains `NOT_OCCURRED`.
