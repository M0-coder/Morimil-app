# Document status: CURRENT

# QA-4 — Mutation testing pilot evidence

## Evidence identity

```text
BASE_MAIN=ec7fb540c0fb200573d9276296fd631140d974c8
MEASUREMENT_HEAD=793d7a25620d4568be6db67f9ad38a5e61691c05
ANDROID_CI_RUN=31085820512
ANDROID_CI_RUN_NUMBER=593
ANDROID_CI_RESULT=SUCCESS
ARTIFACT_ID=8961443611
ARTIFACT_FILES=1528
ARTIFACT_BYTES=3404137
ARTIFACT_SHA256=faaffa0d0932a32f34e3dcf341d8d9202009de016ca2ae2eb7cff192eae0573b
ARTIFACT_DIGEST_MATCH=TRUE
```

The downloaded ZIP matched the GitHub-published byte count and SHA-256 digest exactly.

## Execution mechanism

QA-4 executed PIT `1.22.1` directly through an isolated Gradle init script and an ephemeral `:app:pitestDebug` `JavaExec` task.

The task depended on the existing `testDebugUnitTest` task and derived its test classes and runtime classpath from that authoritative AGP task at execution time.

No PIT plugin was added to the project build scripts. No normal debug, release, IDE, runtime, or application dependency path was modified.

## Quality-tool verification

```text
QUALITY_TOOL_TESTS=22
QUALITY_TOOL_FAILURES=0
QUALITY_TOOL_ERRORS=0
```

The QA-4 analyzer suite includes ten tests covering valid reports, target inner classes, reviewed Kotlin inline attribution, unknown attribution rejection, required primary attribution, empty reports, class-boundary escape, unknown statuses, malformed detection values, and survivor/no-coverage calculations.

Python operational-tool coverage after adding QA-4:

```text
PYTHON_COMPOSITE_COVERAGE=61.77%
PYTHON_LINE_COVERAGE=64.77%
PYTHON_BRANCH_COVERAGE=54.15%
QA4_ANALYZER_COMPOSITE_COVERAGE=67.06%
```

## Raw mutation report identity

```text
MUTATIONS_XML=app/build/reports/pitest/debug/mutations.xml
MUTATIONS_XML_BYTES=47034
MUTATIONS_XML_SHA256=368b2304df8714d64dc1e14ebda59f22294c5447250d35a02dbb47ebe79b5fc1
SUMMARY_JSON=build/quality/android-mutation-pilot.json
SUMMARY_JSON_BYTES=5012
SUMMARY_JSON_SHA256=245aa3b91dd53eb8179535d421878a041df18cce42cb1f1d303a5bd173b1497a
RAW_DERIVED_CROSS_CHECK=PASS
```

The derived JSON was recalculated against the raw XML. Report bytes, report hash, generated count, detected count, killing-test count, statuses, class inventory, source-attribution inventory, and attribution totals matched.

## Approved target boundary

```text
TARGET_CLASS_PREFIX=com.morimil.app.data.genesis.GenesisManifestVerifierCore
TARGET_TEST_CLASS=com.morimil.app.data.genesis.GenesisManifestVerifierCoreTest
PRIMARY_SOURCE_ATTRIBUTION=GenesisManifestVerifier.kt
REVIEWED_INLINE_SOURCE_ATTRIBUTION=Comparisons.kt
CLASS_SCOPE_ESCAPE=FALSE
SOURCE_ATTRIBUTION_ESCAPE=FALSE
```

Observed mutated classes:

1. `com.morimil.app.data.genesis.GenesisManifestVerifierCore` — 56 mutants
2. `com.morimil.app.data.genesis.GenesisManifestVerifierCore$ManifestFile` — 6 mutants
3. `com.morimil.app.data.genesis.GenesisManifestVerifierCore$verify$$inlined$sortedBy$1` — 1 mutant

Observed source attributions:

| Source attribution | Role | Mutants | Result |
|---|---|---:|---|
| `GenesisManifestVerifier.kt` | primary authored source | 62 | 39 killed, 12 survived, 11 no coverage |
| `Comparisons.kt` | reviewed Kotlin inline comparator | 1 | 1 killed |

The single `Comparisons.kt` mutation belongs to the target-bound class `GenesisManifestVerifierCore$verify$$inlined$sortedBy$1`, method `compare`, line attribution `102`. No other inline or standard-library source attribution was accepted.

## Mutation results

```text
MUTANTS_GENERATED=63
MUTANTS_DETECTED=40
MUTANTS_UNDETECTED=23
KILLED=40
SURVIVED=12
NO_COVERAGE=11
TIMED_OUT=0
NON_VIABLE=0
MEMORY_ERROR=0
RUN_ERROR=0
MUTATION_SCORE=40/63=63.492063%
TEST_STRENGTH=40/52=76.923077%
MUTATION_LINE_COVERAGE_PROXY=52/63=82.539683%
UNIQUE_MUTATION_LOCATIONS=42
KILLING_TEST_RECORDED=40
```

PIT additionally reported line coverage for the mutated classes as:

```text
PIT_MUTATED_CLASS_LINES=106/125=84.8%
PIT_TEST_CLASSES_EXAMINED=1
PIT_MUTANT_TEST_EXECUTIONS=108
PIT_SLOWEST_TEST_MILLIS=57
PIT_LARGEST_TEST_BLOCKS=392
```

The PIT display rounds mutation score to `63%`, test strength to `77%`, and mutated-class line coverage to `85%`. The evidence above preserves the exact ratios derived from XML and the exact line fraction printed by PIT.

## Mutator inventory

| Mutator | Generated | Killed | Survived | No coverage |
|---|---:|---:|---:|---:|
| `RemoveConditionalMutator_EQUAL_ELSE` | 27 | 23 | 2 | 2 |
| `VoidMethodCallMutator` | 10 | 1 | 7 | 2 |
| `EmptyObjectReturnValsMutator` | 7 | 5 | 0 | 2 |
| `RemoveConditionalMutator_ORDER_ELSE` | 4 | 2 | 1 | 1 |
| `ConditionalsBoundaryMutator` | 4 | 2 | 1 | 1 |
| `IncrementsMutator` | 3 | 2 | 0 | 1 |
| `NullReturnValsMutator` | 3 | 3 | 0 | 0 |
| `MathMutator` | 2 | 1 | 1 | 0 |
| `PrimitiveReturnsMutator` | 1 | 1 | 0 | 0 |
| `BooleanTrueReturnValsMutator` | 1 | 0 | 0 | 1 |
| `BooleanFalseReturnValsMutator` | 1 | 0 | 0 | 1 |

The seven surviving `VoidMethodCallMutator` cases are the largest observed survivor group. They are candidates for individual test review; QA-4 does not classify every survivor as a defect and does not hide or normalize them.

## Existing coverage remained stable

```text
ANDROID_RAW_INSTRUCTIONS=73692/260776=28.2587%
ANDROID_RAW_BRANCHES=4294/18312=23.4491%
ANDROID_RAW_LINES=11811/36415=32.4344%
ANDROID_AUTHORED_INSTRUCTIONS=73692/227336=32.4155%
ANDROID_AUTHORED_BRANCHES=4294/17510=24.5231%
ANDROID_AUTHORED_LINES=11811/28200=41.8830%
ZERO_LINE_COVERAGE_SOURCES=117
```

QA-4 did not alter application source, so the existing QA-3 JVM coverage values remained unchanged.

## Interpretation boundary

PIT emitted the warning that the project uses Kotlin without the separate ArcMutate Kotlin plugin. QA-4 therefore reports a bounded open-source JVM bytecode experiment, not complete semantic Kotlin mutation testing.

The pilot does not establish:

- a global mutation baseline for the repository;
- adequate mutation resistance for all verification paths;
- a blocking mutation threshold;
- semantic equivalence of all bytecode mutants;
- physical ARM64 mutation coverage;
- release readiness or activation authorization.

## Report-only policy

```text
MUTATION_THRESHOLD=0
COVERAGE_THRESHOLD=0
GLOBAL_THRESHOLD=NONE
SURVIVORS_BLOCK_MERGE=FALSE
EMPTY_REPORT_BLOCKS_CI=TRUE
SCOPE_ESCAPE_BLOCKS_CI=TRUE
BASELINE_TEST_FAILURE_BLOCKS_CI=TRUE
```

## Operational boundary

```text
APPLICATION_RUNTIME_CHANGED=FALSE
BUNDLED_GENESIS_ASSETS_CHANGED=FALSE
RELEASE_EXECUTED=FALSE
BODY_MODIFIED=FALSE
GUARDIAN_MODIFIED=FALSE
SEED_IMPORTED=FALSE
GENESIS_EXECUTED=FALSE
BIRTH_OCCURRED=FALSE
```

This evidence does not authorize merge, release, Body, Guardian, Seed, Genesis, activation, or birth operations.
