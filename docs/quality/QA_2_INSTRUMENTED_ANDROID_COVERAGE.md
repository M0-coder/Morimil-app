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

The full instrumentation suite must pass on:

```text
pixel2Api30 — Pixel 2, API 30, aosp-atd
pixel2Api35 — Pixel 2, API 35, aosp
```

Coverage publication is bound to one separately executed canonical device:

```text
pixel2Api30
```

API 35 remains a mandatory compatibility check. Its execution is not silently combined with API 30 counters.

## AGP output-label anomaly

Exploratory runs established that AGP 8.6.1 can write API 30 coverage data beneath a directory named `pixel2Api35`, even when API 30 is the only coverage-instrumented device. The directory label is therefore not accepted as device provenance.

The authoritative provenance is the successful ADB pull log stored beneath the API 30 test-result tree. It records:

- the canonical device-scoped testlog path;
- the remote `coverage.ec` source;
- the exact local destination selected by AGP;
- `EXIT CODE: 0`.

The collector requires the destination parsed from that log to resolve to the exact `.ec` file being hashed and measured. A mismatched device path, destination or exit code fails closed. Whether the AGP destination label matches the canonical device is recorded explicitly.

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
4. require exactly one non-empty `.ec` under the managed-device coverage output tree;
5. require exactly one successful API 30 ADB coverage-pull log;
6. bind the pull destination to the selected `.ec` file;
7. invoke the discovered report task while excluding the API 35 test dependency;
8. require one XML report with exactly one JaCoCo session;
9. bind XML, execution data, pull provenance and device ID in one record.

## Evidence requirements

The collector requires:

- a valid canonical device identifier;
- one explicit non-empty `.ec` or `.exec` file;
- one explicit successful device-scoped ADB pull log;
- one explicit non-empty JaCoCo XML report;
- exactly one JaCoCo session with monotonic timestamps;
- non-empty `INSTRUCTION`, `BRANCH`, and `LINE` root counters;
- a non-empty source inventory;
- every tracked critical source to be present;
- byte size and SHA-256 for XML, execution data and provenance log.

The schema is:

```text
morimil.android.instrumented.coverage.v1
```

Generated summaries:

```text
build/quality/android-instrumented-coverage-task.txt
build/quality/android-instrumented-coverage.json
build/quality/android-instrumented-coverage.md
```

The raw report, execution data and ADB log remain authoritative and are preserved in the workflow artifact.

## Interpretation boundary

QA-2 reports API 30 instrumentation coverage independently from QA-1 JVM coverage. Percentages are not added because doing so would double-count source lines exercised by both suites.

A source may be classified as:

```text
JVM_COVERED
INSTRUMENTED_COVERED
COVERED_BY_BOTH
NOT_OBSERVED_BY_CURRENT_SUITES
```

The last category is a measurement result, not an automatic severity classification.

The raw AndroidTest report still includes generated code. A later authored-source device view may apply the reviewed QA-1 whole-file exclusion policy, but QA-2 does not silently alter the raw denominator.

## Acceptance criteria

QA-2 is technically complete only when:

- the exact final PR head passes all required workflows;
- API 30 and API 35 compatibility tests pass;
- the canonical API 30 execution is rerun independently;
- the report task is selected deterministically;
- one `.ec` is bound to one successful API 30 pull log;
- the XML contains one session and reproducible counters;
- critical-source attribution is reproduced from the downloaded artifact;
- evidence records the exact head, run, artifact digest, metrics, anomaly and limitations;
- the PR remains unmerged until explicit authorization.
