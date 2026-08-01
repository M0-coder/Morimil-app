#!/usr/bin/env python3
"""Reproduce and verify the Morimil Canvas runtime-recovery v1 bundle."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import sys
import tempfile
import zipfile

ARTIFACT_SHA256 = "72c00b39491d4ba8b46478f9749e5e09d936718795bd314ce15e17df8a166c54"
ARTIFACT_SIZE_BYTES = 42_121_669
ARTIFACT_ENTRY_COUNT = 812
DEFAULT_APK_ENTRY = "app/build/outputs/apk/debug/app-debug.apk"
APK_SHA256 = "314b99a5a67d60f8d2d379d8efc1d7ef52caeacdc24d7dd1b32eb7b448cab623"
APK_ASSET_PREFIX = "assets/morimil-canvas/"
RUNTIME_FILE_COUNT = 48
RUNTIME_TOTAL_BYTES = 3_922_742
MANIFEST_SCHEMA = "morimil.canvas.bundle.v1"
CONTENT_VERSION = "0.3.1"
ENTRYPOINT = "index.html"
BRIDGE_SCHEMA = "morimil.canvas.bridge.v1"
MANIFEST_DECLARED_PAYLOAD_BYTES = 3_913_521
CANONICAL_TREE_SHA256 = "e3d58636c98987d41f57409cc91e473564207eacd0e81e385108a0f54ddd6985"
SUCCESSOR_BUNDLE_NAME = "morimil-canvas-0.3.1-runtime-recovery-v1.zip"
SUCCESSOR_BUNDLE_SIZE_BYTES = 3_931_846
SUCCESSOR_BUNDLE_SHA256 = "6bbc1a5127f6db742db87a3cb6af9631bba387e7c0ff543309d48ffb5eac4835"
RECOVERY_ID = "morimil.canvas.runtime-recovery.v1"
PROVENANCE_SCHEMA = "morimil.canvas.runtime-recovery.provenance.v1"
PROVENANCE_JSON_SIZE_BYTES = 964
PROVENANCE_JSON_SHA256 = "cf57eff71ac919cc59a18e1815d49dd97702b3fe8e4864bb101f016f7147a542"
ORIGINAL_BUNDLE_SHA256 = "73b061406d9fff999a859025f497bece4680a896ad19eccb6a391cdb50cd0507"
SOURCE_WORKFLOW_RUN_ID = 30_592_451_855
SOURCE_ARTIFACT_ID = 8_779_073_588
SOURCE_ARTIFACT_DIGEST = f"sha256:{ARTIFACT_SHA256}"
SOURCE_ARTIFACT_EXPIRES_AT = "2026-10-29T00:04:24Z"
SOURCE_HEAD = "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"


class VerificationError(RuntimeError):
    """Raised when any normative byte or metadata check diverges."""


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise VerificationError(message)


def validate_relative_posix_path(path: str) -> None:
    require(path != "", "Runtime path is empty")
    require("\\" not in path, f"Runtime path uses a backslash: {path}")
    pure = PurePosixPath(path)
    require(not pure.is_absolute(), f"Runtime path is absolute: {path}")
    require(all(part not in ("", ".", "..") for part in pure.parts), f"Unsafe runtime path: {path}")
    require(str(pure) == path, f"Runtime path is not canonical POSIX: {path}")


def read_source_artifact(artifact: Path, apk_entry: str) -> bytes:
    require(artifact.is_file(), f"Artifact is not a file: {artifact}")
    require(artifact.stat().st_size == ARTIFACT_SIZE_BYTES, "Artifact size mismatch")
    require(sha256_file(artifact) == ARTIFACT_SHA256, "Artifact SHA-256 mismatch")
    with zipfile.ZipFile(artifact, "r") as archive:
        infos = archive.infolist()
        require(len(infos) == ARTIFACT_ENTRY_COUNT, "Artifact entry-count mismatch")
        names = [info.filename for info in infos]
        require(apk_entry in names, f"APK entry is missing: {apk_entry}")
        require(names.count(apk_entry) == 1, f"APK entry is duplicated: {apk_entry}")
        apk_bytes = archive.read(apk_entry)
    require(sha256_bytes(apk_bytes) == APK_SHA256, "APK SHA-256 mismatch")
    return apk_bytes


def extract_runtime(apk_bytes: bytes) -> dict[str, bytes]:
    runtime: dict[str, bytes] = {}
    with zipfile.ZipFile(io.BytesIO(apk_bytes), "r") as apk:
        for info in apk.infolist():
            if not info.filename.startswith(APK_ASSET_PREFIX):
                continue
            relative = info.filename[len(APK_ASSET_PREFIX) :]
            if info.is_dir():
                continue
            validate_relative_posix_path(relative)
            require(relative not in runtime, f"Duplicate runtime path: {relative}")
            runtime[relative] = apk.read(info)
    require(len(runtime) == RUNTIME_FILE_COUNT, "Runtime file-count mismatch")
    require(sum(len(data) for data in runtime.values()) == RUNTIME_TOTAL_BYTES, "Runtime total-byte mismatch")
    return runtime


def validate_manifest(runtime: dict[str, bytes]) -> None:
    manifest_name = "morimil-canvas.manifest.json"
    require(manifest_name in runtime, "Runtime manifest is missing")
    require(ENTRYPOINT in runtime, "Runtime entrypoint is missing")
    try:
        manifest = json.loads(runtime[manifest_name].decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise VerificationError(f"Runtime manifest is invalid JSON: {exc}") from exc

    require(manifest.get("schema") == MANIFEST_SCHEMA, "Manifest schema mismatch")
    require(manifest.get("version") == CONTENT_VERSION, "Manifest content version mismatch")
    require(manifest.get("entrypoint") == ENTRYPOINT, "Manifest entrypoint mismatch")
    require(manifest.get("bridgeSchema") == BRIDGE_SCHEMA, "Manifest bridge schema mismatch")
    require(
        manifest.get("totalBytes") == MANIFEST_DECLARED_PAYLOAD_BYTES,
        "Manifest declared payload-byte mismatch",
    )
    files = manifest.get("files")
    require(isinstance(files, list), "Manifest files must be an array")
    require(len(files) == RUNTIME_FILE_COUNT - 1, "Manifest file-entry count mismatch")

    declared: dict[str, dict[str, object]] = {}
    for item in files:
        require(isinstance(item, dict), "Manifest file entry must be an object")
        path = item.get("path")
        require(isinstance(path, str), "Manifest file path must be a string")
        validate_relative_posix_path(path)
        require(path != manifest_name, "Manifest must not declare itself as payload")
        require(path not in declared, f"Manifest duplicates path: {path}")
        declared[path] = item

    require(set(declared) == set(runtime) - {manifest_name}, "Manifest/runtime path inventory mismatch")
    payload_bytes = 0
    for path, item in declared.items():
        data = runtime[path]
        require(item.get("size") == len(data), f"Manifest size mismatch: {path}")
        require(item.get("sha256") == sha256_bytes(data), f"Manifest SHA-256 mismatch: {path}")
        payload_bytes += len(data)
    require(payload_bytes == MANIFEST_DECLARED_PAYLOAD_BYTES, "Manifest payload sum mismatch")


def canonical_tree_sha256(runtime: dict[str, bytes]) -> str:
    digest = hashlib.sha256()
    for path in sorted(runtime):
        data = runtime[path]
        digest.update(f"{path}\0{len(data)}\0{sha256_bytes(data)}\n".encode("utf-8"))
    return digest.hexdigest()


def build_canonical_zip(runtime: dict[str, bytes]) -> bytes:
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_STORED, allowZip64=True) as bundle:
        bundle.comment = b""
        for path in sorted(runtime):
            info = zipfile.ZipInfo(path, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_STORED
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            info.extra = b""
            info.comment = b""
            info.flag_bits = 0
            bundle.writestr(info, runtime[path], compress_type=zipfile.ZIP_STORED)
    return output.getvalue()


def provenance_bytes(apk_entry: str) -> bytes:
    provenance = {
        "apkEntry": apk_entry,
        "apkSha256": APK_SHA256,
        "canonicalTreeSha256": CANONICAL_TREE_SHA256,
        "originalBundleRecovered": False,
        "originalBundleSha256": ORIGINAL_BUNDLE_SHA256,
        "recoveryId": RECOVERY_ID,
        "runtimeFileCount": RUNTIME_FILE_COUNT,
        "runtimeTotalBytes": RUNTIME_TOTAL_BYTES,
        "schema": PROVENANCE_SCHEMA,
        "sourceArtifactDigest": SOURCE_ARTIFACT_DIGEST,
        "sourceArtifactExpiresAt": SOURCE_ARTIFACT_EXPIRES_AT,
        "sourceArtifactId": SOURCE_ARTIFACT_ID,
        "sourceHead": SOURCE_HEAD,
        "sourceWorkflowRunId": SOURCE_WORKFLOW_RUN_ID,
        "successorBundleName": SUCCESSOR_BUNDLE_NAME,
        "successorBundleSha256": SUCCESSOR_BUNDLE_SHA256,
        "successorBundleSizeBytes": SUCCESSOR_BUNDLE_SIZE_BYTES,
    }
    return json.dumps(provenance, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")


def write_atomic(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=path.parent, prefix=f".{path.name}.", delete=False) as stream:
        temporary = Path(stream.name)
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temporary, path)


def verify_or_write(path: Path, expected: bytes, verify_only: bool, label: str) -> None:
    if verify_only:
        require(path.is_file(), f"{label} is missing: {path}")
        require(path.read_bytes() == expected, f"{label} bytes diverge: {path}")
    else:
        write_atomic(path, expected)
        require(path.read_bytes() == expected, f"{label} write verification failed: {path}")


def run(args: argparse.Namespace) -> None:
    artifact = args.artifact.resolve()
    output = args.output.resolve()
    provenance_output = args.provenance_output.resolve()

    apk_bytes = read_source_artifact(artifact, args.apk_entry)
    runtime = extract_runtime(apk_bytes)
    validate_manifest(runtime)
    tree_sha256 = canonical_tree_sha256(runtime)
    require(tree_sha256 == CANONICAL_TREE_SHA256, "Canonical runtime-tree SHA-256 mismatch")

    with tempfile.TemporaryDirectory(prefix="morimil-canvas-recovery-a-") as first_dir, tempfile.TemporaryDirectory(
        prefix="morimil-canvas-recovery-b-"
    ) as second_dir:
        first = Path(first_dir) / SUCCESSOR_BUNDLE_NAME
        second = Path(second_dir) / SUCCESSOR_BUNDLE_NAME
        first.write_bytes(build_canonical_zip(runtime))
        second.write_bytes(build_canonical_zip(runtime))
        first_bytes = first.read_bytes()
        second_bytes = second.read_bytes()
        require(first_bytes == second_bytes, "Successor ZIP reproductions are not byte-identical")

    require(len(first_bytes) == SUCCESSOR_BUNDLE_SIZE_BYTES, "Successor ZIP size mismatch")
    require(sha256_bytes(first_bytes) == SUCCESSOR_BUNDLE_SHA256, "Successor ZIP SHA-256 mismatch")

    provenance = provenance_bytes(args.apk_entry)
    with tempfile.TemporaryDirectory(prefix="morimil-canvas-provenance-a-") as first_dir, tempfile.TemporaryDirectory(
        prefix="morimil-canvas-provenance-b-"
    ) as second_dir:
        first_provenance = Path(first_dir) / "provenance.json"
        second_provenance = Path(second_dir) / "provenance.json"
        first_provenance.write_bytes(provenance)
        second_provenance.write_bytes(provenance_bytes(args.apk_entry))
        require(
            first_provenance.read_bytes() == second_provenance.read_bytes(),
            "Provenance JSON reproductions are not byte-identical",
        )

    require(len(provenance) == PROVENANCE_JSON_SIZE_BYTES, "Provenance JSON size mismatch")
    require(sha256_bytes(provenance) == PROVENANCE_JSON_SHA256, "Provenance JSON SHA-256 mismatch")

    verify_or_write(output, first_bytes, args.verify_only, "Successor ZIP")
    verify_or_write(provenance_output, provenance, args.verify_only, "Provenance JSON")

    print(f"ARTIFACT_SHA256={sha256_file(artifact)}")
    print(f"APK_SHA256={sha256_bytes(apk_bytes)}")
    print(f"FILE_COUNT={len(runtime)}")
    print(f"TOTAL_BYTES={sum(len(data) for data in runtime.values())}")
    print(f"TREE_SHA256={tree_sha256}")
    print(f"ZIP_SIZE={len(first_bytes)}")
    print(f"ZIP_SHA256={sha256_bytes(first_bytes)}")
    print(f"PROVENANCE_SHA256={sha256_bytes(provenance)}")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact", required=True, type=Path)
    parser.add_argument("--apk-entry", default=DEFAULT_APK_ENTRY)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--provenance-output", required=True, type=Path)
    parser.add_argument("--verify-only", action="store_true")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    try:
        run(parse_args(sys.argv[1:] if argv is None else argv))
        return 0
    except (VerificationError, OSError, zipfile.BadZipFile, KeyError, ValueError) as exc:
        print(f"ERROR={exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
