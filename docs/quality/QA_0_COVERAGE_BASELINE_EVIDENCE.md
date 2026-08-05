# Document status: CURRENT

# QA-0 — Coverage baseline evidence

## Verified source

- Base commit: `a856d2045769eff805aa2d03a43c97a86723f3cc`
- Measurement head: `053850091b74fb4507c84a97c011ddaae6695b52`
- Workflow: Android CI run `31019736548`
- Coverage artifact ID: `8936235911`
- Coverage artifact SHA-256: `6ac9b601bef92db2e94d1cc492779469b967d49fda2f662897e8fe20f6a44597`

## Verified workflow result

The following workflows completed successfully on the exact measurement head:

- Android CI
- Genesis Body Preparation
- Reference Checks
- CodeQL
- SBOM

## Python operational-tool baseline

Python test source files execute normally but are excluded from the published denominator.

- Composite coverage: `59.36%`
- Line coverage: `62.74%`
- Branch coverage: `51.21%`
- Statements: `2799`
- Covered statements: `1756`
- Branches: `1158`
- Covered branches: `593`

## Android JVM raw baseline

The Android report is the raw Android Gradle Plugin/JaCoCo inventory. It includes generated classes and therefore must not yet be used as a minimum quality threshold.

- Instruction coverage: `73027 / 260721 = 28.0096%`
- Branch coverage: `4247 / 18312 = 23.1924%`
- Line coverage: `11720 / 36399 = 32.1987%`

## Interpretation boundary

These figures establish a reproducible measurement baseline. They do not establish adequate coverage, production readiness, mutation resistance, physical ARM64 coverage, or permission to promote, merge, release, activate, import a Seed, execute Genesis, or declare birth.

The next quality phase must separate authored Android code from generated code and define a reviewed non-regression policy before introducing thresholds.
