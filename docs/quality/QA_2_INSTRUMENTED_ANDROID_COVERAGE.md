# Document status: CURRENT

# QA-2 — Instrumented Android coverage baseline

## Purpose

QA-0 and QA-1 measure JVM unit-test execution. They do not measure code executed by `androidTest` on Android devices.

QA-2 adds a separate, auditable AndroidTest baseline and prevents JVM-only zero coverage from being presented as proof that a component has no tests.

This phase is measurement-only. It introduces no percentage threshold, mutation threshold, runtime behavior change, production release, Body operation, Guardian operation, Seed import, Genesis execution, activation, or birth declaration.

## Isolation boundary

Coverage is enabled only through:

```text
tools/quality/android-instrumented-coverage.init.gradle
```

The init script sets `debug.enableAndroidTestCoverage = true` for isolated CI invocations. The normal module build configuration is unchanged, and release variants are not instrumented.

## Compatibility matrix and canonical measurement

The full instrumentation suite must continue to pass on:

```text
pixel2Api30 — Pixel 2, API 30, aosp-atd
pixel2Api35 — Pixel 2, API 35, aosp
```

Coverage publication is deliberately bound to one separately executed canonical device:

```text
pixel2Api30
```

The API 35 run remains a mandatory compatibility check. Its execution is not silently combined with the API 30 counters.

This separation is required because the first exploratory multi-device coverage run exposed an AGP output collision: API 30 execution data was written beneath a path labelled for API 35, while the generated XML contained a single API 30 session. That exploratory artifact is invalid as combined-device evidence.

## Task discovery

QA-2 parses `:app:tasks --all` and requires exactly one task with the AGP description:

```text
Creates JaCoCo test coverage report from data gathered on the Gradle managed device.
```

Missing or ambiguous task discovery fails closed.

## Canonical execution protocol

CI performs these operations in order:

1. run the ordinary API 30/API 35 compatibility matrix without coverage publication;
2. remove only prior generated coverage outputs;
3. rerun `pixel2Api30DebugAndroidTest` with debug AndroidTest instrumentation enabled;
4. require the canonical execution file at the exact API 30 output path;
5. invoke the discovered managed-device report task while excluding the API 35 test dependency;
6. require one XML report with exactly one JaCoCo session;
7. bind the XML, execution file and device ID in one machine-readable record.

## Evidence requirements

The collector requires:

- a valid canonical device identifier;
- one explicit non-empty `.ec` or `.exec` file;
- one explicit non-empty JaCoCo XML report;
- exactly one JaCoCo session with monotonic timestamps;
- non-empty `INSTRUCTION`, `BRANCH`, and `LINE` root counters;
- a non-empty source inventory;
- every tracked critical source to be present in the report;
- byte size and SHA-256 for the XML and execution data.

The schema is:

```text
morimil.android.instrumented.coverage.v1
```

Generated outputs:

```text
build/quality/android-instrumented-coverage-task.txt
build/quality/android-instrumented-coverage.json
build/quality/android-instrumented-coverage.md
```

The raw report and execution data remain authoritative and are preserved in the workflow artifact.

## Interpretation boundary

QA-2 reports API 30 instrumentation coverage independently from QA-1 JVM coverage. The percentages are not added because doing so would double-count source lines exercised by both suites.

A source may be classified as:

```text
JVM_COVERED
INSTRUMENTED_COVERED
COVERED_BY_BOTH
NOT_OBSERVED_BY_CURRENT_SUITES
```

The last category is a measurement result, not an automatic severity classification.

The raw AndroidTest report still includes generated code. A later authored-source device view may apply the already reviewed QA-1 whole-file exclusion policy, but QA-2 does not silently alter the raw denominator.

## Acceptance criteria

QA-2 is technically complete only when:

- the exact final PR head passes all required workflows;
- API 30 and API 35 compatibility tests pass;
- the canonical API 30 execution is rerun independently;
- the report task is selected deterministically;
- the XML is bound to exactly one session and one explicit API 30 execution file;
- counters and critical-source attribution are reproduced from the downloaded artifact;
- the evidence document records the exact head, run, artifact digest, metrics, anomaly resolution and limitations;
- the PR remains unmerged until explicit authorization.
