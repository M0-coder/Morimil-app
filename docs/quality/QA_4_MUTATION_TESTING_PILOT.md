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

PIT is executed only through:

```text
tools/quality/android-pitest-pilot.init.gradle
```

The init script registers an ephemeral `:app:pitestDebug` `JavaExec` task. That task:

1. depends on the authoritative AGP `testDebugUnitTest` task;
2. derives test classes and runtime classpath from that task at execution time;
3. limits mutable bytecode to the debug production-class directories;
4. invokes the pinned PIT command-line engine directly;
5. writes non-timestamped XML and HTML reports.

No Android PIT Gradle plugin is applied to the project. Normal Gradle builds, debug builds, release builds, IDE synchronization, and the application build scripts do not register or execute PIT.

QA-4 does not modify:

- application runtime behavior;
- the public `GenesisManifestVerifier(Context)` API;
- bundled Genesis assets or manifest contents;
- database schemas or DAOs;
- release signing or packaging policy;
- Body, Guardian, Seed, Genesis state, activation, or birth state.

## Pinned tooling

```text
PIT_COMMAND_LINE=org.pitest:pitest-command-line:1.22.1
PIT_ENGINE=org.pitest:pitest:1.22.1
PIT_ENTRY=org.pitest:pitest-entry:1.22.1
PIT_HTML_REPORT=org.pitest:pitest-html-report:1.22.1
JVM=17
THREADS=1
MUTATORS=DEFAULTS
OUTPUTS=XML,HTML
TIMESTAMPED_REPORTS=FALSE
```

Open-source PIT operates on JVM bytecode and does not provide complete semantic Kotlin mutation support. Therefore QA-4 records a bytecode pilot, not an authoritative Kotlin mutation-quality score.

## Approved mutation boundary

```text
TARGET_CLASS=com.morimil.app.data.genesis.GenesisManifestVerifierCore*
TARGET_TEST=com.morimil.app.data.genesis.GenesisManifestVerifierCoreTest
PRIMARY_SOURCE_ATTRIBUTION=GenesisManifestVerifier.kt
REVIEWED_INLINE_SOURCE_ATTRIBUTION=Comparisons.kt
```

No other application class is authorized for mutation in QA-4.

`Comparisons.kt` is allowed only as an exact source attribution while the mutated class remains inside the approved `GenesisManifestVerifierCore*` prefix. Kotlin inlines the comparator created by `sortedBy`; PIT attributes that generated comparator bytecode to the standard-library source name instead of the authored file.

The approved attribution set is closed. The analyzer:

- requires at least one mutation attributed to `GenesisManifestVerifier.kt`;
- permits `Comparisons.kt` only as the reviewed inline attribution;
- records classes, methods, lines, statuses and mutant counts for every attribution;
- rejects every other source attribution.

The report analyzer also rejects:

- an empty or missing mutation report;
- malformed XML;
- unknown PIT statuses;
- malformed `detected` attributes;
- mutations outside the approved class prefix;
- missing class, source, method, line, or mutator metadata.

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
MUTATION_LOCATION_INVENTORY
SOURCE_ATTRIBUTION_INVENTORY
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
- the report violates the approved class, attribution, or evidence contract.

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
3. the analyzer proves the mutation class and source-attribution scope did not escape;
4. the raw XML and derived JSON are audited from the downloaded artifact;
5. all five required repository workflows pass on the final evidence head;
6. the PR remains draft and unmerged until separate explicit authorization.

## Operational boundary

QA-4 does not authorize release, physical-device mutation, Body modification, Guardian modification, Seed import, Genesis execution, activation, or birth.
