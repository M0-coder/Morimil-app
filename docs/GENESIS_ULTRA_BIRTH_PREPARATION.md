# Document status: CURRENT

# Genesis Ultra birth preparation boundary

Status: Phase 3.1 implemented as an inspection-only boundary.

## Purpose

`GenesisUltraBirthPreparationCoordinator` determines whether the Android body is structurally ready to begin constructing a signed Genesis Ultra birth candidate.

It does not construct an Instance, sign birth documents or commit a birth.

```text
preparation readiness != candidate validity != birth authorization != committed birth
```

## Inspected durable state

The coordinator reads:

- the atomic Genesis Ultra birth store;
- the pre-birth Body identity root store;
- the pinned Guardian trust-anchor store;
- legacy `LocalInstanceIdentity` count;
- legacy `GenesisCore` count;
- isolated Genesis Ultra canonical-memory event count.

It does not provision or mutate any of these stores.

## Statuses

### `INCONSISTENT`

Returned when any cryptographic store is inconsistent or when canonical Genesis Ultra memory events exist without a committed birth.

No candidate may be constructed.

### `ALREADY_COMMITTED`

A Genesis Ultra birth already exists. Candidate construction cannot be restarted.

### `LEGACY_CONFLICT`

Legacy local identity or Genesis Core rows exist. Genesis Ultra cannot commit over them and the coordinator reports the collision before candidate construction.

Legacy data is not deleted or rewritten by this boundary.

### `BODY_IDENTITY_REQUIRED`

The durable pre-birth Body identity root has not been provisioned.

### `GUARDIAN_TRUST_REQUIRED`

The Body root exists, but no independently confirmed Guardian public-key epoch is pinned.

### `READY_FOR_SIGNED_CANDIDATE`

The local security prerequisites are present:

- atomic birth state is `ABSENT`;
- no legacy identity or Genesis Core rows exist;
- no orphan canonical memory events exist;
- Body identity root is `READY`;
- Guardian trust anchor is `READY`.

This status sets:

```text
candidateConstructionReady = true
birthCommitAuthorized = false
```

The following requirements remain:

- verify a signed Seed release through the pinned Guardian anchor;
- confirm the canonical companion name;
- construct and verify the complete atomic-birth evidence graph;
- record explicit host consent for this exact candidate.

## Separation from the legacy path

The coordinator is not called from `MemoryRepository.birthLocalIdentity()` and does not install the legacy Genesis bundle.

The current legacy `bornInstance(alias)` path remains blocked. Future onboarding must call a dedicated Ultra workflow rather than adding Ultra behavior inside that function.

## Failure policy

Preparation fails closed:

- no automatic key creation;
- no automatic Guardian trust-on-first-use;
- no deletion of legacy data;
- no canonical-memory repair;
- no implicit consent;
- no birth commit.
