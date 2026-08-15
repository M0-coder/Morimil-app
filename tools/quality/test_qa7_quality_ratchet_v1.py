import json
import tempfile
import unittest
from pathlib import Path

from qa7_quality_ratchet_v1 import (
    COUNTERS,
    SCHEMA,
    QualityRatchetError,
    baseline_string_set,
    evaluate_instrumented,
    evaluate_jvm,
    fraction_at_least,
    lint_fingerprint,
    new_or_increased,
    normalize_repo_path,
    parse_kotlin_warnings,
)
import collections
import xml.etree.ElementTree as ET


def baseline():
    return {
        "schema": SCHEMA,
        "androidAuthored": {
            "counters": {name: {"covered": 1, "total": 2} for name in COUNTERS},
            "maxZeroLineCoverageSources": 2,
        },
        "python": {
            "statements": {"covered": 1, "total": 2},
            "branches": {"covered": 1, "total": 2},
        },
        "kotlinWarnings": {
            "maxTotal": 1,
            "fingerprints": [
                {"fingerprint": "file|app/src/main/A.kt|old warning", "count": 1}
            ],
        },
        "lint": {
            "maxErrors": 0,
            "maxWarnings": 1,
            "gradleDependencyCoordinates": ["g:a"],
            "warningFingerprints": [
                {"fingerprint": "GradleDependency|app/build.gradle.kts|g:a", "count": 1}
            ],
        },
        "instrumented": {
            "counters": {name: {"covered": 1, "total": 2} for name in COUNTERS},
            "maxZeroLineCoverageSources": 2,
        },
    }


def authored(covered=1, total=2, zero=2):
    return {
        "authoredSourceView": {
            "counters": {
                name: {"covered": covered, "total": total} for name in COUNTERS
            }
        },
        "inventory": {"zeroLineCoverageIncludedSources": zero},
    }


def python_coverage(covered=1, total=2):
    return {
        "totals": {
            "covered_lines": covered,
            "num_statements": total,
            "covered_branches": covered,
            "num_branches": total,
        }
    }


def lint_xml(
    message="A newer version of g:a than 1 is available: 2",
    severity="Warning",
    issue_id="GradleDependency",
):
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n<issues>'
        f'<issue id="{issue_id}" severity="{severity}" message="{message}">'
        '<location file="/home/runner/work/Morimil-app/Morimil-app/app/build.gradle.kts" />'
        "</issue></issues>"
    )


class QualityRatchetTests(unittest.TestCase):
    def test_fraction_equal_improve_regress(self):
        self.assertTrue(fraction_at_least(1, 2, 1, 2))
        self.assertTrue(fraction_at_least(3, 4, 1, 2))
        self.assertFalse(fraction_at_least(1, 3, 1, 2))

    def test_fraction_rejects_invalid_counters(self):
        with self.assertRaises(QualityRatchetError):
            fraction_at_least(3, 2, 1, 2)
        with self.assertRaises(QualityRatchetError):
            fraction_at_least(0, 0, 1, 2)

    def test_path_normalization(self):
        self.assertEqual(
            normalize_repo_path("file:///x/Morimil-app/app/src/main/A.kt"),
            "app/src/main/A.kt",
        )
        self.assertEqual(
            normalize_repo_path("/x/app/src/main/A.kt"),
            "app/src/main/A.kt",
        )

    def test_kotlin_warning_is_line_independent_and_multiset_aware(self):
        with tempfile.TemporaryDirectory() as temp:
            log = Path(temp) / "compile.log"
            log.write_text(
                "w: file:///x/Morimil-app/app/src/main/A.kt:1:2 old warning\n"
                "w: file:///x/Morimil-app/app/src/main/A.kt:99:7 old warning\n"
                "w: generic warning\n",
                encoding="utf-8",
            )
            warnings = parse_kotlin_warnings(log)
            self.assertEqual(warnings["file|app/src/main/A.kt|old warning"], 2)
            self.assertEqual(warnings["generic|generic warning"], 1)

    def test_gradle_dependency_fingerprint_ignores_latest_available_version(self):
        first = ET.fromstring(
            '<issue id="GradleDependency" severity="Warning" '
            'message="A newer version of g:a than 1 is available: 2">'
            '<location file="/x/Morimil-app/app/build.gradle.kts" /></issue>'
        )
        second = ET.fromstring(
            '<issue id="GradleDependency" severity="Warning" '
            'message="A newer version of g:a than 1 is available: 99">'
            '<location file="/x/Morimil-app/app/build.gradle.kts" /></issue>'
        )
        self.assertEqual(lint_fingerprint(first), lint_fingerprint(second))

    def test_gradle_dependency_unexpected_format_fails_closed(self):
        issue = ET.fromstring(
            '<issue id="GradleDependency" severity="Warning" message="changed">'
            '<location file="app/build.gradle.kts" /></issue>'
        )
        with self.assertRaises(QualityRatchetError):
            lint_fingerprint(issue)

    def test_baseline_string_set_is_sorted_unique_and_canonical(self):
        self.assertEqual(
            baseline_string_set(["a:a", "b:b"], "fixture"),
            {"a:a", "b:b"},
        )
        with self.assertRaises(QualityRatchetError):
            baseline_string_set(["b:b", "a:a"], "fixture")
        with self.assertRaises(QualityRatchetError):
            baseline_string_set(["a:a", "a:a"], "fixture")
        with self.assertRaises(QualityRatchetError):
            baseline_string_set([" a:a"], "fixture")

    def test_new_or_increased_detects_new_and_multiplicity(self):
        current = collections.Counter({"a": 3, "c": 1})
        allowed = collections.Counter({"a": 2, "b": 1})
        changed = new_or_increased(current, allowed)
        self.assertEqual({item["fingerprint"] for item in changed}, {"a", "c"})

    def evaluate_jvm_fixture(self, *, current_authored=None, current_python=None, kotlin=None, lint=None, base=None):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            kotlin_log = root / "compile.log"
            lint_path = root / "lint.xml"
            kotlin_log.write_text(
                kotlin
                or "w: file:///x/Morimil-app/app/src/main/A.kt:10:20 old warning\n",
                encoding="utf-8",
            )
            lint_path.write_text(lint or lint_xml(), encoding="utf-8")
            return evaluate_jvm(
                base or baseline(),
                current_authored or authored(),
                current_python or python_coverage(),
                kotlin_log,
                lint_path,
            )

    def test_jvm_equal_passes(self):
        result = self.evaluate_jvm_fixture()
        self.assertTrue(result["pass"], result["failures"])

    def test_existing_frozen_dependency_can_age_without_repository_regression(self):
        configured = baseline()
        configured["lint"]["warningFingerprints"] = []
        configured["lint"]["maxWarnings"] = 1
        result = self.evaluate_jvm_fixture(
            base=configured,
            lint=lint_xml(
                message="A newer version of g:a than 1 is available: 99"
            ),
        )
        self.assertTrue(result["pass"], result["failures"])
        self.assertEqual(result["lint"]["newOrIncreasedWarnings"], [])

    def test_new_dependency_coordinate_still_fails_when_it_is_already_outdated(self):
        configured = baseline()
        configured["lint"]["warningFingerprints"] = []
        configured["lint"]["maxWarnings"] = 1
        result = self.evaluate_jvm_fixture(
            base=configured,
            lint=lint_xml(
                message="A newer version of g:b than 1 is available: 2"
            ),
        )
        self.assertFalse(result["pass"])
        self.assertEqual(
            result["lint"]["newOrIncreasedWarnings"][0]["fingerprint"],
            "GradleDependency|app/build.gradle.kts|g:b",
        )

    def test_android_coverage_regression_fails(self):
        result = self.evaluate_jvm_fixture(current_authored=authored(1, 3))
        self.assertFalse(result["pass"])
        self.assertTrue(any("Android authored" in item for item in result["failures"]))

    def test_python_coverage_regression_fails(self):
        result = self.evaluate_jvm_fixture(current_python=python_coverage(1, 3))
        self.assertFalse(result["pass"])
        self.assertTrue(any("Python" in item for item in result["failures"]))

    def test_zero_coverage_file_growth_fails(self):
        result = self.evaluate_jvm_fixture(current_authored=authored(zero=3))
        self.assertFalse(result["pass"])

    def test_new_kotlin_warning_fails(self):
        result = self.evaluate_jvm_fixture(
            kotlin=(
                "w: file:///x/Morimil-app/app/src/main/A.kt:10:20 old warning\n"
                "w: new generic\n"
            )
        )
        self.assertFalse(result["pass"])
        self.assertTrue(result["kotlinWarnings"]["newOrIncreased"])

    def test_new_lint_warning_fails_even_below_total_ceiling(self):
        configured = baseline()
        configured["lint"]["maxWarnings"] = 2
        two_issues = (
            '<?xml version="1.0"?><issues>'
            '<issue id="GradleDependency" severity="Warning" '
            'message="A newer version of g:a than 1 is available: 5">'
            '<location file="app/build.gradle.kts"/></issue>'
            '<issue id="Other" severity="Warning" message="new">'
            '<location file="app/src/main/A.kt"/></issue>'
            "</issues>"
        )
        result = self.evaluate_jvm_fixture(base=configured, lint=two_issues)
        self.assertFalse(result["pass"])
        self.assertTrue(result["lint"]["newOrIncreasedWarnings"])

    def test_lint_error_fails(self):
        configured = baseline()
        configured["lint"]["maxWarnings"] = 0
        configured["lint"]["warningFingerprints"] = []
        result = self.evaluate_jvm_fixture(
            base=configured,
            lint=lint_xml(message="boom", severity="Error", issue_id="Fatal"),
        )
        self.assertFalse(result["pass"])
        self.assertTrue(any("Lint errors" in item for item in result["failures"]))

    def test_instrumented_equal_passes(self):
        current = {
            "report": {
                "counters": {
                    name: {"covered": 2, "total": 4} for name in COUNTERS
                }
            },
            "source_inventory": {"zero_line_coverage": 1},
        }
        self.assertTrue(evaluate_instrumented(baseline(), current)["pass"])

    def test_instrumented_regression_fails(self):
        current = {
            "report": {
                "counters": {
                    name: {"covered": 1, "total": 3} for name in COUNTERS
                }
            },
            "source_inventory": {"zero_line_coverage": 3},
        }
        result = evaluate_instrumented(baseline(), current)
        self.assertFalse(result["pass"])
        self.assertGreaterEqual(len(result["failures"]), 4)

    def test_wrong_schema_fails_closed(self):
        configured = baseline()
        configured["schema"] = "wrong"
        with self.assertRaises(QualityRatchetError):
            evaluate_instrumented(
                configured,
                {"report": {}, "source_inventory": {}},
            )


if __name__ == "__main__":
    unittest.main()
