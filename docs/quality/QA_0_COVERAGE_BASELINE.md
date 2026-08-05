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

Expected report root:

```text
app/build/reports/coverage/test/debug/
```

The init script is intentionally isolated from normal debug and release builds. It does not alter application source, release signing, Body, Guardian, Seed, Genesis, database state, or runtime activation.

## Python coverage

CI uses the exact `coverage.py` version declared in the workflow and the repository configuration in `.coveragerc`.

Expected machine-readable outputs:

```text
build/quality/python-coverage.xml
build/quality/python-coverage.json
```

The initial measured suites are:

- `tools/governance/test_*.py`
- `tools/model-artifacts/test_*.py`
- `tools/benchmarks/test_*.py`
- `tools/android-arm64/test_current_trimotor_physical_evidence_v0.py`

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
2. Android coverage HTML/XML data is produced by the Android Gradle Plugin task.
3. Python XML and JSON coverage reports are non-empty.
4. Reports are retained as CI artifacts.
5. No release, signing, Genesis, Body, Guardian, Seed, or birth workflow is executed or modified.
