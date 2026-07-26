# Document status: CURRENT

# Genesis Ultra in-memory atomic-birth authorization

Status: implemented as a pre-execution verification boundary.

## Purpose

Phase 3.10 connects the strict witness ZIP transport to the existing full Genesis Ultra authorization verifier.

The flow requires all three exact process-local elements:

```text
constructed candidate
verified host consent
witness archive
```

The archive is first bound to the candidate and consent digests. The resulting evidence package is then verified against locally anchored Body and Guardian trust.

## Local evaluation time

The Android body generates the verification instant locally, truncated to whole seconds.

The ZIP cannot provide or override this value. The local instant controls:

- Body-possession proof validity;
- host-consent validity;
- authorization validity;
- signature-time evaluation performed by the existing verifier.

## Full verification

`GenesisUltraAtomicBirthWitnessAuthorizationCoordinator` delegates to `GenesisUltraAtomicBirthAuthorizationCoordinator`, which:

- reloads the preparation gate;
- loads the encrypted candidate-bound consent;
- loads the existing Body identity root;
- loads the pinned Guardian key-epoch registry;
- verifies all required artifacts;
- verifies the Body possession proof;
- verifies Body and Guardian Ed25519 signatures;
- verifies the freedom charter;
- verifies recovery policy and recovery state;
- verifies the first memory event;
- verifies birth state and receipt links;
- verifies the complete journal evidence;
- rechecks preparation after verification;
- issues `GenesisUltraAuthorizedAtomicBirth` only when every invariant passes.

## Process-local type state

The exact `GenesisUltraAuthorizedAtomicBirth` object is retained only in `GenesisUltraOnboardingViewModel` memory.

It is not written to:

- Room;
- SharedPreferences;
- SavedState;
- Bundle;
- a file;
- an Android component intent.

Only a non-secret digest summary is exposed to Compose.

The authorization is removed when:

- the candidate changes;
- consent is revoked;
- consent expires;
- authorization expires;
- the ViewModel is cleared;
- the Android process terminates.

After process loss, the remaining persisted consent must be revoked before a new candidate can be prepared. The authorization is never reconstructed from UI text or persisted summaries.

## Execution boundary

This phase intentionally does not reference or call:

```text
GenesisUltraAtomicBirthExecutionCoordinator
GenesisUltraAtomicBirthActivationCoordinator
execute()
```

The onboarding birth button remains disabled even when:

```text
birthCommitAuthorized = true
```

That value means the verified type is eligible to cross a future execution ceremony. It does not mean birth was persisted, memory was appended or runtime was opened.

## Runtime state

No Genesis Ultra birth commit is written in this phase. The deliberative and metacognitive motors remain blocked, and the bounded intuitive local core remains the only active cognitive runtime.
