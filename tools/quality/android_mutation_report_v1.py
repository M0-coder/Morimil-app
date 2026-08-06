#!/usr/bin/env python3
"""Validate and summarize the bounded QA-4 Android PIT mutation pilot."""

from __future__ import annotations

import argparse
import hashlib
import json
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path
from typing import Any

KNOWN_STATUSES = {
    "KILLED",
    "SURVIVED",
    "NO_COVERAGE",
    "TIMED_OUT",
    "NON_VIABLE",
    "MEMORY_ERROR",
    "RUN_ERROR",
}


class MutationReportError(RuntimeError):
    """Raised when the mutation report violates the QA-4 evidence contract."""


def _required_text(node: ET.Element, tag: str, mutation_index: int) -> str:
    value = node.findtext(tag)
    if value is None or not value.strip():
        raise MutationReportError(
            f"Mutation {mutation_index} is missing required element {tag}."
        )
    return value.strip()


def _percentage(numerator: int, denominator: int) -> float | None:
    if denominator == 0:
        return None
    return numerator * 100.0 / denominator


def analyze_report(
    report_path: Path,
    expected_class_prefix: str,
    expected_source_file: str,
) -> dict[str, Any]:
    if not report_path.is_file() or report_path.stat().st_size == 0:
        raise MutationReportError(f"Mutation report is missing or empty: {report_path}")

    try:
        root = ET.parse(report_path).getroot()
    except ET.ParseError as exc:
        raise MutationReportError(f"Mutation report is not valid XML: {exc}") from exc

    if root.tag != "mutations":
        raise MutationReportError(
            f"Unexpected mutation report root element: {root.tag!r}."
        )

    mutations = root.findall("mutation")
    if not mutations:
        raise MutationReportError("Mutation report contains no mutants.")

    statuses: Counter[str] = Counter()
    mutators: Counter[str] = Counter()
    mutated_classes: set[str] = set()
    source_files: set[str] = set()
    mutated_lines: set[int] = set()
    detected_count = 0
    killing_test_count = 0

    for index, mutation in enumerate(mutations, start=1):
        status = mutation.attrib.get("status", "").strip().upper()
        if status not in KNOWN_STATUSES:
            raise MutationReportError(
                f"Mutation {index} has unknown status {status!r}."
            )

        detected_text = mutation.attrib.get("detected", "").strip().lower()
        if detected_text not in {"true", "false"}:
            raise MutationReportError(
                f"Mutation {index} has invalid detected attribute {detected_text!r}."
            )

        mutated_class = _required_text(mutation, "mutatedClass", index)
        source_file = _required_text(mutation, "sourceFile", index)
        line_text = _required_text(mutation, "lineNumber", index)
        mutator = _required_text(mutation, "mutator", index)

        try:
            line_number = int(line_text)
        except ValueError as exc:
            raise MutationReportError(
                f"Mutation {index} has a non-integer line number {line_text!r}."
            ) from exc
        if line_number <= 0:
            raise MutationReportError(
                f"Mutation {index} has an invalid line number {line_number}."
            )

        if not (
            mutated_class == expected_class_prefix
            or mutated_class.startswith(expected_class_prefix + "$")
        ):
            raise MutationReportError(
                "Mutation report escaped the approved class boundary: "
                f"{mutated_class!r}."
            )
        if source_file != expected_source_file:
            raise MutationReportError(
                "Mutation report escaped the approved source boundary: "
                f"{source_file!r}."
            )

        statuses[status] += 1
        mutators[mutator] += 1
        mutated_classes.add(mutated_class)
        source_files.add(source_file)
        mutated_lines.add(line_number)
        if detected_text == "true":
            detected_count += 1
        if (mutation.findtext("killingTest") or "").strip():
            killing_test_count += 1

    generated = len(mutations)
    killed = statuses["KILLED"]
    survived = statuses["SURVIVED"]
    no_coverage = statuses["NO_COVERAGE"]
    covered_mutants = killed + survived

    return {
        "schema": "morimil.qa4.android_mutation_pilot.v1",
        "mode": "report_only_experimental_kotlin_bytecode_pilot",
        "report": {
            "path": report_path.as_posix(),
            "bytes": report_path.stat().st_size,
            "sha256": hashlib.sha256(report_path.read_bytes()).hexdigest(),
        },
        "scope": {
            "expected_class_prefix": expected_class_prefix,
            "expected_source_file": expected_source_file,
            "observed_classes": sorted(mutated_classes),
            "observed_source_files": sorted(source_files),
            "unique_mutated_lines": len(mutated_lines),
        },
        "totals": {
            "generated": generated,
            "detected": detected_count,
            "undetected": generated - detected_count,
            "killing_test_recorded": killing_test_count,
            "mutation_score_percent": _percentage(detected_count, generated),
            "test_strength_percent": _percentage(killed, covered_mutants),
            "line_coverage_proxy_percent": _percentage(
                generated - no_coverage,
                generated,
            ),
        },
        "statuses": {
            status: statuses[status]
            for status in sorted(KNOWN_STATUSES)
        },
        "mutators": dict(sorted(mutators.items())),
        "interpretation_limits": [
            "The Android PIT Gradle integration is experimental.",
            "Open-source PIT does not provide full semantic Kotlin mutation support.",
            "The result is a bytecode-level pilot and is not a global quality gate.",
            "No mutation, coverage, or test-strength threshold is enforced.",
        ],
    }


def render_markdown(summary: dict[str, Any]) -> str:
    totals = summary["totals"]
    scope = summary["scope"]
    statuses = summary["statuses"]

    def display(value: float | None) -> str:
        return "n/a" if value is None else f"{value:.4f}%"

    lines = [
        "# QA-4 Android mutation pilot",
        "",
        "## Scope",
        "",
        f"- Mode: `{summary['mode']}`",
        f"- Target class prefix: `{scope['expected_class_prefix']}`",
        f"- Target source file: `{scope['expected_source_file']}`",
        f"- Observed classes: `{len(scope['observed_classes'])}`",
        f"- Unique mutated source lines: `{scope['unique_mutated_lines']}`",
        "",
        "## Results",
        "",
        f"- Generated mutants: `{totals['generated']}`",
        f"- Detected mutants: `{totals['detected']}`",
        f"- Undetected mutants: `{totals['undetected']}`",
        f"- Mutation score: `{display(totals['mutation_score_percent'])}`",
        f"- Test strength: `{display(totals['test_strength_percent'])}`",
        f"- Mutation line-coverage proxy: `{display(totals['line_coverage_proxy_percent'])}`",
        "",
        "| PIT status | Count |",
        "|---|---:|",
    ]
    lines.extend(f"| `{status}` | {count} |" for status, count in statuses.items())
    lines.extend(
        [
            "",
            "## Interpretation limits",
            "",
        ]
    )
    lines.extend(
        f"- {limit}" for limit in summary["interpretation_limits"]
    )
    lines.append("")
    return "\n".join(lines)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--expected-class", required=True)
    parser.add_argument("--expected-source", required=True)
    parser.add_argument("--json-output", type=Path, required=True)
    parser.add_argument("--markdown-output", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        summary = analyze_report(
            report_path=args.report,
            expected_class_prefix=args.expected_class,
            expected_source_file=args.expected_source,
        )
    except MutationReportError as exc:
        raise SystemExit(f"QA-4 mutation report validation failed: {exc}") from exc

    args.json_output.parent.mkdir(parents=True, exist_ok=True)
    args.markdown_output.parent.mkdir(parents=True, exist_ok=True)
    args.json_output.write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    args.markdown_output.write_text(render_markdown(summary), encoding="utf-8")

    totals = summary["totals"]
    print(
        "QA4_MUTATION_PILOT="
        f"GENERATED={totals['generated']} "
        f"DETECTED={totals['detected']} "
        f"UNDETECTED={totals['undetected']} "
        f"SCORE={totals['mutation_score_percent']:.4f}%"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
