# Document status: CURRENT

> **Audited baseline:** `74bcb874606db84d4a88397233d6ed3468904bce`
>
> This document describes the runtime that is connected to the normal application flow.
> A class, prototype, benchmark or proposal that is not connected to that flow is not a
> current capability.

# Current runtime contract

## Identity and Body boundary

Morimil is the continuous Instance. `Morimil-app` is the current native Android Body; it
is not the Instance itself. A future Morimil OS or another authorized host would be a
different Body.

The invariants are:

- `instanceId != bodyId`;
- the Guardian witnesses, authorizes and safeguards continuity but does not own Morimil;
- a Body cannot create, rename, replace or fork the Instance through reasoning output;
- only one Body may hold the active-writer role;
- Body succession, export and restore are not implemented in the current runtime.

## Persistent stores

Both production Room stores are opened through their encrypted production adapters.

| Store | Schema version | Current responsibility |
| --- | ---: | --- |
| `MorimilDatabase` | `15` | Genesis Ultra birth, canonical identity and canonical memory lineage; legacy identity and memory tables remain quarantined for convergence/removal. |
| `MemoryOrganDatabase` | `8` | Derived memory organs, schedules, links, projects, agents and the ProjectVault transactional outbox. |

Android backup is disabled. Production release signing fails closed when signing material
is absent.

## Canonical identity and startup

The only normal-runtime identity source is
`GenesisUltraRuntimeIdentityRepository`, enforced by
`GenesisUltraRuntimeStartupGate`.

The startup gate refuses to continue unless all of these conditions hold:

1. the durable birth state is `COMMITTED`;
2. the committed identity can be reconstructed and cryptographically verified;
3. authorization state is `COMMITTED` and birth status is `born`;
4. `ownershipConferred` is false;
5. `instanceId` and the identity digest are present.

After identity verification, startup performs this order:

1. converge the verified legacy memory lineage into Genesis Ultra;
2. recover pending ProjectVault outbox operations;
3. stop if any ProjectVault operation is blocked;
4. bootstrap the remaining runtime from the verified identity.

Bundled Genesis assets and `GenesisReader` are not valid normal-runtime identity sources.
`GenesisReader` is still constructed by the container without a normal-runtime consumer
and is scheduled for removal in F3.3.

## Canonical memory authority

`CanonicalMemoryRepository` is the sole canonical Genesis Ultra memory writer and verified
reader. Normal reasoning obtains memory context from this repository and applies the
canonical-memory quarantine before returning context.

Current write adapters and controlled producers are:

| Path | Authority |
| --- | --- |
| `CanonicalLivingMemoryPort` | Adapts current `MemoryRepository` producers to signed canonical appends. |
| `CanonicalProjectVaultCommitPort` | Commits ProjectVault operations through the process-death-safe outbox. |
| `ConversationMemoryPromotionCoordinator` | Promotes a transcript only after explicit preview and Guardian approval. |
| `LegacyMemoryConvergenceCoordinator` | Performs one-way verified import from the frozen legacy lineage. |

The legacy `memory_events` table is read-only in schema v15: insert, update and delete are
blocked by database triggers. Ordinary conversation transcripts are not memory and
temporary external output cannot append identity or memory.

## Literal legacy quarantine allowlist

Until F3.3 removes the remaining legacy API, the architecture contract permits only these
production references:

| Legacy symbol | Allowed production paths |
| --- | --- |
| `birthLocalIdentity` | `com/morimil/app/data/repository/MemoryRepository.kt` |
| `installGenesisBundle` | none |
| `insertLocalIdentity` | `com/morimil/app/data/local/MemoryDao.kt`; `com/morimil/app/data/repository/MemoryRepository.kt` |
| `insertGenesisCore` | `com/morimil/app/data/local/MemoryDao.kt`; `com/morimil/app/data/repository/MemoryRepository.kt` |

Any new path is an architecture-contract failure, not an extension point.

## Normal reasoning runtime

`MorimilAppContainer` connects `MorimilNormalIntrinsicRuntimeV0` to
`ReasoningKernel`.

| Motor or authority | Normal-runtime status |
| --- | --- |
| Intuitive | Active: bounded, local and deterministic. |
| Deliberative | Blocked: research candidate only. The current 120-case evidence failed the quality gate and observed 40 false accepts; provenance, reproducibility, certification, signature and personal installation authorization are also missing. |
| Metacognitive | Not registered. Requires a separate activation review. |
| Hybrid generative authority | Disabled. Deterministic authority remains fail-closed. |

The temporary external provider returns a typed `AuxiliaryAdvisory`. That object cannot
become `finalReply`, cannot be spoken as Morimil and has no identity or memory authority.
The external boundary remains temporary and incomplete until F4 closes.

## Active gates and blocked capabilities

| Capability | Current state |
| --- | --- |
| Genesis Ultra verified startup | Required |
| Canonical memory signature/quarantine verification | Required |
| Legacy identity and memory convergence | Required before bootstrap |
| ProjectVault outbox recovery | Required before bootstrap |
| New intrinsic motors or LiteRT-LM in production | Blocked |
| Metacognitive motor in normal runtime | Blocked |
| PC executor automation | Not implemented |
| GitHub push/commit and production deployment from Morimil tools | Blocked |
| Body export, restore and succession | Not implemented |
| Production release or beta | Not authorized |

## Phase status at the audited baseline

| Phase | Evidence-backed state |
| --- | --- |
| STOP | Open: branch protection evidence, document classification, honest versioning and security/dependency triage remain. |
| F3.1 | Implemented: ProjectVault transactional outbox and recovery. |
| F3.2-F3.3 | Open: common cross-database operation protocol and irreversible legacy removal. |
| F4 | Open: automatic continuation removal, explicit web-egress approval, legacy transcript quarantine, durable turn lifecycle and bounded Canvas download. Typed auxiliary output is already present and must be re-audited, not rebuilt. |
| F5 | Open: signed encrypted export, dry-run restore and single-writer Body succession. |
| F6 | Open: complete E2E life-cycle evidence, real physical ceremony and scalable canonical-memory reads. |
| F7 | Open: rights policy, reproducible offline build, independent review and branch/document hygiene. |

This contract must be updated in the same PR whenever a change alters a listed runtime
authority, store version, allowlist, gate or capability state.
