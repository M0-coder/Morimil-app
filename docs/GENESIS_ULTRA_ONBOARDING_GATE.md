# Document status: CURRENT

# Genesis Ultra onboarding gate

## Purpose

The Android UI must not treat a legacy `LocalInstanceIdentity` row as proof that
Morimil has been born. Runtime navigation is derived only from the audited
Genesis Ultra preparation state.

```text
ALREADY_COMMITTED -> runtime
all other states  -> onboarding or blocked recovery state
```

## Pre-birth isolation

`MorimilApp` creates `GenesisUltraOnboardingViewModel` first. It does not create
`MorimilViewModel` until the durable state is `ALREADY_COMMITTED`. Therefore a
legacy identity cannot start chat, rest cycles, recall, orchestration or other
runtime initialization in the background.

## Canonical companion name

The screen asks for the canonical companion name, not a device alias. The draft
is local UI state only and is not persisted or treated as consent. It must be:

- Unicode NFC;
- trimmed without outer whitespace;
- 1 to 128 characters;
- free of control characters.

The name alone cannot construct or authorize a birth.

## Pre-birth provisioning

Before Seed selection, onboarding now owns two explicit local-presence
ceremonies:

1. create the first Android Body root only from `BODY_IDENTITY_REQUIRED`;
2. pin one Guardian epoch only from `GUARDIAN_TRUST_REQUIRED` after importing
   an exact 32-byte RAW key and matching its fingerprint against a separately
   confirmed value.

Both actions expose public reconstructable receipts, advance only through a
re-inspected durable state, and remain unable to authorize or commit birth.

## Legacy path

`MorimilChatCoordinator.bornInstance()` remains only as a fail-closed source and
binary compatibility boundary. It returns
`legacy_local_birth_path_disabled_use_genesis_ultra` and does not:

- install the legacy Genesis bundle;
- call `MemoryRepository.birthLocalIdentity()`;
- create `GenesisCore`;
- seed legacy memory.

## Signed Seed preview

When Body identity and the Guardian trust anchor are already prepared, the
onboarding screen can select a signed Seed ZIP. Android verifies the exact
manifest, payload digests, Seed root and detached Guardian signature, then
constructs an in-memory candidate bound to the canonical companion name.

Only a non-secret summary is displayed. Changing the name or rechecking local
state clears the preview. The archive and candidate are not persisted.

## Runtime boundary

The wider onboarding flow records candidate-bound host consent, verifies the
final Guardian/Body testimony, retains authorization only in process memory,
and requires a second local-presence ceremony before atomic execution. No
preparation or preview state opens runtime. Deliberative and metacognitive
motors remain blocked.
