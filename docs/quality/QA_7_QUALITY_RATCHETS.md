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
- managed-device Android line, branch, and instruction coverage;
- managed-device source files with zero line coverage.

Coverage comparisons use integer fractions rather than rounded percentages.

## Warning policy

Existing warnings are debt, not an approval of the warning condition. QA-7 allows a warning to disappear but rejects a new fingerprint or a higher multiplicity of an existing fingerprint.

Kotlin warning fingerprints remove absolute checkout paths and source line/column numbers. This keeps the gate stable when unrelated edits move a warning without changing its semantic message.

Android Lint fingerprints use issue ID, repository-relative path, and message.

### GradleDependency remote-ageing rule

`GradleDependency` is intentionally normalized to issue ID + path + dependency coordinate because the text embeds the newest version currently visible to Lint.

The baseline also records every dependency coordinate already present in the frozen `app/build.gradle.kts`, regardless of whether that coordinate emitted `GradleDependency` at baseline capture time.

This distinction is required because repository state and remote release state are different evidence domains:

```text
frozen coordinate exists in baseline
+ remote ecosystem later publishes a newer version
+ unchanged coordinate begins emitting GradleDependency
= REMOTE AGEING, not repository regression
```

That warning is allowed only while the total warning ceiling remains satisfied. It does not approve the old dependency version and does not modify the dependency.

By contrast:

```text
coordinate not present in frozen baseline
+ current candidate introduces it
+ Lint reports GradleDependency
= NEW REPOSITORY WARNING
```

That remains a ratchet failure.

A newly published remote version must therefore not make an unchanged repository fail CI, while a newly warned dependency coordinate introduced after the frozen baseline still fails.

The frozen coordinate list is validated as sorted, unique, non-empty canonical strings. QA-7 fails closed if that baseline structure is malformed.

## Coverage policy

Frozen floors are the exact audited fractions, not rounded percentages. QA-7 also prevents growth in the count of zero-line-coverage source files.

This is a ratchet, not a target. Future work should reduce zero-coverage files and warning debt and then move the baseline upward. Lowering a baseline requires its own evidence and review; it is not an automatic escape hatch.

## CI placement

Android CI produces the JVM/Python evidence, captures one deterministic Kotlin compile-warning log, generates `lintDebug` XML, and runs the JVM ratchet.

Genesis Body Preparation continues to produce canonical API-30 managed-device coverage and runs only the instrumented ratchet against the resulting JSON. The workflow does not execute Genesis, import Seed material, activate Morimil, or declare operational birth.

## Acceptance criteria

1. QA-7 tooling tests pass.
2. JVM authored coverage does not regress from the frozen fractions.
3. Python statement/branch coverage does not regress from the frozen fractions.
4. Kotlin warnings do not exceed 12 and no warning fingerprint/multiplicity is new.
5. Android Lint errors remain 0 and warnings do not exceed 23.
6. Non-`GradleDependency` lint fingerprints cannot be new or increased.
7. `GradleDependency` may newly appear only for a coordinate already frozen in the baseline; a newly warned non-baseline coordinate fails.
8. Canonical API-30 instrumented coverage does not regress and zero-line source count does not exceed 211.
9. Required machine-readable ratchet result JSON files are uploaded as CI evidence.
10. No dependency versions, production source, Body, Guardian, Seed, Genesis asset/state, activation, release, or birth operation is changed by QA-7.
