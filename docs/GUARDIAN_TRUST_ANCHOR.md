# Document status: CURRENT

# Genesis Ultra Guardian trust anchor

Status: storage and verification boundary implemented; production anchor not provisioned.

## Role boundary

The Guardian is a custodian and cryptographic witness. The Guardian is not the owner of Morimil, the Android Body, the intrinsic engines or canonical memory.

```text
Guardian public key != Morimil identity
Guardian public key != Body identity
Guardian public key != Android release certificate
Guardian public key != database encryption key
```

The trust anchor authorizes verification of Guardian-signed Genesis Ultra evidence. It does not grant ownership, unrestricted operational authority, memory-write authority or the right to replace Morimil's identity.

## Threat being closed

A signature is meaningful only when its verification key is already trusted independently of the signed package. Accepting a Guardian key contained inside the same Seed bundle would create trust-on-first-use controlled by that bundle.

The Android adapter therefore requires a durable local pin before it can verify a Seed release through the production path.

```text
Seed package
  X cannot supply its own trusted Guardian key

out-of-band confirmed Guardian fingerprint
  + exact guardian_id
  + exact key_epoch_id
  + RAW Ed25519 public key
  + custody purpose
  -> one local trust anchor
```

## Provisioning input

`GenesisUltraGuardianTrustAnchorProvisioningRequest` requires:

- `guardianId`;
- `keyEpochId`;
- a RAW 32-byte Ed25519 public key;
- its independently confirmed `sha256:<64 lowercase hex>` fingerprint;
- purpose exactly `birth_witness_and_recovery_custody`.

The implementation recomputes the fingerprint from the supplied key. A copied, mistyped or substituted fingerprint is rejected before any local key material is created.

The future onboarding UI must show the full fingerprint and require confirmation obtained through a channel independent of the Seed package. Merely displaying a fingerprint extracted from that package is not independent confirmation.

## Pin-once behavior

`GenesisUltraAndroidGuardianTrustAnchorStore.provisionBeforeBirth()` is legal only while the durable Genesis Ultra birth state is `ABSENT`.

The first valid request creates one active trusted Guardian epoch. Repeating the exact same request is idempotent. Any request for a different Guardian, epoch or public key is rejected.

There is intentionally no general replacement or rotation API in this phase. Rotation belongs to the later recovery and revocation protocol and must preserve an auditable chain of authority.

## Durable protection

The anchor contains public information, but unauthorized substitution would transfer trust. The complete anchor record is therefore encrypted and authenticated with AES-256-GCM through a dedicated Android Keystore key.

Default storage identifiers:

```text
preferences = genesis_ultra_guardian_trust_anchor_v1
record      = guardian_trust_anchor
KEK alias   = com.morimil.app.genesis.ultra.guardian.trust.anchor.kek.v1
```

The record is independent from:

- the Body identity KEK;
- the Body Ed25519 key;
- SQLCipher passphrases;
- the legacy memory-event signer;
- the Android APK/AAB signing certificate.

## State machine

```text
ABSENT
  no encrypted record
  no Android Keystore KEK

READY
  one authenticated record
  matching KEK exists
  record decrypts and validates

INCONSISTENT
  only one component exists
  decryption fails
  fields or digest changed
  key was lost
  record was damaged
```

`loadExisting()` never creates, regenerates or replaces trust material. An inconsistent state fails closed.

## Verification path

`loadExistingRegistry()` constructs `GenesisUltraTrustedGuardianKeyEpochRegistry` only from the authenticated local anchor.

`verifyRelease(bundle)` then invokes the existing Genesis Ultra verifier with that registry. This closes the production boundary:

```text
local authenticated trust anchor
  -> exact Guardian tuple
  -> Ed25519 signature verifier
  -> verified Seed release
```

A release signed by another Guardian, another epoch or another key remains untrusted even when its internal files and hashes are otherwise valid.

## Tests

Managed Android tests on API 30 and API 35 cover:

- initial `ABSENT` state;
- one-time confirmed pinning;
- exact reload after store reconstruction;
- trusted-registry construction;
- rejection of mismatched out-of-band fingerprints;
- rejection of replacement Guardian epochs;
- ciphertext tamper detection;
- Android Keystore loss handling;
- absence of trust-on-first-use during load;
- absence of the RAW public key in the outer preferences record.

## Not completed by this phase

This phase does not:

- include or provision a production Guardian public key;
- define the user-facing fingerprint confirmation screen;
- rotate, revoke or recover a Guardian epoch;
- build a Genesis Ultra birth candidate;
- activate onboarding or birth;
- confer ownership on the Guardian;
- activate deliberative or metacognitive engines.

The production fingerprint and its independent confirmation ceremony remain deployment inputs. Birth must stay closed until the Body root, Guardian anchor and verified Seed are composed atomically by the real Genesis Ultra coordinator.
