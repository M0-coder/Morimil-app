#!/usr/bin/env python3
"""Derive an auditable authored-source coverage view from an AGP/JaCoCo XML report.

The raw report remains authoritative and untouched. This tool excludes only whole
source files that match reviewed generated-source naming rules and are absent from
the repository's authored source roots. Compiler-generated classes that share an
authored source file remain included by design.
"""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Mapping

SCHEMA_VERSION = "morimil.android.authored.coverage.v1"
COUNTER_TYPES = ("INSTRUCTION", "BRANCH", "LINE", "COMPLEXITY", "METHOD", "CLASS")
MEASURED_COUNTER_TYPES = ("INSTRUCTION", "BRANCH", "LINE")
GENERATED_SUFFIXES = ("_Impl.kt", "_Impl.java")
GENERATED_EXACT_NAMES = frozenset(
    {
        "BuildConfig.java",
        "R.java",
        "Manifest.java",
    }
)


class CoveragePolicyError(RuntimeError):
    """Raised when the report or generated-source policy is inconsistent."""


@dataclass(frozen=True)
class Counter:
    missed: int = 0
    covered: int = 0

    @property
    def total(self) -> int:
        return self.missed + self.covered

    @property
    def percent(self) -> float:
        return 100.0 * self.covered / self.total if self.total else 0.0

    def subtract(self, other: "Counter") -> "Counter":
        missed = self.missed - other.missed
        covered = self.covered - other.covered
        if missed < 0 or covered < 0:
            raise CoveragePolicyError("Counter subtraction produced a negative value.")
        return Counter(missed=missed, covered=covered)

    def to_dict(self) -> dict[str, int | float]:
        return {
            "missed": self.missed,
            "covered": self.covered,
            "total": self.total,
            "percent": round(self.percent, 6),
        }


@dataclass(frozen=True)
class SourceCoverage:
    package: str
    name: str
    counters: Mapping[str, Counter]

    @property
    def logical_path(self) -> str:
        return f"{self.package}/{self.name}" if self.package else self.name


def parse_counters(element: ET.Element) -> dict[str, Counter]:
    parsed: dict[str, Counter] = {}
    for item in element.findall("counter"):
        counter_type = item.attrib.get("type")
        if not counter_type:
            raise CoveragePolicyError("Counter without type.")
        try:
            missed = int(item.attrib["missed"])
            covered = int(item.attrib["covered"])
        except (KeyError, ValueError) as exc:
            raise CoveragePolicyError(f"Invalid {counter_type} counter.") from exc
        if missed < 0 or covered < 0:
            raise CoveragePolicyError(f"Negative {counter_type} counter.")
        parsed[counter_type] = Counter(missed=missed, covered=covered)
    return parsed


def add_counter_maps(items: Iterable[Mapping[str, Counter]]) -> dict[str, Counter]:
    totals = {counter_type: Counter() for counter_type in COUNTER_TYPES}
    for item in items:
        for counter_type in COUNTER_TYPES:
            value = item.get(counter_type, Counter())
            current = totals[counter_type]
            totals[counter_type] = Counter(
                missed=current.missed + value.missed,
                covered=current.covered + value.covered,
            )
    return totals


def parse_report(report_path: Path) -> tuple[dict[str, Counter], list[SourceCoverage]]:
    try:
        root = ET.parse(report_path).getroot()
    except (ET.ParseError, OSError) as exc:
        raise CoveragePolicyError(f"Cannot parse JaCoCo XML: {report_path}") from exc

    if root.tag != "report":
        raise CoveragePolicyError(f"Unexpected root element: {root.tag}")

    raw_counters = parse_counters(root)
    for counter_type in MEASURED_COUNTER_TYPES:
        value = raw_counters.get(counter_type)
        if value is None or value.total <= 0:
            raise CoveragePolicyError(f"Missing or empty root {counter_type} counter.")

    sources: list[SourceCoverage] = []
    seen: set[tuple[str, str]] = set()
    for package in root.findall("package"):
        package_name = package.attrib.get("name", "")
        for source_file in package.findall("sourcefile"):
            source_name = source_file.attrib.get("name")
            if not source_name:
                raise CoveragePolicyError("sourcefile without name.")
            key = (package_name, source_name)
            if key in seen:
                raise CoveragePolicyError(
                    f"Duplicate sourcefile entry: {package_name}/{source_name}"
                )
            seen.add(key)
            sources.append(
                SourceCoverage(
                    package=package_name,
                    name=source_name,
                    counters=parse_counters(source_file),
                )
            )

    if not sources:
        raise CoveragePolicyError("JaCoCo report contains no source files.")

    source_totals = add_counter_maps(source.counters for source in sources)
    for counter_type in COUNTER_TYPES:
        root_value = raw_counters.get(counter_type, Counter())
        if source_totals[counter_type] != root_value:
            raise CoveragePolicyError(
                f"Sourcefile totals do not match root {counter_type} counter."
            )

    return raw_counters, sources


def generated_reason(source_name: str) -> str | None:
    if source_name.endswith(GENERATED_SUFFIXES):
        return "room_or_processor_impl"
    if source_name in GENERATED_EXACT_NAMES:
        return "android_generated_exact_name"
    return None


def authored_candidates(source_root: Path, source: SourceCoverage) -> tuple[Path, ...]:
    package = Path(*source.package.split("/")) if source.package else Path()
    return (
        source_root / "java" / package / source.name,
        source_root / "kotlin" / package / source.name,
    )


def classify_sources(
    sources: Iterable[SourceCoverage],
    source_root: Path,
) -> tuple[list[SourceCoverage], list[tuple[SourceCoverage, str]]]:
    included: list[SourceCoverage] = []
    excluded: list[tuple[SourceCoverage, str]] = []

    for source in sources:
        reason = generated_reason(source.name)
        candidates = authored_candidates(source_root, source)
        authored_matches = [path for path in candidates if path.is_file()]

        if reason is None:
            included.append(source)
            continue

        if authored_matches:
            joined = ", ".join(str(path) for path in authored_matches)
            raise CoveragePolicyError(
                f"Generated-name source exists in authored roots: "
                f"{source.logical_path}: {joined}"
            )

        excluded.append((source, reason))

    if not included:
        raise CoveragePolicyError("Generated-source policy excluded every source file.")

    return included, excluded


def source_to_dict(source: SourceCoverage, reason: str | None = None) -> dict[str, object]:
    result: dict[str, object] = {
        "package": source.package,
        "name": source.name,
        "logicalPath": source.logical_path,
        "counters": {
            counter_type: source.counters.get(counter_type, Counter()).to_dict()
            for counter_type in COUNTER_TYPES
        },
    }
    if reason is not None:
        result["reason"] = reason
    return result


def build_summary(
    report_path: Path,
    source_root: Path,
    require_generated_exclusion: bool,
) -> dict[str, object]:
    raw_counters, sources = parse_report(report_path)
    included, excluded = classify_sources(sources, source_root)

    if require_generated_exclusion and not excluded:
        raise CoveragePolicyError(
            "Expected at least one reviewed generated source exclusion."
        )

    excluded_totals = add_counter_maps(source.counters for source, _ in excluded)
    authored_totals = {
        counter_type: raw_counters.get(counter_type, Counter()).subtract(
            excluded_totals[counter_type]
        )
        for counter_type in COUNTER_TYPES
    }
    included_totals = add_counter_maps(source.counters for source in included)
    if included_totals != authored_totals:
        raise CoveragePolicyError(
            "Included source totals do not match raw-minus-excluded totals."
        )

    zero_coverage = sorted(
        (
            source
            for source in included
            if source.counters.get("LINE", Counter()).total > 0
            and source.counters.get("LINE", Counter()).covered == 0
        ),
        key=lambda source: (
            -source.counters.get("LINE", Counter()).total,
            source.logical_path,
        ),
    )

    return {
        "schemaVersion": SCHEMA_VERSION,
        "input": {
            "report": report_path.as_posix(),
            "sourceRoot": source_root.as_posix(),
        },
        "policy": {
            "wholeSourceFileExclusionsOnly": True,
            "generatedSuffixes": list(GENERATED_SUFFIXES),
            "generatedExactNames": sorted(GENERATED_EXACT_NAMES),
            "authoredSourceExistenceCheck": True,
            "residualBoundary": (
                "Compiler-generated classes that share an authored source file "
                "remain included."
            ),
            "thresholdApplied": False,
        },
        "inventory": {
            "sourceFilesRaw": len(sources),
            "sourceFilesIncluded": len(included),
            "sourceFilesExcludedGenerated": len(excluded),
            "zeroLineCoverageIncludedSources": len(zero_coverage),
        },
        "raw": {
            counter_type: raw_counters.get(counter_type, Counter()).to_dict()
            for counter_type in COUNTER_TYPES
        },
        "excludedGenerated": {
            "totals": {
                counter_type: excluded_totals[counter_type].to_dict()
                for counter_type in COUNTER_TYPES
            },
            "sources": [
                source_to_dict(source, reason)
                for source, reason in sorted(
                    excluded, key=lambda item: item[0].logical_path
                )
            ],
        },
        "authoredSourceView": {
            "counters": {
                counter_type: authored_totals[counter_type].to_dict()
                for counter_type in COUNTER_TYPES
            },
            "zeroLineCoverageSources": [
                source_to_dict(source) for source in zero_coverage
            ],
        },
    }


def render_markdown(summary: Mapping[str, object]) -> str:
    raw = summary["raw"]
    authored = summary["authoredSourceView"]["counters"]
    inventory = summary["inventory"]
    excluded_sources = summary["excludedGenerated"]["sources"]
    zero_sources = summary["authoredSourceView"]["zeroLineCoverageSources"]

    lines = [
        "# Android authored-source coverage summary",
        "",
        f"Schema: `{summary['schemaVersion']}`",
        "",
        "## Counters",
        "",
        "| View | Lines | Branches | Instructions |",
        "| --- | ---: | ---: | ---: |",
        (
            f"| Raw | {raw['LINE']['percent']:.4f}% "
            f"({raw['LINE']['covered']}/{raw['LINE']['total']}) | "
            f"{raw['BRANCH']['percent']:.4f}% "
            f"({raw['BRANCH']['covered']}/{raw['BRANCH']['total']}) | "
            f"{raw['INSTRUCTION']['percent']:.4f}% "
            f"({raw['INSTRUCTION']['covered']}/{raw['INSTRUCTION']['total']}) |"
        ),
        (
            f"| Authored-source view | {authored['LINE']['percent']:.4f}% "
            f"({authored['LINE']['covered']}/{authored['LINE']['total']}) | "
            f"{authored['BRANCH']['percent']:.4f}% "
            f"({authored['BRANCH']['covered']}/{authored['BRANCH']['total']}) | "
            f"{authored['INSTRUCTION']['percent']:.4f}% "
            f"({authored['INSTRUCTION']['covered']}/{authored['INSTRUCTION']['total']}) |"
        ),
        "",
        "## Inventory",
        "",
        f"- Raw source files: `{inventory['sourceFilesRaw']}`",
        f"- Included source files: `{inventory['sourceFilesIncluded']}`",
        (
            "- Excluded generated source files: "
            f"`{inventory['sourceFilesExcludedGenerated']}`"
        ),
        (
            "- Included source files with zero line coverage: "
            f"`{inventory['zeroLineCoverageIncludedSources']}`"
        ),
        "",
        "## Excluded generated source files",
        "",
    ]
    for source in excluded_sources:
        lines.append(f"- `{source['logicalPath']}` — `{source['reason']}`")

    lines.extend(
        [
            "",
            "## Largest included sources with zero line coverage",
            "",
            "| Source | Lines |",
            "| --- | ---: |",
        ]
    )
    for source in zero_sources[:25]:
        line_counter = source["counters"]["LINE"]
        lines.append(f"| `{source['logicalPath']}` | {line_counter['total']} |")

    lines.extend(
        [
            "",
            "## Interpretation boundary",
            "",
            (
                "Only whole source files that match reviewed generated naming "
                "rules and are absent from authored source roots are excluded."
            ),
            (
                "Compiler-generated classes that share an authored source file "
                "remain included. No coverage threshold is applied."
            ),
            "",
        ]
    )
    return "\n".join(lines)


def write_outputs(
    summary: Mapping[str, object],
    json_output: Path,
    markdown_output: Path,
) -> None:
    json_output.parent.mkdir(parents=True, exist_ok=True)
    markdown_output.parent.mkdir(parents=True, exist_ok=True)
    json_output.write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    markdown_output.write_text(
        render_markdown(summary),
        encoding="utf-8",
        newline="\n",
    )


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--json-output", type=Path, required=True)
    parser.add_argument("--markdown-output", type=Path, required=True)
    parser.add_argument(
        "--require-generated-exclusion",
        action="store_true",
        help="Fail if the report contains no reviewed generated source exclusion.",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    try:
        summary = build_summary(
            report_path=args.report,
            source_root=args.source_root,
            require_generated_exclusion=args.require_generated_exclusion,
        )
        write_outputs(summary, args.json_output, args.markdown_output)
    except CoveragePolicyError as exc:
        print(f"ANDROID AUTHORED COVERAGE: FAIL: {exc}", file=sys.stderr)
        return 2

    authored = summary["authoredSourceView"]["counters"]
    inventory = summary["inventory"]
    print(
        "ANDROID_AUTHORED_SOURCE_COVERAGE="
        f"LINE={authored['LINE']['covered']}/{authored['LINE']['total']}="
        f"{authored['LINE']['percent']:.4f}% "
        f"BRANCH={authored['BRANCH']['covered']}/{authored['BRANCH']['total']}="
        f"{authored['BRANCH']['percent']:.4f}% "
        f"INSTRUCTION={authored['INSTRUCTION']['covered']}/"
        f"{authored['INSTRUCTION']['total']}="
        f"{authored['INSTRUCTION']['percent']:.4f}%"
    )
    print(
        "ANDROID_AUTHORED_SOURCE_INVENTORY="
        f"RAW={inventory['sourceFilesRaw']} "
        f"INCLUDED={inventory['sourceFilesIncluded']} "
        f"EXCLUDED_GENERATED={inventory['sourceFilesExcludedGenerated']} "
        f"ZERO_LINE_COVERAGE={inventory['zeroLineCoverageIncludedSources']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
