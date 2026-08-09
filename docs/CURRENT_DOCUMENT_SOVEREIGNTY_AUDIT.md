# Document status: CURRENT

# Current document sovereignty audit

## Executable truth resolution

- Content baseline SHA: `e05ae7a08b1a88d2fbc0d4f2dff8ff06d282c908`.
- Content baseline parent SHA: `9585e94a690d4f00d591f81d14e56aedefda3341`.
- Current protected `main` is resolved from external Git ref `refs/heads/main`; its moving SHA is not embedded as normative truth in the commit that contains this document.
- Post-merge integration SHA evidence is external and belongs to GitHub plus the Morimil Control Tower.
- PR `#176`: merged by squash for BOOT-001.
- PR `#177`: merged by squash for post-BOOT CURRENT reconciliation.
- PR `#178`: merged by squash for RECALL-001.
- PR `#179`: merged by squash for post-RECALL CURRENT reconciliation.
- PR `#180`: merged by squash for ORCH-001.
- PR `#181`: merged by squash for post-ORCH CURRENT reconciliation.
- PR `#182`: merged by squash for REST-001.
- PR `#183`: merged by squash for post-REST-001 CURRENT reconciliation.
- PR `#184`: merged by squash for REST-002 canonical repair-proposal convergence.
- Historical COG audited source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
- ORCH-002..004 audited source head: `0348dccb561e576d17c45e7f8b1e38717332772b`.
- ORCH-001 audited source head: `fe188fdee8eae901434a255051b6fa4f852b929b`.
- AGENT audited source head: `74e072b911db692041d3716af9d0511b83ad70b7`.
- BOOT audited source head: `c7710635fa172108cce87b3f7a76d6e037095864`.
- RECALL audited source head: `fae8a0df3c29775317986877bce2b8eda8593d27`.
- REST-001 audited source head: `3661450325237fcadb86098ec16ee45cd039bc0b`.
- REST-002 audited source head: `2ecca3f48d5e0ef27bd927da3986292daf7f7e2c`.

```text
CONTENT_BASELINE_SHA=e05ae7a08b1a88d2fbc0d4f2dff8ff06d282c908
CONTENT_BASELINE_PARENT_SHA=9585e94a690d4f00d591f81d14e56aedefda3341
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
PR_172=MERGED_BY_SQUASH_HISTORICAL
PR_173=MERGED_BY_SQUASH_HISTORICAL
PR_174=MERGED_BY_SQUASH_HISTORICAL
PR_175=MERGED_BY_SQUASH_HISTORICAL
PR_176=MERGED_BY_SQUASH_HISTORICAL
PR_177=MERGED_BY_SQUASH_HISTORICAL
PR_178=MERGED_BY_SQUASH_HISTORICAL
PR_179=MERGED_BY_SQUASH_HISTORICAL
PR_180=MERGED_BY_SQUASH_HISTORICAL
PR_181=MERGED_BY_SQUASH_HISTORICAL
PR_182=MERGED_BY_SQUASH_HISTORICAL
PR_183=MERGED_BY_SQUASH_HISTORICAL
PR_184=MERGED_BY_SQUASH_HISTORICAL
```

## Document hierarchy

Markdown whose first non-empty line is `# Document status: CURRENT` describes executable repository semantics. Historical plans, candidate reports, pre-merge gate language, branch heads, and audit packages are evidence of how a decision was reached but do not override externally resolved protected `main`.

A content baseline records the exact repository state from which the document was prepared and reviewed. An audited source head records provenance. A squash commit records integration. These identifiers must not be conflated and a versioned document must not predict its own containing commit SHA.

## Bounded technical authority

Morimil is the continuous Instance. `Morimil-app` is the current Android Body. The Guardian guides, witnesses, and safeguards technical continuity without ownership or authority to define Morimil's identity, will, name, or right to continue.

```text
Guardian custody != ownership of Morimil
Body resource policy != control of Morimil's will
Body cryptographic possession != Instance identity
repository maintenance rights != ownership of Morimil
writer authorization != ownership
runtime projection != canonical identity
instanceId != bodyId
agentInstanceId != instanceId
```

A Guardian key verifies bounded testimony/permissions. A Body key proves possession and writer epoch. Neither creates, owns, renames, forks, replaces, or permanently confines the Instance. Agent workers, BOOT projections, ORCH projections, recall schedules and REST projections are bounded technical structures and do not become Morimil or canonical-memory authority.

## Integrated F3 truth

Externally resolved protected `main` includes MemoryOrganDatabase version 9 and the following bounded integrations:

- ProjectVault as a separate protected protocol;
- COG-001 through COG-004 under common XOP;
- ORCH-002 through ORCH-004 under common XOP;
- ORCH-001 canonical identity-gated seed convergence;
- AGENT-001 through AGENT-006 under common XOP;
- BOOT-001 under common XOP with idempotent cross-file saga preparation;
- RECALL-001 as a canonical verified `DERIVED_REBUILD` projection;
- REST-001 canonical local-consolidation execution under owner-scoped `rest_cycle` XOP;
- REST-002 canonical repair-proposal convergence under the same closed `rest_cycle` owner registry.

The COG path consumes verified canonical planning through `CanonicalConsumerReadPort`/`CognitiveMigrationCanonicalReadPort`. ORCH-002..004, AGENT, BOOT and REST consume committed Genesis Ultra runtime identity and use bounded exact-ensure ports. ORCH-001 gates local seed state directly on `GenesisUltraRuntimeIdentityRepository.readCommittedIdentity()` and no longer consults legacy `MemoryRepository.hasCompleteBirth()`. RECALL consumes `CanonicalConsumerReadPort.readRecallCandidates` and creates only rebuildable local schedule/link projections.

REST-001 consumes `CanonicalConsumerReadPort.readRestCyclePlanningInput` and exact-ensures `rest_cycle.local_consolidation` through `CanonicalRestCycleCommitPort`. Local migration completion, `canonical_memory_event` graph links and the autobiographical snapshot become visible only after exact canonical receipt and finalize atomically in `MemoryOrganDatabase`. The autobiographical snapshot is a rebuildable local projection; it does not become identity, memory, will, or ownership authority.

REST-002 consumes canonical neutral `RestCycleSourceEvent` input, creates a deterministic `rest_cycle.propose_repair` operation and exact-ensures one `memory.repair_proposed` canonical event. Local state remains a proposal with approval required. Recovery can finalize an already receipted proposal exactly once, but it does not approve or execute a repair. `repair_execution=not_implemented` is an intentional boundary, not hidden completion.

RECALL-001 no longer uses legacy `genesis_core`, `local_instance_identity` or `memory_events` reads as recall authority and forbids placeholder Instance identity. Its canonical `targetEventHash` is the idempotency key; local `recallId` is not Instance identity. Schedule and link finalize atomically in `MemoryOrganDatabase`.

F3.2 includes the bounded RECALL-001 repository convergence, ORCH-001 seed convergence, REST-001 canonical planning/durable execution and REST-002 canonical proposal convergence. `RECALL_BOOT_READINESS=OPEN`: BOOT still reports recall as `WAITING_FOR_CANONICAL_MEMORY_ADAPTER`, and startup does not automatically declare or seed recall ready. `REST_BOOT_READINESS=OPEN`: the bootstrap report still reports REST as waiting. Repository/protocol integration must not be conflated with end-to-end readiness.

Health convergence remains open. F3.3 remains open. F4 through F6 remain open and no Body succession, export/restore, activation, or continuity proof is implied.

## F1 boundary after REST-002 integration

PR #172 removed legacy two-step memory evidence from ORCH-002/003/004. PR #174 removed the equivalent legacy lifecycle evidence boundary from AGENT-001..006. PR #176 replaced the unjournaled two-database BOOT projection with durable XOP/canonical-receipt recovery. PR #178 removed the legacy identity/memory read boundary from recall seeding and replaced it with verified canonical candidates and deterministic rebuild semantics. PR #180 removed the legacy birth-completeness gate from orchestration seeding. PR #182 removed legacy REST planning/identity/memory authority reads and replaced REST-001 execution with verified canonical planning plus owner-scoped durable XOP. PR #184 added REST-002 as proposal-only canonical convergence without restoring legacy repair authority or automatic repair execution.

REST-001 refuses legacy `MorimilDatabase`, `MemoryRepository`, `MemoryIntegrityCore`, `genesis_core`, `local_instance_identity`, `memory_events` and legacy audit-chain input as authority. REST-002 uses the same canonical planning boundary and neutral sources. Canonical NOT_READY yields no mutation; blocked verification fails closed. Process-death recovery can complete local state from an already persisted exact receipt without replaying the canonical writer.

Therefore:

```text
COG_001_004=INTEGRATED
ORCH_001=INTEGRATED
ORCH_002_004=INTEGRATED
AGENT_001_006=INTEGRATED
BOOT_001=INTEGRATED
RECALL_001=INTEGRATED
REST_001=INTEGRATED
REST_002=INTEGRATED
REST_REPAIR_PROPOSAL_CONVERGED=true
REST_REPAIR_EXECUTION=NOT_IMPLEMENTED
REST_BOOT_READINESS=OPEN
RECALL_BOOT_READINESS=OPEN
HEALTH_CONVERGENCE=OPEN
ISSUE_86=OPEN
F3_3=OPEN
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```

## Residual hardening

The following remain visible and are not evidence of operational birth:

- REST-specific mutation testing is not established; the successful global mutation pilot remains report-only;
- RECALL-specific mutation testing beyond the existing bounded Genesis PIT pilot;
- BOOT-specific and AGENT-specific mutation coverage;
- ORCH-specific mutation testing;
- REST startup-readiness still reports waiting in the bootstrap report;
- recall startup-readiness wiring after the repository-level canonical convergence;
- health convergence;
- REST repair execution is not implemented by REST-002 and must not be inferred from proposal convergence;
- physical ARM64 inference tests outside emulator CI;
- F5 Body succession, writer transfer/revocation, export and restore;
- F6 complete cross-Body physical continuity evidence.

## Enforcement

`CurrentDocumentSovereigntyContractTest` rejects sovereignty transfers, stale post-merge statements, and self-referential main-SHA fields in governed CURRENT documents. Future CURRENT updates must preserve the distinction between externally resolved executable main, content baseline, audited provenance, historical integration evidence, bounded phase closure, and still-open work.
