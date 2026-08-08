# Document status: PROPOSAL

# F1 / F3 — RECALL-001 canonical derived rebuild

Baseline: `main@bdbb5b2a040b728508948cd3cfbd8807b40a12f6`.

This document records candidate implementation evidence only. It does not change CURRENT truth until a separately authorized merge is completed and reconciled.

## Scope

`RecallScheduleRepository.seedFromRecentMemoryIfNeeded` moves from legacy runtime reads to the shared verified canonical consumer boundary.

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
- the recall graph source node is `recall:<canonical-event-hash>`;
- repeated seeding cannot create a second schedule or graph link for the same canonical event;
- canonical NOT_READY returns without mutating the organ;
- retryable or blocked canonical verification failures fail closed and create no projection.

## Legacy reconciliation boundary

`MemoryOrganReconciliation` must not invalidate canonical recall projections by comparing their target hashes against the legacy `memory_events` hash set. Canonical recall sources use `source=canonical_memory_event`; legacy recalls retain legacy orphan checking until their own retirement.

This does not converge RestCycle, health, ORCH-001, or F3.3.

## Required evidence before merge consideration

```text
architecture=PASS
compile=PASS
unit_tests=PASS
instrumented_API30=PASS
instrumented_API35=PASS
coverage_ratchet=PASS
static_analysis=PASS
CI_required_workflows=PASS
security_fail_closed=PASS
reproducibility_no_regression=PASS
mutation_truth=no_false_claim
technical_debt=no_hidden_scope_escape
exact_head_evidence=REQUIRED
```

Operational birth remains `NOT_OCCURRED`.
