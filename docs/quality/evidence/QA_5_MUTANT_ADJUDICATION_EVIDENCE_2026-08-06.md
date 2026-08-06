# Document status: CURRENT

# QA-5 — Mutant adjudication evidence

## Evidence identity

```text
BASE_MAIN=81d3dbe96380b8372bf77210507d2496e800a202
BRANCH=qa/qa-5-mutant-adjudication-tests
QA4_FINAL_HEAD=3be51caa8b3e8c7f1fe7c69dcebc3cafba406639
QA4_FINAL_ARTIFACT_BYTES=3403999
QA4_FINAL_ARTIFACT_SHA256=50309c86a100675d829a3f886bf0600fa5465f2ae224b4e7d77b8bd3a7c2ea88
QA4_MUTATIONS_XML_BYTES=47048
QA4_MUTATIONS_XML_SHA256=cb70a47cd78cbf7ad8e6f4925310b97dae02bb6c21ff58d6375817de464ba886
QA4_SUMMARY_JSON_BYTES=5012
QA4_SUMMARY_JSON_SHA256=32be4287547aab91049b496341934362ef7abbe4e293a725fd71a077343c0813
```

The QA-4 artifact was independently inspected before any QA-5 repository write. Its raw XML and derived JSON agreed on the complete 63-mutant inventory.

## Frozen QA-4 result

```text
MUTANTS_GENERATED=63
KILLED=40
SURVIVED=12
NO_COVERAGE=11
UNDETECTED_TOTAL=23
TIMED_OUT=0
NON_VIABLE=0
MEMORY_ERROR=0
RUN_ERROR=0
```

## Read-only adjudication

The 23 undetected mutants were mapped to exact methods, source lines, mutators, PIT descriptions, and current call paths.

| Category | Count |
|---|---:|
| `BEHAVIORALLY_DISTINGUISHABLE_TEST_GAP` | 3 |
| `COMPILER_NULL_CHECK_EQUIVALENT_CANDIDATE` | 9 |
| `SEMANTICALLY_REDUNDANT_STRING_BRANCH` | 1 |
| `SYNTHETIC_PRIVATE_ACCESSOR_OUTSIDE_ACTIVE_CALL_GRAPH` | 4 |
| `UNREACHABLE_GENERIC_CANONICALIZER_BRANCH_IN_CURRENT_VERIFY_PATH` | 5 |
| `UNREACHABLE_NULL_BRANCH_IN_CURRENT_VERIFY_PATH` | 1 |

No evidence was found that justifies declaring a critical runtime defect. Three source-level behaviors were distinguishable and required direct tests.

## Implementation under validation

The implementation adds one JVM test class and no production source changes:

```text
TEST_CLASS=GenesisManifestCanonicalizationBoundaryTest
TESTS_ADDED=3
PRODUCTION_SOURCE_CHANGED=FALSE
BUNDLED_GENESIS_ASSETS_CHANGED=FALSE
APPROVED_GENESIS_HASH_CHANGED=FALSE
DATABASE_CHANGED=FALSE
WORKFLOW_CHANGED=FALSE
DEPENDENCY_CHANGED=FALSE
```

The tests exercise:

1. one-character canonical strings;
2. `U+0020 SPACE` at the exact control boundary;
3. `U+001F UNIT SEPARATOR` below the control boundary.

Expected canonical record bytes are constructed independently from the production private canonicalizer. The tests call `GenesisManifestVerifierCore.verify()` normally and do not use reflection.

## Evidence still required

This document does not claim technical completion yet. The exact final head must still provide:

```text
FINAL_HEAD=PENDING
QA5_BOUNDARY_TESTS=PENDING
REPEATED_PIT=PENDING
TARGETED_MUTANTS_KILLED=PENDING
REMAINING_MUTANT_INVENTORY=PENDING
ANDROID_CI=PENDING
REFERENCE_CHECKS=PENDING
CODEQL=PENDING
SBOM=PENDING
GENESIS_BODY_PREPARATION_VALIDATION=PENDING
FINAL_ARTIFACT_AUDIT=PENDING
```

## Operational boundary

```text
RELEASE_EXECUTED=FALSE
BODY_MODIFIED=FALSE
GUARDIAN_MODIFIED=FALSE
SEED_IMPORTED=FALSE
GENESIS_EXECUTED=FALSE
ACTIVATION_EXECUTED=FALSE
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
MERGE_AUTHORIZED=FALSE
```
