#!/usr/bin/env python3
"""QA-6 supply-chain inventory, SBOM, vulnerability, license, and APK validator."""

from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import math
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile
from pathlib import Path
from typing import Any, Mapping, Sequence

SCHEMA_SUMMARY = "morimil.qa6.supply_chain_summary.v1"
SCHEMA_APK = "morimil.qa6.apk_inventory.v1"
SCHEMA_LICENSES = "morimil.qa6.license_inventory.v1"
SCHEMA_VULNS = "morimil.qa6.vulnerability_inventory.v1"


class Qa6Error(RuntimeError):
    """Raised when QA-6 evidence is incomplete or inconsistent."""


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise Qa6Error(f"Unable to read JSON {path}: {exc}") from exc


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(payload, indent=2, ensure_ascii=False, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def write_text(path: Path, value: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(value, encoding="utf-8")


def maven_purl(group: str, name: str, version: str) -> str:
    group_path = "/".join(
        urllib.parse.quote(segment, safe="") for segment in group.split(".")
    )
    return (
        f"pkg:maven/{group_path}/{urllib.parse.quote(name, safe='')}"
        f"@{urllib.parse.quote(version, safe='')}"
    )


def spdx_id(value: str) -> str:
    normalized = re.sub(r"[^A-Za-z0-9.-]+", "-", value).strip("-")
    digest = hashlib.sha256(value.encode("utf-8")).hexdigest()[:12]
    return f"SPDXRef-{normalized[:60]}-{digest}"


def normalize_components(inventory: Mapping[str, Any]) -> list[dict[str, Any]]:
    if inventory.get("schema") != "morimil.qa6.gradle-resolved-inventory.v1":
        raise Qa6Error("Unexpected Gradle inventory schema.")
    raw_components = inventory.get("uniqueComponents")
    if not isinstance(raw_components, list) or not raw_components:
        raise Qa6Error("Gradle inventory has no uniqueComponents.")

    artifacts_by_coordinate: dict[str, list[dict[str, Any]]] = {}
    raw_artifacts = inventory.get("uniqueArtifacts")
    if not isinstance(raw_artifacts, list) or not raw_artifacts:
        raise Qa6Error("Gradle inventory has no uniqueArtifacts.")
    for artifact in raw_artifacts:
        coordinate = artifact.get("coordinate")
        if not isinstance(coordinate, str):
            raise Qa6Error("Resolved artifact is missing coordinate.")
        artifacts_by_coordinate.setdefault(coordinate, []).append(dict(artifact))

    components: list[dict[str, Any]] = []
    seen: set[str] = set()
    for raw in raw_components:
        group = raw.get("group")
        name = raw.get("name")
        version = raw.get("version")
        if not all(isinstance(item, str) and item for item in (group, name, version)):
            raise Qa6Error(f"Invalid component record: {raw!r}")
        coordinate = f"{group}:{name}:{version}"
        if coordinate in seen:
            raise Qa6Error(f"Duplicate component coordinate: {coordinate}")
        seen.add(coordinate)
        components.append(
            {
                "group": group,
                "name": name,
                "version": version,
                "coordinate": coordinate,
                "packageName": f"{group}:{name}",
                "purl": maven_purl(group, name, version),
                "artifacts": sorted(
                    artifacts_by_coordinate.get(coordinate, []),
                    key=lambda item: (
                        item.get("classifier", ""),
                        item.get("extension", ""),
                        item.get("fileName", ""),
                        item.get("sha256", ""),
                    ),
                ),
            }
        )
    return sorted(components, key=lambda item: item["coordinate"])


def build_apk_inventory(apk_path: Path) -> dict[str, Any]:
    if not apk_path.is_file() or apk_path.stat().st_size <= 0:
        raise Qa6Error(f"APK is missing or empty: {apk_path}")
    entries: list[dict[str, Any]] = []
    names: set[str] = set()
    with zipfile.ZipFile(apk_path) as archive:
        for info in sorted(archive.infolist(), key=lambda item: item.filename):
            if info.is_dir():
                continue
            if info.filename in names:
                raise Qa6Error(f"Duplicate APK entry: {info.filename}")
            names.add(info.filename)
            data = archive.read(info)
            entries.append(
                {
                    "path": info.filename,
                    "size": info.file_size,
                    "compressedSize": info.compress_size,
                    "crc32": f"{info.CRC:08x}",
                    "sha256": sha256_bytes(data),
                }
            )
    dex = [item for item in entries if re.fullmatch(r"classes\d*\.dex", item["path"])]
    native = [
        item
        for item in entries
        if item["path"].startswith("lib/") and item["path"].endswith(".so")
    ]
    assets = [item for item in entries if item["path"].startswith("assets/")]
    return {
        "schema": SCHEMA_APK,
        "apk": {
            "fileName": apk_path.name,
            "bytes": apk_path.stat().st_size,
            "sha256": sha256_file(apk_path),
        },
        "entryCount": len(entries),
        "dexFiles": dex,
        "nativeLibraries": native,
        "assetCount": len(assets),
        "entries": entries,
    }


def request_json(
    url: str,
    *,
    payload: Mapping[str, Any] | None = None,
    timeout: int = 30,
    attempts: int = 3,
) -> Any:
    body = None
    headers = {
        "Accept": "application/json",
        "User-Agent": "Morimil-QA6-Supply-Chain/1",
    }
    if payload is not None:
        body = json.dumps(payload, sort_keys=True).encode("utf-8")
        headers["Content-Type"] = "application/json"
    last_error: Exception | None = None
    for attempt in range(attempts):
        request = urllib.request.Request(
            url,
            data=body,
            headers=headers,
            method="POST" if body else "GET",
        )
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                return json.loads(response.read().decode("utf-8"))
        except (
            urllib.error.URLError,
            urllib.error.HTTPError,
            TimeoutError,
            json.JSONDecodeError,
        ) as exc:
            last_error = exc
            if attempt + 1 < attempts:
                time.sleep(1.5 * (attempt + 1))
    raise Qa6Error(f"Request failed after {attempts} attempts: {url}: {last_error}")


def query_osv(
    components: Sequence[Mapping[str, Any]],
    policy: Mapping[str, Any],
) -> dict[str, Any]:
    endpoint = policy["network_endpoints"]["osv_query_batch"]
    queries = [
        {
            "package": {
                "ecosystem": "Maven",
                "name": component["packageName"],
            },
            "version": component["version"],
        }
        for component in components
    ]
    response = request_json(endpoint, payload={"queries": queries})
    results = response.get("results")
    if not isinstance(results, list) or len(results) != len(queries):
        raise Qa6Error("OSV querybatch response does not align with component inventory.")

    vulnerability_ids: set[str] = set()
    component_matches: list[dict[str, Any]] = []
    for component, result in zip(components, results, strict=True):
        raw_vulns = result.get("vulns", []) if isinstance(result, dict) else []
        ids = sorted(
            {
                item["id"]
                for item in raw_vulns
                if isinstance(item, dict) and isinstance(item.get("id"), str)
            }
        )
        vulnerability_ids.update(ids)
        component_matches.append(
            {
                "coordinate": component["coordinate"],
                "purl": component["purl"],
                "vulnerabilityIds": ids,
            }
        )

    detail_template = policy["network_endpoints"]["osv_vulnerability"]
    details: dict[str, Any] = {}
    for vuln_id in sorted(vulnerability_ids):
        quoted = urllib.parse.quote(vuln_id, safe="")
        details[vuln_id] = request_json(detail_template.format(id=quoted))

    classified: list[dict[str, Any]] = []
    for vuln_id in sorted(details):
        detail = details[vuln_id]
        severity, score, source = classify_vulnerability(detail)
        classified.append(
            {
                "id": vuln_id,
                "severity": severity,
                "score": score,
                "classificationSource": source,
                "aliases": sorted(detail.get("aliases", [])),
                "summary": detail.get("summary", ""),
                "modified": detail.get("modified"),
                "published": detail.get("published"),
                "withdrawn": detail.get("withdrawn"),
            }
        )

    return {
        "schema": SCHEMA_VULNS,
        "queryEndpoint": endpoint,
        "componentCount": len(components),
        "componentsWithVulnerabilities": sum(
            1 for item in component_matches if item["vulnerabilityIds"]
        ),
        "uniqueVulnerabilityCount": len(classified),
        "criticalCount": sum(
            1 for item in classified if item["severity"] == "CRITICAL"
        ),
        "unknownSeverityCount": sum(
            1 for item in classified if item["severity"] == "UNKNOWN"
        ),
        "componentMatches": component_matches,
        "vulnerabilities": classified,
        "rawBatchResponse": response,
        "rawVulnerabilityDetails": details,
    }


def _severity_label(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    normalized = value.strip().upper()
    aliases = {
        "MODERATE": "MEDIUM",
        "IMPORTANT": "HIGH",
        "NEGLIGIBLE": "LOW",
    }
    normalized = aliases.get(normalized, normalized)
    if normalized in {"CRITICAL", "HIGH", "MEDIUM", "LOW"}:
        return normalized
    return None


def classify_vulnerability(detail: Mapping[str, Any]) -> tuple[str, float | None, str]:
    for container_name in ("database_specific", "ecosystem_specific"):
        container = detail.get(container_name)
        if isinstance(container, Mapping):
            label = _severity_label(container.get("severity"))
            if label:
                return label, None, f"{container_name}.severity"

    best_score: float | None = None
    best_source = ""
    severity_items = detail.get("severity", [])
    if not isinstance(severity_items, list):
        severity_items = []
    for item in severity_items:
        if not isinstance(item, Mapping):
            continue
        raw_score = item.get("score")
        if isinstance(raw_score, (int, float)):
            score = float(raw_score)
        elif isinstance(raw_score, str) and raw_score.startswith(
            ("CVSS:3.0/", "CVSS:3.1/")
        ):
            score = cvss3_base_score(raw_score)
        elif isinstance(raw_score, str):
            try:
                score = float(raw_score)
            except ValueError:
                continue
        else:
            continue
        if best_score is None or score > best_score:
            best_score = score
            best_source = f"severity[{item.get('type', 'unknown')}]"

    if best_score is None:
        return "UNKNOWN", None, "unclassified"
    if best_score >= 9.0:
        label = "CRITICAL"
    elif best_score >= 7.0:
        label = "HIGH"
    elif best_score >= 4.0:
        label = "MEDIUM"
    elif best_score > 0:
        label = "LOW"
    else:
        label = "UNKNOWN"
    return label, round(best_score, 1), best_source


def cvss3_base_score(vector: str) -> float:
    parts = vector.split("/")
    if not parts or parts[0] not in {"CVSS:3.0", "CVSS:3.1"}:
        raise Qa6Error(f"Unsupported CVSS vector: {vector}")
    metrics: dict[str, str] = {}
    for part in parts[1:]:
        if ":" not in part:
            raise Qa6Error(f"Malformed CVSS metric: {part}")
        key, value = part.split(":", 1)
        metrics[key] = value

    required = {"AV", "AC", "PR", "UI", "S", "C", "I", "A"}
    if not required.issubset(metrics):
        raise Qa6Error(f"Incomplete CVSS vector: {vector}")

    av = {"N": 0.85, "A": 0.62, "L": 0.55, "P": 0.2}[metrics["AV"]]
    ac = {"L": 0.77, "H": 0.44}[metrics["AC"]]
    scope_changed = metrics["S"] == "C"
    pr_table = (
        {"N": 0.85, "L": 0.68, "H": 0.5}
        if scope_changed
        else {"N": 0.85, "L": 0.62, "H": 0.27}
    )
    pr = pr_table[metrics["PR"]]
    ui = {"N": 0.85, "R": 0.62}[metrics["UI"]]
    impact_values = {"N": 0.0, "L": 0.22, "H": 0.56}
    confidentiality = impact_values[metrics["C"]]
    integrity = impact_values[metrics["I"]]
    availability = impact_values[metrics["A"]]

    iss = 1 - ((1 - confidentiality) * (1 - integrity) * (1 - availability))
    if scope_changed:
        impact = 7.52 * (iss - 0.029) - 3.25 * ((iss - 0.02) ** 15)
    else:
        impact = 6.42 * iss
    exploitability = 8.22 * av * ac * pr * ui
    if impact <= 0:
        return 0.0
    base = (
        min(1.08 * (impact + exploitability), 10)
        if scope_changed
        else min(impact + exploitability, 10)
    )
    return math.ceil(base * 10 - 1e-9) / 10


def query_licenses(
    components: Sequence[Mapping[str, Any]],
    policy: Mapping[str, Any],
    workers: int = 8,
) -> dict[str, Any]:
    template = policy["network_endpoints"]["deps_dev_version"]

    def load(component: Mapping[str, Any]) -> dict[str, Any]:
        name = urllib.parse.quote(component["packageName"], safe="")
        version = urllib.parse.quote(component["version"], safe="")
        url = template.format(name=name, version=version)
        try:
            payload = request_json(url)
            licenses = sorted(
                {
                    item
                    for item in payload.get("licenses", [])
                    if isinstance(item, str) and item.strip()
                }
            )
            status = "FOUND" if licenses else "NOASSERTION"
            advisories = sorted(
                {
                    item.get("id")
                    for item in payload.get("advisoryKeys", [])
                    if isinstance(item, Mapping)
                    and isinstance(item.get("id"), str)
                }
            )
            return {
                "coordinate": component["coordinate"],
                "purl": component["purl"],
                "licenses": licenses,
                "status": status,
                "advisoryIds": advisories,
                "source": url,
            }
        except Qa6Error as exc:
            return {
                "coordinate": component["coordinate"],
                "purl": component["purl"],
                "licenses": [],
                "status": "QUERY_ERROR",
                "advisoryIds": [],
                "source": url,
                "error": str(exc),
            }

    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
        records = list(executor.map(load, components))
    records.sort(key=lambda item: item["coordinate"])
    return {
        "schema": SCHEMA_LICENSES,
        "componentCount": len(records),
        "knownLicenseCount": sum(
            1 for item in records if item["status"] == "FOUND"
        ),
        "noAssertionCount": sum(
            1 for item in records if item["status"] == "NOASSERTION"
        ),
        "queryErrorCount": sum(
            1 for item in records if item["status"] == "QUERY_ERROR"
        ),
        "components": records,
    }


def build_spdx(
    components: Sequence[Mapping[str, Any]],
    licenses: Mapping[str, Any],
    inventory_sha256: str,
    apk_sha256: str,
) -> dict[str, Any]:
    license_by_coordinate = {
        item["coordinate"]: item
        for item in licenses.get("components", [])
        if isinstance(item, Mapping)
    }
    packages: list[dict[str, Any]] = []
    relationships: list[dict[str, str]] = []
    document_id = "SPDXRef-DOCUMENT"
    root_id = "SPDXRef-Morimil-App-Debug"
    packages.append(
        {
            "SPDXID": root_id,
            "name": "Morimil-app-debug",
            "versionInfo": "qa6-baseline",
            "downloadLocation": "NOASSERTION",
            "filesAnalyzed": False,
            "licenseConcluded": "NOASSERTION",
            "licenseDeclared": "NOASSERTION",
            "externalRefs": [
                {
                    "referenceCategory": "OTHER",
                    "referenceType": "morimil:apk-sha256",
                    "referenceLocator": apk_sha256,
                },
                {
                    "referenceCategory": "OTHER",
                    "referenceType": "morimil:gradle-inventory-sha256",
                    "referenceLocator": inventory_sha256,
                },
            ],
        }
    )
    relationships.append(
        {
            "spdxElementId": document_id,
            "relationshipType": "DESCRIBES",
            "relatedSpdxElement": root_id,
        }
    )
    for component in components:
        record = license_by_coordinate.get(component["coordinate"], {})
        detected = record.get("licenses") or []
        license_expr = " AND ".join(detected) if detected else "NOASSERTION"
        package_id = spdx_id(component["coordinate"])
        checksums = []
        for artifact in component.get("artifacts", []):
            checksum = artifact.get("sha256")
            if isinstance(checksum, str) and checksum:
                checksums.append(
                    {
                        "algorithm": "SHA256",
                        "checksumValue": checksum,
                    }
                )
        package = {
            "SPDXID": package_id,
            "name": component["packageName"],
            "versionInfo": component["version"],
            "downloadLocation": "NOASSERTION",
            "filesAnalyzed": False,
            "licenseConcluded": license_expr,
            "licenseDeclared": license_expr,
            "externalRefs": [
                {
                    "referenceCategory": "PACKAGE-MANAGER",
                    "referenceType": "purl",
                    "referenceLocator": component["purl"],
                }
            ],
        }
        if checksums:
            package["checksums"] = sorted(
                checksums, key=lambda item: item["checksumValue"]
            )
        packages.append(package)
        relationships.append(
            {
                "spdxElementId": root_id,
                "relationshipType": "DEPENDS_ON",
                "relatedSpdxElement": package_id,
            }
        )
    namespace_seed = f"{inventory_sha256}:{apk_sha256}"
    return {
        "spdxVersion": "SPDX-2.3",
        "dataLicense": "CC0-1.0",
        "SPDXID": document_id,
        "name": "Morimil QA-6 resolved dependency SBOM",
        "documentNamespace": (
            "https://morimil.invalid/spdx/qa6/"
            + str(uuid.uuid5(uuid.NAMESPACE_URL, namespace_seed))
        ),
        "creationInfo": {
            "created": "2026-08-06T00:00:00Z",
            "creators": ["Tool: Morimil QA-6 supply-chain reporter v1"],
        },
        "packages": sorted(packages, key=lambda item: item["SPDXID"]),
        "relationships": sorted(
            relationships,
            key=lambda item: (
                item["spdxElementId"],
                item["relationshipType"],
                item["relatedSpdxElement"],
            ),
        ),
    }


def validate_structure(
    components: Sequence[Mapping[str, Any]],
    apk_inventory: Mapping[str, Any],
    policy: Mapping[str, Any],
    licenses: Mapping[str, Any],
    vulnerabilities: Mapping[str, Any],
    *,
    mode: str,
    adjudications: Mapping[str, Any],
) -> list[str]:
    failures: list[str] = []
    available_modules = {component["packageName"] for component in components}
    for field in ("expected_runtime_modules", "expected_test_modules"):
        expected = set(policy.get(field, []))
        missing = sorted(expected - available_modules)
        if missing:
            failures.append(f"{field} missing: {missing}")

    entry_paths = {
        item["path"]
        for item in apk_inventory.get("entries", [])
        if isinstance(item, Mapping)
    }
    for required in policy.get("required_apk_entries", []):
        if required not in entry_paths:
            failures.append(f"APK required entry missing: {required}")
    for prefix in policy.get("required_apk_prefixes", []):
        if not any(path.startswith(prefix) for path in entry_paths):
            failures.append(f"APK required prefix missing: {prefix}")
    if not apk_inventory.get("dexFiles"):
        failures.append("APK contains no classes*.dex files.")

    if licenses.get("queryErrorCount", 0) > 0:
        failures.append("License inventory contains query errors.")

    adjudicated_ids = {
        item
        for item in adjudications.get("acceptedVulnerabilityIds", [])
        if isinstance(item, str)
    }
    if mode == "strict":
        unadjudicated_critical = sorted(
            item["id"]
            for item in vulnerabilities.get("vulnerabilities", [])
            if item.get("severity") == "CRITICAL"
            and item.get("id") not in adjudicated_ids
        )
        if unadjudicated_critical:
            failures.append(
                f"Unadjudicated critical vulnerabilities: {unadjudicated_critical}"
            )
    return failures


def markdown_report(summary: Mapping[str, Any]) -> str:
    failures = summary.get("failures", [])
    lines = [
        "# QA-6 supply-chain report",
        "",
        "```text",
        f"MODE={summary['mode']}",
        f"GRADLE_COMPONENTS={summary['gradleComponents']}",
        f"GRADLE_ARTIFACTS={summary['gradleArtifacts']}",
        f"APK_ENTRIES={summary['apkEntries']}",
        f"APK_NATIVE_LIBRARIES={summary['apkNativeLibraries']}",
        f"KNOWN_LICENSES={summary['knownLicenseCount']}",
        f"LICENSE_NOASSERTION={summary['licenseNoAssertionCount']}",
        f"LICENSE_QUERY_ERRORS={summary['licenseQueryErrorCount']}",
        f"VULNERABILITIES={summary['vulnerabilityCount']}",
        f"CRITICAL_VULNERABILITIES={summary['criticalVulnerabilityCount']}",
        f"STRUCTURAL_FAILURES={len(failures)}",
        "```",
        "",
    ]
    if failures:
        lines += ["## Failures", ""] + [f"- {item}" for item in failures] + [""]
    else:
        lines += ["All configured QA-6 structural gates passed.", ""]
    return "\n".join(lines)


def run_collect(args: argparse.Namespace) -> int:
    inventory_path = Path(args.gradle_inventory)
    apk_path = Path(args.apk)
    policy_path = Path(args.policy)
    adjudications_path = Path(args.adjudications)
    output_dir = Path(args.output_dir)

    inventory = load_json(inventory_path)
    policy = load_json(policy_path)
    adjudications = load_json(adjudications_path)
    components = normalize_components(inventory)
    apk_inventory = build_apk_inventory(apk_path)
    licenses = query_licenses(components, policy, workers=args.workers)
    vulnerabilities = query_osv(components, policy)

    inventory_sha = sha256_file(inventory_path)
    spdx = build_spdx(
        components,
        licenses,
        inventory_sha256=inventory_sha,
        apk_sha256=apk_inventory["apk"]["sha256"],
    )
    failures = validate_structure(
        components,
        apk_inventory,
        policy,
        licenses,
        vulnerabilities,
        mode=args.mode,
        adjudications=adjudications,
    )

    output_dir.mkdir(parents=True, exist_ok=True)
    write_json(output_dir / "apk-inventory.json", apk_inventory)
    write_json(output_dir / "licenses.json", licenses)
    write_json(output_dir / "vulnerabilities.json", vulnerabilities)
    write_json(output_dir / "resolved-components.spdx.json", spdx)

    summary = {
        "schema": SCHEMA_SUMMARY,
        "mode": args.mode,
        "gradleInventorySha256": inventory_sha,
        "gradleComponents": len(components),
        "gradleArtifacts": sum(
            len(item.get("artifacts", [])) for item in components
        ),
        "apkSha256": apk_inventory["apk"]["sha256"],
        "apkEntries": apk_inventory["entryCount"],
        "apkNativeLibraries": len(apk_inventory["nativeLibraries"]),
        "knownLicenseCount": licenses["knownLicenseCount"],
        "licenseNoAssertionCount": licenses["noAssertionCount"],
        "licenseQueryErrorCount": licenses["queryErrorCount"],
        "vulnerabilityCount": vulnerabilities["uniqueVulnerabilityCount"],
        "criticalVulnerabilityCount": vulnerabilities["criticalCount"],
        "unknownSeverityCount": vulnerabilities["unknownSeverityCount"],
        "failures": failures,
    }
    write_json(output_dir / "summary.json", summary)
    write_text(output_dir / "summary.md", markdown_report(summary))
    if failures:
        raise Qa6Error("; ".join(failures))
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)
    collect = subparsers.add_parser("collect")
    collect.add_argument("--gradle-inventory", required=True)
    collect.add_argument("--apk", required=True)
    collect.add_argument("--policy", required=True)
    collect.add_argument("--adjudications", required=True)
    collect.add_argument("--output-dir", required=True)
    collect.add_argument("--mode", choices=("baseline", "strict"), default="baseline")
    collect.add_argument("--workers", type=int, default=8)
    collect.set_defaults(func=run_collect)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        return int(args.func(args))
    except Qa6Error as exc:
        print(f"QA6_ERROR={exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
