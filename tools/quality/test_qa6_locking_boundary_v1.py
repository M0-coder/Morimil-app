from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class Qa6LockingBoundaryTest(unittest.TestCase):
    def test_production_locking_stays_strict_with_one_androidtest_runtime_exception(self):
        text = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn("lockAllConfigurations()", text)
        self.assertIn("lockMode = LockMode.STRICT", text)
        self.assertEqual(text.count("resolutionStrategy.deactivateDependencyLocking()"), 1)
        self.assertEqual(text.count('name == "debugAndroidTestRuntimeClasspath"'), 1)
        for production_name in (
            "debugRuntimeClasspath",
            "releaseRuntimeClasspath",
            "releaseCompileClasspath",
            "releaseUnsignedRuntimeClasspath",
        ):
            self.assertNotIn(f'name == "{production_name}"', text)

    def test_unit_coverage_locking_exception_is_exactly_ephemeral_jacoco_tooling(self):
        text = (ROOT / "tools/quality/android-unit-coverage.init.gradle").read_text(encoding="utf-8")
        match = re.search(r"configuration\.name in \[(.*?)\]", text)
        self.assertIsNotNone(match)
        names = set(re.findall(r'"([^"]+)"', match.group(1)))
        self.assertEqual(names, {"jacocoAgent", "jacocoAnt", "androidJacocoAnt"})
        self.assertEqual(text.count("deactivateDependencyLocking()"), 1)

    def test_instrumented_coverage_exception_is_exactly_runtime_and_jacoco_ant(self):
        text = (ROOT / "tools/quality/android-instrumented-coverage.init.gradle").read_text(
            encoding="utf-8"
        )
        self.assertIn("debug.enableAndroidTestCoverage = true", text)
        match = re.search(r"configuration\.name in \[(.*?)\]", text)
        self.assertIsNotNone(match)
        names = set(re.findall(r'"([^"]+)"', match.group(1)))
        self.assertEqual(names, {"debugRuntimeClasspath", "androidJacocoAnt"})
        self.assertEqual(text.count("deactivateDependencyLocking()"), 1)
        for forbidden in (
            "releaseRuntimeClasspath",
            "releaseCompileClasspath",
            "releaseUnsignedRuntimeClasspath",
            "debugAndroidTestRuntimeClasspath",
        ):
            self.assertNotIn(forbidden, text)


if __name__ == "__main__":
    unittest.main()
