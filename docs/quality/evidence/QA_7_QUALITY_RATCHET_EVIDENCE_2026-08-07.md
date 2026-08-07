# Document status: CURRENT

# QA-7 quality-ratchet evidence — 2026-08-07

## Frozen source

- Base: `main@826c85553d4561d777f05c1e2f6897fbf6bf8ab5`
- Base commit: `[QA-6] Establish resolved supply-chain truth (#168)`
- QA-7 branch: `qa/qa-7-quality-ratchets`
- Production source modified by QA-7: `FALSE`

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

## First QA-7 exact-head CI proof

Candidate head: `5fc67a40a8387df3c8303612ed5f2bbce820bcce`.

All five pull-request workflows completed successfully on that same head:

- Android CI: run `31220359325`, run number `638`, `success`
- SBOM: run `31220359230`, run number `349`, `success`
- CodeQL: run `31220359204`, run number `351`, `success`
- Reference Checks: run `31220359221`, run number `462`, `success`
- Genesis Body Preparation: run `31220359263`, run number `632`, `success`

Android CI QA-7 artifact:

- artifact ID: `9010281741`
- published digest: `sha256:8ea9e75da241fcc782911e89f2d3307a2e99bb5fc8b32cb73dc62381919c3cd4`
- independently recomputed ZIP digest: `8ea9e75da241fcc782911e89f2d3307a2e99bb5fc8b32cb73dc62381919c3cd4`
- digest match: `TRUE`
- JVM ratchet: `PASS`
- authored Android counters: unchanged at the baseline
- authored zero-line files: `117`
- Kotlin warnings: `12`, new/increased fingerprints: `0`
- Lint: `0` errors, `23` warnings, `2` information, new/increased warning fingerprints: `0`
- Python statements: `2661/4203 = 63.3119200571%` (improved)
- Python branches: `890/1644 = 54.1362530414%` (improved)

Genesis QA-7 artifact:

- artifact ID: `9010520249`
- published digest: `sha256:98fa981f54b3afb29c8d86e1a8e06c9b8c060fd961cca25f6b9208688c3f1796`
- independently recomputed ZIP digest: `98fa981f54b3afb29c8d86e1a8e06c9b8c060fd961cca25f6b9208688c3f1796`
- digest match: `TRUE`
- instrumented ratchet: `PASS`
- lines: `10245/36415`
- branches: `1926/18312`
- instructions: `56072/260776`
- zero-line-coverage source files: `211`

This evidence update is intentionally the last repository mutation in QA-7 implementation. Because it creates a new head, QA-7 is not considered `CI_PR_VERDE` until all five workflows reproduce green on that final head. The final head SHA and workflow results are authoritative in GitHub/PR state rather than recursively editing this evidence file again.

## Boundary

This evidence establishes a non-regression floor only. It does not claim production readiness, complete coverage, complete static-analysis remediation, Body succession, activation, or operational birth.
