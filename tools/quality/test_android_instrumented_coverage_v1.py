from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("android_instrumented_coverage_v1.py")
SPEC = importlib.util.spec_from_file_location("android_instrumented_coverage_v1", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)

InstrumentedCoverageError = MODULE.InstrumentedCoverageError
build_summary = MODULE.build_summary
render_markdown = MODULE.render_markdown
select_managed_device_coverage_task = MODULE.select_managed_device_coverage_task


REPORT = """<?xml version="1.0" encoding="UTF-8"?>
<report name="fixture">
  <sessioninfo id="fixture-session" start="100" dump="200"/>
  <package name="com/morimil/app/security">
    <sourcefile name="SecretVault.kt">
      <counter type="INSTRUCTION" missed="2" covered="8"/>
      <counter type="BRANCH" missed="1" covered="3"/>
      <counter type="LINE" missed="1" covered="4"/>
    </sourcefile>
  </package>
  <counter type="INSTRUCTION" missed="2" covered="8"/>
  <counter type="BRANCH" missed="1" covered="3"/>
  <counter type="LINE" missed="1" covered="4"/>
</report>
"""


def write_provenance(root: Path, destination: Path, device: str = "pixel2Api30") -> Path:
    log = root / "outputs" / "androidTest-results" / device / "testlog" / "adb.pull.ok.txt"
    log.parent.mkdir(parents=True)
    log.write_text(
        "EXECUTING: /sdk/adb -s emulator-5554 pull "
        f"/data/local/tmp/coverage.ec {destination}\n"
        "EXIT CODE: 0\n",
        encoding="utf-8",
    )
    return log


class AndroidInstrumentedCoverageV1Test(unittest.TestCase):
    def test_selects_task_by_exact_managed_device_description(self) -> None:
        text = """
createDebugAndroidTestCoverageReport - Creates JaCoCo test coverage report from data gathered on the device.
createManagedDeviceDebugAndroidTestCoverageReport - Creates JaCoCo test coverage report from data gathered on the Gradle managed device.
"""
        self.assertEqual(
            "createManagedDeviceDebugAndroidTestCoverageReport",
            select_managed_device_coverage_task(text),
        )

    def test_task_selection_rejects_missing_or_ambiguous_inventory(self) -> None:
        with self.assertRaises(InstrumentedCoverageError):
            select_managed_device_coverage_task("help - Displays help.\n")
        duplicate = "\n".join(
            [
                "first - Creates JaCoCo test coverage report from data gathered on the Gradle managed device.",
                "second - Creates JaCoCo test coverage report from data gathered on the Gradle managed device.",
            ]
        )
        with self.assertRaises(InstrumentedCoverageError):
            select_managed_device_coverage_task(duplicate)

    def test_build_summary_binds_device_execution_and_pull_log(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            report = root / "report.xml"
            execution = root / "mislabelled" / "pixel2Api35" / "coverage.ec"
            report.write_text(REPORT, encoding="utf-8")
            execution.parent.mkdir(parents=True)
            execution.write_bytes(b"jacoco-fixture")
            provenance = write_provenance(root, execution)

            source = "com/morimil/app/security/SecretVault.kt"
            summary = build_summary(
                "pixel2Api30",
                report,
                execution,
                provenance,
                [source],
            )
            self.assertEqual("pixel2Api30", summary["canonical_device_id"])
            self.assertFalse(
                summary["adb_pull_provenance"]["destination_label_matches_device"]
            )
            self.assertEqual(4, summary["report"]["counters"]["LINE"]["covered"])
            self.assertEqual(4, summary["tracked_sources"][source]["LINE"]["covered"])
            markdown = render_markdown(summary)
            self.assertIn("80.0000%", markdown)
            self.assertIn("False", markdown)

    def test_summary_rejects_missing_or_wrong_execution_data(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            report = root / "report.xml"
            report.write_text(REPORT, encoding="utf-8")
            missing = root / "missing.ec"
            provenance = write_provenance(root, missing)
            with self.assertRaises(InstrumentedCoverageError):
                build_summary("pixel2Api30", report, missing, provenance)

            wrong = root / "coverage.bin"
            wrong.write_bytes(b"coverage")
            provenance = write_provenance(root, wrong)
            with self.assertRaises(InstrumentedCoverageError):
                build_summary("pixel2Api30", report, wrong, provenance)

    def test_summary_rejects_wrong_provenance_destination_or_device(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            report = root / "report.xml"
            execution = root / "coverage.ec"
            other = root / "other.ec"
            report.write_text(REPORT, encoding="utf-8")
            execution.write_bytes(b"coverage")
            other.write_bytes(b"other")
            wrong_destination = write_provenance(root, other)
            with self.assertRaises(InstrumentedCoverageError):
                build_summary(
                    "pixel2Api30", report, execution, wrong_destination
                )

            wrong_device = write_provenance(root, execution, "pixel2Api35")
            with self.assertRaises(InstrumentedCoverageError):
                build_summary("pixel2Api30", report, execution, wrong_device)

    def test_summary_rejects_ambiguous_sessions_or_missing_source(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            report = root / "report.xml"
            execution = root / "coverage.ec"
            execution.write_bytes(b"coverage")
            provenance = write_provenance(root, execution)
            report.write_text(
                REPORT.replace(
                    '<sessioninfo id="fixture-session" start="100" dump="200"/>',
                    '<sessioninfo id="one" start="100" dump="200"/>'
                    '<sessioninfo id="two" start="110" dump="210"/>',
                ),
                encoding="utf-8",
            )
            with self.assertRaises(InstrumentedCoverageError):
                build_summary("pixel2Api30", report, execution, provenance)

            report.write_text(REPORT, encoding="utf-8")
            with self.assertRaises(InstrumentedCoverageError):
                build_summary(
                    "pixel2Api30",
                    report,
                    execution,
                    provenance,
                    ["com/morimil/app/Missing.kt"],
                )


if __name__ == "__main__":
    unittest.main()
