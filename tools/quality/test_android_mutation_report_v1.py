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
PRIMARY_SOURCE = "GenesisManifestVerifier.kt"
INLINE_SOURCE = "Comparisons.kt"


def mutation_node(
    *,
    status: str = "KILLED",
    detected: str = "true",
    mutated_class: str = EXPECTED_CLASS,
    source_file: str = PRIMARY_SOURCE,
    mutated_method: str = "verify",
    line_number: int = 42,
    killing_test: str = "GenesisManifestVerifierCoreTest.verifiesValidInMemoryBundle",
) -> str:
    return f"""
  <mutation detected='{detected}' status='{status}' numberOfTestsRun='1'>
    <sourceFile>{source_file}</sourceFile>
    <mutatedClass>{mutated_class}</mutatedClass>
    <mutatedMethod>{mutated_method}</mutatedMethod>
    <methodDescription>()V</methodDescription>
    <lineNumber>{line_number}</lineNumber>
    <mutator>org.pitest.mutationtest.engine.gregor.mutators.ConditionalsBoundaryMutator</mutator>
    <killingTest>{killing_test}</killingTest>
    <description>changed conditional boundary</description>
  </mutation>"""


def report_xml(*mutations: str) -> str:
    return (
        "<?xml version='1.0' encoding='UTF-8'?>\n"
        "<mutations partial='true'>"
        + "".join(mutations)
        + "\n</mutations>\n"
    )


class AndroidMutationReportTest(unittest.TestCase):
    def analyze(self, xml: str, allowed_inline_sources=()):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "mutations.xml"
            report.write_text(xml, encoding="utf-8")
            return MODULE.analyze_report(
                report,
                EXPECTED_CLASS,
                PRIMARY_SOURCE,
                allowed_inline_sources,
            )

    def test_summarizes_valid_report(self):
        summary = self.analyze(report_xml(mutation_node()))

        self.assertEqual(1, summary["totals"]["generated"])
        self.assertEqual(1, summary["totals"]["detected"])
        self.assertEqual(1, summary["statuses"]["KILLED"])
        self.assertEqual(100.0, summary["totals"]["mutation_score_percent"])
        self.assertEqual(100.0, summary["totals"]["test_strength_percent"])
        self.assertEqual(
            "primary",
            summary["scope"]["source_attributions"][PRIMARY_SOURCE]["role"],
        )

    def test_accepts_target_inner_class(self):
        inner_class = EXPECTED_CLASS + "$ManifestFile"
        summary = self.analyze(
            report_xml(mutation_node(mutated_class=inner_class))
        )

        self.assertEqual([inner_class], summary["scope"]["observed_classes"])

    def test_accepts_reviewed_kotlin_inline_source_attribution(self):
        inline_class = EXPECTED_CLASS + "$verify$$inlined$sortedBy$1"
        summary = self.analyze(
            report_xml(
                mutation_node(),
                mutation_node(
                    mutated_class=inline_class,
                    source_file=INLINE_SOURCE,
                    mutated_method="compare",
                    line_number=102,
                ),
            ),
            allowed_inline_sources=(INLINE_SOURCE,),
        )

        attribution = summary["scope"]["source_attributions"][INLINE_SOURCE]
        self.assertEqual("reviewed_inline", attribution["role"])
        self.assertEqual(1, attribution["mutants"])
        self.assertEqual([102], attribution["lines"])
        self.assertEqual({inline_class: 1}, attribution["classes"])

    def test_rejects_unapproved_source_attribution(self):
        with self.assertRaisesRegex(
            MODULE.MutationReportError,
            "escaped the approved source-attribution boundary",
        ):
            self.analyze(
                report_xml(mutation_node(source_file="Collections.kt")),
                allowed_inline_sources=(INLINE_SOURCE,),
            )

    def test_requires_primary_source_attribution(self):
        with self.assertRaisesRegex(
            MODULE.MutationReportError,
            "did not contain the required primary source attribution",
        ):
            self.analyze(
                report_xml(
                    mutation_node(
                        mutated_class=EXPECTED_CLASS + "$verify$$inlined$sortedBy$1",
                        source_file=INLINE_SOURCE,
                        mutated_method="compare",
                    )
                ),
                allowed_inline_sources=(INLINE_SOURCE,),
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
                report_xml(
                    mutation_node(mutated_class="com.morimil.app.security.SecretVault")
                )
            )

    def test_rejects_unknown_status(self):
        with self.assertRaisesRegex(MODULE.MutationReportError, "unknown status"):
            self.analyze(
                report_xml(mutation_node(status="MYSTERY", detected="false"))
            )

    def test_rejects_invalid_detected_attribute(self):
        with self.assertRaisesRegex(
            MODULE.MutationReportError,
            "invalid detected attribute",
        ):
            self.analyze(report_xml(mutation_node(detected="yes")))

    def test_calculates_survivor_and_no_coverage_metrics(self):
        summary = self.analyze(
            report_xml(
                mutation_node(
                    status="SURVIVED",
                    detected="false",
                    killing_test="",
                ),
                mutation_node(
                    status="NO_COVERAGE",
                    detected="false",
                    line_number=43,
                    killing_test="",
                ),
            )
        )

        self.assertEqual(2, summary["totals"]["generated"])
        self.assertEqual(1, summary["statuses"]["SURVIVED"])
        self.assertEqual(1, summary["statuses"]["NO_COVERAGE"])
        self.assertEqual(0.0, summary["totals"]["mutation_score_percent"])
        self.assertEqual(0.0, summary["totals"]["test_strength_percent"])
        self.assertEqual(50.0, summary["totals"]["line_coverage_proxy_percent"])


if __name__ == "__main__":
    unittest.main()
