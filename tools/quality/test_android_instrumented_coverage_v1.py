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


class AndroidInstrumentedCoverageV1Test(unittest.TestCase):
    def test_selects_task_by_exact_managed_device_description(self) -> None:
        text = """
Verification tasks
------------------
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

    def test_build_summary_binds_one_device_report_and_execution_file(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            report = root / "report.xml"
            execution = root / "pixel2Api30" / "coverage.ec"
            report.write_text(REPORT, encoding="utf-8")
            execution.parent.mkdir()
            execution.write_bytes(b"jacoco-fixture")

            source = "com/morimil/app/security/SecretVault.kt"
            summary = build_summary(
                "pixel2Api30",
                report,
                execution,
                [source],
            )
            self.assertEqual("pixel2Api30", summary["canonical_device_id"])
            self.assertEqual(4, summary["report"]["counters"]["LINE"]["covered"])
            self.assertEqual(1, summary["source_inventory"]["total"])
            self.assertEqual(4, summary["tracked_sources"][source]["LINE"]["covered"])
            markdown = render_markdown(summary)
            self.assertIn("80.0000%", markdown)
            self.assertIn("pixel2Api30", markdown)
            self.assertIn("SecretVault.kt", markdown)

    def test_summary_rejects_missing_execution_data_and_wrong_suffix(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            report = root / "report.xml"
            report.write_text(REPORT, encoding="utf-8")
            with self.assertRaises(InstrumentedCoverageError):
                build_summary("pixel2Api30", report, root / "missing.ec")
            wrong = root / "coverage.bin"
            wrong.write_bytes(b"coverage")
            with self.assertRaises(InstrumentedCoverageError):
                build_summary("pixel2Api30", report, wrong)

    def test_summary_rejects_ambiguous_sessions(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            report = root / "report.xml"
            report.write_text(
                REPORT.replace(
                    '<sessioninfo id="fixture-session" start="100" dump="200"/>',
                    '<sessioninfo id="one" start="100" dump="200"/>'
                    '<sessioninfo id="two" start="110" dump="210"/>',
                ),
                encoding="utf-8",
            )
            execution = root / "coverage.ec"
            execution.write_bytes(b"coverage")
            with self.assertRaises(InstrumentedCoverageError):
                build_summary("pixel2Api30", report, execution)

    def test_summary_rejects_untracked_source_or_invalid_device(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            report = root / "report.xml"
            execution = root / "coverage.ec"
            report.write_text(REPORT, encoding="utf-8")
            execution.write_bytes(b"coverage")
            with self.assertRaises(InstrumentedCoverageError):
                build_summary("bad device", report, execution)
            with self.assertRaises(InstrumentedCoverageError):
                build_summary(
                    "pixel2Api30",
                    report,
                    execution,
                    ["com/morimil/app/Missing.kt"],
                )


if __name__ == "__main__":
    unittest.main()
