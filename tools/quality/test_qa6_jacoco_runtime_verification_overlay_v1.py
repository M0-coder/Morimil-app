from pathlib import Path
import hashlib
import tempfile
import unittest

from tools.quality import qa6_jacoco_runtime_verification_overlay_v1 as overlay

ROOT = Path(__file__).resolve().parents[2]


class Qa6JacocoRuntimeVerificationOverlayTest(unittest.TestCase):
    def test_exact_one_artifact_overlay_matches_frozen_hashes(self):
        canonical = (ROOT / "gradle/verification-metadata.xml").read_bytes()
        self.assertEqual(hashlib.sha256(canonical).hexdigest(), overlay.BASE_METADATA_SHA256)

        effective = overlay.build_effective_metadata(canonical)
        self.assertEqual(hashlib.sha256(effective).hexdigest(), overlay.EFFECTIVE_METADATA_SHA256)
        self.assertIn(overlay.JACOCO_RUNTIME_SHA256.encode("ascii"), effective)
        self.assertEqual(effective.count(b"org.jacoco.agent-0.8.11-runtime.jar"), 1)

    def test_apply_writes_only_expected_effective_metadata(self):
        canonical = (ROOT / "gradle/verification-metadata.xml").read_bytes()
        with tempfile.TemporaryDirectory() as tmp:
            metadata = Path(tmp) / "verification-metadata.xml"
            metadata.write_bytes(canonical)
            overlay.apply_overlay(metadata)
            self.assertEqual(
                hashlib.sha256(metadata.read_bytes()).hexdigest(),
                overlay.EFFECTIVE_METADATA_SHA256,
            )

    def test_overlay_rejects_unexpected_base(self):
        with self.assertRaisesRegex(ValueError, "audited QA-6 base"):
            overlay.build_effective_metadata(b"not-the-audited-metadata")


if __name__ == "__main__":
    unittest.main()
