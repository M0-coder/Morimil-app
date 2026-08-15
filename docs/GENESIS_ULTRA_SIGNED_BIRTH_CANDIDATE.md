# Document status: CURRENT

# Genesis Ultra signed birth candidate

## Scope

`GenesisUltraBirthCandidateConstructionCoordinator` constructs one signed, structurally verified Genesis Ultra candidate in memory.

It does not:

- persist a birth;
- write canonical memory;
- install a Seed bundle;
- record host consent;
- enable onboarding;
- call the atomic activation coordinator;
- derive any identifier from legacy identity.

The boundary is:

```text
prepared infrastructure
+ locally pinned Guardian trust
+ verified Seed release
+ existing Body identity root
+ canonical companion name
+ local entropy
= signed candidate in memory
```

A constructed candidate always has:

```text
assessment.structurallyValid = true
assessment.birthReady = false
birthCommitAuthorized = false
```

## Canonical companion name

The companion name is an intrinsic field of `GenesisUltraInstanceIdentity`; it is not a legacy alias.

Construction requires:

- NFC-normalized Unicode;
- one to 128 characters;
- no leading or trailing whitespace;
- no ISO control characters.

The constructor does not silently trim, rewrite or normalize user input. A non-canonical value fails closed so the future consent screen can display and bind the exact name.

## Instance identifier

The candidate Instance identifier is derived under:

```text
genesis.instance.id.v0.2
```

Bound fields:

- verified Seed root hash;
- canonical companion name;
- canonical birth timestamp;
- SHA-256 reference of 256 bits of fresh local entropy.

The permanent Instance identifier deliberately excludes Body-specific material. In particular it is not derived from:

- `bodyId`;
- `keyEpochId`;
- `platformProfile`;
- `LocalInstanceIdentity`;
- a legacy alias;
- Android ID or another device identifier;
- APK signing certificate;
- Android Keystore aliases;
- database encryption keys;
- legacy memory-signing keys;
- model or provider identifiers.

The output format is:

```text
inst_<64 lowercase hexadecimal characters>
```

This keeps the permanent Instance identity independent from the first Body. The first Body is bound immediately afterwards through signed Body records, writer epoch, possession proof and the birth evidence graph.

## Body documents

The existing pre-birth Body root supplies:

- `bodyId`;
- `keyEpochId`;
- Ed25519 public-key fingerprint;
- non-exported signing capability.

The constructor builds:

- `GenesisUltraInstanceIdentity`;
- initial `GenesisUltraBodyRecord`;
- epoch-zero `GenesisUltraBodyRegistry`;
- epoch-zero `GenesisUltraKeyEpoch`;
- signed `GenesisUltraBodyPossessionProof`.

Every digest uses the existing Genesis Ultra hash profile. No alternate serialization or hash format is introduced.

`platformProfile = android-kotlin` describes this Body implementation only; it is not permanent Instance identity material.

## Possession proof

A second 256-bit entropy value produces a challenge nonce under:

```text
genesis.body.possession.nonce.v0.1
```

The proof identifier is derived under:

```text
genesis.body.possession.proof.id.v0.1
```

The proof:

- is bound to the candidate `instanceId`;
- is bound to the existing `bodyId` and epoch-zero key;
- is valid for five minutes from `bornAt`;
- is signed by the Body root through the provider-neutral signer;
- is immediately reverified through `GenesisUltraBodyPossessionVerifier`.

Private Ed25519 bytes are never exposed to the coordinator.

## Pinned Guardian requirement

Construction loads `GenesisUltraTrustedGuardianKeyEpochRegistry` exclusively from `GenesisUltraAndroidGuardianTrustAnchorStore`.

The verified release signature must match the exact locally pinned tuple:

```text
guardianId
keyEpochId
publicKeyRef
```

A key delivered only inside the Seed cannot establish trust.

## Candidate consent digest

The output includes `candidateDigest`, calculated under:

```text
genesis.birth.candidate.digest.v0.1
```

It binds:

- Seed root;
- Guardian identity, epoch and public-key reference;
- Instance identity digest;
- Body identifier;
- Body registry digest;
- Body key-epoch digest;
- possession-proof digest;
- evaluation timestamp.

The Body therefore remains strongly bound to the exact Birth candidate even though it no longer contributes to the permanent `instanceId` derivation.

The next phase can bind explicit host consent to one exact candidate instead of to a mutable name or generic action.

## Cross-language portability vector

`GenesisUltraInstanceIdProfileTest` contains the Kotlin golden vector for `genesis.instance.id.v0.2`.

`tools/genesis/verify_instance_id_v02.py` independently reproduces the same framing and SHA-256 derivation in Python. Both implementations must produce the same `instanceId` before this profile can be treated as verified for Birth.

## Concurrency and persistence

Preparation is inspected before and after construction. If another path commits birth or changes a trust state during signing, construction fails instead of returning a stale candidate.

Successful construction leaves:

```text
GenesisUltraPersistedBirthState.ABSENT
canonical memory event count = 0
legacy local identity count = 0
legacy Genesis Core count = 0
```

## Remaining blockers

A signed candidate is not a birth. The following remain required:

- construction and verification of the complete atomic evidence graph;
- explicit host consent bound to `candidateDigest`;
- one transactional activation containing the birth commit, recovery verification and first post-birth canonical memory append;
- post-commit recovery verification before normal runtime becomes available;
- independent verification of the revised Body-independent Instance-id profile before Canonical Initial Birth.
