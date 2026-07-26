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

## Remaining work

The onboarding gate still does not record host consent, receive the final
Guardian birth testimony, issue atomic-birth authorization or invoke atomic
activation. A verified preview cannot open runtime and the birth button remains
disabled. Deliberative and metacognitive motors remain blocked.
