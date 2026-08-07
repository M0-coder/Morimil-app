# Document status: CURRENT

# QA-7 quality-ratchet evidence — 2026-08-07

## Frozen source

- Base: `main@826c85553d4561d777f05c1e2f6897fbf6bf8ab5`
- Base commit: `[QA-6] Establish resolved supply-chain truth (#168)`
- QA-7 branch: `qa/qa-7-quality-ratchets`
- Production source modified by baseline/tooling commits: `FALSE`

## JVM/Python baseline provenance

Android CI exact-head source run before the QA-6 merge:

- workflow: `Android CI`
- run: `31210166643` / run number `634`
- head: `7114a4bec070d93eefb66caed0a4b4b857babe8a`
- conclusion: `success`
- artifact ID: `9006509392`
- artifact digest: `sha256:010bf085d485c6df1af1bfd5cd9667cd2ecf77cd2f554b901320337b5878bf6f`

Authored Android JVM baseline:

- lines: `11813/28200 = 41.890071%`
- branches: `4295/17510 = 24.528841%`
- instructions: `73709/227336 = 32.422933%`
- authored source files: `288`
- reviewed generated exclusions: `11`
- authored files with zero line coverage: `117`

The authored Android counters were identical in Android CI run `31205494050` / run number `629`, establishing repeated-run stability before introducing the ratchet.

Python baseline from run 634:

- statements: `2485/3960 = 62.7525252525%`
- branches: `819/1538 = 53.2509752926%`

Kotlin compiler debt captured from the same build family:

- production warnings: `10`
- test warnings: `2`
- total: `12`

The versioned baseline stores normalized warning fingerprints and multiplicity; it does not depend on source line numbers or absolute runner paths.

## Lint and managed-device provenance

Genesis Body Preparation exact-head source run before the QA-6 merge:

- workflow: `Genesis Body Preparation`
- run: `31210166494` / run number `629`
- head: `7114a4bec070d93eefb66caed0a4b4b857babe8a`
- conclusion: `success`
- artifact ID: `9006863754`
- artifact digest: `sha256:16d3fa6144a4313385abea36b7505b02ef0ebc88de0cc82c8aa49263ec6e18e0`

Android Lint XML contains:

- errors: `0`
- warnings: `23`
- information: `2`
- warning fingerprints stored in baseline: `23`

Fifteen warnings are `GradleDependency`. Their fingerprint is normalized to the dependency coordinate so a new version appearing upstream does not create a false repository regression.

Canonical API-30 instrumented baseline:

- lines: `10245/36415 = 28.134011%`
- branches: `1926/18312 = 10.517693%`
- instructions: `56072/260776 = 21.501979%`
- report source files: `299`
- zero-line-coverage source files: `211`

## Local pre-publication contract verification

Before versioning CI integration, the QA-7 evaluator was exercised against the downloaded run-634/run-629 evidence and a reconstruction of the 12 exact Kotlin warning messages:

```text
QA7_QUALITY_RATCHET_JVM=PASS
QA7_QUALITY_RATCHET_INSTRUMENTED=PASS
```

The unit contract contains explicit tests for equality, improvement, coverage regression, zero-coverage growth, new Kotlin warning fingerprints, lint errors, new lint fingerprints, GradleDependency remote-version normalization, malformed GradleDependency messages, instrumented regression, and schema fail-closed behavior.

## Boundary

This evidence establishes a non-regression floor only. It does not claim production readiness, complete coverage, complete static-analysis remediation, Body succession, activation, or operational birth.

Final exact-head QA-7 CI run IDs and artifact digests remain `PENDING` until the draft PR executes.
