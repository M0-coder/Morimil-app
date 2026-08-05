from __future__ import annotations

import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

from android_authored_coverage_v1 import (
    CoveragePolicyError,
    SCHEMA_VERSION,
    build_summary,
    main,
)


def add_counter(element: ET.Element, counter_type: str, missed: int, covered: int) -> None:
    ET.SubElement(
        element,
        "counter",
        {
            "type": counter_type,
            "missed": str(missed),
            "covered": str(covered),
        },
    )


def write_report(path: Path, *, root_line_missed: int = 11) -> None:
    report = ET.Element("report", {"name": "debug"})
    package = ET.SubElement(report, "package", {"name": "com/example"})

    authored = ET.SubElement(package, "sourcefile", {"name": "Authored.kt"})
    add_counter(authored, "INSTRUCTION", 2, 8)
    add_counter(authored, "BRANCH", 1, 3)
    add_counter(authored, "LINE", 1, 3)

    generated = ET.SubElement(package, "sourcefile", {"name": "ExampleDao_Impl.kt"})
    add_counter(generated, "INSTRUCTION", 20, 0)
    add_counter(generated, "BRANCH", 4, 0)
    add_counter(generated, "LINE", 10, 0)

    add_counter(report, "INSTRUCTION", 22, 8)
    add_counter(report, "BRANCH", 5, 3)
    add_counter(report, "LINE", root_line_missed, 3)

    ET.ElementTree(report).write(path, encoding="utf-8", xml_declaration=True)


class AndroidAuthoredCoverageV1Test(unittest.TestCase):
    def test_excludes_absent_generated_source_and_preserves_raw(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            report = root / "report.xml"
            source_root = root / "app/src/main"
            source_root.mkdir(parents=True)
            write_report(report)

            summary = build_summary(report, source_root, True)

            self.assertEqual(SCHEMA_VERSION, summary["schemaVersion"])
            self.assertEqual(2, summary["inventory"]["sourceFilesRaw"])
            self.assertEqual(1, summary["inventory"]["sourceFilesIncluded"])
            self.assertEqual(1, summary["inventory"]["sourceFilesExcludedGenerated"])
            self.assertEqual(14, summary["raw"]["LINE"]["total"])
            self.assertEqual(
                4,
                summary["authoredSourceView"]["counters"]["LINE"]["total"],
            )
            self.assertEqual(
                "com/example/ExampleDao_Impl.kt",
                summary["excludedGenerated"]["sources"][0]["logicalPath"],
            )

    def test_fails_closed_when_generated_name_exists_in_authored_root(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            report = root / "report.xml"
            source_root = root / "app/src/main"
            generated = source_root / "java/com/example/ExampleDao_Impl.kt"
            generated.parent.mkdir(parents=True)
            generated.write_text("package com.example\n", encoding="utf-8")
            write_report(report)

            with self.assertRaisesRegex(
                CoveragePolicyError,
                "Generated-name source exists in authored roots",
            ):
                build_summary(report, source_root, True)

    def test_fails_when_source_totals_do_not_match_root(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            report = root / "report.xml"
            source_root = root / "app/src/main"
            source_root.mkdir(parents=True)
            write_report(report, root_line_missed=12)

            with self.assertRaisesRegex(
                CoveragePolicyError,
                "Sourcefile totals do not match root LINE counter",
            ):
                build_summary(report, source_root, True)

    def test_cli_writes_json_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            report = root / "report.xml"
            source_root = root / "app/src/main"
            json_output = root / "build/quality/authored.json"
            markdown_output = root / "build/quality/authored.md"
            source_root.mkdir(parents=True)
            write_report(report)

            exit_code = main(
                [
                    "--report",
                    str(report),
                    "--source-root",
                    str(source_root),
                    "--json-output",
                    str(json_output),
                    "--markdown-output",
                    str(markdown_output),
                    "--require-generated-exclusion",
                ]
            )

            self.assertEqual(0, exit_code)
            self.assertTrue(json_output.is_file())
            self.assertTrue(markdown_output.is_file())
            self.assertIn(
                "Android authored-source coverage summary",
                markdown_output.read_text(encoding="utf-8"),
            )


if __name__ == "__main__":
    unittest.main()
