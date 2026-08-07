from pathlib import Path
import hashlib
import os
import re
import unittest
import urllib.request

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

    @unittest.skipUnless(os.environ.get("GITHUB_ACTIONS") == "true", "CI-only checksum probe")
    def test_ci_probe_jacoco_runtime_checksum_against_maven_central_sha1(self):
        base = "https://repo.maven.apache.org/maven2/org/jacoco/org.jacoco.agent/0.8.11"
        jar_url = f"{base}/org.jacoco.agent-0.8.11-runtime.jar"
        sha1_url = f"{jar_url}.sha1"
        with urllib.request.urlopen(jar_url, timeout=30) as response:
            jar = response.read()
        with urllib.request.urlopen(sha1_url, timeout=30) as response:
            published_sha1 = response.read().decode("ascii").strip()
        self.assertEqual(len(jar), 300661)
        self.assertRegex(published_sha1, r"^[0-9a-f]{40}$")
        self.assertEqual(hashlib.sha1(jar).hexdigest(), published_sha1)
        sha256 = hashlib.sha256(jar).hexdigest()
        self.assertRegex(sha256, r"^[0-9a-f]{64}$")
        print(f"QA6_JACOCO_RUNTIME_SHA256={sha256}")


if __name__ == "__main__":
    unittest.main()
