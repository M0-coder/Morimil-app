# Document status: CURRENT

# Genesis Ultra pre-birth provisioning ceremony

## Purpose

This boundary makes the two existing local security stores reachable from the
production onboarding UI without weakening their fail-closed rules.

```text
BODY_IDENTITY_REQUIRED
  -> explicit local-presence Body ceremony
  -> GUARDIAN_TRUST_REQUIRED
  -> RAW Guardian public key + independent fingerprint confirmation
  -> READY_FOR_SIGNED_CANDIDATE
```

It prepares the first Android Body. It does not construct an Instance, accept a
Seed, authorize birth, commit Genesis, open the runtime, or activate an
intrinsic motor.

## Single production facade

`GenesisUltraPreBirthProvisioningCoordinator` is the only facade exposed to
onboarding for these mutations. Before and after each action it re-runs
`GenesisUltraBirthPreparationCoordinator.inspect()` against durable state.

The Body action is legal only from:

```text
BODY_IDENTITY_REQUIRED
```

The Guardian action is legal only from:

```text
GUARDIAN_TRUST_REQUIRED
```

Legacy conflicts, orphan canonical memory, inconsistent stores, committed
birth, stale UI, and every other status fail before mutation.

## Body ceremony

The user must explicitly confirm local presence and select the action that
creates the Body root. The facade then calls the existing one-time
`GenesisUltraAndroidBodyIdentityRootStore.provisionBeforeBirth()` boundary.

The UI never receives private-key material. It receives only a reconstructable
receipt containing:

```text
bodyId
keyEpochId
publicKeyRef
protectionProfile
receiptDigest
```

The receipt is derived from the authenticated store and can be reconstructed
after process restart. It is not a second authority record.

## Guardian ceremony

The selected document must be exactly 32 bytes: one RAW Ed25519 public key.
PEM, JSON, certificates, Seed archives, files shorter than 32 bytes, and files
longer than 32 bytes are rejected.

The selected bytes remain in process memory only. Compose UI state receives
only the calculated `sha256:<64 lowercase hex>` fingerprint.

Pinning additionally requires:

- exact `guardianId`;
- exact `keyEpochId`;
- the full fingerprint obtained through a channel independent of the key file
  and Seed;
- an explicit acknowledgement of that independent confirmation;
- explicit local-presence confirmation.

The store recomputes the fingerprint before creating any Android Keystore
material. A mismatch fails before pinning.

The reconstructable Guardian receipt contains:

```text
guardianId
keyEpochId
publicKeyRef
anchorDigest
pinnedAtMillis
receiptDigest
```

## Process-death and retry behavior

- A selected but unpinned Guardian key is discarded on process death.
- A Body root or Guardian anchor that committed before a process death is
  recovered from its authenticated store on the next inspection.
- The UI does not retry either mutation automatically.
- Re-entering the Body action after the state advanced is rejected.
- The Guardian store still accepts only an exact idempotent request and rejects
  a replacement epoch.

## Authority boundary

```text
body provisioning != Instance birth
Guardian pinning != ownership
provisioning receipt != canonical memory
READY_FOR_SIGNED_CANDIDATE != birth authorization
```

The coordinator has no reference to the activation coordinator, atomic
execution coordinator, legacy birth path, reasoning runtime, Recall, RestCycle,
or model installation.

## Verification

JVM tests cover ceremony validation, state gating, deterministic receipts,
wrong fingerprints, exact RAW key length, replay rejection, and the absence of
raw key bytes from UI state.

Managed Android tests on API 30 and API 35 execute the complete clean-install
transition through the real Tink and Android Keystore stores and verify that
birth, legacy identity, Genesis Core, and canonical memory remain absent.
