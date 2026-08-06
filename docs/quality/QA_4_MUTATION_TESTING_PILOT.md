# Document status: CURRENT

# QA-4 — Mutation testing pilot

## Objective

Establish a reproducible, report-only mutation-testing pilot for the deterministic Genesis manifest verification core introduced by QA-3.

QA-4 measures whether the existing JVM tests detect bounded bytecode mutations. It does not establish a global mutation score, a release gate, or complete semantic support for Kotlin.

## Fixed base

```text
BASE_MAIN=ec7fb540c0fb200573d9276296fd631140d974c8
BRANCH=qa/qa-4-mutation-testing-pilot
PR_MODE=DRAFT
MERGE_AUTHORIZED=FALSE
```

## Isolation

The mutation plugin is loaded only through:

```text
tools/quality/android-pitest-pilot.init.gradle
```

Normal Gradle builds, debug builds, release builds, IDE synchronization, and the application build scripts do not apply PIT.

QA-4 does not modify:

- application runtime behavior;
- the public `GenesisManifestVerifier(Context)` API;
- bundled Genesis assets or manifest contents;
- database schemas or DAOs;
- release signing or packaging policy;
- Body, Guardian, Seed, Genesis state, activation, or birth state.

## Pinned tooling

```text
ANDROID_PIT_GRADLE_PLUGIN=pl.droidsonroids.pitest:0.2.27
PIT_ENGINE=org.pitest:pitest-command-line:1.22.1
JVM=17
THREADS=1
MUTATORS=DEFAULTS
OUTPUTS=XML,HTML
TIMESTAMPED_REPORTS=FALSE
```

The Android Gradle PIT integration identifies itself as experimental. Open-source PIT operates on JVM bytecode and does not provide complete semantic Kotlin mutation support. Therefore QA-4 records a bytecode pilot, not an authoritative Kotlin mutation-quality score.

## Approved mutation boundary

```text
TARGET_CLASS=com.morimil.app.data.genesis.GenesisManifestVerifierCore*
TARGET_TEST=com.morimil.app.data.genesis.GenesisManifestVerifierCoreTest
TARGET_SOURCE=GenesisManifestVerifier.kt
```

No other application class is authorized for mutation in QA-4.

The report analyzer rejects:

- an empty or missing mutation report;
- malformed XML;
- unknown PIT statuses;
- malformed `detected` attributes;
- mutations outside the approved class prefix;
- mutations attributed to another source file;
- missing class, source, line, or mutator metadata.

## Published metrics

QA-4 publishes, without a blocking percentage threshold:

```text
MUTANTS_GENERATED
MUTANTS_DETECTED
MUTANTS_UNDETECTED
KILLED
SURVIVED
NO_COVERAGE
TIMED_OUT
NON_VIABLE
MEMORY_ERROR
RUN_ERROR
MUTATION_SCORE
TEST_STRENGTH
MUTATION_LINE_COVERAGE_PROXY
MUTATOR_INVENTORY
MUTATED_LINE_INVENTORY
```

`mutation_score` is the percentage of generated mutants whose PIT `detected` attribute is true.

`test_strength` is `KILLED / (KILLED + SURVIVED)` when that denominator is nonzero.

`mutation_line_coverage_proxy` is `(generated - NO_COVERAGE) / generated`. It is not a replacement for JaCoCo line coverage.

## Report-only policy

```text
MUTATION_THRESHOLD=0
COVERAGE_THRESHOLD=0
GLOBAL_THRESHOLD=NONE
CHANGED_LINES_THRESHOLD=NONE
```

PIT still fails the workflow when:

- the baseline tests fail under PIT;
- no mutations are generated;
- the task cannot complete;
- the report violates the approved scope or evidence contract.

A survived mutant is evidence for test review, not an automatic merge failure in QA-4.

## Expected artifacts

```text
app/build/reports/pitest/debug/mutations.xml
app/build/reports/pitest/debug/index.html
build/quality/android-mutation-pilot.json
build/quality/android-mutation-pilot.md
```

The raw PIT report and derived machine-readable summary must both be uploaded by the existing governed Android CI artifact step.

## Completion gate

QA-4 is technically complete only after:

1. quality-tool unit tests pass;
2. `:app:pitestDebug` completes on the exact PR head;
3. the analyzer proves the mutation scope did not escape;
4. the raw XML and derived JSON are audited from the downloaded artifact;
5. all five required repository workflows pass on the final evidence head;
6. the PR remains draft and unmerged until separate explicit authorization.

## Operational boundary

QA-4 does not authorize release, physical-device mutation, Body modification, Guardian modification, Seed import, Genesis execution, activation, or birth.
