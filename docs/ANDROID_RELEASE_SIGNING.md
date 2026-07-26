# Document status: CURRENT

# Android release signing

## Scope

This document governs the certificate used to sign distributable Android APK or AAB files.
It does **not** define Morimil's identity, Genesis identity, guardian authority, memory signing,
or the cryptographic identity of a body installation.

```text
Android distribution certificate != Morimil body identity
```

The Android signing key authenticates the publisher of an application update. It must never be
reused as:

- the future body identity key;
- a Genesis Ultra identity key;
- a guardian trust-anchor key;
- a memory-event signing key;
- a database encryption or wrapping key.

## Fail-closed Gradle contract

Release builds require all four values below, supplied as environment variables or Gradle
properties:

```text
MORIMIL_RELEASE_STORE_FILE
MORIMIL_RELEASE_STORE_PASSWORD
MORIMIL_RELEASE_KEY_ALIAS
MORIMIL_RELEASE_KEY_PASSWORD
```

The repository contains no keystore and no passwords. `assembleRelease`, `bundleRelease`, and
other release tasks depend on `:app:validateReleaseSigning`. Missing or unreadable material must
stop the build rather than produce an unsigned package or fall back to debug signing.

Local example:

```bash
export MORIMIL_RELEASE_STORE_FILE="/absolute/path/to/morimil-release.jks"
export MORIMIL_RELEASE_STORE_PASSWORD="<store password>"
export MORIMIL_RELEASE_KEY_ALIAS="<key alias>"
export MORIMIL_RELEASE_KEY_PASSWORD="<key password>"
./gradlew --no-daemon :app:assembleRelease
```

Never place these values in `gradle.properties`, source-controlled scripts, issue comments, build
logs, screenshots, or chat transcripts.

## Production GitHub Actions secrets

The manual workflow `.github/workflows/signed-release-apk.yml` requires:

```text
MORIMIL_RELEASE_KEYSTORE_BASE64
MORIMIL_RELEASE_STORE_PASSWORD
MORIMIL_RELEASE_KEY_ALIAS
MORIMIL_RELEASE_KEY_PASSWORD
MORIMIL_RELEASE_CERT_SHA256
```

`MORIMIL_RELEASE_KEYSTORE_BASE64` is the complete keystore encoded as one Base64 value. The
workflow reconstructs it only under the ephemeral runner directory, assigns restrictive file
permissions, and removes it in an `always()` cleanup step.

`MORIMIL_RELEASE_CERT_SHA256` is the pinned SHA-256 fingerprint of the production signing
certificate. Colons and letter case are ignored during comparison. The workflow refuses to upload
an APK when the certificate extracted by `apksigner` differs from this value.

## Fingerprint registration

On a trusted offline workstation, inspect the certificate before entering its fingerprint as a
GitHub Actions secret:

```bash
keytool -list -v \
  -keystore /absolute/path/to/morimil-release.jks \
  -alias <key alias>
```

Record the SHA-256 certificate fingerprint through a second channel and compare it before adding
`MORIMIL_RELEASE_CERT_SHA256`. Do not derive trust solely from a value printed by the same CI run
that is being verified.

## Workflow output

A successful manual run uploads a single artifact containing:

```text
app-release.apk
app-release.apk.sha256
app-release-signature.txt
release-signing-manifest.txt
```

The manifest binds the APK digest, certificate fingerprint, and source commit. The workflow does
not create a GitHub Release or publish to an application store.

## CI certificate

`Genesis Body Preparation` generates a short-lived certificate during pull-request validation.
That certificate only tests the mechanics of fail-closed signing and `apksigner` verification. It
is not a production certificate, is removed after the job, and its APK is not uploaded as a
release artifact.

## Custody requirements

- Keep the production keystore outside the repository.
- Maintain at least two encrypted offline backups under separate physical custody.
- Store passwords separately from the keystore backups.
- Register and preserve the certificate SHA-256 fingerprint independently.
- Never rotate or replace the production key silently.
- Treat loss, disclosure, or unexpected fingerprint change as a release-blocking incident.
