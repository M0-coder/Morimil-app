#!/usr/bin/env python3
"""Independent cross-language verifier for genesis.instance.id.v0.2."""

from __future__ import annotations

import hashlib
import unicodedata

DOMAIN = "genesis.instance.id.v0.2"
RELEASE_ROOT = "sha256:" + "1" * 64
COMPANION_NAME = "Morimil"
BORN_AT = "2026-08-14T20:30:00Z"
ENTROPY_REF = "sha256:" + "2" * 64
EXPECTED = "inst_d1c20f93ea1b5304e4bee85e8c9a1cd009fad01aeb79af5b2233f5e595451e0a"


def frame(value: str) -> bytes:
    if unicodedata.normalize("NFC", value) != value:
        raise ValueError("text_not_nfc")
    payload = value.encode("utf-8")
    return str(len(payload)).encode("ascii") + b":" + payload + b"\n"


def hash_fields(domain: str, fields: list[str]) -> str:
    preimage = frame(domain) + b"".join(frame(field) for field in fields)
    return "sha256:" + hashlib.sha256(preimage).hexdigest()


def derive_instance_id() -> str:
    digest = hash_fields(
        DOMAIN,
        [RELEASE_ROOT, COMPANION_NAME, BORN_AT, ENTROPY_REF],
    )
    return "inst_" + digest.removeprefix("sha256:")


def main() -> int:
    actual = derive_instance_id()
    if actual != EXPECTED:
        print(f"FAIL expected={EXPECTED} actual={actual}")
        return 1
    print(f"PASS genesis.instance.id.v0.2 {actual}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
