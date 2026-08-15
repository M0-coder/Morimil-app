#!/usr/bin/env python3
"""Fail-closed QA-7 quality regression ratchets.

This tool compares machine-readable quality evidence against a reviewed baseline.
It allows improvement and equality, but rejects regressions or new warning
fingerprints. It never edits source, reports, Gradle state, or runtime assets.
"""
from __future__ import annotations

import argparse
import collections
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Iterable, Mapping

SCHEMA = "morimil.qa7.quality_ratchet_baseline.v1"
RESULT_SCHEMA = "morimil.qa7.quality_ratchet_result.v1"
COUNTERS = ("LINE", "BRANCH", "INSTRUCTION")
KOTLIN_FILE_WARNING = re.compile(
    r"^w: (?:file://)?(?P<path>.*?):(?P<line>\d+):(?P<column>\d+) (?P<message>.+)$"
)
GRADLE_DEPENDENCY = re.compile(r"^A newer version of (?P<coordinate>.+?) than .+ is available: .+$")


class QualityRatchetError(RuntimeError):
    pass


def load_json(path: Path) -> Mapping[str, object]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise QualityRatchetError(f"Cannot read JSON: {path}") from exc
    if not isinstance(value, dict):
        raise QualityRatchetError(f"Expected JSON object: {path}")
    return value


def normalize_repo_path(raw: str) -> str:
    path = raw.replace("\\", "/").strip()
    if path.startswith("file://"):
        path = path[7:]
    marker = "/Morimil-app/"
    if marker in path:
        path = path.rsplit(marker, 1)[1]
    else:
        for prefix in ("app/", "tools/", ".github/", "gradle/"):
            idx = path.find(prefix)
            if idx >= 0:
                path = path[idx:]
                break
    return path.lstrip("/")


def fraction_at_least(cur_covered: int, cur_total: int, base_covered: int, base_total: int) -> bool:
    if min(cur_covered, cur_total, base_covered, base_total) < 0:
        raise QualityRatchetError("Coverage counters must be non-negative.")
    if cur_total <= 0 or base_total <= 0:
        raise QualityRatchetError("Coverage totals must be positive.")
    if cur_covered > cur_total or base_covered > base_total:
        raise QualityRatchetError("Coverage covered count exceeds total.")
    return cur_covered * base_total >= base_covered * cur_total


def fingerprint_counter(items: Iterable[str]) -> collections.Counter[str]:
    return collections.Counter(items)


def baseline_counter(items: object, field: str) -> collections.Counter[str]:
    if not isinstance(items, list):
        raise QualityRatchetError(f"Baseline {field} must be an array.")
    result: collections.Counter[str] = collections.Counter()
    for item in items:
        if not isinstance(item, dict):
            raise QualityRatchetError(f"Baseline {field} entry must be an object.")
        fingerprint = item.get("fingerprint")
        count = item.get("count")
        if not isinstance(fingerprint, str) or not fingerprint:
            raise QualityRatchetError(f"Baseline {field} fingerprint must be non-empty.")
        if not isinstance(count, int) or count <= 0:
            raise QualityRatchetError(f"Baseline {field} count must be positive.")
        if fingerprint in result:
            raise QualityRatchetError(f"Duplicate baseline fingerprint: {fingerprint}")
        result[fingerprint] = count
    return result


def baseline_string_set(items: object, field: str) -> set[str]:
    if not isinstance(items, list):
        raise QualityRatchetError(f"Baseline {field} must be an array.")
    values: list[str] = []
    for item in items:
        if not isinstance(item, str) or not item.strip():
            raise QualityRatchetError(f"Baseline {field} entries must be non-empty strings.")
        if item != item.strip():
            raise QualityRatchetError(f"Baseline {field} entries must be canonical strings.")
        values.append(item)
    if len(values) != len(set(values)):
        raise QualityRatchetError(f"Baseline {field} contains duplicates.")
    if values != sorted(values):
        raise QualityRatchetError(f"Baseline {field} must be sorted.")
    return set(values)


def new_or_increased(current: collections.Counter[str], allowed: collections.Counter[str]) -> list[dict[str, object]]:
    changes = []
    for fingerprint, count in sorted(current.items()):
        permitted = allowed.get(fingerprint, 0)
        if count > permitted:
            changes.append({"fingerprint": fingerprint, "current": count, "allowed": permitted})
    return changes


def parse_kotlin_warnings(path: Path) -> collections.Counter[str]:
    try:
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError as exc:
        raise QualityRatchetError(f"Cannot read Kotlin warning log: {path}") from exc
    fingerprints: list[str] = []
    for raw in lines:
        line = raw.strip()
        match = KOTLIN_FILE_WARNING.match(line)
        if match:
            repo_path = normalize_repo_path(match.group("path"))
            fingerprints.append(f"file|{repo_path}|{match.group('message').strip()}")
        elif line.startswith("w: "):
            fingerprints.append(f"generic|{line[3:].strip()}")
    return fingerprint_counter(fingerprints)


def lint_fingerprint(issue: ET.Element) -> str:
    issue_id = issue.attrib.get("id", "").strip()
    message = issue.attrib.get("message", "").strip()
    if not issue_id or not message:
        raise QualityRatchetError("Lint issue is missing id or message.")
    location = issue.find("location")
    file_name = normalize_repo_path(location.attrib.get("file", "")) if location is not None else ""
    if issue_id == "GradleDependency":
        match = GRADLE_DEPENDENCY.match(message)
        if not match:
            raise QualityRatchetError(f"Unexpected GradleDependency message: {message}")
        message_key = match.group("coordinate")
    else:
        message_key = message
    return f"{issue_id}|{file_name}|{message_key}"


def parse_lint(path: Path) -> tuple[collections.Counter[str], collections.Counter[str]]:
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as exc:
        raise QualityRatchetError(f"Cannot parse lint XML: {path}") from exc
    if root.tag != "issues":
        raise QualityRatchetError(f"Unexpected lint root: {root.tag}")
    severities: collections.Counter[str] = collections.Counter()
    warnings: collections.Counter[str] = collections.Counter()
    for issue in root.findall("issue"):
        severity = issue.attrib.get("severity", "").strip()
        if not severity:
            raise QualityRatchetError("Lint issue is missing severity.")
        severities[severity] += 1
        if severity == "Warning":
            warnings[lint_fingerprint(issue)] += 1
    return severities, warnings


def require_baseline(baseline: Mapping[str, object]) -> None:
    if baseline.get("schema") != SCHEMA:
        raise QualityRatchetError(f"Unexpected baseline schema: {baseline.get('schema')!r}")


def counter_values(container: Mapping[str, object], name: str) -> tuple[int, int]:
    raw = container.get(name)
    if not isinstance(raw, dict):
        raise QualityRatchetError(f"Missing counter: {name}")
    covered, total = raw.get("covered"), raw.get("total")
    if not isinstance(covered, int) or not isinstance(total, int):
        raise QualityRatchetError(f"Invalid counter values: {name}")
    return covered, total


def evaluate_ratio_group(label: str, current: Mapping[str, object], baseline: Mapping[str, object], names: Iterable[str], failures: list[str]) -> dict[str, object]:
    observed: dict[str, object] = {}
    for name in names:
        cc, ct = counter_values(current, name)
        bc, bt = counter_values(baseline, name)
        ok = fraction_at_least(cc, ct, bc, bt)
        observed[name] = {"covered": cc, "total": ct, "baselineCovered": bc, "baselineTotal": bt, "pass": ok}
        if not ok:
            failures.append(f"{label} {name} coverage regressed: {cc}/{ct} < {bc}/{bt}")
    return observed


def evaluate_jvm(baseline: Mapping[str, object], authored: Mapping[str, object], python_cov: Mapping[str, object], kotlin_log: Path, lint_xml: Path) -> dict[str, object]:
    require_baseline(baseline)
    failures: list[str] = []
    android_base = baseline.get("androidAuthored")
    authored_view = authored.get("authoredSourceView")
    inventory = authored.get("inventory")
    if not isinstance(android_base, dict) or not isinstance(authored_view, dict) or not isinstance(inventory, dict):
        raise QualityRatchetError("Android authored coverage structure is invalid.")
    authored_counters, base_counters = authored_view.get("counters"), android_base.get("counters")
    if not isinstance(authored_counters, dict) or not isinstance(base_counters, dict):
        raise QualityRatchetError("Android authored counters are invalid.")
    android_observed = evaluate_ratio_group("Android authored", authored_counters, base_counters, COUNTERS, failures)
    zero, max_zero = inventory.get("zeroLineCoverageIncludedSources"), android_base.get("maxZeroLineCoverageSources")
    if not isinstance(zero, int) or not isinstance(max_zero, int):
        raise QualityRatchetError("Android zero-coverage inventory is invalid.")
    if zero > max_zero:
        failures.append(f"Android zero-line-coverage sources increased: {zero} > {max_zero}")

    py_base, totals = baseline.get("python"), python_cov.get("totals")
    if not isinstance(py_base, dict) or not isinstance(totals, dict):
        raise QualityRatchetError("Python coverage structure is invalid.")
    py_current = {
        "STATEMENT": {"covered": totals.get("covered_lines"), "total": totals.get("num_statements")},
        "BRANCH": {"covered": totals.get("covered_branches"), "total": totals.get("num_branches")},
    }
    py_baseline = {"STATEMENT": py_base.get("statements"), "BRANCH": py_base.get("branches")}
    python_observed = evaluate_ratio_group("Python", py_current, py_baseline, ("STATEMENT", "BRANCH"), failures)

    kotlin_base = baseline.get("kotlinWarnings")
    if not isinstance(kotlin_base, dict):
        raise QualityRatchetError("Baseline kotlinWarnings missing.")
    kotlin_current = parse_kotlin_warnings(kotlin_log)
    kotlin_allowed = baseline_counter(kotlin_base.get("fingerprints"), "kotlinWarnings.fingerprints")
    kotlin_delta = new_or_increased(kotlin_current, kotlin_allowed)
    max_kotlin = kotlin_base.get("maxTotal")
    if not isinstance(max_kotlin, int):
        raise QualityRatchetError("Baseline kotlinWarnings.maxTotal is invalid.")
    if sum(kotlin_current.values()) > max_kotlin:
        failures.append(f"Kotlin warnings increased: {sum(kotlin_current.values())} > {max_kotlin}")
    if kotlin_delta:
        failures.append(f"New/increased Kotlin warning fingerprints: {len(kotlin_delta)}")

    lint_base = baseline.get("lint")
    if not isinstance(lint_base, dict):
        raise QualityRatchetError("Baseline lint missing.")
    severities, lint_current = parse_lint(lint_xml)
    lint_allowed = baseline_counter(lint_base.get("warningFingerprints"), "lint.warningFingerprints")
    baseline_gradle_coordinates = baseline_string_set(
        lint_base.get("gradleDependencyCoordinates"),
        "lint.gradleDependencyCoordinates",
    )
    for coordinate in baseline_gradle_coordinates:
        fingerprint = f"GradleDependency|app/build.gradle.kts|{coordinate}"
        lint_allowed[fingerprint] = max(lint_allowed.get(fingerprint, 0), 1)
    lint_delta = new_or_increased(lint_current, lint_allowed)
    max_warnings, max_errors = lint_base.get("maxWarnings"), lint_base.get("maxErrors")
    if not isinstance(max_warnings, int) or not isinstance(max_errors, int):
        raise QualityRatchetError("Baseline lint ceilings are invalid.")
    if severities.get("Warning", 0) > max_warnings:
        failures.append(f"Lint warnings increased: {severities.get('Warning', 0)} > {max_warnings}")
    if severities.get("Error", 0) > max_errors:
        failures.append(f"Lint errors exceeded ceiling: {severities.get('Error', 0)} > {max_errors}")
    if lint_delta:
        failures.append(f"New/increased lint warning fingerprints: {len(lint_delta)}")

    return {
        "schema": RESULT_SCHEMA, "mode": "jvm", "pass": not failures, "failures": failures,
        "androidAuthored": {"counters": android_observed, "zeroLineCoverageSources": zero, "maxZeroLineCoverageSources": max_zero},
        "python": python_observed,
        "kotlinWarnings": {"total": sum(kotlin_current.values()), "maxTotal": max_kotlin, "newOrIncreased": kotlin_delta},
        "lint": {
            "severities": dict(sorted(severities.items())),
            "maxWarnings": max_warnings,
            "maxErrors": max_errors,
            "baselineGradleDependencyCoordinateCount": len(baseline_gradle_coordinates),
            "newOrIncreasedWarnings": lint_delta,
        },
    }


def evaluate_instrumented(baseline: Mapping[str, object], instrumented: Mapping[str, object]) -> dict[str, object]:
    require_baseline(baseline)
    failures: list[str] = []
    inst_base, report, inventory = baseline.get("instrumented"), instrumented.get("report"), instrumented.get("source_inventory")
    if not isinstance(inst_base, dict) or not isinstance(report, dict) or not isinstance(inventory, dict):
        raise QualityRatchetError("Instrumented coverage structure is invalid.")
    current_counters, base_counters = report.get("counters"), inst_base.get("counters")
    if not isinstance(current_counters, dict) or not isinstance(base_counters, dict):
        raise QualityRatchetError("Instrumented counters are invalid.")
    observed = evaluate_ratio_group("Android instrumented", current_counters, base_counters, COUNTERS, failures)
    zero, max_zero = inventory.get("zero_line_coverage"), inst_base.get("maxZeroLineCoverageSources")
    if not isinstance(zero, int) or not isinstance(max_zero, int):
        raise QualityRatchetError("Instrumented zero-coverage inventory is invalid.")
    if zero > max_zero:
        failures.append(f"Instrumented zero-line-coverage sources increased: {zero} > {max_zero}")
    return {"schema": RESULT_SCHEMA, "mode": "instrumented", "pass": not failures, "failures": failures, "instrumented": {"counters": observed, "zeroLineCoverageSources": zero, "maxZeroLineCoverageSources": max_zero}}


def write_result(result: Mapping[str, object], output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="mode", required=True)
    jvm = sub.add_parser("jvm")
    jvm.add_argument("--baseline", type=Path, required=True)
    jvm.add_argument("--android-authored", type=Path, required=True)
    jvm.add_argument("--python-coverage", type=Path, required=True)
    jvm.add_argument("--kotlin-log", type=Path, required=True)
    jvm.add_argument("--lint-xml", type=Path, required=True)
    jvm.add_argument("--output", type=Path, required=True)
    inst = sub.add_parser("instrumented")
    inst.add_argument("--baseline", type=Path, required=True)
    inst.add_argument("--instrumented", type=Path, required=True)
    inst.add_argument("--output", type=Path, required=True)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    try:
        baseline = load_json(args.baseline)
        if args.mode == "jvm":
            result = evaluate_jvm(baseline, load_json(args.android_authored), load_json(args.python_coverage), args.kotlin_log, args.lint_xml)
        else:
            result = evaluate_instrumented(baseline, load_json(args.instrumented))
        write_result(result, args.output)
    except QualityRatchetError as exc:
        print(f"QA7 QUALITY RATCHET: FAIL: {exc}", file=sys.stderr)
        return 2
    if not result["pass"]:
        for failure in result["failures"]:
            print(f"QA7 QUALITY RATCHET: FAIL: {failure}", file=sys.stderr)
        return 2
    print(f"QA7_QUALITY_RATCHET_{args.mode.upper()}=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
