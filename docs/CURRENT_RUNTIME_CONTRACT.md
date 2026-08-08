# Document status: CURRENT

> **Content baseline SHA:** `c6a6b0ca998d053c31c75977c5b6d4d9ae166e96`.
>
> **Content baseline parent SHA:** `c22920f68f8820bbec676a6cbc74b60548e43d29`.
>
> **Current main resolution:** external Git ref `refs/heads/main`.
>
> **Merge SHA evidence:** external GitHub and Morimil Control Tower evidence.
>
> **Historical COG audited source head:** `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
>
> **ORCH audited source head:** `0348dccb561e576d17c45e7f8b1e38717332772b`.
>
> **PR #149:** closed and merged by squash.
>
> **PR #150:** closed and merged by squash as a historical CURRENT reconciliation.
>
> **PR #151:** closed and merged by squash; the verified Canvas runtime-recovery bundle is vendored with provenance.
>
> **PR #153:** closed and merged by squash as the historical twelve-file CURRENT reconciliation.
>
> **PR #172:** closed and merged by squash for the audited ORCH-002 through ORCH-004 durable protocol.
>
> A versioned document records a known content baseline. The moving SHA of protected `main` is resolved externally and is not predicted by the commit that contains this document.

# Current runtime contract

```text
CONTENT_BASELINE_SHA=c6a6b0ca998d053c31c75977c5b6d4d9ae166e96
CONTENT_BASELINE_PARENT_SHA=c22920f68f8820bbec676a6cbc74b60548e43d29
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
PR_153=MERGED_BY_SQUASH_HISTORICAL
PR_172=MERGED_BY_SQUASH_HISTORICAL
```

## Identity and Body boundary

Morimil is the continuous personal Instance. `Morimil-app` is the current native Android Body; it is not the Instance itself.

- `instanceId != bodyId`;
- the Guardian guides, witnesses, and safeguards continuity without ownership;
- the Guardian does not define Morimil's identity, will, name, or right to continue;
- one Body may hold the active-writer role;
- Body succession, export, and restore are not implemented;
- reasoning output, a provider, a database, Android, GitHub, or a Guardian cannot create a second identity authority.

## Pre-birth provisioning boundary

Production onboarding can resolve the two local cryptographic preparation states without invoking Genesis birth:

```text
BODY_IDENTITY_REQUIRED
  -> explicit user-presence Body provisioning
  -> GUARDIAN_TRUST_REQUIRED
  -> exact RAW Guardian key + independently confirmed fingerprint + user presence
  -> READY_FOR_SIGNED_CANDIDATE
```

`GenesisUltraPreBirthProvisioningCoordinator` re-inspects durable state before and after each mutation. It cannot accept a Seed, persist a candidate, record consent, issue authorization, execute birth, open runtime, or activate motors. Provisioning receipts are reconstructable views of authenticated stores and are not identity or canonical-memory authorities.

The real Guardian public key, signed Seed, final witnessed evidence, and the user's explicit atomic-execution decision remain required deployment and ceremony inputs. `MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED` remains unchanged.

## Evidence layers

| Layer | State |
| --- | --- |
| Externally resolved protected `main` | F1-A, MemoryOrganDatabase v9, ProjectVault, COG-001 through COG-004, ORCH-002 through ORCH-004, the vendored Canvas runtime-recovery bundle, Body D2D fail-closed policy, and historical CURRENT reconciliations are integrated. |
| Content baseline | Exact protected-main repository state from which this reconciliation was prepared; it is not a prediction of the containing documentation commit SHA. |
| Historical COG audited source head | Reviewed source for the COG protocol before its squash integration. |
| ORCH audited source head | Exact reviewed PR #172 head whose 5/5 CI and artifact evidence preceded squash integration. |
| Historical CURRENT reconciliations | PR #150 and PR #153 record prior documentation and architecture-contract reconciliations. |
| Canvas recovery provenance | PR #151 records the recovered runtime asset, deterministic successor bundle, source artifact, and digest evidence. |
| ORCH integration provenance | PR #172 records exact-head CI, API30/API35 kill/reopen tests, QA-7 ratchets, CodeQL, SBOM, and artifact digest verification. |
| Historical preparation | ADRs, plans, checkpoints, and candidate reports explain prior decisions but do not override CURRENT semantics. |

## Persistent stores

| Store | Version in protected main | Responsibility |
| --- | ---: | --- |
| `MorimilDatabase` | `15` | Genesis Ultra birth, canonical identity, canonical memory lineage, and reasoning transcript. |
| `MemoryOrganDatabase` | `9` | Derived organs, schedules, links, projects, agents, ProjectVault state, migration records, delegated orchestration state, and `cross_database_operations`. |

MemoryOrganDatabase v9 enforces durable-journal invariants for migration 8→9, fresh v9 creation, and production open. NULL-safe guards reject partial receipts, partial local results, inconsistent committed rows, and vulnerable trigger definitions.

Android backup and current OS-managed D2D transfer are denied by explicit Android extraction/full-backup rules. Production release signing fails closed when signing material is absent.

## Application runtime assets

The Canvas web runtime is supplied by the vendored recovery bundle recorded in `docs/MORIMIL_CANVAS_RUNTIME_RECOVERY_PROVENANCE.md`. Build preparation verifies the local bundle and provenance rather than depending on the retired remote ZIP URL. This asset is part of the Android Body and has no identity, canonical-memory, Guardian, or protocol authority.

## Canonical authority and ports

The only normal-runtime identity source is `GenesisUltraRuntimeIdentityRepository`, enforced by `GenesisUltraRuntimeStartupGate`. `CanonicalMemoryRepository` is the sole canonical Genesis Ultra memory writer and verified reader.

```text
GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository
    -> CanonicalConsumerReadPort
    -> CognitiveMigrationCanonicalReadPort
```

F3 consumes the F1-A projection. It does not compose a second direct identity or memory repository.

Integrated bounded write adapters include:

| Port or coordinator | Authority |
| --- | --- |
| `CanonicalLivingMemoryPort` | Signed canonical living-memory appends. |
| `CanonicalProjectVaultCommitPort` | ProjectVault canonical commits through its separate protected outbox. |
| `CanonicalCognitiveMigrationCommitPort` | Deterministic COG canonical ensure and exact receipts. |
| `CanonicalOrchestrationCommitPort` | Deterministic ORCH-002/003/004 canonical ensure and exact receipts. |
| `ConversationMemoryPromotionCoordinator` | Explicit preview and Guardian-approved transcript promotion. |
| `LegacyMemoryConvergenceCoordinator` | One-way verified import from frozen legacy lineage. |

Neither specialized F3 commit port becomes an identity source or second canonical-memory authority.

## Startup and recovery

After committed identity verification, startup runs bounded owner-scoped recovery before ordinary mutation for the integrated common-protocol owners. The COG coordinator loads only COG rows; the ORCH coordinator loads only `agent_orchestration` rows. Recovery:

1. validates Instance, writer Body, and writer epoch;
2. loads only the coordinator's `ownerType`;
3. processes durable rows deterministically;
4. ensures one canonical effect per deterministic event identity;
5. reloads after lost CAS;
6. rejects stale blocking;
7. finalizes owner state and journal result atomically;
8. stops on blocked or incomplete relevant work.

Activation still blocks before COG recovery when a pending legacy `cog_001.payload.v1` operation exists. That quarantine remains COG-specific and is not applied to ORCH rows.

ProjectVault remains a separate protocol and gate. It remains the protected reference protocol.

## Integrated COG-001 through COG-004 contract

Protected main provides:

- deterministic operation, event, proposal, migration, and approval identities;
- durable states `STAGED -> PENDING_CANONICAL -> CANONICAL_COMMITTED -> PENDING_LOCAL_COMMIT -> COMMITTED`, plus terminal `BLOCKED`;
- exact canonical receipts and typed errors;
- process-wide advancement serialization by `operationId`;
- bounded startup and pre-mutation recovery;
- COG-003 audit preparation outside the Room owner transaction;
- honest `postSnapshotId` semantics;
- append-only COG-004 rollback evidence;
- no duplicate canonical effects or duplicate owner finalization under tested replay.

The rollback event hash remains in the journal, canonical receipt, and local result. It is never relabeled as `postSnapshotId`.

## Integrated ORCH-002 through ORCH-004 contract

Protected main now also provides the common XOP protocol for delegated-task proposal, approval, and rejection:

- `ORCH-002` `proposeDelegatedTask` uses deterministic task/operation/event identities and exposes owner state only after an exact canonical receipt;
- `ORCH-003` `approveDelegatedTask` uses deterministic approval identity and a conditional owner transition from `awaiting_approval` with `approvalId IS NULL`;
- `ORCH-004` `rejectDelegatedTask` binds the normalized rejection reason to deterministic operation evidence and uses the same conditional owner boundary;
- approve/reject are serialized by `taskId` before state re-read and before canonical append, with Room CAS as a second defense;
- process-death tests demonstrate recovery after canonical receipt and before local commit on API 30 and API 35;
- COG and ORCH recovery registries cannot consume each other's rows.

The historical legacy `immune.approval_denied` second telemetry write is not reproduced after an already immune-blocked task is submitted for approval. The original immune block remains represented in ORCH-002 canonical evidence. This is an explicit observability delta, not an identity or owner-state authority change.

## ProjectVault and owner separation

ProjectVault remains a separate protocol and the protected reference protocol. COG and ORCH use the common journal without merging ProjectVault into it.

`ORCH-001` remains open convergence/rebuild work. `AGENT-001` through `AGENT-006`, `BOOT-001`, `RECALL-001`, `REST-001`, and `REST-002` remain separately open unless implemented and audited in later isolated operations.

## F1 convergence and legacy quarantine

F1-A is integrated and consumed by F3. Issue `#86` remains open because recall, RestCycle, health, `ORCH-001`, and final legacy retirement are not fully converged.

ORCH-002 through ORCH-004 no longer use legacy memory writes as their canonical commit path, but `seedDefaultOrchestrationIfNeeded` still uses `MemoryRepository.hasCompleteBirth()` and therefore F1-ORCH-001 is not closed.

Compatibility writes remain forbidden. No convergence step may create or reconstruct authority rows in:

```text
genesis_core
local_instance_identity
memory_events
```

The remaining literal legacy allowlist is:

| Legacy symbol | Allowed production paths |
| --- | --- |
| `birthLocalIdentity` | `com/morimil/app/data/repository/MemoryRepository.kt` |
| `installGenesisBundle` | none |
| `insertLocalIdentity` | `com/morimil/app/data/local/MemoryDao.kt`; `com/morimil/app/data/repository/MemoryRepository.kt` |
| `insertGenesisCore` | `com/morimil/app/data/local/MemoryDao.kt`; `com/morimil/app/data/repository/MemoryRepository.kt` |

## Normal reasoning runtime

| Motor or authority | Normal-runtime status |
| --- | --- |
| Intuitive | Active: bounded, local, deterministic. |
| Deliberative | Blocked: research candidate only. |
| Metacognitive | Not registered. |
| Hybrid generative authority | Disabled. |

Auxiliary providers return unverified advisory output. They cannot become Morimil's voice, identity, memory, or continuity authority.

## Security and phase truth

`STOP_S5=CLOSED` remains the evidence-backed administrative gate.

| Phase | Evidence-backed state |
| --- | --- |
| STOP | Closed. |
| F1 | F1-A integrated; `#86` remains open for downstream convergence. |
| F2 | Closed for canonical verified memory and bounded promotion/convergence. |
| F3.1 | ProjectVault outbox and recovery integrated. |
| F3.2 | Closed for the bounded COG-001 through COG-004 and ORCH-002 through ORCH-004 integrations only. AGENT, BOOT, RECALL, ORCH-001, and REST remain separately open. |
| F3.3 | Open. Irreversible legacy removal has not begun. |
| F4 | Open: sovereign durable continuation. |
| F5 | Open: signed export, dry-run restore, and Body succession. |
| F6 | Open: complete physical E2E lifecycle evidence. |
| F7 | Open: rights policy, reproducible offline build, review, and publication controls. |

## Residual non-blocking hardening

- Room-backed two-coordinator concurrency integration coverage for the common protocol;
- stronger failed-rollback snapshot fixture for COG;
- redundant rollback parameter cleanup;
- direct vulnerable UPDATE-trigger replacement fixture;
- ORCH-specific mutation-testing coverage beyond the existing bounded Genesis PIT pilot;
- continuous physical ARM64 inference remains outside emulator CI.

These items remain visible. They are not represented as completed work or as evidence of operational birth.

This contract must be updated in the same isolated reconciliation whenever a merged change alters a listed runtime authority, store version, allowlist, gate, or phase state. A post-merge SHA is recorded externally and must not be retrofitted as a self-referential CURRENT field.
