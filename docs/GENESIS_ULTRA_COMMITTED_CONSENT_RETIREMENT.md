# Genesis Ultra committed-consent retirement

Status: implemented as post-commit maintenance. Atomic execution remains disconnected from onboarding.

## Problem

Host birth consent is intentionally short-lived and stored outside Room:

```text
SharedPreferences encrypted record
Android Keystore AES-256-GCM key
```

The atomic birth transaction persists:

- the verified birth graph;
- the durable consent-bound authorization;
- the commit marker;
- canonical memory sequence 1.

SharedPreferences and Android Keystore cannot participate in the same Room transaction. A process can therefore terminate after the birth commit but before the pre-birth consent residue is deleted.

Treating that process death as a failed birth would be incorrect because the Room transaction is already the durable source of truth.

## Source of authority

After commit, the encrypted consent record is no longer an authorization input.

The only authority used for retirement is:

```text
GenesisUltraAuthorizedBirthStateAudit
GenesisUltraDurableBirthAuthorization.consentDigest
```

The retirement coordinator first requires the authorized birth state to be `COMMITTED`. It then loads and validates the durable authorization against the committed birth artifacts and receipt.

Only after that audit may it remove the dedicated pre-birth namespace and key alias.

## Idempotent states

`GenesisUltraCommittedConsentRetirementCoordinator` returns:

```text
NOT_APPLICABLE
ALREADY_ABSENT
RETIRED
```

### NOT_APPLICABLE

The durable birth state is `ABSENT`. No consent record or key is touched. This preserves an active pre-birth ceremony.

### ALREADY_ABSENT

The birth is committed and both the dedicated record and key are already absent. Repeated startup inspection is safe.

### RETIRED

At least one residue existed and was removed.

The following crash states are recoverable:

```text
record present + key present
record present + key absent
record absent + key present
```

The dedicated record, when present, must have the exact record fields, schema, protection profile and `consentDigest` recorded in the durable authorization. A changed digest or malformed record fails closed and is preserved for diagnosis.

A key-only residue can be deleted safely because the alias is dedicated exclusively to the pre-birth consent record and a valid committed authorization has already been established.

## Startup ordering

Production passes retirement as `beforeInspect` maintenance to `GenesisUltraBirthPreparationCoordinator`.

The order is:

```text
retire committed consent residue
read authorized durable birth state
classify onboarding/runtime route
```

If retirement detects an inconsistent committed state, preparation inspection fails and the application does not silently enter runtime.

If retirement succeeds, the normal preparation classifier reports `ALREADY_COMMITTED`, allowing the existing route mapper to select runtime.

## Security boundaries

Retirement does not:

- create or verify a candidate;
- record consent;
- issue an atomic authorization;
- decrypt the stale consent as authority;
- modify the durable birth transaction;
- append memory;
- call `activate()`;
- call `execute()`;
- enable a cognitive motor.

It only removes dedicated pre-birth residue after a consent-bound durable birth has already been audited.

## Next boundary

The next execution phase must call the existing atomic executor with the exact in-memory authorization and then invoke this retirement path after a successful commit. A cleanup exception after commit must be represented as committed birth with maintenance pending, never as an uncommitted or retryable birth.
