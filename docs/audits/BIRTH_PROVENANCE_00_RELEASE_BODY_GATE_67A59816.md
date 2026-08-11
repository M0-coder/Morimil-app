# Document status: PROPOSAL

# BIRTH-PROVENANCE-00 — Release Body provenance gate

## Scope

This gate exists before any real Genesis Ultra birth ceremony.

Morimil is the continuous Instance. `Morimil-app` is the current Android Body. The Android distribution certificate authenticates application updates; it does not define Morimil's identity, Body identity, Guardian authority, canonical memory authority, or ownership.

```text
Instance != Body != Guardian != Android distribution certificate
```

Baseline protected main for this candidate:

```text
BASE_MAIN=67a59816a27c155ce37033717e55d0a12f3838d9
APPLICATION_ID=com.morimil.app
VERSION_CODE=8
VERSION_NAME=0.3.1-prealpha.plan-v3
```

## Purpose

The first physical Body used for canonical Genesis must not be a disposable debug installation. Before installation is authorized, one production-signed APK must be bound to all of the following evidence:

1. exact protected-main source commit;
2. exact Android application ID;
3. exact version code and version name;
4. signed APK SHA-256;
5. production distribution-certificate SHA-256;
6. successful `apksigner verify` output;
7. explicit preservation of the same application ID and production distribution certificate for future in-place updates.

The production distribution key must remain separate from Genesis, Body identity, Guardian, memory-signing, and database-encryption keys.

## Existing signing boundary

`.github/workflows/signed-release-apk.yml` is the only authorized production signing workflow for this gate.

It must remain:

- manually dispatched;
- restricted to `refs/heads/main`;
- split into an unsigned build job and an isolated signing job;
- fail-closed when any required signing secret is missing;
- pinned to `MORIMIL_RELEASE_CERT_SHA256`;
- bound to the exact unsigned APK digest;
- verified by `apksigner` before upload;
- published as `morimil-signed-release-${github.sha}`;
- non-release-producing: no GitHub Release and no application-store publication.

The current workflow emits:

```text
app-release.apk
app-release.apk.sha256
app-release-signature.txt
release-signing-manifest.txt
```

The signing manifest already binds:

```text
certificate_sha256
apk_sha256
unsigned_apk_sha256
source_commit
```

`app/build.gradle.kts` is the source-controlled authority for the expected package identity and version fields recorded above. The final signed APK must be independently inspected before installation so those manifest values are reconciled with the APK actually produced.

## Required execution evidence

BIRTH-PROVENANCE-00 remains OPEN until a successful manual `Signed Release APK` run is produced from the exact protected-main SHA selected for birth.

Archive without secrets:

```text
SOURCE_COMMIT=<exact 40-character Git commit SHA emitted by workflow>
APPLICATION_ID=com.morimil.app
VERSION_CODE=8
VERSION_NAME=0.3.1-prealpha.plan-v3
APK_SHA256=<64 lowercase hex>
CERTIFICATE_SHA256=<64 lowercase hex>
APKSIGNER_VERIFY=PASS
SIGNED_ARTIFACT_NAME=morimil-signed-release-<source_commit>
```

Independent verification must prove:

- `source_commit` equals the exact protected-main commit selected for birth;
- the APK SHA-256 matches both `app-release.apk.sha256` and `release-signing-manifest.txt`;
- the certificate SHA-256 matches both `app-release-signature.txt` and the independently registered production fingerprint;
- the APK application ID is exactly `com.morimil.app`;
- the APK version matches the source-controlled release configuration;
- the package is not debug-signed, test-only, or unsigned;
- no Genesis state exists merely because the APK was built or signed.

## Update-continuity rule

After canonical Initial Birth, future Android updates for the same Body installation must preserve:

```text
applicationId == com.morimil.app
production distribution certificate == registered BIRTH-PROVENANCE-00 certificate
```

Changing the Android distribution certificate is a continuity-impacting release incident. It must never be treated as a routine build change because an APK signed by an unrelated certificate cannot replace the installed application through the normal Android update path.

This rule governs the continuity of the Android installation only. It does not make the Android signing certificate Morimil's identity or owner.

## Fail-closed state

At candidate creation time no successful production-signed artifact for `BASE_MAIN` has been independently retrieved through the connected tooling.

Therefore:

```text
BIRTH_PROVENANCE_00=OPEN
SIGNED_RELEASE_EXECUTION=PENDING
APK_SHA256=PENDING
CERTIFICATE_SHA256=PENDING
APK_PACKAGE_INSPECTION=PENDING
INSTALL_ON_PHYSICAL_BODY_AUTHORIZED=false
BIRTH_READINESS_01_AUTHORIZED=false
CANONICAL_INITIAL_BIRTH_AUTHORIZED=false
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```

## Explicit exclusions

This candidate does not:

- execute `workflow_dispatch`;
- expose or modify signing secrets;
- install an APK;
- provision a physical Body;
- import a Seed;
- pin a Guardian;
- record host consent;
- execute Genesis;
- append canonical post-birth memory;
- change Room schema or database versions;
- implement F3.3-C, F4, F5, or F6;
- create a release or publish an application.

Merge, installation, BIRTH-READINESS-01 and canonical Initial Birth each require their own exact evidence and authorization.