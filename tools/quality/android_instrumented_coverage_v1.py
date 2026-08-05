#!/usr/bin/env python3
"""Discover and summarize AGP managed-device AndroidTest coverage evidence.

The tool has two deliberately separate operations:

* ``select-task`` chooses the single Gradle task whose description identifies
  the AGP managed-device JaCoCo report task. It does not guess a version-specific
  task name.
* ``summarize`` inventories non-empty JaCoCo execution data and requires one
  unambiguous instrumented-test XML report before publishing counters.

The raw files remain authoritative and are not modified.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

SCHEMA_VERSION = "morimil.android.instrumented.coverage.v1"
MANAGED_DEVICE_REPORT_DESCRIPTION = (
    "Creates JaCoCo test coverage report from data gathered on the Gradle managed device."
)
MEASURED_COUNTER_TYPES = ("INSTRUCTION", "BRANCH", "LINE")
TASK_LINE = re.compile(r"^\s*([A-Za-z0-9_.:-]+)\s+-\s+(.+?)\s*$")


class InstrumentedCoverageError(RuntimeError):
    """Raised when coverage evidence is missing, ambiguous, or inconsistent."""


@dataclass(frozen=True)
class Counter:
    missed: int
    covered: int

    @property
    def total(self) -> int:
        return self.missed + self.covered

    @property
    def percent(self) -> float:
        return 100.0 * self.covered / self.total if self.total else 0.0

    def to_dict(self) -> dict[str, int | float]:
        return {
            "missed": self.missed,
            "covered": self.covered,
            "total": self.total,
            "percent": round(self.percent, 6),
        }


def select_managed_device_coverage_task(tasks_text: str) -> str:
    candidates: list[str] = []
    for line in tasks_text.splitlines():
        match = TASK_LINE.match(line)
        if match is None:
            continue
        task_name, description = match.groups()
        if " ".join(description.split()) == MANAGED_DEVICE_REPORT_DESCRIPTION:
            candidates.append(task_name)

    unique = sorted(set(candidates))
    if len(unique) != 1:
        raise InstrumentedCoverageError(
            "Expected exactly one AGP managed-device coverage report task; "
            f"found {unique}."
        )
    return unique[0]


def _local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _counter_map(root: ET.Element, source: Path) -> dict[str, Counter]:
    counters: dict[str, Counter] = {}
    for element in root:
        if _local_name(element.tag) != "counter":
            continue
        counter_type = element.attrib.get("type")
        if counter_type is None:
            raise InstrumentedCoverageError(f"Counter without type in {source}.")
        if counter_type in counters:
            raise InstrumentedCoverageError(
                f"Duplicate root counter {counter_type} in {source}."
            )
        try:
            missed = int(element.attrib["missed"])
            covered = int(element.attrib["covered"])
        except (KeyError, ValueError) as error:
            raise InstrumentedCoverageError(
                f"Invalid {counter_type} counter in {source}."
            ) from error
        if missed < 0 or covered < 0:
            raise InstrumentedCoverageError(
                f"Negative {counter_type} counter in {source}."
            )
        counters[counter_type] = Counter(missed=missed, covered=covered)

    for counter_type in MEASURED_COUNTER_TYPES:
        counter = counters.get(counter_type)
        if counter is None or counter.total <= 0:
            raise InstrumentedCoverageError(
                f"Missing or empty {counter_type} counter in {source}."
            )
    return counters


def _looks_instrumented(path: Path) -> bool:
    normalized = path.as_posix().lower()
    return (
        "androidtest" in normalized
        or "manageddevice" in normalized
        or "managed_device" in normalized
    )


def discover_instrumented_reports(build_root: Path) -> list[tuple[Path, dict[str, Counter]]]:
    reports: list[tuple[Path, dict[str, Counter]]] = []
    for path in sorted(build_root.rglob("*.xml")):
        relative = path.relative_to(build_root)
        normalized = relative.as_posix().lower()
        if "coverage" not in normalized or not _looks_instrumented(relative):
            continue
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as error:
            raise InstrumentedCoverageError(
                f"Malformed instrumented coverage XML: {relative}."
            ) from error
        if _local_name(root.tag) != "report":
            continue
        reports.append((relative, _counter_map(root, relative)))
    return reports


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def discover_execution_data(build_root: Path) -> list[dict[str, int | str]]:
    records: list[dict[str, int | str]] = []
    for path in sorted(build_root.rglob("*")):
        if not path.is_file() or path.suffix.lower() not in {".ec", ".exec"}:
            continue
        if path.stat().st_size <= 0:
            raise InstrumentedCoverageError(
                f"Empty JaCoCo execution data: {path.relative_to(build_root)}."
            )
        records.append(
            {
                "path": path.relative_to(build_root).as_posix(),
                "bytes": path.stat().st_size,
                "sha256": sha256(path),
            }
        )
    if not records:
        raise InstrumentedCoverageError(
            "No non-empty .ec or .exec instrumented coverage data was found."
        )
    return records


def build_summary(build_root: Path) -> dict[str, object]:
    if not build_root.is_dir():
        raise InstrumentedCoverageError(f"Build root does not exist: {build_root}.")

    reports = discover_instrumented_reports(build_root)
    if len(reports) != 1:
        paths = [path.as_posix() for path, _ in reports]
        raise InstrumentedCoverageError(
            "Expected exactly one instrumented JaCoCo XML report; "
            f"found {paths}."
        )

    report_path, counters = reports[0]
    execution_data = discover_execution_data(build_root)
    return {
        "schema": SCHEMA_VERSION,
        "build_root": build_root.as_posix(),
        "report": {
            "path": report_path.as_posix(),
            "bytes": (build_root / report_path).stat().st_size,
            "sha256": sha256(build_root / report_path),
            "counters": {
                counter_type: counters[counter_type].to_dict()
                for counter_type in MEASURED_COUNTER_TYPES
            },
        },
        "execution_data": execution_data,
    }


def render_markdown(summary: dict[str, object]) -> str:
    report = summary["report"]
    assert isinstance(report, dict)
    counters = report["counters"]
    assert isinstance(counters, dict)
    execution_data = summary["execution_data"]
    assert isinstance(execution_data, list)

    lines = [
        "# Android instrumented coverage baseline",
        "",
        f"Schema: `{summary['schema']}`",
        "",
        f"Report: `{report['path']}`",
        f"Report SHA-256: `{report['sha256']}`",
        "",
        "| Counter | Covered | Total | Coverage |",
        "|---|---:|---:|---:|",
    ]
    for counter_type in MEASURED_COUNTER_TYPES:
        counter = counters[counter_type]
        assert isinstance(counter, dict)
        lines.append(
            f"| {counter_type} | {counter['covered']} | {counter['total']} | "
            f"{counter['percent']:.4f}% |"
        )

    lines.extend(
        [
            "",
            f"Execution-data files: `{len(execution_data)}`",
            "",
            "| Path | Bytes | SHA-256 |",
            "|---|---:|---|",
        ]
    )
    for record in execution_data:
        assert isinstance(record, dict)
        lines.append(
            f"| `{record['path']}` | {record['bytes']} | `{record['sha256']}` |"
        )
    lines.append("")
    return "\n".join(lines)


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")


def command_select_task(args: argparse.Namespace) -> int:
    tasks_text = args.tasks_file.read_text(encoding="utf-8")
    task = select_managed_device_coverage_task(tasks_text)
    write_text(args.output, task + "\n")
    print(f"ANDROID_INSTRUMENTED_COVERAGE_TASK={task}")
    return 0


def command_summarize(args: argparse.Namespace) -> int:
    summary = build_summary(args.build_root)
    write_text(
        args.json_output,
        json.dumps(summary, indent=2, sort_keys=True) + "\n",
    )
    write_text(args.markdown_output, render_markdown(summary))

    report = summary["report"]
    assert isinstance(report, dict)
    counters = report["counters"]
    assert isinstance(counters, dict)
    execution_data = summary["execution_data"]
    assert isinstance(execution_data, list)

    metrics = []
    for counter_type in MEASURED_COUNTER_TYPES:
        counter = counters[counter_type]
        assert isinstance(counter, dict)
        metrics.append(
            f"{counter_type}={counter['covered']}/{counter['total']}="
            f"{counter['percent']:.4f}%"
        )
    print("ANDROID_INSTRUMENTED_COVERAGE=" + " ".join(metrics))
    print(
        "ANDROID_INSTRUMENTED_COVERAGE_INVENTORY="
        f"REPORT={report['path']} EXECUTION_FILES={len(execution_data)}"
    )
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    select_task = subparsers.add_parser("select-task")
    select_task.add_argument("--tasks-file", type=Path, required=True)
    select_task.add_argument("--output", type=Path, required=True)
    select_task.set_defaults(handler=command_select_task)

    summarize = subparsers.add_parser("summarize")
    summarize.add_argument("--build-root", type=Path, required=True)
    summarize.add_argument("--json-output", type=Path, required=True)
    summarize.add_argument("--markdown-output", type=Path, required=True)
    summarize.set_defaults(handler=command_summarize)
    return parser


def main(argv: Iterable[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(list(argv) if argv is not None else None)
    try:
        return int(args.handler(args))
    except InstrumentedCoverageError as error:
        print(f"ANDROID_INSTRUMENTED_COVERAGE_ERROR={error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
