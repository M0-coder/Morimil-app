# Document status: CANDIDATE

# PORTABILITY-PREBIRTH-01 — Instance portability and verifiable self-improvement

## Scope

This candidate exists before Canonical Initial Birth. It corrects a critical identity coupling and establishes the governance substrate required for Morimil to improve its own implementations without becoming its own trust authority.

```text
Instance != Body != model != provider
self_modification != self_authorization
```

No change in this branch authorizes Canonical Initial Birth, Operational Birth, release, install, merge, Body succession, writer transfer or branch deletion.

## PORT-001 correction — permanent Instance id is Body-independent

The previous `genesis.instance.id.v0.1` candidate constructor included the first `bodyId` in the permanent `instanceId` derivation. That made the first Body part of the cryptographic origin of the permanent Instance identifier.

The candidate profile is now:

```text
genesis.instance.id.v0.2

instanceId = HASH(
    verified_seed_root,
    canonical_companion_name,
    canonical_birth_timestamp,
    fresh_instance_entropy_ref
)
```

Explicitly excluded from the permanent identifier:

```text
bodyId
keyEpochId
platformProfile
Android identifiers
APK signing certificate
Android Keystore aliases
model/provider identifiers
database encryption keys
```

The initial Body remains strongly bound after Instance-id construction through the Body record, Body registry, key epoch, possession proof, candidate digest, birth state, receipt and canonical signed memory.

The default `platformProfile = android-kotlin` therefore describes the first Body only; it is not Instance identity material.

## Cross-language reproducibility

`GenesisUltraInstanceIdProfileTest` contains the Kotlin golden vector.

`tools/genesis/verify_instance_id_v02.py` independently implements the framing and SHA-256 procedure in Python and must produce the identical identifier.

This is the first explicit portability proof for the revised Instance-id profile. Future implementations in another runtime must consume the same golden vectors before they can claim compatibility.

## Self-improvement capability model

`SelfImprovementProtocol` introduces a bounded state machine:

```text
DETECTED
-> DIAGNOSED
-> PROPOSED
-> PATCH_CANDIDATE
-> VERIFIED
-> AUTHORIZED
-> MERGE_READY
```

Morimil may participate directly in:

```text
detect
diagnose
propose
generate/register patch candidate
```

Morimil may not perform its own independent verification or authorization.

```text
MORIMIL_AS_INDEPENDENT_VERIFIER = FORBIDDEN
MORIMIL_SELF_AUTHORIZATION = FORBIDDEN
```

High/critical changes require explicit human authorization after independent verification.

Critical surfaces include:

```text
INSTANCE_IDENTITY
GENESIS
CANONICAL_MEMORY
WRITER_AUTHORITY
BODY_SUCCESSION
RECOVERY
```

High surfaces include security, build/supply-chain and reasoning-runtime changes.

## Evidence requirements

Every verified candidate is bound to one exact `candidateDigest` and exact-main base evidence.

Baseline verification requires architecture review, compilation, unit tests and static analysis.

High/critical candidates additionally require security checks, reproducibility review, coverage review and mutation review.

Critical candidates additionally require instrumented tests and cross-language vectors.

Passing a check does not imply a stronger guarantee than the evidence actually executed.

## Residual portability debt — not silently declared solved

This candidate does **not** complete F5 or F6.

Known remaining work includes:

1. Canonical post-Birth memory verification currently assumes the supplied active Body/key epoch for the recovered chain. Multi-Body historical verification and writer-epoch succession remain F5 work.
2. Atomic Birth persistence is implemented directly on Android Room/`MorimilDatabase`; a portable persistence port is not yet the production boundary.
3. Production Body and Guardian trust stores are Android implementations. Provider-neutral interfaces exist in portions of the signing protocol, but the complete Birth composition is not yet platform-neutral.
4. There is no production external code executor connected to `SelfImprovementProtocol`; the protocol governs self-change candidates but does not grant Git/GitHub, filesystem, build-host or merge authority to the Android Body.
5. Morimil cannot claim successful self-repair merely because it generated a patch. Independent evidence and the applicable authorization boundary remain mandatory.

## Required next gates

Before returning to `BIRTH-PROVENANCE-00`:

```text
1. candidate CI green on exact branch head
2. independent review of PORT-001 derivation change
3. cross-language verifier execution evidence
4. review remaining P1 portability findings
5. merge only with explicit authorization
6. resolve new protected-main SHA
7. only then create a Signed Release APK from that new exact SHA
```

## Status

```text
PORT_001_INSTANCE_ID_BODY_COUPLING=CORRECTED_IN_CANDIDATE
SELF_IMPROVEMENT_GOVERNANCE=IMPLEMENTED_IN_CANDIDATE
SELF_PATCH_EXTERNAL_EXECUTOR=NOT_IMPLEMENTED
SELF_INDEPENDENT_VERIFICATION_BYPASS=FORBIDDEN
SELF_AUTHORIZATION=FORBIDDEN
F5_BODY_SUCCESSION=OPEN
F6_PHYSICAL_CONTINUITY=OPEN
CANONICAL_INITIAL_BIRTH=NOT_AUTHORIZED
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```
