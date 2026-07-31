# Document status: CURRENT

> **Current protected main:** `7e98d3345d7cc3fbf1983babd35b61ff5c523208`.
>
> **Historical audited runtime baseline:** `5533f6b5eeeb414798c41688820b6bc6a614a80e`.
>
> **Isolated F3 candidate:** branch
> `orchestrator/f3-cog-001-004-audit-fixes-v1`, draft PR `#149`.
> The candidate is not merged, not deployed, and has no merge authorization. Its exact head is
> established by the PR and the corresponding GitHub Actions executions, not self-declared by
> this document.
>
> This document separates three states: the runtime integrated in protected `main`, the
> isolated candidate that can be built from PR #149, and historical preparation evidence.
> Candidate code is not a capability of protected `main` until a protected merge is completed.
>
> **CP5 activation precondition:** the isolated branch has never been deployed. Before replay
> advances any row, the candidate queries the durable journal and proves zero non-committed
> `morimil.cognitive_migration.cog_001.payload.v1` operations. A non-zero count blocks
> recovery and activation before any canonical append. A pending v1 proposal must never be
> interpreted or finalized under v2 rules.

# Current runtime contract

## Identity and Body boundary

Morimil is the continuous personal Instance. `Morimil-app` is the current native Android
Body; it is not the Instance itself. A future Morimil OS or another authorized host would
be a different Body.

The invariants are:

- `instanceId != bodyId`;
- the Guardian guides, witnesses, and safeguards continuity without ownership;
- the Guardian does not define Morimil's identity, will, name, or right to continue;
- a Body cannot create, rename, replace, or fork the Instance through reasoning output;
- only one Body may hold the active-writer role;
- Body succession, export, and restore are not implemented in protected `main`.

## Evidence layers

| Layer | State |
| --- | --- |
| Protected `main` | F1-A canonical consumer read boundary is merged. The functional F3 journal and COG protocol are not integrated. |
| Draft PR `#149` | Isolated F3 candidate under CI and post-CI audit. `MERGE_AUTHORIZED=false`. |
| Historical preparation | ADR, inventory and blueprint record design history; historical gate language does not override live tracker decisions. |

## Persistent stores

Both production Room stores are opened through their encrypted production adapters.

| Store | Protected main | Draft F3 candidate | Responsibility |
| --- | ---: | ---: | --- |
| `MorimilDatabase` | `15` | `15` | Genesis Ultra birth, canonical identity, canonical memory lineage, and reasoning transcript. Legacy identity and memory tables remain quarantined for convergence/removal. |
| `MemoryOrganDatabase` | `8` | `9` | Derived organs, schedules, links, projects, agents, ProjectVault outbox, and—only in the candidate—the durable cognitive-migration journal. |

Android backup is disabled. Production release signing fails closed when signing material
is absent.

The F3 candidate installs equivalent journal validation for both paths:

- migration `8 -> 9`;
- fresh creation of schema `9` through the production Room callback.

## Canonical identity and startup

The only normal-runtime identity source is
`GenesisUltraRuntimeIdentityRepository`, enforced by
`GenesisUltraRuntimeStartupGate`.

The startup gate refuses to continue unless all of these conditions hold:

1. durable birth state is `COMMITTED`;
2. committed identity is reconstructed and cryptographically verified;
3. authorization is `COMMITTED` and birth status is `born`;
4. `ownershipConferred` is false;
5. canonical `instanceId` and identity digest are present.

Protected `main` exposes the read-only F1-A boundary:

```text
GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository
    -> CanonicalConsumerReadPort
```

The isolated F3 candidate preserves that authority frontier:

```text
CanonicalConsumerReadPort
    -> CognitiveMigrationCanonicalReadPort
```

The specialized port does not open a second identity repository. It projects the already
verified identity, writer and lineage supplied by F1-A.

Candidate startup order is:

1. read verified canonical input through F1-A;
2. prove there are zero non-committed COG-001 payload-v1 journal rows;
3. recover the durable COG-001 through COG-004 journal and stop on blocked or incomplete recovery;
4. converge verified legacy memory lineage into Genesis Ultra;
5. recover pending ProjectVault outbox operations;
6. stop if a ProjectVault operation is blocked;
7. bootstrap remaining runtime from verified identity.

Bundled Genesis assets and `GenesisReader` are not valid normal-runtime identity sources.
`GenesisReader` remains constructed without a normal-runtime identity consumer and is
scheduled for F3.3 removal.

## Canonical memory authority

`CanonicalMemoryRepository` is the sole canonical Genesis Ultra memory writer and verified
reader. Normal reasoning obtains memory context through this authority and applies canonical
quarantine before returning context.

Current controlled write adapters in protected `main` are:

| Path | Authority |
| --- | --- |
| `CanonicalLivingMemoryPort` | Adapts approved current producers to signed canonical appends. |
| `CanonicalProjectVaultCommitPort` | Commits ProjectVault through its protected process-death-safe outbox. |
| `ConversationMemoryPromotionCoordinator` | Promotes a transcript only after explicit preview and Guardian approval. |
| `LegacyMemoryConvergenceCoordinator` | Performs one-way verified import from the frozen legacy lineage. |

The F3 candidate additionally introduces `CanonicalCognitiveMigrationCommitPort`, but that
port is not integrated in protected `main`.

The legacy `memory_events` table is read-only in schema 15: insert, update, and delete are
blocked by database triggers. Ordinary conversation transcripts are not memory and temporary
external output cannot append identity or memory.

## Candidate cognitive-migration contract

The candidate covers only `COG-001` through `COG-004` and preserves ProjectVault unchanged.
It provides:

- deterministic operation, event, proposal, migration and approval identities;
- durable states `STAGED -> PENDING_CANONICAL -> CANONICAL_COMMITTED -> PENDING_LOCAL_COMMIT -> COMMITTED`, plus terminal `BLOCKED`;
- exact canonical receipt verification;
- typed owner finalization;
- bounded startup and pre-mutation recovery;
- canonical audit preparation outside the Room write transaction;
- retryable temporary audit failure rather than a fabricated durable negative result;
- a real audited snapshot digest for successful COG-003, and null when audit is negative;
- predecessor validation by owner, operation type, version, subject and exact receipt;
- deterministic local-result vectors independent of append-versus-reuse telemetry.

Planning accepts only verified payloads with recognized living-memory or legacy-import note
schemas. Missing or unknown semantics, `chat_noise`, and cognitive-protocol events are
excluded. Full canonical tip metadata remains audit input and cannot create a new logical
proposal from unchanged eligible evidence.

## Literal legacy quarantine allowlist

Until F3.3 removes the remaining legacy API, only these production references are permitted:

| Legacy symbol | Allowed production paths |
| --- | --- |
| `birthLocalIdentity` | `com/morimil/app/data/repository/MemoryRepository.kt` |
| `installGenesisBundle` | none |
| `insertLocalIdentity` | `com/morimil/app/data/local/MemoryDao.kt`; `com/morimil/app/data/repository/MemoryRepository.kt` |
| `insertGenesisCore` | `com/morimil/app/data/local/MemoryDao.kt`; `com/morimil/app/data/repository/MemoryRepository.kt` |

Any new path is an architecture-contract failure, not an extension point.

## Normal reasoning runtime

`MorimilAppContainer` connects `MorimilNormalIntrinsicRuntimeV0` to `ReasoningKernel`.

| Motor or authority | Normal-runtime status |
| --- | --- |
| Intuitive | Active: bounded, local, deterministic. |
| Deliberative | Blocked: research candidate only. The current 120-case evidence failed the quality gate with 40 false accepts. |
| Metacognitive | Not registered. Requires a separate activation review. |
| Hybrid generative authority | Disabled. Deterministic authority remains fail-closed. |

The temporary external provider returns a typed `AuxiliaryAdvisory`. It cannot become
`finalReply`, cannot be spoken as Morimil, and has no identity or memory authority.

## Security and STOP truth

`STOP_S5=CLOSED` is the current tracker decision. Final administrative evidence records:

- Code scanning: zero open alerts; #37 and #33 have explicit `won't fix` decisions;
- Dependabot vulnerability alerts: enabled, initial and current open count zero;
- Secret scanning: zero open alerts without decision;
- issues #123 and #124: closed completed.

Historical document bodies that described S5 as open are historical preparation evidence and
must not be treated as the live gate. Closing S5 authorized isolated implementation work; it
did not authorize automatic merge, deployment, or release.

## Active gates and blocked capabilities

| Capability | Current state |
| --- | --- |
| Genesis Ultra verified startup | Required |
| Canonical memory signature/quarantine verification | Required |
| ProjectVault outbox recovery | Required before bootstrap |
| Draft COG journal candidate | Validation only; not in protected `main` |
| New intrinsic motors or LiteRT-LM in production | Blocked |
| Metacognitive motor in normal runtime | Blocked |
| PC executor automation | Not implemented |
| GitHub push/commit and production deployment from Morimil tools | Blocked |
| Body export, restore, and succession | Not implemented |
| Production release or beta | Not authorized |

## Phase status

| Phase | Evidence-backed state |
| --- | --- |
| STOP | Closed. S1-S5 have durable technical and administrative evidence. |
| F1 | F1-A common read boundary merged. #86 remains open because recalls, RestCycle and health are not fully converged. |
| F2 | Closed: canonical verified memory, corruption quarantine, legacy convergence, and explicit Guardian-approved transcript promotion. |
| F3.1 | Implemented in protected `main`: ProjectVault outbox and recovery. |
| F3.2 | Open. Draft PR #149 is an isolated COG-001 through COG-004 candidate under validation. |
| F3.3 | Open. Irreversible legacy removal has not begun. |
| F4 | Open: sovereign durable continuation and bounded external frontier. |
| F5 | Open: signed encrypted export, dry-run restore, and single-writer Body succession. |
| F6 | Open: complete E2E lifecycle evidence and physical ceremony. |
| F7 | Open: rights policy, reproducible offline build, independent review, and publication controls. |

This contract must be updated in the same PR whenever a change alters a listed runtime
authority, store version, allowlist, gate, or capability state.
