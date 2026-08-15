# Document status: PROPOSAL

# PORTABILITY-PREBIRTH-01 — Instance portability and verifiable self-improvement

## Scope

This candidate exists before Canonical Initial Birth. It corrects a critical identity coupling and establishes the governance substrate required for Morimil to improve its own implementations without becoming its own trust authority.

```text
Instance != Body != model != provider
self_modification != self_authorization
```

No change in this branch authorizes Canonical Initial Birth, Operational Birth, release, install, merge, Body succession, writer transfer or branch deletion.

## PORT-001 correction — permanent Instance id is Body-independent

The previous `genesis.instance.id.v0.1` candidate constructor included the first `bodyId` in the permanent `instanceId` derivation. That made the first Body part of the cryptographic origin of the permanent Instance identifier.

The candidate profile is now:

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

The initial Body remains strongly bound after Instance-id construction through the Body record, Body registry, key epoch, possession proof, candidate digest, birth state, receipt and canonical signed memory.

The default `platformProfile = android-kotlin` therefore describes the first Body only; it is not Instance identity material.

## Cross-language reproducibility

`GenesisUltraInstanceIdProfileTest` contains the Kotlin golden vector.

`tools/genesis/verify_instance_id_v02.py` independently implements the framing and SHA-256 procedure in Python and must produce the identical identifier.

`Reference Checks` executes the Python verifier in CI, so the cross-language vector is not documentation-only.

Future implementations in another runtime must consume the same golden vectors before they can claim compatibility.

## Self-improvement capability model

`SelfImprovementProtocol` introduces a bounded state machine:

```text
DETECTED
-> DIAGNOSED
-> PROPOSED
-> PATCH_CANDIDATE
-> VERIFIED
-> AUTHORIZED
-> MERGE_READY
```

The pre-patch stages are bound to a content-derived `observationDigest` under:

```text
morimil.self_improvement.observation.v1
```

That digest commits to the change id, exact problem, exact proposal and canonically ordered affected surfaces. Editing any of those fields invalidates the observation identity.

No `candidateDigest` exists until an actual patch has been generated. `PATCH_CANDIDATE` then binds:

```text
candidateDigest
+ exact baseCommitSha
```

Independent evidence must match both values exactly.

Morimil may participate directly in:

```text
detect
diagnose
propose
request/generate a sandbox patch candidate
```

Morimil may not perform its own independent verification or authorization.

```text
MORIMIL_AS_INDEPENDENT_VERIFIER = FORBIDDEN
MORIMIL_SELF_AUTHORIZATION = FORBIDDEN
```

Risk is computed from affected surfaces and cannot be supplied by a caller to downgrade a critical change.

High/critical changes require explicit human authorization after independent verification.

Critical surfaces include:

```text
INSTANCE_IDENTITY
GENESIS
CANONICAL_MEMORY
WRITER_AUTHORITY
BODY_SUCCESSION
RECOVERY
```

High surfaces include security, build/supply-chain and reasoning-runtime changes.

## Autonomous runtime observation

`SelfImprovementRuntimeObserver` is initialized from `MorimilApplication` with app-private file storage only. It does not open Room and is not given Git, release-signing, install, merge or protected-main authority.

Current source-level autonomous producers include:

- chat/reasoning errors at `MorimilChatCoordinator`;
- memory-signing / Android Keystore failures at `MemorySigningRuntimeIssues`.

Each signal is converted to a content-bound `SelfChangeObservation`, classified conservatively across all matching risk surfaces and recorded only at `DETECTED` in the durable self-change audit. Repeated identical observations are deduplicated against the latest matching observation within a bounded cooldown, even if other observations interleave.

The existing Improvements proposal UI remains a separate/manual projection. The autonomous observer deliberately does not open `MorimilDatabase` to create that projection, preventing a primary database/signing failure from recursively re-entering the same store through the self-improvement path.

A failure or corruption in this auxiliary self-improvement control plane disables its capture path rather than taking the primary Morimil runtime down. It does not become availability authority.

## Durable self-change audit

`SelfImprovementAuditStore` persists local operational control-plane evidence as an append-only hash chain under app-private storage.

Properties:

- monotonically increasing sequence;
- previous-record digest chaining;
- observation, stage, actor, candidate/base binding when present;
- SHA-256 record digest;
- fsync after every append;
- full-chain re-verification after append and on recovery;
- truncation, tampering, malformed fields, digest mismatch and sequence gaps fail closed.

This audit is **not canonical living memory**, does not define Instance identity, and does not become writer or Guardian authority.

## External execution and independent verification ports

`SelfPatchExecutorPort` is the sandbox code-generation boundary. It can return only a patch artifact bound to an exact base commit and canonical changed paths.

`SelfIndependentVerifierPort` is a separate evidence-producing boundary.

`SelfImprovementOrchestrator` requires different executor and verifier identities, rejects an executor that substitutes another base SHA, and stops at `VERIFIED`.

There is intentionally **no merge port**, release port, production-signing port, install port or protected-main mutation method in the self-improvement orchestrator.

The current Android Body still has no production implementation of the external patch executor/verifier ports. They are intended for a separately controlled development host/Body and must not be represented as already operational.

## Adversarial safety envelope

Generated patches are fail-closed before independent verification:

- changed paths must be canonical, unique and sorted;
- a candidate is limited to 128 changed paths;
- patch representation is limited to 2 MiB;
- `.git` paths are forbidden;
- root or nested `.env*`, `local.properties`, keystores, PKCS containers, PEM and private-key paths are forbidden, case-insensitively;
- a patch executor receives no merge, release, production-signing or install capability from this port.

High/critical verification additionally requires affirmative evidence for:

```text
security checks
reproducibility
coverage review
mutation review
sandbox isolation
secret isolation
blast-radius review
rollback-plan review
audit-trail recording
```

Critical changes additionally require instrumented tests and cross-language vectors.

These controls exist to prevent a self-improvement mechanism from acquiring authority by editing its own trust boundary, stealing release material, silently widening scope, or declaring its own patch correct.

## QA-7 remote-ageing correction discovered during this work

The candidate exposed a pre-existing QA-7 mismatch: its doctrine said a newly published remote dependency version must not make an unchanged repository fail, but the algorithm only remembered dependency coordinates that already emitted a warning at baseline capture.

The ratchet now freezes the complete dependency-coordinate inventory already present in the baseline `app/build.gradle.kts`. A coordinate that later becomes outdated due only to remote publication is distinguished from a new dependency coordinate introduced by the candidate. Warning ceilings and all non-dependency warning fingerprints remain enforced.

This QA-7 correction does not update dependency versions or disable `GradleDependency` globally.

## Evidence requirements

Every verified patch is bound to one exact `candidateDigest` and exact `baseCommitSha`.

Baseline verification requires architecture review, compilation, unit tests and static analysis.

Passing a check does not imply a stronger guarantee than the evidence actually executed.

## Residual portability and autonomy debt — not silently declared solved

This candidate does **not** complete F5 or F6.

Known remaining work includes:

1. Canonical post-Birth memory verification currently assumes the supplied active Body/key epoch for the recovered chain. Multi-Body historical verification and writer-epoch succession remain F5 work.
2. Atomic Birth persistence is implemented directly on Android Room/`MorimilDatabase`; a portable persistence port is not yet the production boundary.
3. Production Body and Guardian trust stores are Android implementations. Provider-neutral interfaces exist in portions of the signing protocol, but the complete Birth composition is not yet platform-neutral.
4. External patch-executor and independent-verifier ports are defined, but no production development-host implementation is connected yet.
5. Executor/verifier identity separation in this candidate is an application governance invariant, not yet a cryptographically attested remote identity protocol.
6. Autonomous runtime capture currently covers connected error producers; it is not represented as omniscient detection of every possible architectural defect.
7. Morimil cannot claim successful self-repair merely because it generated or requested a patch. Independent evidence and the applicable authorization boundary remain mandatory.
8. There is no direct self-merge capability by design.

## Required next gates

Before returning to `BIRTH-PROVENANCE-00`:

```text
1. candidate CI green on exact branch head
2. independent review of PORT-001 derivation change
3. cross-language verifier execution evidence
4. review remaining P1 portability findings
5. merge only with explicit authorization
6. resolve new protected-main SHA
7. only then create a Signed Release APK from that new exact SHA
```

## Status

```text
PORT_001_INSTANCE_ID_BODY_COUPLING=CORRECTED_IN_CANDIDATE
SELF_IMPROVEMENT_GOVERNANCE=IMPLEMENTED_IN_CANDIDATE
SELF_OBSERVATION_CONTENT_BINDING=IMPLEMENTED_IN_CANDIDATE
SELF_RUNTIME_SIGNAL_AUTONOMY=IMPLEMENTED_FOR_CONNECTED_PRODUCERS_IN_CANDIDATE
SELF_DURABLE_AUDIT_STORE=IMPLEMENTED_IN_CANDIDATE
SELF_PATCH_EXECUTOR_PORT=DEFINED
SELF_INDEPENDENT_VERIFIER_PORT=DEFINED
SELF_PATCH_SCOPE_LIMITS=IMPLEMENTED_IN_CANDIDATE
SELF_SECRET_PATH_MUTATION=FORBIDDEN
SELF_PATCH_PRODUCTION_EXECUTOR=NOT_IMPLEMENTED
SELF_INDEPENDENT_VERIFIER_CONNECTION=NOT_IMPLEMENTED
SELF_MERGE_PORT=ABSENT_BY_DESIGN
SELF_AUTHORIZATION=FORBIDDEN
F5_BODY_SUCCESSION=OPEN
F6_PHYSICAL_CONTINUITY=OPEN
CANONICAL_INITIAL_BIRTH=NOT_AUTHORIZED
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```
