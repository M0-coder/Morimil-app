from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import qa6_supply_chain_scope_v1 as scope


class Qa6ScopeTest(unittest.TestCase):
    def test_lockfile_parser(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "gradle.lockfile"
            path.write_text(
                "# header\ncom.example:demo:1.0=debugRuntimeClasspath,testRuntimeClasspath\nempty=foo\n",
                encoding="utf-8",
            )
            coordinates, configurations = scope.parse_lockfile(path)
        self.assertEqual({"com.example:demo:1.0"}, coordinates)
        self.assertEqual(
            {"debugRuntimeClasspath", "testRuntimeClasspath"}, configurations
        )

    def test_runtime_high_is_rejected(self) -> None:
        result = scope.validate(
            inventory={
                "uniqueComponents": [{"coordinate": "com.example:demo:1.0"}],
                "uniqueArtifacts": [
                    {
                        "coordinate": "com.example:demo:1.0",
                        "fileName": "demo.jar",
                        "sha256": "a" * 64,
                    }
                ],
                "configurations": [
                    {
                        "name": "debugRuntimeClasspath",
                        "components": [{"coordinate": "com.example:demo:1.0"}],
                    }
                ],
            },
            lock_coordinates={"com.example:demo:1.0"},
            lock_configurations={"debugRuntimeClasspath"},
            verification_records={
                ("com.example:demo:1.0", "demo.jar", "a" * 64)
            },
            vulnerabilities={
                "vulnerabilities": [{"id": "GHSA-TEST", "severity": "HIGH"}],
                "componentMatches": [
                    {
                        "coordinate": "com.example:demo:1.0",
                        "vulnerabilityIds": ["GHSA-TEST"],
                    }
                ],
            },
            licenses={"components": []},
            syft={
                "packages": [
                    {"name": "app.apk", "versionInfo": "sha256:" + "b" * 64}
                ]
            },
            apk={"apk": {"fileName": "app.apk", "sha256": "b" * 64}},
            policy={
                "runtime_configurations": ["debugRuntimeClasspath"],
                "severity_policy": {
                    "strict_runtime_block_levels": ["HIGH", "CRITICAL"]
                },
            },
            adjudications={
                "acceptedVulnerabilityIds": [],
                "licenseEvidenceOverrides": [],
            },
        )
        self.assertEqual(1, result["unadjudicatedRuntimeHighOrCritical"])
        self.assertTrue(result["failures"])

    def test_build_only_high_and_licensed_override_pass(self) -> None:
        result = scope.validate(
            inventory={
                "uniqueComponents": [{"coordinate": "com.example:demo:1.0"}],
                "uniqueArtifacts": [
                    {
                        "coordinate": "com.example:demo:1.0",
                        "fileName": "demo.jar",
                        "sha256": "a" * 64,
                    }
                ],
                "configurations": [
                    {
                        "name": "toolingClasspath",
                        "components": [{"coordinate": "com.example:demo:1.0"}],
                    }
                ],
            },
            lock_coordinates={"com.example:demo:1.0"},
            lock_configurations={"toolingClasspath", "debugRuntimeClasspath"},
            verification_records={
                ("com.example:demo:1.0", "demo.jar", "a" * 64)
            },
            vulnerabilities={
                "vulnerabilities": [{"id": "GHSA-TEST", "severity": "HIGH"}],
                "componentMatches": [
                    {
                        "coordinate": "com.example:demo:1.0",
                        "vulnerabilityIds": ["GHSA-TEST"],
                    }
                ],
            },
            licenses={
                "components": [
                    {
                        "coordinate": "com.example:demo:1.0",
                        "status": "NOASSERTION",
                    }
                ]
            },
            syft={
                "packages": [
                    {"name": "app.apk", "versionInfo": "sha256:" + "b" * 64}
                ]
            },
            apk={"apk": {"fileName": "app.apk", "sha256": "b" * 64}},
            policy={
                "runtime_configurations": ["debugRuntimeClasspath"],
                "severity_policy": {
                    "strict_runtime_block_levels": ["HIGH", "CRITICAL"]
                },
            },
            adjudications={
                "acceptedVulnerabilityIds": [],
                "licenseEvidenceOverrides": [
                    {
                        "coordinate": "com.example:demo:1.0",
                        "licenseExpression": "LicenseRef-Demo",
                        "source": "https://example.invalid/license",
                    }
                ],
            },
        )
        self.assertEqual([], result["failures"])
        self.assertEqual(0, result["runtimeVulnerabilityCount"])
        self.assertEqual(1, result["buildTestVulnerabilityCount"])


if __name__ == "__main__":
    unittest.main()
