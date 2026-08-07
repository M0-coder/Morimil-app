# Document status: CURRENT

# QA-6 — Supply-chain truth

## Objective

Establish a reproducible and fail-closed chain of evidence from resolved Gradle components to the debug APK, a normalized SBOM, known-vulnerability queries, license metadata, dependency locks, and Gradle artifact checksums.

QA-6 does not claim that Maven coordinates can be reconstructed from DEX bytes after packaging. Instead, it binds the APK hash to the exact locked and verified resolution inventory used to build it, then independently inventories the APK archive and Syft output.

## Fixed baseline

```text
BASE_MAIN=92d15c0269b1d56ce26d4201e2b464c951a6f175
BRANCH=qa/qa-6-supply-chain-truth
PR_MODE=DRAFT
MERGE_AUTHORIZED=FALSE
```

## Scope

QA-6 may change only build configuration, dependency lock state, Gradle verification metadata, quality tooling, the existing SBOM workflow, security policy, documentation, and evidence.

It must not change application production source, public runtime behavior, databases, identity, memory, writer authority, bundled Genesis assets, approved Genesis hashes, Body, Guardian, Seed, activation, or birth state.

## Evidence model

1. `qa6ResolveAndLockAll` resolves every resolvable configuration while Gradle writes lock state and SHA-256 verification metadata.
2. `qa6ResolvedDependencyInventory` records every external module and resolved artifact with file size and SHA-256.
3. The debug APK is built under the generated verification metadata.
4. The Python reporter inventories every APK ZIP entry and native library.
5. A resolved-component SPDX 2.3 document is built from Gradle coordinates and artifact checksums.
6. OSV is queried for every exact Maven package version.
7. deps.dev is queried for package license metadata.
8. Syft scans the APK independently.
9. A validator checks expected direct modules, required APK structure, network-query completeness, and vulnerability adjudication policy.

## Bootstrap and enforcement

The first candidate run is a controlled bootstrap. It generates `app/gradle.lockfile` and `gradle/verification-metadata.xml` as CI artifacts. Those files must be independently inspected and committed before QA-6 can switch to strict enforcement.

After bootstrap:

```text
DEPENDENCY_LOCK_MODE=STRICT
DEPENDENCY_VERIFICATION=STRICT
LOCKFILE_REGENERATION_IN_NORMAL_CI=FORBIDDEN
VERIFICATION_METADATA_REGENERATION_IN_NORMAL_CI=FORBIDDEN
```

Generated checksums are a baseline, not proof of publisher identity. QA-6 records integrity through SHA-256; signature provenance remains a separate strengthening step where trustworthy PGP material exists.

## Vulnerability policy

The initial run is report-only so that the actual baseline can be adjudicated. Final strict mode fails on critical vulnerabilities that are not explicitly listed in the versioned adjudication file.

No vulnerability is accepted merely because it is transitive or because a fix is inconvenient. Acceptance requires evidence of presence, reachability, mitigations, available upgrades, and residual risk.

## License policy

License metadata is collected from deps.dev for each exact Maven version. Unknown licenses remain `NOASSERTION`; they are not silently converted into permissive licenses.

The repository's own license is not selected by QA-6 because that is a legal/governance decision requiring separate authorization.

## Required final gate

```text
RESOLVED_GRAPH_INVENTORY=PASS
APK_COMPONENT_INVENTORY=PASS
SBOM_RESOLVED_COMPONENTS=PASS
SYFT_APK_SBOM=PASS
OSV_QUERY_COMPLETE=PASS
UNADJUDICATED_CRITICAL_VULNERABILITIES=0
LICENSE_QUERY_ERRORS=0
DEPENDENCY_LOCKING=STRICT
GRADLE_VERIFICATION=STRICT
SECURITY_POLICY=PRESENT
BASELINE_TESTS=PASS
SCOPE_ESCAPE=FALSE
```

## Operational boundaries

```text
RELEASE_EXECUTED=FALSE
BODY_MODIFIED=FALSE
GUARDIAN_MODIFIED=FALSE
SEED_IMPORTED=FALSE
GENESIS_EXECUTED=FALSE
ACTIVATION_EXECUTED=FALSE
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```
