# Document status: CURRENT

# QA-3 — Critical verification tests evidence

## Evidence scope

This record captures the QA-3 measurement head and its downloaded GitHub Actions artifacts. It documents test execution, coverage, compatibility, and limitations.

It does not authorize merge, release, Body mutation, Guardian mutation, Seed import, Genesis execution, activation, or birth.

```text
REPOSITORY=morimilpabfelon-cell/Morimil-app
PR=165
BRANCH=qa/qa-3-critical-verification-tests
BASE_MAIN=ffc3805a534409e9cc702ba3b5463d2d50dfd3f5
MEASUREMENT_HEAD=49e7a822d720b4b978f007f7ed301f456e7f2199
MEASUREMENT_DATE_UTC=2026-08-06
```

This evidence file is committed after `MEASUREMENT_HEAD`. The resulting evidence commit must therefore pass the full workflow set before QA-3 can be considered technically complete.

## Workflow results on the measurement head

All required pull-request workflows completed successfully on the same head:

| Workflow | Run ID | Run number | Result |
|---|---:|---:|---|
| Android CI | `31063636824` | `579` | `success` |
| Genesis Body Preparation | `31063636814` | `577` | `success` |
| Reference Checks | `31063636876` | `403` | `success` |
| CodeQL | `31063636844` | `292` | `success` |
| SBOM | `31063636918` | `290` | `success` |

The workflow name `Genesis Body Preparation` identifies the Android validation pipeline. No Genesis execution occurred.

## JVM verification

The downloaded Gradle JUnit XML inventory contained:

```text
JVM_TEST_SUITES=154
JVM_TESTS=749
JVM_FAILURES=0
JVM_ERRORS=0
JVM_SKIPPED=0
```

The QA-3 suite result was:

```text
SUITE=com.morimil.app.data.genesis.GenesisManifestVerifierCoreTest
TESTS=15
FAILURES=0
ERRORS=0
SKIPPED=0
```

The suite covers a valid in-memory bundle and the documented fail-closed cases for malformed JSON, schema, approved hash, startup verification, file count, required flags, unsafe paths, duplicate paths, file integrity, exact inventory, and canonical core hash.

The fixture uses an in-memory `GenesisAssetSource`. It does not read, rewrite, stage, install, or execute the repository's bundled Genesis assets.

## Android CI coverage artifact

```text
ARTIFACT_ID=8953119523
ARTIFACT_NAME=current-trimotor-evidence-validation
ARTIFACT_BYTES=3383394
ARTIFACT_FILES=1520
ARTIFACT_GITHUB_DIGEST=sha256:0400721762fe7dadae55118221859971edf086d45e81ad5644b914851b2008d2
DOWNLOADED_ZIP_SHA256=0400721762fe7dadae55118221859971edf086d45e81ad5644b914851b2008d2
ARTIFACT_DIGEST_MATCH=TRUE
```

Key report hashes:

```text
JVM_JACOCO_XML_SHA256=58d81feb30f49e543c243b9b8e483d0e053f44e419e80189468801ef7434a082
AUTHORED_COVERAGE_JSON_SHA256=53b7acf6644a13cf081188abd6d9e562f2a6f99dd886e0c573526830c006956e
PYTHON_COVERAGE_JSON_SHA256=f2b4a9226d86fcd42c2b4971c999f2e2ab93340550ba64160e1acc4b745e2077
```

## Targeted JVM coverage

`GenesisManifestVerifier.kt` moved from zero observed JVM line coverage in the QA-1 baseline to:

| Counter | Covered | Total | Coverage |
|---|---:|---:|---:|
| Instructions | 650 | 954 | 68.1342% |
| Branches | 47 | 72 | 65.2778% |
| Lines | 90 | 120 | 75.0000% |

The deterministic `GenesisManifestVerifierCore` itself measured:

| Counter | Covered | Total | Coverage |
|---|---:|---:|---:|
| Instructions | 561 | 680 | 82.5000% |
| Branches | 47 | 64 | 73.4375% |
| Lines | 76 | 90 | 84.4444% |
| Methods | 9 | 9 | 100.0000% |

The public Android wrapper and `AndroidGenesisAssetSource` remain unexecuted by the JVM suite. That is expected because QA-3 intentionally tests the deterministic core without mocking Android assets.

## Authored Android JVM baseline change

QA-1 baseline:

```text
AUTHORED_LINES=11720/28184=41.5839%
AUTHORED_BRANCHES=4247/17510=24.2547%
AUTHORED_INSTRUCTIONS=73027/227281=32.1307%
ZERO_LINE_COVERAGE_SOURCES=119
```

QA-3 measurement head:

```text
AUTHORED_LINES=11811/28200=41.8830%
AUTHORED_BRANCHES=4294/17510=24.5231%
AUTHORED_INSTRUCTIONS=73692/227336=32.4155%
ZERO_LINE_COVERAGE_SOURCES=117
```

The denominator changed because the refactor introduced explicit adapter and core source structure. These values are evidence, not a percentage gate.

## Managed-device validation artifact

```text
ARTIFACT_ID=8953298913
ARTIFACT_NAME=morimil-validation-reports
ARTIFACT_BYTES=50327141
ARTIFACT_FILES=2364
ARTIFACT_GITHUB_DIGEST=sha256:a775c550562a1963a58648215e72394e0425c480acfcfb95e23bab187a5cfcec
DOWNLOADED_ZIP_SHA256=a775c550562a1963a58648215e72394e0425c480acfcfb95e23bab187a5cfcec
ARTIFACT_DIGEST_MATCH=TRUE
```

Managed-device JUnit results:

| Device | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `pixel2Api30` | 113 | 0 | 0 | 4 |
| `pixel2Api35` | 113 | 0 | 0 | 4 |

The four skipped cases on each emulator require physical ARM64 hardware and are not represented as executed.

## Canonical AndroidTest coverage integrity

```text
CANONICAL_DEVICE=pixel2Api30
JACOCO_SESSIONS=1
EXECUTION_DATA_FILES=1
API30_ADB_PROVENANCE_LOGS=1
ADB_PULL_EXIT_CODE=0
AGP_DESTINATION_LABEL_MATCHES_DEVICE=FALSE
```

Raw AndroidTest counters:

| Counter | Covered | Total | Coverage |
|---|---:|---:|---:|
| Instructions | 56,072 | 260,776 | 21.501979% |
| Branches | 1,926 | 18,312 | 10.517693% |
| Lines | 10,245 | 36,415 | 28.134011% |

Evidence hashes:

```text
ANDROIDTEST_JACOCO_XML_SHA256=1edff6851109fb961b9ef78a8a7e05782b708176262593c8e14840e394718618
EXECUTION_DATA_SHA256=11a6f1e68c0cca6fd1d6d093e1b13ad0ba61065819aa43dbeb1289ddf53410fc
API30_ADB_PROVENANCE_SHA256=54aafbc40a88d15f7c8513d9778702df7f4d5e33168e223899d6bc4dc0334c76
ANDROIDTEST_COVERAGE_JSON_SHA256=5f41f0952fc2fb5ae10a0e2341653c94301e881a50efd4851b54b2d045afc3df
```

`GenesisManifestVerifier.kt` remained at `0/120` instrumented lines because QA-3 adds JVM tests only. JVM and AndroidTest measurements remain separate to avoid double counting.

## Behavioral-equivalence boundary

QA-3 preserves the public constructor and method signature used by `GenesisReader`, the production approved hash and count, validation order, and rejection messages. Existing JVM, lint, APK, release-isolation, and managed-device suites passed.

These results support behavioral compatibility; they are not a formal proof that every possible Android `AssetManager` behavior is equivalent.

## Operational boundary

QA-3 does not establish mutation resistance, physical ARM64 coverage, complete Android-adapter coverage, production readiness, release authorization, Seed authorization, Genesis authorization, activation, or birth.

```text
QA_3_MEASUREMENT_HEAD_VALIDATED=TRUE
QA_3_EVIDENCE_VERSIONED=TRUE
FINAL_EVIDENCE_COMMIT_WORKFLOWS=REQUIRED
PR_165_MERGED=FALSE
MAIN_MODIFIED=FALSE
BODY_MODIFIED=FALSE
GUARDIAN_MODIFIED=FALSE
SEED_IMPORTED=FALSE
GENESIS_EXECUTED=FALSE
BIRTH_OCCURRED=FALSE
```
