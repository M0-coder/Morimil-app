# Document status: CURRENT

# QA-2 — Instrumented Android coverage evidence

## Evidence scope

This record captures the downloaded GitHub Actions artifact produced from the QA-2 measurement head. It is evidence of test execution and coverage measurement only.

It does not authorize or declare a merge, production release, Body mutation, Guardian mutation, Seed import, Genesis execution, activation, or birth.

```text
REPOSITORY=morimilpabfelon-cell/Morimil-app
PR=164
BRANCH=qa/qa-2-instrumented-coverage-baseline
BASE_MAIN=03f0544fdceb31724c787b9b0e86e7c46687f20b
EVIDENCE_SOURCE_HEAD=0520058ef013725ff08345ea4665e61ec17dca18
MEASUREMENT_DATE_UTC=2026-08-05
```

The evidence file is committed after `EVIDENCE_SOURCE_HEAD`. Therefore the resulting evidence commit must itself pass the complete workflow set before QA-2 can be considered technically closed. This record deliberately avoids claiming that later validation before it exists.

## Workflow results on the evidence source head

All five pull-request workflows completed successfully on the same source head:

| Workflow | Run ID | Run number | Result |
|---|---:|---:|---|
| Android CI | `31039814186` | `575` | `success` |
| CodeQL | `31039814393` | `288` | `success` |
| Reference Checks | `31039814295` | `399` | `success` |
| SBOM | `31039814213` | `286` | `success` |
| Genesis Body Preparation | `31039814273` | `574` | `success` |

The workflow name `Genesis Body Preparation` identifies the Android validation pipeline. No Genesis execution occurred.

## Workflow artifact

```text
ARTIFACT_ID=8944656840
ARTIFACT_NAME=morimil-validation-reports
ARTIFACT_BYTES=50334222
ARTIFACT_GITHUB_DIGEST=sha256:c3d23352da76fcb3fc0d2b4f3bf241b6075eeec4f36068cad6bfa4dc79ca2d79
DOWNLOADED_ZIP_SHA256=c3d23352da76fcb3fc0d2b4f3bf241b6075eeec4f36068cad6bfa4dc79ca2d79
ARTIFACT_DIGEST_MATCH=TRUE
```

The downloaded ZIP digest exactly matches the digest published by GitHub Actions.

## Managed-device compatibility matrix

The authoritative JUnit XML records are:

| Device | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `pixel2Api30` | 113 | 0 | 0 | 4 |
| `pixel2Api35` | 113 | 0 | 0 | 4 |

The four skipped cases are physical ARM64-only tests and are not represented as emulator execution.

## Canonical API 30 coverage binding

```text
CANONICAL_DEVICE_ID=pixel2Api30
REPORT_TASK=createManagedDeviceDebugAndroidTestCoverageReport
JACOCO_SESSIONS=1
SOURCE_FILES=299
ZERO_LINE_COVERAGE_SOURCES=211
```

JaCoCo session:

```text
SESSION_ID=unknownhost-9882bf70
SESSION_START_EPOCH_MS=1785959143061
SESSION_DUMP_EPOCH_MS=1785959406524
SESSION_TIMESTAMPS_MONOTONIC=TRUE
```

The canonical execution data is stored by AGP beneath a directory labelled `pixel2Api35`, despite the independent coverage run executing only `pixel2Api30`:

```text
EXECUTION_DATA_PATH=app/build/outputs/managed_device_code_coverage/debug/pixel2Api35/coverage.ec
AGP_DESTINATION_LABEL_MATCHES_CANONICAL_DEVICE=FALSE
```

Device provenance is instead bound through the successful API 30 ADB pull record:

```text
PROVENANCE_PATH=app/build/outputs/androidTest-results/managedDevice/debug/pixel2Api30/testlog/adb.032.pull.coverage.ec.ok.txt
ADB_PULL_EXIT_CODE=0
ADB_PULL_DESTINATION_MATCHES_EXECUTION_DATA=TRUE
EXECUTION_DATA_FILES=1
PROVENANCE_LOGS=1
```

No API 35 execution data is combined with the canonical API 30 counters.

## Global raw coverage

The raw AndroidTest report includes generated code and is not combined with QA-1 JVM counters.

| Counter | Covered | Total | Coverage |
|---|---:|---:|---:|
| Instructions | 56,072 | 260,721 | 21.506515% |
| Branches | 1,927 | 18,312 | 10.523154% |
| Lines | 10,245 | 36,399 | 28.146378% |

## Critical-source attribution

| Source | Lines | Branches | Instructions |
|---|---:|---:|---:|
| `com/morimil/app/security/SecretVault.kt` | 107/135 — 79.259259% | 35/68 — 51.470588% | 623/872 — 71.444954% |
| `com/morimil/app/data/local/MorimilDatabaseEncryption.kt` | 146/242 — 60.330579% | 48/128 — 37.500000% | 566/1212 — 46.699670% |
| `com/morimil/app/data/genesis/ultra/GenesisUltraAndroidBodyMemoryKeyStore.kt` | 109/120 — 90.833333% | 30/54 — 55.555556% | 585/730 — 80.136986% |
| `com/morimil/app/data/genesis/GenesisManifestVerifier.kt` | 0/104 — 0% | 0/72 — 0% | 0/899 — 0% |
| `com/morimil/app/data/genesis/ultra/GenesisUltraSignedSeedPreviewCoordinator.kt` | 0/106 — 0% | 0/80 — 0% | 0/910 — 0% |

The zero values are observed coverage results. They are not proof that the source has no tests in every possible suite, and they are not an automatic severity classification.

## Evidence hashes

```text
ARTIFACT_ZIP_SHA256=c3d23352da76fcb3fc0d2b4f3bf241b6075eeec4f36068cad6bfa4dc79ca2d79
COVERAGE_JSON_SHA256=13c05a0a9c802aefef47fca229ef9b770d724af30b2aeb4e050b95f295502057
COVERAGE_MARKDOWN_SHA256=d0cf506580ba7912405bedd21bbe1c5427635ceb349a13af2a926b1419171e70
JACOCO_XML_SHA256=66cb0c70deaa2987104931aa71b4804c076650235d62071721eb21b8e43ebdb7
EXECUTION_DATA_SHA256=7b3bea3771cb98fba12455bec1f6fd79651faa1a4e47df70ee5f459e12833b39
API30_ADB_PROVENANCE_SHA256=9b9f03e5d57e32ba37af1e4a6710ae36519db3e553597be19b7e047f7acdf41b
CANONICAL_API30_LOG_SHA256=8f19068333c81262dcb41b240c374a7368e3482b593c9e23df0c2f2b0f1cc484
COMPATIBILITY_MATRIX_LOG_SHA256=a128794d6a8a383333e03d1c2379bdf7b837e1d9a6c41c3f2175845cbd2e13fd
```

## Interpretation and limitations

- This is a baseline, not a percentage quality gate.
- Raw AndroidTest coverage includes generated code.
- JVM and AndroidTest percentages are reported separately to avoid double counting.
- Physical ARM64 execution remains outside the managed-emulator evidence.
- `GenesisManifestVerifier.kt` and `GenesisUltraSignedSeedPreviewCoordinator.kt` were not observed executing in this canonical run.
- AGP 8.6.1's destination-label anomaly is retained in the evidence rather than hidden or renamed.
- The large workflow artifact is retained by GitHub Actions; this repository versions only the compact evidence record and hashes.

## State at evidence creation

```text
QA_2_MEASUREMENT_HEAD_VALIDATED=TRUE
QA_2_EVIDENCE_VERSIONED=TRUE
FINAL_EVIDENCE_COMMIT_WORKFLOWS=REQUIRED
PR_164_MERGED=FALSE
MAIN_MODIFIED=FALSE
PRODUCTION_RELEASE_EXECUTED=FALSE
BODY_MODIFIED=FALSE
GUARDIAN_MODIFIED=FALSE
SEED_IMPORTED=FALSE
GENESIS_EXECUTED=FALSE
BIRTH_OCCURRED=FALSE
```
