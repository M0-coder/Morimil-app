#!/usr/bin/env python3
"""QA-6 cross-check of lock state, verification metadata, runtime scope and licenses."""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any, Mapping, Sequence

SCHEMA = "morimil.qa6.supply_chain_scope.v1"
VERIFY_NS = {"v": "https://schema.gradle.org/dependency-verification"}
SEVERITY_ORDER = {"UNKNOWN": 0, "LOW": 1, "MEDIUM": 2, "HIGH": 3, "CRITICAL": 4}


class ScopeError(RuntimeError):
    pass


def load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ScopeError(f"Unable to read JSON {path}: {exc}") from exc


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_markdown(path: Path, payload: Mapping[str, Any]) -> None:
    lines = [
        "# QA-6 scope cross-check",
        "",
        "```text",
        f"LOCKED_COMPONENTS={payload['lockedComponents']}",
        f"INVENTORIED_COMPONENTS={payload['inventoriedComponents']}",
        f"VERIFIED_ARTIFACTS={payload['verifiedArtifacts']}",
        f"INVENTORIED_ARTIFACTS={payload['inventoriedArtifacts']}",
        f"RUNTIME_VULNERABILITIES={payload['runtimeVulnerabilityCount']}",
        f"BUILD_TEST_VULNERABILITIES={payload['buildTestVulnerabilityCount']}",
        f"UNADJUDICATED_RUNTIME_HIGH_OR_CRITICAL={payload['unadjudicatedRuntimeHighOrCritical']}",
        f"LICENSE_EVIDENCE_OVERRIDES={payload['licenseEvidenceOverrideCount']}",
        f"UNEXPLAINED_LICENSE_NOASSERTION={payload['unexplainedLicenseNoAssertionCount']}",
        f"FAILURES={len(payload['failures'])}",
        "```",
        "",
    ]
    if payload["failures"]:
        lines.extend(["## Failures", ""])
        lines.extend(f"- {item}" for item in payload["failures"])
        lines.append("")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")


def parse_lockfile(path: Path) -> tuple[set[str], set[str]]:
    coordinates: set[str] = set()
    configurations: set[str] = set()
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or line.startswith("empty="):
            continue
        if "=" not in line:
            raise ScopeError(f"Malformed lockfile line: {line}")
        coordinate, config_list = line.split("=", 1)
        coordinates.add(coordinate)
        configurations.update(item for item in config_list.split(",") if item)
    if not coordinates:
        raise ScopeError("Lockfile contains no component coordinates.")
    return coordinates, configurations


def parse_verification_metadata(path: Path) -> set[tuple[str, str, str]]:
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as exc:
        raise ScopeError(f"Unable to read verification metadata: {exc}") from exc
    configuration = root.find("v:configuration", VERIFY_NS)
    if configuration is None:
        raise ScopeError("Verification metadata has no configuration block.")
    verify_metadata = configuration.findtext("v:verify-metadata", namespaces=VERIFY_NS)
    if verify_metadata != "true":
        raise ScopeError("Gradle metadata verification is not enabled.")
    records: set[tuple[str, str, str]] = set()
    for component in root.findall(".//v:component", VERIFY_NS):
        coordinate = ":".join(
            component.attrib[key] for key in ("group", "name", "version")
        )
        for artifact in component.findall("v:artifact", VERIFY_NS):
            checksums = artifact.findall("v:sha256", VERIFY_NS)
            if len(checksums) != 1:
                raise ScopeError(
                    f"Artifact requires exactly one SHA-256: {coordinate}:{artifact.attrib.get('name')}"
                )
            value = checksums[0].attrib.get("value", "")
            if len(value) != 64:
                raise ScopeError(f"Invalid SHA-256 for {coordinate}:{artifact.attrib.get('name')}")
            records.add((coordinate, artifact.attrib["name"], value))
    if not records:
        raise ScopeError("Verification metadata contains no artifact checksums.")
    return records


def component_configurations(inventory: Mapping[str, Any]) -> dict[str, set[str]]:
    result: dict[str, set[str]] = {}
    for configuration in inventory.get("configurations", []):
        name = configuration.get("name")
        if not isinstance(name, str):
            raise ScopeError("Inventory configuration is missing a name.")
        for component in configuration.get("components", []):
            coordinate = component.get("coordinate")
            if isinstance(coordinate, str):
                result.setdefault(coordinate, set()).add(name)
    return result


def validate(
    inventory: Mapping[str, Any],
    lock_coordinates: set[str],
    lock_configurations: set[str],
    verification_records: set[tuple[str, str, str]],
    vulnerabilities: Mapping[str, Any],
    licenses: Mapping[str, Any],
    syft: Mapping[str, Any],
    apk: Mapping[str, Any],
    policy: Mapping[str, Any],
    adjudications: Mapping[str, Any],
) -> dict[str, Any]:
    failures: list[str] = []
    inventory_coordinates = {
        item["coordinate"] for item in inventory.get("uniqueComponents", [])
    }
    inventory_artifacts = {
        (item["coordinate"], item["fileName"], item["sha256"])
        for item in inventory.get("uniqueArtifacts", [])
    }
    missing_locks = sorted(inventory_coordinates - lock_coordinates)
    extra_locks = sorted(lock_coordinates - inventory_coordinates)
    if missing_locks:
        failures.append(f"Inventory components missing from lockfile: {missing_locks}")
    if extra_locks:
        failures.append(f"Lockfile components missing from inventory: {extra_locks}")

    missing_checksums = sorted(inventory_artifacts - verification_records)
    if missing_checksums:
        failures.append(f"Inventory artifacts missing verification checksums: {missing_checksums[:20]}")

    required_runtime_configs = set(policy.get("runtime_configurations", []))
    missing_runtime_configs = sorted(required_runtime_configs - lock_configurations)
    if missing_runtime_configs:
        failures.append(f"Runtime configurations missing from lock state: {missing_runtime_configs}")

    configs_by_coordinate = component_configurations(inventory)
    vulnerability_by_id = {
        item["id"]: item for item in vulnerabilities.get("vulnerabilities", [])
    }
    accepted_vulnerability_ids = set(adjudications.get("acceptedVulnerabilityIds", []))
    blocked_levels = set(
        policy.get("severity_policy", {}).get(
            "strict_runtime_block_levels", ["HIGH", "CRITICAL"]
        )
    )
    scope_records: list[dict[str, Any]] = []
    unadjudicated_runtime: set[str] = set()
    runtime_vulnerability_ids: set[str] = set()
    build_test_vulnerability_ids: set[str] = set()

    for match in vulnerabilities.get("componentMatches", []):
        ids = match.get("vulnerabilityIds", [])
        if not ids:
            continue
        coordinate = match["coordinate"]
        configurations = configs_by_coordinate.get(coordinate, set())
        runtime_configs = sorted(configurations & required_runtime_configs)
        scope = "APK_RUNTIME_CANDIDATE" if runtime_configs else "BUILD_TEST_ONLY"
        if runtime_configs:
            runtime_vulnerability_ids.update(ids)
        else:
            build_test_vulnerability_ids.update(ids)
        highest = max(
            (vulnerability_by_id[item]["severity"] for item in ids),
            key=lambda item: SEVERITY_ORDER.get(item, 0),
        )
        for vuln_id in ids:
            severity = vulnerability_by_id[vuln_id]["severity"]
            if (
                runtime_configs
                and severity in blocked_levels
                and vuln_id not in accepted_vulnerability_ids
            ):
                unadjudicated_runtime.add(vuln_id)
        scope_records.append(
            {
                "coordinate": coordinate,
                "scope": scope,
                "configurations": sorted(configurations),
                "runtimeConfigurations": runtime_configs,
                "highestSeverity": highest,
                "vulnerabilityIds": sorted(ids),
            }
        )
    if unadjudicated_runtime:
        failures.append(
            "Unadjudicated runtime HIGH/CRITICAL vulnerabilities: "
            + repr(sorted(unadjudicated_runtime))
        )

    noassertion = {
        item["coordinate"]
        for item in licenses.get("components", [])
        if item.get("status") == "NOASSERTION"
    }
    evidence_records = adjudications.get("licenseEvidenceOverrides", [])
    evidence_by_coordinate: dict[str, Mapping[str, Any]] = {}
    for record in evidence_records:
        coordinate = record.get("coordinate")
        expression = record.get("licenseExpression")
        source = record.get("source")
        if not all(isinstance(item, str) and item for item in (coordinate, expression, source)):
            failures.append(f"Invalid license evidence record: {record!r}")
            continue
        if not source.startswith("https://"):
            failures.append(f"License evidence source is not HTTPS: {coordinate}")
        evidence_by_coordinate[coordinate] = record
    unexplained_noassertion = sorted(noassertion - evidence_by_coordinate.keys())
    if unexplained_noassertion:
        failures.append(f"Unexplained license NOASSERTION: {unexplained_noassertion}")

    apk_sha = apk.get("apk", {}).get("sha256")
    syft_packages = syft.get("packages", [])
    syft_digest_matches = any(
        package.get("name") == apk.get("apk", {}).get("fileName")
        and package.get("versionInfo") == f"sha256:{apk_sha}"
        for package in syft_packages
    )
    if not syft_digest_matches:
        failures.append("Syft APK identity does not match the independently inventoried APK SHA-256.")

    return {
        "schema": SCHEMA,
        "lockedComponents": len(lock_coordinates),
        "lockedConfigurations": len(lock_configurations),
        "inventoriedComponents": len(inventory_coordinates),
        "inventoriedArtifacts": len(inventory_artifacts),
        "verifiedArtifacts": len(inventory_artifacts & verification_records),
        "verificationMetadataArtifactCount": len(verification_records),
        "runtimeVulnerabilityCount": len(runtime_vulnerability_ids),
        "buildTestVulnerabilityCount": len(build_test_vulnerability_ids),
        "unadjudicatedRuntimeHighOrCritical": len(unadjudicated_runtime),
        "licenseEvidenceOverrideCount": len(evidence_by_coordinate),
        "unexplainedLicenseNoAssertionCount": len(unexplained_noassertion),
        "syftApkDigestMatch": syft_digest_matches,
        "vulnerabilityScopes": sorted(scope_records, key=lambda item: item["coordinate"]),
        "failures": failures,
    }


def run(args: argparse.Namespace) -> int:
    inventory = load_json(Path(args.inventory))
    vulnerabilities = load_json(Path(args.vulnerabilities))
    licenses = load_json(Path(args.licenses))
    syft = load_json(Path(args.syft))
    apk = load_json(Path(args.apk_inventory))
    policy = load_json(Path(args.policy))
    adjudications = load_json(Path(args.adjudications))
    lock_coordinates, lock_configurations = parse_lockfile(Path(args.lockfile))
    verification_records = parse_verification_metadata(Path(args.verification_metadata))
    result = validate(
        inventory,
        lock_coordinates,
        lock_configurations,
        verification_records,
        vulnerabilities,
        licenses,
        syft,
        apk,
        policy,
        adjudications,
    )
    output = Path(args.output)
    write_json(output, result)
    write_markdown(output.with_suffix(".md"), result)
    if result["failures"]:
        raise ScopeError("; ".join(result["failures"]))
    return 0


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser()
    result.add_argument("--inventory", required=True)
    result.add_argument("--lockfile", required=True)
    result.add_argument("--verification-metadata", required=True)
    result.add_argument("--vulnerabilities", required=True)
    result.add_argument("--licenses", required=True)
    result.add_argument("--syft", required=True)
    result.add_argument("--apk-inventory", required=True)
    result.add_argument("--policy", required=True)
    result.add_argument("--adjudications", required=True)
    result.add_argument("--output", required=True)
    return result


def main(argv: Sequence[str] | None = None) -> int:
    args = parser().parse_args(argv)
    try:
        return run(args)
    except ScopeError as exc:
        print(f"QA6_SCOPE_ERROR={exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
