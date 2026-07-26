import importlib.util
import sys
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("verify_version_policy.py")
SPEC = importlib.util.spec_from_file_location("verify_version_policy", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def build_gradle(version_name: str, version_code: int = 8) -> str:
    return f'''
android {{
    defaultConfig {{
        versionCode = {version_code}
        versionName = "{version_name}"
    }}
}}
'''


def policy_document(version_code: int = 8) -> str:
    return f"""
# Document status: CURRENT

`{MODULE.EXPECTED_VERSION_NAME}`
`versionCode`: `{version_code}`
monotonically increasing Android deployment counter
instanceId
"""


class VersionPolicyTest(unittest.TestCase):
    def test_accepts_current_honest_version(self) -> None:
        declaration = MODULE.validate(
            build_gradle(MODULE.EXPECTED_VERSION_NAME),
            policy_document(),
        )
        self.assertEqual(MODULE.EXPECTED_VERSION_NAME, declaration.name)
        self.assertEqual(8, declaration.code)

    def test_rejects_maturity_inflation(self) -> None:
        with self.assertRaisesRegex(MODULE.VersionPolicyError, "versionName mismatch"):
            MODULE.validate(
                build_gradle("0.8.0-phase5d"),
                policy_document(),
            )

    def test_rejects_version_code_rollback(self) -> None:
        with self.assertRaisesRegex(MODULE.VersionPolicyError, "remain monotonic"):
            MODULE.validate(
                build_gradle(MODULE.EXPECTED_VERSION_NAME, version_code=7),
                policy_document(version_code=7),
            )

    def test_rejects_undocumented_policy(self) -> None:
        with self.assertRaisesRegex(
            MODULE.VersionPolicyError,
            "missing required declarations",
        ):
            MODULE.validate(
                build_gradle(MODULE.EXPECTED_VERSION_NAME),
                "# Document status: CURRENT\n",
            )


if __name__ == "__main__":
    unittest.main()
