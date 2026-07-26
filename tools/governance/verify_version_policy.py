#!/usr/bin/env python3
"""Fail-closed verification for Morimil-app's public Android version."""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path


EXPECTED_VERSION_NAME = "0.3.1-prealpha.plan-v3"
MINIMUM_VERSION_CODE = 8
VERSION_NAME_PATTERN = re.compile(
    r'^\d+\.\d+\.\d+-prealpha\.plan-v\d+$'
)
GRADLE_VERSION_NAME_PATTERN = re.compile(
    r'^\s*versionName\s*=\s*"([^"]+)"\s*$',
    re.MULTILINE,
)
GRADLE_VERSION_CODE_PATTERN = re.compile(
    r"^\s*versionCode\s*=\s*(\d+)\s*$",
    re.MULTILINE,
)


class VersionPolicyError(ValueError):
    """Raised when the repository version contract is inconsistent."""


@dataclass(frozen=True)
class VersionDeclaration:
    name: str
    code: int


def parse_gradle_version(build_gradle: str) -> VersionDeclaration:
    name_matches = GRADLE_VERSION_NAME_PATTERN.findall(build_gradle)
    code_matches = GRADLE_VERSION_CODE_PATTERN.findall(build_gradle)
    if len(name_matches) != 1:
        raise VersionPolicyError(
            f"expected one versionName declaration, found {len(name_matches)}"
        )
    if len(code_matches) != 1:
        raise VersionPolicyError(
            f"expected one versionCode declaration, found {len(code_matches)}"
        )
    return VersionDeclaration(name=name_matches[0], code=int(code_matches[0]))


def validate(build_gradle: str, policy_document: str) -> VersionDeclaration:
    declaration = parse_gradle_version(build_gradle)
    if declaration.name != EXPECTED_VERSION_NAME:
        raise VersionPolicyError(
            "versionName mismatch: "
            f"expected {EXPECTED_VERSION_NAME!r}, found {declaration.name!r}"
        )
    if not VERSION_NAME_PATTERN.fullmatch(declaration.name):
        raise VersionPolicyError(
            f"versionName has an invalid pre-alpha format: {declaration.name!r}"
        )
    if declaration.code < MINIMUM_VERSION_CODE:
        raise VersionPolicyError(
            "versionCode must remain monotonic: "
            f"expected >= {MINIMUM_VERSION_CODE}, found {declaration.code}"
        )

    required_policy_tokens = (
        "# Document status: CURRENT",
        f"`{EXPECTED_VERSION_NAME}`",
        f"`versionCode`: `{declaration.code}`",
        "monotonically increasing Android deployment counter",
        "instanceId",
    )
    missing = [
        token for token in required_policy_tokens if token not in policy_document
    ]
    if missing:
        raise VersionPolicyError(
            "version policy is missing required declarations: "
            + ", ".join(repr(token) for token in missing)
        )
    return declaration


def repository_root() -> Path:
    return Path(__file__).resolve().parents[2]


def check_repository(root: Path) -> VersionDeclaration:
    build_gradle = (root / "app" / "build.gradle.kts").read_text(
        encoding="utf-8"
    )
    policy_document = (root / "docs" / "VERSION_POLICY.md").read_text(
        encoding="utf-8"
    )
    return validate(build_gradle, policy_document)


def main(argv: list[str]) -> int:
    if argv != ["check"]:
        print(
            "usage: python tools/governance/verify_version_policy.py check",
            file=sys.stderr,
        )
        return 2
    try:
        declaration = check_repository(repository_root())
    except (OSError, VersionPolicyError) as error:
        print(f"version policy check failed: {error}", file=sys.stderr)
        return 1
    print(
        "version policy check passed: "
        f"versionName={declaration.name} versionCode={declaration.code}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
