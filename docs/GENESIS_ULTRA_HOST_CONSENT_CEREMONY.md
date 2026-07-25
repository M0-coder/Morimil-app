# Genesis Ultra host consent ceremony

## Purpose

This phase lets the local host approve one exact, verified Genesis Ultra birth
candidate. It does not authorize or execute birth.

```text
verified signed Seed
+ canonical companion name
+ local Body root
+ pinned Guardian epoch
= exact in-memory candidate

exact candidate
+ displayed candidate digest
+ typed 12-character confirmation code
+ explicit local presence
= encrypted host consent
```

## Exact candidate session

The full `GenesisUltraConstructedBirthCandidate` is retained only in the
`GenesisUltraOnboardingViewModel` process memory. It is not serializable and is
not written to Room, SharedPreferences, files, logs or saved instance state.

The UI receives only a non-secret summary:

- Seed identifier and verified root;
- Guardian and key epoch identifiers;
- canonical companion name;
- Instance and Body identifiers;
- candidate digest;
- session expiry.

Changing the name, selecting another Seed, refreshing before consent or process
destruction discards the exact candidate session.

## Confirmation ceremony

The screen displays the candidate digest and a 12-character code derived from
that digest. Consent is accepted only when the user:

1. reviews the candidate summary;
2. types the exact code;
3. confirms local presence;
4. presses the explicit consent action before the Body possession proof expires.

The request is rechecked by `GenesisUltraAndroidHostBirthConsentStore` against
the exact in-memory candidate object. UI text cannot substitute another
candidate.

## Durable consent

The resulting consent is encrypted and authenticated by a dedicated Android
Keystore AES-256-GCM key. It binds:

- `candidateDigest`;
- `instanceId`;
- canonical companion name;
- Seed root;
- Body identifier;
- Guardian identifier and key epoch;
- confirmation mode and purpose;
- consent and expiry times.

The consent window remains at most two minutes and never extends beyond the
Body possession proof. `birthCommitAuthorized` remains `false`.

## Process restart and revocation

The candidate is intentionally lost when Android destroys the process, while a
previously recorded consent may still exist. The app detects `READY` or
`EXPIRED` consent without a candidate session and blocks importing a new Seed.

An explicit revocation action removes the encrypted record and its dedicated
Keystore key while durable birth is still `ABSENT`. The app never reconstructs,
guesses or substitutes the lost candidate. An inconsistent consent record is
not erased automatically and remains fail-closed.

## Still blocked

This phase does not:

- accept the final Guardian testimony package;
- build the complete atomic birth evidence graph;
- issue `GenesisUltraAuthorizedAtomicBirth`;
- call the atomic execution coordinator;
- open runtime navigation;
- activate deliberative or metacognitive motors.
