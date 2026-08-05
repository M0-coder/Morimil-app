# Document status: CURRENT

# QA-2 — Instrumented Android coverage baseline

## Purpose

QA-0 and QA-1 measure JVM unit-test execution. They do not measure code executed by `androidTest` on Android devices.

QA-2 adds a separate, auditable baseline for the existing Gradle Managed Device suite on API 30 and API 35. This prevents a JVM-only zero-coverage result from being misrepresented as proof that a component has no tests.

This phase is measurement-only. It introduces no percentage threshold, mutation threshold, runtime behavior change, release operation, Body operation, Guardian operation, Seed import, Genesis execution, activation, or birth declaration.

## Isolation boundary

AndroidTest coverage is enabled only through:

```text
tools/quality/android-instrumented-coverage.init.gradle
```

The init script sets `debug.enableAndroidTestCoverage = true` for the CI invocation. The normal module build configuration is not changed, and release variants are not instrumented.

## Device matrix

The existing managed-device matrix remains authoritative:

```text
pixel2Api30 — Pixel 2, API 30, aosp-atd
pixel2Api35 — Pixel 2, API 35, aosp
```

The same instrumentation suite must pass on both devices before a report is accepted.

## Task discovery

AGP task names are implementation details and can change between plugin versions. QA-2 therefore does not hard-code a guessed managed-device coverage task name.

The collector parses `:app:tasks --all` and requires exactly one task with the AGP description:

```text
Creates JaCoCo test coverage report from data gathered on the Gradle managed device.
```

Missing or ambiguous task discovery fails closed.

## Evidence requirements

The collector requires all of the following:

1. the API 30 and API 35 managed-device tests succeed;
2. the selected AGP report task succeeds;
3. at least one non-empty `.ec` or `.exec` JaCoCo execution-data file exists;
4. exactly one instrumented JaCoCo XML report is discoverable;
5. the XML contains non-empty `INSTRUCTION`, `BRANCH`, and `LINE` root counters;
6. every accepted raw file is recorded by relative path, byte size, and SHA-256.

The machine-readable schema is:

```text
morimil.android.instrumented.coverage.v1
```

Generated outputs:

```text
build/quality/android-instrumented-coverage-task.txt
build/quality/android-instrumented-coverage.json
build/quality/android-instrumented-coverage.md
```

The raw AGP report and execution data remain authoritative and are preserved in the workflow artifact.

## Interpretation boundary

QA-2 reports instrumentation coverage independently from QA-1 JVM coverage.

It does not add the percentages together. A future combined view may be proposed only after a reproducible mapping is established that avoids double-counting the same source lines across JVM and device reports.

A source can therefore be classified as:

```text
JVM_COVERED
INSTRUMENTED_COVERED
COVERED_BY_BOTH
NOT_OBSERVED_BY_CURRENT_SUITES
```

The final category means only that the current measured suites did not execute the source. It is not an automatic defect or severity classification.

## Acceptance criteria

QA-2 is technically complete only when:

- the exact PR head passes all required repository workflows;
- the managed-device coverage task is selected deterministically;
- raw execution data and one XML report are preserved;
- counters are reproduced from the downloaded artifact;
- the evidence document records the exact head, workflow run, artifact digest, device matrix, metrics, and limitations;
- the PR remains unmerged until explicit authorization.
