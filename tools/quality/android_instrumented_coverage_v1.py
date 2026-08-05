#!/usr/bin/env python3
"""Validate one canonical AGP managed-device AndroidTest coverage report.

Compatibility tests may run on several devices. Coverage publication is bound to
one explicitly named device, one execution-data file, and the successful ADB pull
log that proves where that device's coverage file was written. This is necessary
because AGP 8.6.1 can label the output directory with a different managed-device
name.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shlex
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Mapping

SCHEMA_VERSION = "morimil.android.instrumented.coverage.v1"
MANAGED_DEVICE_REPORT_DESCRIPTION = (
    "Creates JaCoCo test coverage report from data gathered on the Gradle managed device."
)
MEASURED_COUNTER_TYPES = ("INSTRUCTION", "BRANCH", "LINE")
TASK_LINE = re.compile(r"^\s*([A-Za-z0-9_.:-]+)\s+-\s+(.+?)\s*$")
DEVICE_ID = re.compile(r"^[A-Za-z0-9_.-]{1,80}$")


class InstrumentedCoverageError(RuntimeError):
    """Raised when coverage evidence is missing, ambiguous, or inconsistent."""


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


def _parse_counter_elements(
    elements: Iterable[ET.Element],
    source: str,
) -> dict[str, Counter]:
    counters: dict[str, Counter] = {}
    for element in elements:
        if _local_name(element.tag) != "counter":
            continue
        counter_type = element.attrib.get("type")
        if counter_type is None or counter_type in counters:
            raise InstrumentedCoverageError(
                f"Missing or duplicate counter type in {source}."
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
    return counters


def _require_root_counters(
    counters: Mapping[str, Counter],
    source: str,
) -> None:
    for counter_type in MEASURED_COUNTER_TYPES:
        counter = counters.get(counter_type)
        if counter is None or counter.total <= 0:
            raise InstrumentedCoverageError(
                f"Missing or empty {counter_type} counter in {source}."
            )


def _normalize_source_counters(
    counters: Mapping[str, Counter],
) -> dict[str, Counter]:
    """Represent legitimate source-level omissions as explicit zero counters.

    JaCoCo may omit BRANCH for files without decisions and may omit all measured
    counters for declarations such as DAO interfaces that contain no executable
    bytecode. The global report counters remain strictly required and non-empty.
    """

    normalized = dict(counters)
    for counter_type in MEASURED_COUNTER_TYPES:
        normalized.setdefault(counter_type, Counter())
    return normalized


def parse_report(
    report_path: Path,
) -> tuple[dict[str, Counter], dict[str, object], dict[str, dict[str, Counter]]]:
    if not report_path.is_file() or report_path.stat().st_size <= 0:
        raise InstrumentedCoverageError(
            f"Coverage report is missing or empty: {report_path}."
        )
    try:
        root = ET.parse(report_path).getroot()
    except ET.ParseError as error:
        raise InstrumentedCoverageError(
            f"Malformed instrumented coverage XML: {report_path}."
        ) from error
    if _local_name(root.tag) != "report":
        raise InstrumentedCoverageError(
            f"Coverage XML root is not report: {report_path}."
        )

    sessions = [element for element in root if _local_name(element.tag) == "sessioninfo"]
    if len(sessions) != 1:
        raise InstrumentedCoverageError(
            f"Expected exactly one canonical-device JaCoCo session; found {len(sessions)}."
        )
    session = sessions[0]
    try:
        session_record: dict[str, object] = {
            "id": session.attrib["id"],
            "start_epoch_ms": int(session.attrib["start"]),
            "dump_epoch_ms": int(session.attrib["dump"]),
        }
    except (KeyError, ValueError) as error:
        raise InstrumentedCoverageError("Invalid JaCoCo session metadata.") from error
    if session_record["dump_epoch_ms"] < session_record["start_epoch_ms"]:
        raise InstrumentedCoverageError("JaCoCo session dump precedes its start.")

    root_counters = _parse_counter_elements(root, str(report_path))
    _require_root_counters(root_counters, str(report_path))

    sources: dict[str, dict[str, Counter]] = {}
    for package in root:
        if _local_name(package.tag) != "package":
            continue
        package_name = package.attrib.get("name", "")
        for source_file in package:
            if _local_name(source_file.tag) != "sourcefile":
                continue
            name = source_file.attrib.get("name")
            if not name:
                raise InstrumentedCoverageError("Source file without a name in report.")
            logical_path = f"{package_name}/{name}" if package_name else name
            if logical_path in sources:
                raise InstrumentedCoverageError(
                    f"Duplicate source file in report: {logical_path}."
                )
            parsed = _parse_counter_elements(source_file, logical_path)
            sources[logical_path] = _normalize_source_counters(parsed)

    if not sources:
        raise InstrumentedCoverageError("Coverage report contains no source files.")
    return root_counters, session_record, sources


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def file_record(path: Path) -> dict[str, int | str]:
    if not path.is_file() or path.stat().st_size <= 0:
        raise InstrumentedCoverageError(f"Evidence file is missing or empty: {path}.")
    return {
        "path": path.as_posix(),
        "bytes": path.stat().st_size,
        "sha256": sha256(path),
    }


def parse_pull_provenance(
    device_id: str,
    provenance_log: Path,
    execution_data_path: Path,
) -> dict[str, object]:
    record = file_record(provenance_log)
    if device_id not in provenance_log.parts:
        raise InstrumentedCoverageError(
            "ADB pull provenance path is not scoped to the canonical device."
        )
    text = provenance_log.read_text(encoding="utf-8")
    if "EXIT CODE: 0" not in text:
        raise InstrumentedCoverageError("ADB coverage pull did not record exit code 0.")
    executing = [line for line in text.splitlines() if line.startswith("EXECUTING: ")]
    if len(executing) != 1:
        raise InstrumentedCoverageError(
            "ADB pull provenance must contain exactly one EXECUTING line."
        )
    try:
        tokens = shlex.split(executing[0][len("EXECUTING: ") :])
        pull_index = tokens.index("pull")
        remote_source = tokens[pull_index + 1]
        destination = Path(tokens[pull_index + 2])
    except (ValueError, IndexError) as error:
        raise InstrumentedCoverageError(
            "ADB pull provenance command is malformed."
        ) from error
    if destination.resolve() != execution_data_path.resolve():
        raise InstrumentedCoverageError(
            "ADB pull destination does not match the selected execution-data file."
        )
    record.update(
        {
            "canonical_device_id": device_id,
            "remote_source": remote_source,
            "destination": destination.as_posix(),
            "destination_label_matches_device": device_id in destination.parts,
            "exit_code": 0,
        }
    )
    return record


def build_summary(
    device_id: str,
    report_path: Path,
    execution_data_path: Path,
    provenance_log: Path,
    tracked_sources: Iterable[str] = (),
) -> dict[str, object]:
    if DEVICE_ID.fullmatch(device_id) is None:
        raise InstrumentedCoverageError(f"Invalid canonical device id: {device_id}.")
    if execution_data_path.suffix.lower() not in {".ec", ".exec"}:
        raise InstrumentedCoverageError(
            "Canonical execution data must use .ec or .exec."
        )

    root_counters, session, sources = parse_report(report_path)
    execution_data = file_record(execution_data_path)
    provenance = parse_pull_provenance(device_id, provenance_log, execution_data_path)
    report = file_record(report_path)
    report["counters"] = {
        counter_type: root_counters[counter_type].to_dict()
        for counter_type in MEASURED_COUNTER_TYPES
    }
    report["session"] = session

    tracked: dict[str, object] = {}
    for source in sorted(set(tracked_sources)):
        counters = sources.get(source)
        if counters is None:
            raise InstrumentedCoverageError(
                f"Tracked source is absent from coverage report: {source}."
            )
        tracked[source] = {
            counter_type: counters[counter_type].to_dict()
            for counter_type in MEASURED_COUNTER_TYPES
        }

    zero_line_sources = sorted(
        source
        for source, counters in sources.items()
        if counters["LINE"].covered == 0
    )
    return {
        "schema": SCHEMA_VERSION,
        "canonical_device_id": device_id,
        "report": report,
        "execution_data": execution_data,
        "adb_pull_provenance": provenance,
        "source_inventory": {
            "total": len(sources),
            "zero_line_coverage": len(zero_line_sources),
        },
        "tracked_sources": tracked,
    }


def render_markdown(summary: dict[str, object]) -> str:
    report = summary["report"]
    execution_data = summary["execution_data"]
    provenance = summary["adb_pull_provenance"]
    tracked_sources = summary["tracked_sources"]
    assert isinstance(report, dict)
    assert isinstance(execution_data, dict)
    assert isinstance(provenance, dict)
    assert isinstance(tracked_sources, dict)
    counters = report["counters"]
    assert isinstance(counters, dict)

    lines = [
        "# Android instrumented coverage baseline",
        "",
        f"Schema: `{summary['schema']}`",
        f"Canonical device: `{summary['canonical_device_id']}`",
        "",
        f"Report: `{report['path']}`",
        f"Report SHA-256: `{report['sha256']}`",
        f"Execution data: `{execution_data['path']}`",
        f"Execution-data SHA-256: `{execution_data['sha256']}`",
        f"ADB provenance: `{provenance['path']}`",
        f"AGP destination label matches device: `{provenance['destination_label_matches_device']}`",
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

    if tracked_sources:
        lines.extend(
            [
                "",
                "## Tracked sources",
                "",
                "| Source | Covered lines | Total lines | Line coverage |",
                "|---|---:|---:|---:|",
            ]
        )
        for source, raw_counters in tracked_sources.items():
            assert isinstance(raw_counters, dict)
            line = raw_counters["LINE"]
            assert isinstance(line, dict)
            lines.append(
                f"| `{source}` | {line['covered']} | {line['total']} | "
                f"{line['percent']:.4f}% |"
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
    summary = build_summary(
        device_id=args.device_id,
        report_path=args.report,
        execution_data_path=args.execution_data,
        provenance_log=args.provenance_log,
        tracked_sources=args.tracked_source,
    )
    write_text(
        args.json_output,
        json.dumps(summary, indent=2, sort_keys=True) + "\n",
    )
    write_text(args.markdown_output, render_markdown(summary))

    report = summary["report"]
    provenance = summary["adb_pull_provenance"]
    execution_data = summary["execution_data"]
    assert isinstance(report, dict)
    assert isinstance(provenance, dict)
    assert isinstance(execution_data, dict)
    counters = report["counters"]
    assert isinstance(counters, dict)
    metrics = []
    for counter_type in MEASURED_COUNTER_TYPES:
        counter = counters[counter_type]
        assert isinstance(counter, dict)
        metrics.append(
            f"{counter_type}={counter['covered']}/{counter['total']}="
            f"{counter['percent']:.4f}%"
        )
    print(
        f"ANDROID_INSTRUMENTED_COVERAGE_DEVICE={summary['canonical_device_id']}"
    )
    print("ANDROID_INSTRUMENTED_COVERAGE=" + " ".join(metrics))
    print(
        "ANDROID_INSTRUMENTED_COVERAGE_EVIDENCE="
        f"REPORT_SHA256={report['sha256']} "
        f"EXECUTION_SHA256={execution_data['sha256']} "
        f"DESTINATION_LABEL_MATCH={provenance['destination_label_matches_device']}"
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
    summarize.add_argument("--device-id", required=True)
    summarize.add_argument("--report", type=Path, required=True)
    summarize.add_argument("--execution-data", type=Path, required=True)
    summarize.add_argument("--provenance-log", type=Path, required=True)
    summarize.add_argument("--tracked-source", action="append", default=[])
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
