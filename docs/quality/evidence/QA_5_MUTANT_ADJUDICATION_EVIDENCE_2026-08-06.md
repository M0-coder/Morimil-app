# Document status: CURRENT

# QA-5 — Mutant adjudication evidence

## Evidence identity

```text
BASE_MAIN=81d3dbe96380b8372bf77210507d2496e800a202
BRANCH=qa/qa-5-mutant-adjudication-tests
FINAL_HEAD_RESOLUTION=EXTERNAL_GIT_REF
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
MUTATION_SCORE=63.492063%
TEST_STRENGTH=76.923077%
MUTATION_LINE_COVERAGE_PROXY=82.539683%
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

## Implementation boundary

```text
TEST_CLASS=GenesisManifestCanonicalizationBoundaryTest
TESTS_ADDED=3
PIT_TARGET_CLASSES=com.morimil.app.data.genesis.GenesisManifestVerifierCore*
PIT_TARGET_TEST_1=com.morimil.app.data.genesis.GenesisManifestVerifierCoreTest
PIT_TARGET_TEST_2=com.morimil.app.data.genesis.GenesisManifestCanonicalizationBoundaryTest
PIT_TEST_WILDCARD=FALSE
PRODUCTION_SOURCE_CHANGED=FALSE
BUNDLED_GENESIS_ASSETS_CHANGED=FALSE
APPROVED_GENESIS_HASH_CHANGED=FALSE
DATABASE_CHANGED=FALSE
GITHUB_WORKFLOW_CHANGED=FALSE
DEPENDENCY_CHANGED=FALSE
```

The tests exercise:

1. one-character canonical strings;
2. `U+0020 SPACE` at the exact control boundary;
3. `U+001F UNIT SEPARATOR` below the control boundary.

Expected canonical record bytes are constructed independently from the production private canonicalizer. The tests call `GenesisManifestVerifierCore.verify()` normally and do not use reflection.

## Rejected first candidate head

```text
REJECTED_CANDIDATE_HEAD=1d4fdfc9bf1df9b35181be3d4ec3717bbdee66d3
ANDROID_CI_RUN=31100612617
ANDROID_CI_RUN_NUMBER=597
ANDROID_CI_RESULT=SUCCESS
CODEQL_RESULT=SUCCESS
SBOM_RESULT=SUCCESS
REFERENCE_CHECKS_RESULT=CANCELLED_AFTER_HEAD_MOVED
GENESIS_BODY_PREPARATION_RESULT=CANCELLED_AFTER_HEAD_MOVED
ARTIFACT_ID=8967416623
ARTIFACT_BYTES=3404228
ARTIFACT_SHA256=d03be568f03691bde6c5ea350c43ba489d5f588262f9c1dfa8af1fee6867994e
ARTIFACT_DIGEST_MATCH=TRUE
ARTIFACT_FILES=1528
MUTATIONS_XML_BYTES=47048
MUTATIONS_XML_SHA256=c3d7acbf43a09a54991a0da9016d3070558813d0ef9f1f995315ae56eb490b1b
```

The candidate compiled and the normal unit-test task passed, but the PIT init script still targeted only `GenesisManifestVerifierCoreTest`. The new boundary test class was absent from mutation execution.

```text
MUTANTS_GENERATED=63
KILLED=40
SURVIVED=12
NO_COVERAGE=11
TARGETED_BEHAVIORAL_MUTANTS_KILLED=0/3
CANDIDATE_ACCEPTED=FALSE
```

This result is not treated as a QA-5 pass. A green workflow is insufficient when the intended tests were not executed by the mutation engine.

## Corrected measurement head

```text
MEASUREMENT_HEAD=d3aaae9afc49a06188336facc2053eebba7a1730
ANDROID_CI_RUN=31101147504
ANDROID_CI_RUN_NUMBER=600
ANDROID_CI_RESULT=SUCCESS
REFERENCE_CHECKS_RUN=31101147634
REFERENCE_CHECKS_RUN_NUMBER=424
REFERENCE_CHECKS_RESULT=SUCCESS
CODEQL_RUN=31101147800
CODEQL_RUN_NUMBER=313
CODEQL_RESULT=SUCCESS
SBOM_RUN=31101147478
SBOM_RUN_NUMBER=311
SBOM_RESULT=SUCCESS
GENESIS_BODY_PREPARATION_RUN=31101147472
GENESIS_BODY_PREPARATION_RUN_NUMBER=596
GENESIS_BODY_PREPARATION_RESULT=SUCCESS
```

The workflow named `Genesis Body Preparation` executed the existing Android validation pipeline. No Genesis operation occurred.

## Corrected Android CI artifact

```text
ARTIFACT_ID=8967670563
ARTIFACT_FILES=1528
ARTIFACT_BYTES=3404532
ARTIFACT_SHA256=b2f5176b4ffd403c21ea0642b2a21b90030b4bdbb39d8980866a75c17c5b5527
ARTIFACT_DIGEST_MATCH=TRUE
MUTATIONS_XML_BYTES=47684
MUTATIONS_XML_SHA256=8e48ea76680392129ab0b418e0ee251ab07195a37724ded8d305b7aee11e9427
SUMMARY_JSON_BYTES=5011
SUMMARY_JSON_SHA256=4db0e015dbe9cad050a7a195a012fba5fb8e70c72ffa6439ed807638cc3d52f0
RAW_DERIVED_CROSS_CHECK=PASS
```

The complete mutation signature inventory remained identical to the rejected candidate and QA-4 measurement: same 63 classes, methods, line attributions, mutators, and descriptions. Only detection status, killing test, and execution metadata changed.

## Corrected mutation result

```text
MUTANTS_GENERATED=63
KILLED=43
SURVIVED=10
NO_COVERAGE=10
UNDETECTED_TOTAL=20
TIMED_OUT=0
NON_VIABLE=0
MEMORY_ERROR=0
RUN_ERROR=0
MUTATION_SCORE=68.253968%
TEST_STRENGTH=81.132075%
MUTATION_LINE_COVERAGE_PROXY=84.126984%
TARGETED_BEHAVIORAL_MUTANTS_KILLED=3/3
```

| Source line | Mutator | Killing test |
|---:|---|---|
| 168 | `MathMutator` | `verifiesOneCharacterCanonicalStrings` |
| 180 | `ConditionalsBoundaryMutator` | `preservesSpaceAtCanonicalControlBoundary` |
| 180 | `RemoveConditionalMutator_ORDER_ELSE` | `escapesUnitSeparatorBelowCanonicalControlBoundary` |

The 20 remaining undetected mutants match the non-behavioral adjudication categories:

```text
COMPILER_NULL_CHECK_EQUIVALENT_CANDIDATE=9
SEMANTICALLY_REDUNDANT_STRING_BRANCH=1
SYNTHETIC_PRIVATE_ACCESSOR_OUTSIDE_ACTIVE_CALL_GRAPH=4
UNREACHABLE_GENERIC_CANONICALIZER_BRANCH_IN_CURRENT_VERIFY_PATH=5
UNREACHABLE_NULL_BRANCH_IN_CURRENT_VERIFY_PATH=1
UNEXPLAINED_BEHAVIORAL_SURVIVORS=0
```

One compiler null-check mutation at line 182 changed from `NO_COVERAGE` to `SURVIVED` because the new control-character test reached the line. This does not create a new behavioral gap; the mutation removes a Kotlin-generated non-null check whose value is non-null under the current API contract.

## Coverage observation

The authored JVM view increased only through the added tests:

```text
AUTHORED_LINES=11813/28200=41.890071%
AUTHORED_BRANCHES=4295/17510=24.528841%
AUTHORED_INSTRUCTIONS=73709/227336=32.422933%
```

QA-5 does not establish a global coverage or mutation threshold.

## Managed-device compatibility artifact

```text
VALIDATION_ARTIFACT_ID=8968055727
VALIDATION_ARTIFACT_FILES=2362
VALIDATION_ARTIFACT_BYTES=50321855
VALIDATION_ARTIFACT_SHA256=b2321f47c7fa12463c852667c831ceb24cd2a07dba508151e6dd1c1c9ff0068b
VALIDATION_ARTIFACT_DIGEST_MATCH=TRUE
```

| Device | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `pixel2Api30` | 113 | 0 | 0 | 4 |
| `pixel2Api35` | 113 | 0 | 0 | 4 |

The four skips on each emulator remain the physical ARM64-only tests. No claim of physical ARM64 execution is made.

Canonical API 30 coverage evidence remained valid:

```text
ANDROIDTEST_INSTRUCTIONS=56072/260776=21.501979%
ANDROIDTEST_BRANCHES=1926/18312=10.517693%
ANDROIDTEST_LINES=10245/36415=28.134011%
COVERAGE_JSON_BYTES=4001
COVERAGE_JSON_SHA256=f10ee1bc40d080ea21053f71741c6e8a575bdfdee44e037dc788ee199c38e4bd
ADB_PULL_EXIT_CODE=0
CANONICAL_DEVICE=pixel2Api30
```

## Final-head verification rule

The final PR head is resolved from the external Git ref after this evidence commit. All five workflows must pass again on that unchanged final head. The measurement values above must remain semantically identical; any head movement or mutation-inventory drift invalidates the final gate.

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
