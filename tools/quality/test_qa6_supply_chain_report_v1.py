from __future__ import annotations

import tempfile
import unittest
import zipfile
from pathlib import Path

import qa6_supply_chain_report_v1 as qa6


class Qa6SupplyChainReportTest(unittest.TestCase):
    def test_maven_purl_encodes_coordinate(self) -> None:
        self.assertEqual(
            "pkg:maven/com/example/demo@1.2.3",
            qa6.maven_purl("com.example", "demo", "1.2.3"),
        )

    def test_normalize_components_joins_artifacts(self) -> None:
        inventory = {
            "schema": "morimil.qa6.gradle-resolved-inventory.v1",
            "uniqueComponents": [
                {
                    "group": "com.example",
                    "name": "demo",
                    "version": "1.2.3",
                    "coordinate": "com.example:demo:1.2.3",
                }
            ],
            "uniqueArtifacts": [
                {
                    "group": "com.example",
                    "name": "demo",
                    "version": "1.2.3",
                    "coordinate": "com.example:demo:1.2.3",
                    "classifier": "",
                    "extension": "jar",
                    "fileName": "demo-1.2.3.jar",
                    "size": 3,
                    "sha256": "a" * 64,
                }
            ],
        }
        components = qa6.normalize_components(inventory)
        self.assertEqual(1, len(components))
        self.assertEqual("com.example:demo", components[0]["packageName"])
        self.assertEqual(1, len(components[0]["artifacts"]))

    def test_apk_inventory_hashes_entries_and_native_libraries(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "app-debug.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("AndroidManifest.xml", b"manifest")
                archive.writestr("resources.arsc", b"resources")
                archive.writestr("classes.dex", b"dex")
                archive.writestr("lib/arm64-v8a/libdemo.so", b"native")
            inventory = qa6.build_apk_inventory(apk)
        self.assertEqual(4, inventory["entryCount"])
        self.assertEqual(1, len(inventory["dexFiles"]))
        self.assertEqual(1, len(inventory["nativeLibraries"]))

    def test_cvss31_reference_vector(self) -> None:
        score = qa6.cvss3_base_score(
            "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"
        )
        self.assertEqual(9.8, score)

    def test_classification_uses_cvss(self) -> None:
        severity, score, source = qa6.classify_vulnerability(
            {
                "severity": [
                    {
                        "type": "CVSS_V3",
                        "score": "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H",
                    }
                ]
            }
        )
        self.assertEqual("CRITICAL", severity)
        self.assertEqual(9.8, score)
        self.assertEqual("severity[CVSS_V3]", source)

    def test_validate_structure_reports_missing_component(self) -> None:
        failures = qa6.validate_structure(
            components=[],
            apk_inventory={
                "entries": [
                    {"path": "AndroidManifest.xml"},
                    {"path": "resources.arsc"},
                    {"path": "classes.dex"},
                ],
                "dexFiles": [{"path": "classes.dex"}],
            },
            policy={
                "expected_runtime_modules": ["com.example:missing"],
                "expected_test_modules": [],
                "required_apk_entries": ["AndroidManifest.xml", "resources.arsc"],
                "required_apk_prefixes": ["classes"],
            },
            licenses={"queryErrorCount": 0},
            vulnerabilities={"vulnerabilities": []},
            mode="baseline",
            adjudications={},
        )
        self.assertEqual(
            ["expected_runtime_modules missing: ['com.example:missing']"],
            failures,
        )

    def test_strict_mode_rejects_unadjudicated_critical(self) -> None:
        failures = qa6.validate_structure(
            components=[],
            apk_inventory={
                "entries": [
                    {"path": "AndroidManifest.xml"},
                    {"path": "resources.arsc"},
                    {"path": "classes.dex"},
                ],
                "dexFiles": [{"path": "classes.dex"}],
            },
            policy={
                "expected_runtime_modules": [],
                "expected_test_modules": [],
                "required_apk_entries": ["AndroidManifest.xml", "resources.arsc"],
                "required_apk_prefixes": ["classes"],
            },
            licenses={"queryErrorCount": 0},
            vulnerabilities={
                "vulnerabilities": [
                    {"id": "OSV-TEST-1", "severity": "CRITICAL"}
                ]
            },
            mode="strict",
            adjudications={"acceptedVulnerabilityIds": []},
        )
        self.assertEqual(
            ["Unadjudicated critical vulnerabilities: ['OSV-TEST-1']"],
            failures,
        )


if __name__ == "__main__":
    unittest.main()
