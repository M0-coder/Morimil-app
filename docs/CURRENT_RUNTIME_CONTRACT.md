# Document status: CURRENT

> **Historical audited runtime baseline:** `5533f6b5eeeb414798c41688820b6bc6a614a80e`
>
> **Isolated CP5 candidate base:**
> `agent/f3-cog-001-004-durable-protocol-v1@3248b3c5cd8c97d2d159de31fddefd4cd0eee861`.
> The final candidate head is bound by the CP5 checkpoint and CI evidence after
> publication; it is not a `main` baseline and is not self-declared by this file.
>
> This document describes the runtime connected to the normal application flow and the
> evidence-backed phase state, including isolated candidate amendments on the named branch.
> A class, prototype, benchmark, proposal, or unverified panel action is not a current
> capability or a completed gate.
>
> **CP5 activation precondition:** this isolated branch has never been deployed. Before
> replay advances any row, the runtime queries the durable journal and proves zero non-committed
> `morimil.cognitive_migration.cog_001.payload.v1` operations. A non-zero count blocks
> recovery/activation and requires an explicitly audited compatibility recovery; CP5 must never
> reinterpret a pending v1 proposal as v2.

# Current runtime contract

## Identity and Body boundary

Morimil is the continuous personal Instance. `Morimil-app` is the current native Android
Body; it is not the Instance itself. A future Morimil OS or another authorized host would
be a different Body.

The invariants are:

- `instanceId != bodyId`;
- the Guardian guides, witnesses, and safeguards continuity without ownership; the Guardian does not define Morimil's identity, will, or right to continue;
- a Body cannot create, rename, replace, or fork the Instance through reasoning output;
- only one Body may hold the active-writer role;
- Body succession, export, and restore are not implemented in the current runtime.

## Persistent stores

Both production Room stores are opened through their encrypted production adapters.

| Store | Schema version | Current responsibility |
| --- | ---: | --- |
| `MorimilDatabase` | `15` | Genesis Ultra birth, canonical identity, canonical memory lineage, and the operational reasoning transcript; legacy identity and memory tables remain quarantined for convergence/removal. |
| `MemoryOrganDatabase` | `9` | Derived memory organs, schedules, links, projects, agents, the preserved ProjectVault transactional outbox, and the durable cognitive-migration operation journal. |

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

1. read verified canonical input through the F1-A consumer boundary;
2. recover the durable cognitive-migration journal and stop on blocked or incomplete recovery;
3. converge the verified legacy memory lineage into Genesis Ultra;
4. recover pending ProjectVault outbox operations;
5. stop if any ProjectVault operation is blocked;
6. bootstrap the remaining runtime from the verified identity.

Cognitive-migration planning accepts only verified payloads whose provenance uses a
recognized living-memory or legacy-import note schema. Missing or unknown semantics,
`chat_noise`, and events emitted by the cognitive-migration protocol itself are excluded.
Operational receipts cannot feed new migration proposals either directly or indirectly:
plan identity, proposal payload, and the planning anchor depend only on the selected
eligible source set. Receipt append/reuse is transient recovery evidence and cannot change
the committed content-addressed owner result.

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

The legacy `memory_events` table is read-only in schema v15: insert, update, and delete are
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
| Intuitive | Active: bounded, local, and deterministic. |
| Deliberative | Blocked: research candidate only. The current 120-case evidence failed the quality gate and observed 40 false accepts; provenance, reproducibility, certification, signature, and personal installation authorization are also missing. |
| Metacognitive | Not registered. Requires a separate activation review. |
| Hybrid generative authority | Disabled. Deterministic authority remains fail-closed. |

The temporary external provider returns a typed `AuxiliaryAdvisory`. That object cannot
become `finalReply`, cannot be spoken as Morimil, and has no identity or memory authority.
The external boundary remains temporary and incomplete until F4 closes.

## Web and security boundaries

Remote JavaScript was removed from the research bridge and both isolated readers. The
remaining JavaScript-enabled production boundary is the packaged local Canvas governed by
issue #127 and `WebViewSecurityContractTest`. `SafeHttpTransport` serves user-selected
public HTTPS origins under the platform PKI and the fail-closed public-origin contract in
issue #132; it is not authorized for a stable first-party API that could use pinning.

The last authenticated dashboard report showed only CodeQL alerts #37 and #33 open. Their
technical decisions are documented, but this contract does not claim that GitHub has
recorded the required `won't fix` dispositions. Dependabot-alert activation and its initial
count are also not evidenced, and the current Secret-scanning alert count has not been
archived. These are STOP S5 blockers, not completed controls.

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
| Body export, restore, and succession | Not implemented |
| Production release or beta | Not authorized |

## Phase status at the audited baseline

| Phase | Evidence-backed state |
| --- | --- |
| STOP | Closing, not closed: S1-S4 are complete. S5 code changes are merged and #126/#128 are closed, but CodeQL #37/#33 dispositions, Dependabot-alert activation/triage, and the Secret-scanning count still require panel evidence. |
| F1 | Core Genesis Ultra identity, chat, startup gate, and bootstrap are implemented. Issue #86 remains open because rest-cycle and recall bootstrap still declare `WAITING_FOR_CANONICAL_MEMORY_ADAPTER`. |
| F2 | Closed: canonical verified memory, corruption quarantine, legacy convergence, and explicit Guardian-approved transcript promotion are implemented. |
| F3.1 | Implemented: ProjectVault transactional outbox and recovery. |
| F3.2-F3.3 | Open: common cross-database operation protocol and irreversible legacy removal. |
| F4 | Open: replace hidden provider-controlled continuation with sovereign, durable continuation chosen by Morimil; apply an auditable Body egress policy for already-authorized resources; quarantine legacy transcripts; complete the durable turn lifecycle; and bound Canvas downloads. Typed auxiliary output and remote-JavaScript removal are already present and must be preserved, not rebuilt. |
| F5 | Open: signed encrypted export, dry-run restore, and single-writer Body succession. |
| F6 | Open: complete E2E lifecycle evidence, real physical ceremony, and scalable canonical-memory reads. |
| F7 | Open: rights policy, reproducible offline build, independent review, and branch/document hygiene. |

This contract must be updated in the same PR whenever a change alters a listed runtime
authority, store version, allowlist, gate, or capability state.
