# Document status: HISTORICAL

# F1/F3.2 protected-main reaudit — `b98d1320c8f6908427c4b28a405750207c77f900`

This document is a SHA-bound audit snapshot. It is evidence of the reaudit performed against the protected-main state below; it is not a moving CURRENT contract and does not override the governed CURRENT documents.

## Exact identity

```text
REPOSITORY=M0-coder/Morimil-app
PROTECTED_MAIN=b98d1320c8f6908427c4b28a405750207c77f900
PR_192_SQUASH=2b6c3f2386f141b208720295ac4280854d2eb3da
PR_193_SOURCE_HEAD=4d9be2f4a428dadc3a9a6d218e7546baa9b40ba3
PR_193_SQUASH=31a3b06d0f81ebad4e515c1971c90e40f73f492f
PR_194_SOURCE_HEAD=5b4de6d2254126ee5593dcf4502a077103ab6666
PR_194_SQUASH=b98d1320c8f6908427c4b28a405750207c77f900
```

The external read-only audit used as an additional input was older than this baseline. Its findings were therefore revalidated against the protected-main SHA above rather than accepted as timeless truth.

## Reaudit result

### Canonical authority

Direct production-code inspection found no remaining productive second authority for Morimil identity, canonical memory, REST readiness, RECALL readiness or Local Nervous System Health.

The normal runtime remains bounded by the committed Genesis Ultra identity and verified canonical-memory interfaces. Legacy local identity/memory structures remain as compatibility, migration or read-only residue and are not reclassified here as active authority.

```text
INSTANCE_AUTHORITY=GENESIS_ULTRA_COMMITTED_IDENTITY
CANONICAL_MEMORY_AUTHORITY=CANONICAL_MEMORY_REPOSITORY
REST_BOOT_READINESS=CANONICAL_READ_ONLY
RECALL_BOOT_READINESS=CANONICAL_READ_ONLY
HEALTH_STATE=DEPENDENCY_DERIVED
HEALTH_CAN_READ_CANONICAL_MEMORY=true
HEALTH_CAN_WRITE_CANONICAL_MEMORY=false
HEALTH_CAN_WRITE_LEGACY_MEMORY_EVENTS=false
```

### F3.2 durable recovery evidence

The reaudit found a real evidence gap after PR #192: the cross-database tracker requires interruption/recovery evidence for each bounded cross-database operation, while several operation variants did not yet have durable Room close/reopen coverage.

PR #193 repaired that evidence gap without changing production code. It added persistent Room reopen/recovery tests for:

```text
PROJECT_VAULT
  create
  complete
  archive

COG
  approve
  execute
  rollback

AGENT
  submit_result
  evaluate
  promote
  retire
```

Existing coverage already exercised the remaining bounded owner operations. The PR #193 candidate passed JVM, instrumented API30/API35, coverage ratchets, static analysis and the five PR workflows before squash integration.

The tests prove durable database close/reopen recovery and exact local finalization from an already persisted canonical receipt. They are not represented as a physical Android `kill -9` proof.

### Protected-main CI evidence

The external audit correctly exposed a structural evidence weakness: the strongest `Genesis Body Preparation` workflow did not run automatically on pushes to `main`.

PR #194 repaired that mechanism by adding `main` to the existing `push.branches` trigger and adding an anti-regression contract. No Genesis job, command, permission, runtime path or dependency was changed.

Protected main `b98d1320c8f6908427c4b28a405750207c77f900` therefore contains the structural trigger required for future exact-main Genesis execution.

The currently available GitHub connector exposes commit-associated workflow runs only through a pull-request-filtered read path. During this audit it could not independently retrieve a complete set of push-triggered workflow runs for the squash SHA. Therefore execution on exact protected main is not claimed merely from trigger configuration.

```text
F08_STRUCTURAL_FIX=INTEGRATED
F08_EXACT_PROTECTED_MAIN_EXECUTION_EVIDENCE=PENDING_EXTERNAL_VERIFICATION
```

### README CURRENT drift

The protected-main README at the start of this reaudit still claimed that canonical REST/RECALL bootstrap and the common cross-database operation protocol were pending. That contradicted already integrated runtime truth.

This audit spawned a bounded documentation correction candidate that removes those stale claims and records the still-open exact-main evidence gate without changing runtime semantics.

### Mutation and coverage disposition

The repository mutation pilot remains report-only and Genesis-scoped. This reaudit does not promote it to a global quality gate and does not claim operation-specific mutation evidence for REST, RECALL, Health, BOOT, AGENT, ORCH or the F3.2 recovery matrix.

Known coverage and provenance debt remain recorded rather than hidden, including the existing managed-device destination-label mismatch in instrumented provenance evidence.

## Twelve-axis disposition

```text
ARCHITECTURE=PASS
COMPILATION=PASS_ON_AUDITED_PR_CANDIDATES
UNIT_EVIDENCE=PASS_ON_AUDITED_PR_CANDIDATES
INSTRUMENTED_EVIDENCE=PASS_ON_AUDITED_PR_CANDIDATES
COVERAGE=REVIEWED_RATCHET_PASS_WITH_OPEN_DEBT
STATIC_ANALYSIS=PASS_WITH_KNOWN_WARNINGS
CI_QUALITY=PASS_FOR_AUDITED_PR_CANDIDATES
SECURITY=PASS_BOUNDED
REPRODUCIBILITY=PASS_BOUNDED
MUTATION=REVIEWED_REPORT_ONLY_NOT_GLOBAL
TECHNICAL_DEBT=RECORDED
EXACT_SHA_EVIDENCE=BLOCKED_ON_PROTECTED_MAIN_PUSH_RUN_RETRIEVAL
```

## Closure verdict

The runtime/architecture and F3.2 recovery-matrix portions of the full reaudit pass. The complete Definition-of-Done gate does not close because exact protected-main execution evidence has not been independently retrieved for the current squash SHA.

```text
F1_F3_2_RUNTIME_ARCHITECTURE_REAUDIT=PASS
F3_2_OPERATION_RECOVERY_MATRIX=PASS
F1_F3_2_EXACT_MAIN_EXECUTION_EVIDENCE=PENDING
F1_F3_2_FULL_REAUDIT=REQUIRED
HEALTH_CONVERGENCE=OPEN
HEALTH_CONVERGED=false
F3_3=OPEN
F3_3_AUTHORIZED=false
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```

F3.3 remains a separate irreversible phase and is not authorized by this audit. No release, Body succession claim or Operational Birth is implied.
