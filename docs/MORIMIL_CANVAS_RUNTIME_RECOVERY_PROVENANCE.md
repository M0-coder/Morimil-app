# Morimil Canvas runtime-recovery v1 provenance

DOCUMENT_STATUS=CURRENT
RECOVERY_ID=morimil.canvas.runtime-recovery.v1
MORIMIL_COMMERCIAL_STATUS=NON_COMMERCIAL
MORIMIL_APP_ROLE=CURRENT_PRIVATE_ANDROID_BODY
REPOSITORY_GOVERNANCE_STATE=HOLD_ADMINISTRATIVE
READY_FOR_REVIEW_AUTHORIZED=false
MERGE_AUTHORIZED=false

## Scope

This record documents the reproducible recovery source used to restore local build availability for the current private Android Body. It changes no Canvas runtime byte and grants no functional, review, integration, or release authority.

The original external bundle was not recovered:

```text
originalBundleRecovered=false
originalBundleSha256=73b061406d9fff999a859025f497bece4680a896ad19eccb6a391cdb50cd0507
```

The vendored successor demonstrates exact equivalence of the runtime tree executed by the audited APK. It does **not** establish byte-for-byte identity with the original lost ZIP.

## Forensic source

```text
sourceWorkflowRunId=30592451855
sourceArtifactId=8779073588
sourceArtifactDigest=sha256:72c00b39491d4ba8b46478f9749e5e09d936718795bd314ce15e17df8a166c54
sourceArtifactExpiresAt=2026-10-29T00:04:24Z
sourceHead=7bdbda2aa4b7568695ba8e98be54d506d42c99d5
apkEntry=app/build/outputs/apk/debug/app-debug.apk
apkSha256=314b99a5a67d60f8d2d379d8efc1d7ef52caeacdc24d7dd1b32eb7b448cab623
```

The source artifact is the `morimil-validation-reports` artifact from the green `Genesis Body Preparation` run `30592451855` for the audited PR #149 source head. The APK entry above is the sole authorized extraction source.

## Runtime-tree equivalence

```text
runtimeFileCount=48
runtimeTotalBytes=3922742
manifestSchema=morimil.canvas.bundle.v1
contentVersion=0.3.1
entrypoint=index.html
bridgeSchema=morimil.canvas.bridge.v1
manifestDeclaredPayloadBytes=3913521
canonicalTreeSha256=e3d58636c98987d41f57409cc91e473564207eacd0e81e385108a0f54ddd6985
```

The canonical tree digest covers all 48 files, including `morimil-canvas.manifest.json`, in ascending POSIX-path order. Each digest record is encoded exactly as:

```text
<path>\0<decimal-size>\0<sha256-hex>\n
```

Every manifest-declared payload entry was checked for exact path, size, and SHA-256. No additional runtime file was accepted.

## Vendored successor

```text
successorBundleName=morimil-canvas-0.3.1-runtime-recovery-v1.zip
successorBundleSizeBytes=3931846
successorBundleSha256=6bbc1a5127f6db742db87a3cb6af9631bba387e7c0ff543309d48ffb5eac4835
```

The successor ZIP contains only files, uses ascending POSIX paths, `ZIP_STORED`, timestamp `1980-01-01T00:00:00`, Unix regular-file mode `0100644`, an empty extra field, and empty entry/global comments. Two clean reproductions were byte-identical.

## Machine-readable evidence

```text
path=app/vendor/morimil-canvas/morimil-canvas-0.3.1-runtime-recovery-v1.provenance.json
schema=morimil.canvas.runtime-recovery.provenance.v1
sizeBytes=964
sha256=cf57eff71ac919cc59a18e1815d49dd97702b3fe8e4864bb101f016f7147a542
```

The JSON contains exactly 17 fields, sorted lexicographically, encoded as compact UTF-8 without BOM or trailing newline. It declares `originalBundleRecovered=false` and records runtime-tree equivalence rather than identity with the lost archive.

## Build boundary

`prepareMorimilCanvasAssets` consumes the vendored ZIP by default and fails closed. `MORIMIL_CANVAS_ZIP` is only an explicit local override and remains subject to the same exact successor SHA-256 and provenance checks. No remote URL, network fallback, mutable source reference, or integrity bypass is part of the active path.

This repair does not close the repository HOLD, the remaining F3 work, or any later gate.
