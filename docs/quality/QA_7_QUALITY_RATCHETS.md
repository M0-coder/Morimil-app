# Document status: CURRENT

# QA-7 — Quality regression ratchets

## Purpose

QA-7 converts the stable QA-0/QA-2 quality measurements into fail-closed non-regression gates. It does not claim that current coverage or warning debt is sufficient for a finished system. Equality and improvement pass; regression fails.

Frozen base: `main@826c85553d4561d777f05c1e2f6897fbf6bf8ab5`.

## Versioned baseline

`tools/quality/qa7_quality_ratchet_baseline.json` records reviewed evidence from the exact QA-6 candidate that was squash-merged into the frozen base.

The baseline protects:

- authored Android JVM line, branch, and instruction coverage;
- authored Android source files with zero line coverage;
- Python statement and branch coverage;
- Kotlin compiler warning fingerprints and multiplicity;
- Android Lint error ceiling, warning ceiling, and warning fingerprints;
- the complete frozen set of dependency coordinates already present in `app/build.gradle.kts`;
- the SHA-256 digest of the exact canonical `dependencies {}` block in `app/build.gradle.kts`;
- managed-device Android line, branch, and instruction coverage;
- managed-device source files with zero line coverage.

Coverage comparisons use integer fractions rather than rounded percentages.

## Warning policy

Existing warnings are debt, not an approval of the warning condition. QA-7 allows a warning to disappear but rejects a new fingerprint or a higher multiplicity of an existing fingerprint.

Kotlin warning fingerprints remove absolute checkout paths and source line/column numbers. Android Lint fingerprints use issue ID, repository-relative path, and message.

### GradleDependency remote-ageing rule

`GradleDependency` is normalized to issue ID + path + dependency coordinate because its text embeds the newest version currently visible to Lint.

The baseline records every dependency coordinate already present and the digest of the exact dependency-source block. The remote-ageing exemption is enabled only while that source digest remains unchanged.

```text
frozen dependency source unchanged
+ frozen coordinate
+ remote ecosystem later publishes a newer version
= REMOTE AGEING, not repository regression
```

That warning is allowed only while the total warning ceiling remains satisfied. It does not approve the old dependency version and does not modify the dependency.

By contrast:

```text
same coordinate
+ candidate changes or downgrades its dependency declaration
= dependency-source digest differs
= remote-ageing exemption disabled
= warning evaluated as repository regression
```

and:

```text
coordinate not present in frozen baseline
+ current candidate introduces it
+ Lint reports GradleDependency
= NEW REPOSITORY WARNING
```

Both remain ratchet failures.

The frozen coordinate list is validated as sorted, unique, non-empty canonical strings. The dependency-source digest must be a valid `sha256:<64 lowercase hex>` value. CI passes the current `app/build.gradle.kts` explicitly to the evaluator; malformed or missing source evidence fails closed.

## Coverage policy

Frozen floors are the exact audited fractions, not rounded percentages. QA-7 also prevents growth in the count of zero-line-coverage source files.

This is a ratchet, not a target. Future work should reduce zero-coverage files and warning debt and then move the baseline upward. Lowering a baseline requires its own evidence and review; it is not an automatic escape hatch.

## CI placement

Android CI produces JVM/Python evidence, captures one deterministic Kotlin compile-warning log, generates `lintDebug` XML, supplies the current `app/build.gradle.kts`, and runs the JVM ratchet.

Genesis Body Preparation continues to produce canonical API-30 managed-device coverage and runs only the instrumented ratchet against the resulting JSON. The workflow does not execute Genesis, import Seed material, activate Morimil, or declare operational birth.

## Acceptance criteria

1. QA-7 tooling tests pass.
2. JVM authored coverage does not regress from the frozen fractions.
3. Python statement/branch coverage does not regress from the frozen fractions.
4. Kotlin warnings do not exceed 12 and no warning fingerprint/multiplicity is new.
5. Android Lint errors remain 0 and warnings do not exceed 23.
6. Non-`GradleDependency` lint fingerprints cannot be new or increased.
7. `GradleDependency` remote ageing is exempt only for a frozen coordinate while the exact dependency-source digest remains unchanged.
8. A changed/downgraded dependency declaration disables that exemption; a new warned coordinate also fails.
9. Canonical API-30 instrumented coverage does not regress and zero-line source count does not exceed 211.
10. Required machine-readable ratchet result JSON files are uploaded as CI evidence.
11. No dependency versions, production Body/Guardian/Seed/Genesis state, activation, release, or birth operation is changed by QA-7 itself.
