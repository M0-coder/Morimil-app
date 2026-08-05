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

    def test_build_summary_requires_and_records_raw_execution_data(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            build_root = Path(temporary)
            report = (
                build_root
                / "reports"
                / "coverage"
                / "androidTest"
                / "debug"
                / "managedDevice"
                / "report.xml"
            )
            report.parent.mkdir(parents=True)
            report.write_text(
                """<?xml version="1.0" encoding="UTF-8"?>
<report name="fixture">
  <counter type="INSTRUCTION" missed="2" covered="8"/>
  <counter type="BRANCH" missed="1" covered="3"/>
  <counter type="LINE" missed="1" covered="4"/>
</report>
""",
                encoding="utf-8",
            )
            execution = (
                build_root
                / "outputs"
                / "managed_device_code_coverage"
                / "debug"
                / "pixel2Api30.ec"
            )
            execution.parent.mkdir(parents=True)
            execution.write_bytes(b"jacoco-fixture")

            summary = build_summary(build_root)
            self.assertEqual(
                "morimil.android.instrumented.coverage.v1",
                summary["schema"],
            )
            report_summary = summary["report"]
            self.assertEqual(4, report_summary["counters"]["LINE"]["covered"])
            self.assertEqual(5, report_summary["counters"]["LINE"]["total"])
            self.assertEqual(1, len(summary["execution_data"]))
            markdown = render_markdown(summary)
            self.assertIn("80.0000%", markdown)
            self.assertIn("pixel2Api30.ec", markdown)

    def test_summary_rejects_missing_execution_data(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            build_root = Path(temporary)
            report = (
                build_root
                / "reports"
                / "coverage"
                / "androidTest"
                / "debug"
                / "managedDevice"
                / "report.xml"
            )
            report.parent.mkdir(parents=True)
            report.write_text(
                """<report name="fixture">
<counter type="INSTRUCTION" missed="1" covered="1"/>
<counter type="BRANCH" missed="1" covered="1"/>
<counter type="LINE" missed="1" covered="1"/>
</report>""",
                encoding="utf-8",
            )
            with self.assertRaises(InstrumentedCoverageError):
                build_summary(build_root)

    def test_summary_rejects_multiple_instrumented_reports(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            build_root = Path(temporary)
            for device in ("pixel2Api30", "pixel2Api35"):
                report = (
                    build_root
                    / "reports"
                    / "coverage"
                    / "androidTest"
                    / "debug"
                    / device
                    / "report.xml"
                )
                report.parent.mkdir(parents=True)
                report.write_text(
                    """<report name="fixture">
<counter type="INSTRUCTION" missed="1" covered="1"/>
<counter type="BRANCH" missed="1" covered="1"/>
<counter type="LINE" missed="1" covered="1"/>
</report>""",
                    encoding="utf-8",
                )
            execution = build_root / "outputs" / "managed_device_code_coverage" / "data.ec"
            execution.parent.mkdir(parents=True)
            execution.write_bytes(b"coverage")

            with self.assertRaises(InstrumentedCoverageError):
                build_summary(build_root)


if __name__ == "__main__":
    unittest.main()
