# Document status: CURRENT

# QA-0 — Coverage baseline

## Purpose

This phase establishes reproducible coverage evidence without changing Morimil runtime behavior and without introducing pass/fail percentage thresholds before a real baseline exists.

## Android JVM coverage

CI enables `debug` unit-test coverage only for the coverage invocation by using:

```text
--init-script tools/quality/android-unit-coverage.init.gradle
```

The report task is:

```text
:app:createDebugUnitTestCoverageReport
```

Expected report root and machine-readable report:

```text
app/build/reports/coverage/test/debug/
app/build/reports/coverage/test/debug/report.xml
```

The init script is intentionally isolated from normal debug and release builds. It does not alter application source, release signing, Body, Guardian, Seed, Genesis, database state, or runtime activation.

The first Android baseline is intentionally raw. It includes the class inventory emitted by the Android Gradle Plugin, including generated classes. No Android threshold may be introduced until generated code and authored code have been separated and the exclusions have been reviewed.

## Python coverage

CI uses the exact `coverage.py` version declared in the workflow and the repository configuration in `.coveragerc`.

Expected machine-readable outputs:

```text
build/quality/python-coverage.xml
build/quality/python-coverage.json
```

The measured suites are:

- `tools/governance/test_*.py`
- `tools/model-artifacts/test_*.py`
- `tools/benchmarks/test_*.py`
- `tools/android-arm64/test_current_trimotor_physical_evidence_v0.py`

The tests execute normally, but their own `test_*.py` source files are excluded from the published coverage denominator. The Python report therefore measures the operational tool code exercised by those tests rather than rewarding the test implementation for executing itself.

## Report integrity

CI must fail if any required report is absent, empty, malformed, or contains no measurable statements or counters. The validation step parses:

- Python JSON totals;
- Android JaCoCo XML counters;
- `INSTRUCTION`, `BRANCH`, and `LINE` Android counters.

The workflow logs the measured percentages and retains the complete reports as an artifact.

## Baseline policy

QA-0 is measurement-only:

- no minimum line percentage;
- no minimum branch percentage;
- no changed-lines gate;
- no mutation-score gate;
- no production-promotion claim.

Thresholds may be introduced only after the generated reports have been inspected, exclusions have been justified, generated code has been separated from authored code, and the baseline is stable across repeated CI runs.

## Acceptance criteria

1. Existing unit and Python tests remain green.
2. Android coverage HTML and XML data are produced by the Android Gradle Plugin task.
3. Python XML and JSON coverage reports are non-empty and parse successfully.
4. Python test source is absent from the published denominator.
5. Required Android counters exist and have non-zero totals.
6. Reports are retained as CI artifacts.
7. No release, signing, Genesis, Body, Guardian, Seed, or birth workflow is executed or modified.
