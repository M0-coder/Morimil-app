from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("android_mutation_report_v1.py")
SPEC = importlib.util.spec_from_file_location("android_mutation_report_v1", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)

EXPECTED_CLASS = "com.morimil.app.data.genesis.GenesisManifestVerifierCore"
EXPECTED_SOURCE = "GenesisManifestVerifier.kt"


def mutation_xml(
    *,
    status: str = "KILLED",
    detected: str = "true",
    mutated_class: str = EXPECTED_CLASS,
    source_file: str = EXPECTED_SOURCE,
    line_number: int = 42,
    killing_test: str = "GenesisManifestVerifierCoreTest.verifiesValidInMemoryBundle",
) -> str:
    return f"""<?xml version='1.0' encoding='UTF-8'?>
<mutations partial='true'>
  <mutation detected='{detected}' status='{status}' numberOfTestsRun='1'>
    <sourceFile>{source_file}</sourceFile>
    <mutatedClass>{mutated_class}</mutatedClass>
    <mutatedMethod>verify</mutatedMethod>
    <methodDescription>()V</methodDescription>
    <lineNumber>{line_number}</lineNumber>
    <mutator>org.pitest.mutationtest.engine.gregor.mutators.ConditionalsBoundaryMutator</mutator>
    <killingTest>{killing_test}</killingTest>
    <description>changed conditional boundary</description>
  </mutation>
</mutations>
"""


class AndroidMutationReportTest(unittest.TestCase):
    def analyze(self, xml: str):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "mutations.xml"
            report.write_text(xml, encoding="utf-8")
            return MODULE.analyze_report(report, EXPECTED_CLASS, EXPECTED_SOURCE)

    def test_summarizes_valid_report(self):
        summary = self.analyze(mutation_xml())

        self.assertEqual(1, summary["totals"]["generated"])
        self.assertEqual(1, summary["totals"]["detected"])
        self.assertEqual(1, summary["statuses"]["KILLED"])
        self.assertEqual(100.0, summary["totals"]["mutation_score_percent"])
        self.assertEqual(100.0, summary["totals"]["test_strength_percent"])

    def test_accepts_target_inner_class(self):
        summary = self.analyze(
            mutation_xml(mutated_class=EXPECTED_CLASS + "$Companion")
        )

        self.assertEqual(
            [EXPECTED_CLASS + "$Companion"],
            summary["scope"]["observed_classes"],
        )

    def test_rejects_empty_report(self):
        with self.assertRaisesRegex(MODULE.MutationReportError, "contains no mutants"):
            self.analyze("<mutations />")

    def test_rejects_unexpected_class(self):
        with self.assertRaisesRegex(
            MODULE.MutationReportError,
            "escaped the approved class boundary",
        ):
            self.analyze(
                mutation_xml(mutated_class="com.morimil.app.security.SecretVault")
            )

    def test_rejects_unknown_status(self):
        with self.assertRaisesRegex(MODULE.MutationReportError, "unknown status"):
            self.analyze(mutation_xml(status="MYSTERY", detected="false"))

    def test_rejects_invalid_detected_attribute(self):
        with self.assertRaisesRegex(
            MODULE.MutationReportError,
            "invalid detected attribute",
        ):
            self.analyze(mutation_xml(detected="yes"))

    def test_calculates_survivor_and_no_coverage_metrics(self):
        first = mutation_xml(status="SURVIVED", detected="false", killing_test="")
        second = mutation_xml(
            status="NO_COVERAGE",
            detected="false",
            line_number=43,
            killing_test="",
        )
        combined = first.replace("</mutations>", "") + second.split("<mutations partial='true'>", 1)[1]

        summary = self.analyze(combined)

        self.assertEqual(2, summary["totals"]["generated"])
        self.assertEqual(1, summary["statuses"]["SURVIVED"])
        self.assertEqual(1, summary["statuses"]["NO_COVERAGE"])
        self.assertEqual(0.0, summary["totals"]["mutation_score_percent"])
        self.assertEqual(0.0, summary["totals"]["test_strength_percent"])
        self.assertEqual(50.0, summary["totals"]["line_coverage_proxy_percent"])


if __name__ == "__main__":
    unittest.main()
