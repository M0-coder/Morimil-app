# Document status: PROPOSAL

# PORTABILITY-PREBIRTH-01 — Instance portability and verifiable self-improvement

## Scope

This candidate exists before Canonical Initial Birth. It corrects a critical identity coupling and establishes a bounded substrate for Morimil to improve implementations without becoming its own trust authority.

```text
Instance != Body != model != provider
self_modification != self_authorization
self_generated_claim != external_evidence
```

No change in this branch authorizes Canonical Initial Birth, Operational Birth, release, install, merge, Body succession, writer transfer or branch deletion.

## PORT-001 — permanent Instance id is Body-independent

The previous `genesis.instance.id.v0.1` candidate constructor included the first `bodyId` in the permanent `instanceId` derivation. The candidate profile is now:

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

The initial Body is bound afterwards through the Body record, Body registry, key epoch, possession proof, candidate digest, birth state, receipt and canonical signed memory. `platformProfile = android-kotlin` therefore describes the first Body only.

## Cross-language reproducibility

`GenesisUltraInstanceIdProfileTest` contains the Kotlin golden vector. `tools/genesis/verify_instance_id_v02.py` independently reproduces the framing and SHA-256 derivation in Python, and `Reference Checks` executes that verifier in CI.

Future runtimes must reproduce the same vectors before claiming compatibility.

## Self-improvement state machine

```text
DETECTED
-> DIAGNOSED
-> PROPOSED
-> PATCH_CANDIDATE
-> VERIFIED
-> AUTHORIZED
-> MERGE_READY
```

The pre-patch stages are bound to a content-derived `observationDigest` under `morimil.self_improvement.observation.v1`. It commits to change id, exact problem, exact proposal and canonically ordered affected surfaces.

### F-01 — exact patch-content binding

`SelfPatchArtifact` no longer accepts security-relevant patch metadata from an executor. It is constructed from the exact unified-diff bytes and exact `baseCommitSha`.

The candidate digest uses:

```text
morimil.self_improvement.patch.v1

candidateDigest = SHA256(
    domain,
    exact baseCommitSha,
    exact patch bytes
)
```

From those bytes the implementation independently derives:

- byte count;
- changed paths;
- path-derived risk surfaces;
- candidate digest.

The diff parser rejects non-canonical paths, CR line endings, invalid UTF-8, NUL, binary patches and symbolic-link modes. Credential/Git paths remain forbidden and the candidate stays bounded to 128 changed paths / 2 MiB.

The orchestrator recalculates the digest and rejects any path-derived surface that was not already declared by the content-bound observation. Every production path is classified independently; an otherwise unclassified `app/src/main/**` path becomes `CORE_IMPLEMENTATION` and is HIGH risk.

This prevents an observation presented as UI/local work from silently generating a Genesis, security, build, memory or unknown Core change.

`patchRef` is provenance metadata only. There is still no merge/apply port in this module; a future development-host implementation must prove that the repository candidate it exposes corresponds to the attested patch bytes before any external merge operation.

### F-02 — signed verifier and human authority

Actor enums are audit labels only. They cannot move a candidate through trusted transitions.

`SelfSignedAuthorityAttestation` uses Ed25519 and binds:

```text
role
signerId
publicKeyRef
observationDigest
candidateDigest
baseCommitSha
evidenceBundleDigest
canonical claim set
issuedAtMillis
nonce
```

`SelfImprovementAuthorityVerifier` contains trusted public keys only and cannot create signatures.

An independent verifier attestation must cryptographically verify before `PATCH_CANDIDATE -> VERIFIED`. Verification claims replace the former self-asserted booleans.

Baseline claims require:

```text
PATCH_CONTENT_RECOMPUTED
EXACT_BASE
ARCHITECTURE_REVIEW
COMPILATION
UNIT_TESTS
STATIC_ANALYSIS
```

HIGH/CRITICAL additionally require security, reproducibility, coverage, mutation review, sandbox isolation, secret isolation, blast radius, rollback and audit-trail claims. CRITICAL additionally requires instrumented tests and cross-language vectors.

HIGH/CRITICAL `VERIFIED -> AUTHORIZED` requires a separate signed `HUMAN_AUTHORIZER` attestation. Its evidence reference must equal the exact independent-verification attestation digest, preventing authorization from being detached from the evidence it approved.

LOW/MEDIUM may use the already trusted independent verifier under policy; Morimil cannot create either trusted signature merely by supplying an actor enum.

Production trust is **not yet connected**. The Android Body currently has no production external patch executor, independent-verifier trust source or human-authorizer trust source. Therefore this branch defines and tests the trust protocol but does not claim an operational autonomous code-change loop.

## Autonomous runtime observation

`SelfImprovementRuntimeObserver` is initialized with app-private file storage only. It does not open Room and receives no Git, release-signing, install, merge or protected-main authority.

Current autonomous producers include:

- chat/reasoning errors at `MorimilChatCoordinator`;
- memory-signing / Android Keystore failures at `MemorySigningRuntimeIssues`.

Signals are recorded only at `DETECTED`.

### F-06 — stable problem identity under repeated failures

A changing `failureCount` is occurrence metadata, not part of the problem statement or `observationDigest`. Repeated identical failures therefore retain one stable observation identity and the cooldown cannot be bypassed merely because the counter changed.

## Durable self-change audit

`SelfImprovementAuditStore` v2 keeps a hash-chained append-only local log and a separate app-private head anchor.

The record binds:

- monotonic sequence;
- previous record digest;
- observation/stage/actor;
- candidate/base when present;
- occurrence count;
- timestamp;
- SHA-256 record digest.

The log is fsync'd on append. The anchor is fsync'd and replaced separately. Recovery verifies the record chain and requires the anchored sequence/digest to exist in that chain. Deleting the log while retaining the anchor, zero-truncating it, or rolling it back to an earlier valid prefix fails closed. A valid log extension after a process interruption may advance a stale anchor.

### Local anti-rollback boundary

This is **local anti-truncation/anti-prefix-rollback**, not a complete rollback-proof trust root. If an attacker can delete or restore the entire app-private storage — log and anchor together — the local pair cannot prove that an older snapshot was restored.

A future external/hardware-backed monotonic witness is still required before claiming full anti-rollback protection. Runtime capabilities therefore keep `selfImprovementExternalAuditWitnessConnected=false`.

The self-change audit is not canonical living memory, does not define Instance identity, and does not become writer or Guardian authority.

## F-05 — runtime capability truth

Compiled capability and live readiness are separate.

`CurrentRuntimeCapabilities.value` reads the live observer state:

```text
NOT_INITIALIZED
READY
DEGRADED_AUDIT_UNAVAILABLE
DISABLED
```

`selfImprovementRuntimeSignalAutonomy` and `selfImprovementDurableAuditStore` become true only when the observer is actually `READY`. Audit corruption/rollback leaves the primary Morimil runtime available but marks the auxiliary control plane degraded instead of continuing to advertise readiness.

Connection truth remains explicit:

```text
selfPatchExecutorConnected=false
selfIndependentVerifierConnected=false
selfHumanAuthorizerTrustConnected=false
selfImprovementExternalAuditWitnessConnected=false
selfMergeAuthority=false
```

## External execution and verification boundaries

`SelfPatchExecutorPort` is the sandbox code-generation boundary. `SelfIndependentVerifierPort` is a separate signer of evidence. Executor and verifier identities must differ.

`SelfImprovementOrchestrator` deliberately stops at `VERIFIED`. There is no merge, release, production-signing, install or protected-main mutation method.

## QA-7 F-03 — remote ageing without downgrade bypass

The QA-7 baseline freezes both:

1. the complete dependency-coordinate inventory already present; and
2. the SHA-256 digest of the exact canonical `dependencies {}` block in `app/build.gradle.kts`.

The remote-ageing exemption is activated only if the current dependency-block digest equals the frozen digest.

Therefore:

```text
same dependency source + remote ecosystem publishes newer version
= REMOTE AGEING; existing coordinate may warn without creating repository debt
```

but:

```text
same coordinate + candidate changes/downgrades dependency declaration
= dependency-block digest changes
= remote-ageing exemption disabled
= warning evaluated as repository regression
```

A new outdated coordinate also continues to fail. CI passes `app/build.gradle.kts` explicitly to the QA-7 evaluator. Dependency versions are not modified by this correction.

## Residual portability and autonomy debt

This candidate does **not** complete F5 or F6.

Known remaining work:

1. Canonical post-Birth memory verification still needs multi-Body historical writer/key-epoch verification and succession for F5.
2. Atomic Birth persistence is still directly implemented on Android Room/`MorimilDatabase`; a portable persistence port is not yet the production boundary.
3. Production Body and Guardian trust stores remain Android implementations; the complete Birth composition is not yet platform-neutral.
4. No production development-host `SelfPatchExecutorPort` implementation is connected.
5. No production independent-verifier trust source is connected.
6. No production human-authorizer trust source is connected.
7. No external/hardware monotonic audit witness is connected; whole-store rollback remains outside the local anchor guarantee.
8. Autonomous runtime capture covers connected producers only; it is not omniscient architectural detection.
9. There is no self-merge capability by design.
10. Passing tests/CI does not itself establish operational self-repair until the external executor/verifier/authorization boundaries are physically composed and evidenced.

## Required next gates

Before returning to `BIRTH-PROVENANCE-00`:

```text
1. candidate CI green on one exact branch HEAD
2. repeat independent adversarial review on that exact HEAD
3. resolve every blocking finding without weakening gates
4. merge only under separate explicit authorization
5. resolve new protected-main SHA
6. only then produce Signed Release APK evidence from that exact main
```

## Status

```text
PORT_001_INSTANCE_ID_BODY_COUPLING=CORRECTED_IN_CANDIDATE
F01_PATCH_CONTENT_BINDING=IMPLEMENTED_IN_CANDIDATE
F02_SIGNED_EXTERNAL_AUTHORITY=IMPLEMENTED_IN_CANDIDATE
F03_QA7_DOWNGRADE_BYPASS=CORRECTED_IN_CANDIDATE
F04_LOCAL_AUDIT_PREFIX_ROLLBACK=CORRECTED_IN_CANDIDATE
F05_RUNTIME_CAPABILITY_TRUTH=IMPLEMENTED_IN_CANDIDATE
F06_FAILURE_COUNT_DEDUPE_BYPASS=CORRECTED_IN_CANDIDATE
SELF_PATCH_PRODUCTION_EXECUTOR=NOT_CONNECTED
SELF_INDEPENDENT_VERIFIER_CONNECTION=NOT_CONNECTED
SELF_HUMAN_AUTHORIZER_TRUST=NOT_CONNECTED
SELF_EXTERNAL_AUDIT_WITNESS=NOT_CONNECTED
SELF_MERGE_PORT=ABSENT_BY_DESIGN
F5_BODY_SUCCESSION=OPEN
F6_PHYSICAL_CONTINUITY=OPEN
CANONICAL_INITIAL_BIRTH=NOT_AUTHORIZED
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```
