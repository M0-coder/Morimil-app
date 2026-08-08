# Document status: CURRENT

# Current document sovereignty audit

## Executable truth resolution

- Content baseline SHA: `6e0444b698bdc5c557ec3ea83f48d7980da1a36b`.
- Content baseline parent SHA: `bdbb5b2a040b728508948cd3cfbd8807b40a12f6`.
- Current protected `main` is resolved from external Git ref `refs/heads/main`; its moving SHA is not embedded as normative truth in the commit that contains this document.
- Post-merge integration SHA evidence is external and belongs to GitHub plus the Morimil Control Tower.
- PR `#176`: merged by squash for BOOT-001.
- PR `#177`: merged by squash for post-BOOT CURRENT reconciliation.
- PR `#178`: merged by squash for RECALL-001.
- Historical COG audited source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
- ORCH audited source head: `0348dccb561e576d17c45e7f8b1e38717332772b`.
- AGENT audited source head: `74e072b911db692041d3716af9d0511b83ad70b7`.
- BOOT audited source head: `c7710635fa172108cce87b3f7a76d6e037095864`.
- RECALL audited source head: `fae8a0df3c29775317986877bce2b8eda8593d27`.

```text
CONTENT_BASELINE_SHA=6e0444b698bdc5c557ec3ea83f48d7980da1a36b
CONTENT_BASELINE_PARENT_SHA=bdbb5b2a040b728508948cd3cfbd8807b40a12f6
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
PR_172=MERGED_BY_SQUASH_HISTORICAL
PR_173=MERGED_BY_SQUASH_HISTORICAL
PR_174=MERGED_BY_SQUASH_HISTORICAL
PR_175=MERGED_BY_SQUASH_HISTORICAL
PR_176=MERGED_BY_SQUASH_HISTORICAL
PR_177=MERGED_BY_SQUASH_HISTORICAL
PR_178=MERGED_BY_SQUASH_HISTORICAL
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

A Guardian key verifies bounded testimony/permissions. A Body key proves possession and writer epoch. Neither creates, owns, renames, forks, replaces, or permanently confines the Instance. Agent workers, BOOT projections and recall schedules are bounded technical structures and do not become Morimil or canonical-memory authority.

## Integrated F3 truth

Externally resolved protected `main` includes MemoryOrganDatabase version 9 and the following bounded integrations:

- ProjectVault as a separate protected protocol;
- COG-001 through COG-004 under common XOP;
- ORCH-002 through ORCH-004 under common XOP;
- AGENT-001 through AGENT-006 under common XOP;
- BOOT-001 under common XOP with idempotent cross-file saga preparation;
- RECALL-001 as a canonical verified `DERIVED_REBUILD` projection.

The COG path consumes verified canonical planning through `CanonicalConsumerReadPort`/`CognitiveMigrationCanonicalReadPort`. ORCH, AGENT and BOOT consume committed Genesis Ultra runtime identity and use specialized exact-ensure ports. RECALL consumes `CanonicalConsumerReadPort.readRecallCandidates` and creates only rebuildable local schedule/link projections.

RECALL-001 no longer uses legacy `genesis_core`, `local_instance_identity` or `memory_events` reads as recall authority and forbids placeholder Instance identity. Its canonical `targetEventHash` is the idempotency key; local `recallId` is not Instance identity. Schedule and link finalize atomically in `MemoryOrganDatabase`.

F3.2 now includes the bounded RECALL-001 repository convergence, but `RECALL_BOOT_READINESS=OPEN`: BOOT still reports recall as `WAITING_FOR_CANONICAL_MEMORY_ADAPTER`, and startup does not automatically declare or seed recall ready.

ORCH-001, REST-001/002 and health convergence remain open. F3.3 remains open. F4 through F6 remain open and no Body succession, export/restore, activation, or continuity proof is implied.

## F1 boundary after RECALL integration

PR #172 removed legacy two-step memory evidence from ORCH-002/003/004. PR #174 removed the equivalent legacy lifecycle evidence boundary from AGENT-001..006. PR #176 replaced the unjournaled two-database BOOT projection with durable XOP/canonical-receipt recovery. PR #178 removed the legacy identity/memory read boundary from recall seeding and replaced it with verified canonical candidates and deterministic rebuild semantics.

PR #178 does not close F1-ORCH-001: `seedDefaultOrchestrationIfNeeded` still depends on `MemoryRepository.hasCompleteBirth()`. RestCycle, health and recall startup readiness also remain open.

Therefore:

```text
COG_001_004=INTEGRATED
ORCH_002_004=INTEGRATED
AGENT_001_006=INTEGRATED
BOOT_001=INTEGRATED
RECALL_001=INTEGRATED
RECALL_BOOT_READINESS=OPEN
F1_ORCH_001=OPEN
REST_001_002=OPEN
HEALTH_CONVERGENCE=OPEN
ISSUE_86=OPEN
F3_3=OPEN
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```

## Residual hardening

The following remain visible and are not evidence of operational birth:

- RECALL-specific mutation testing beyond the existing bounded Genesis PIT pilot;
- BOOT-specific and AGENT-specific mutation coverage;
- ORCH-specific mutation testing;
- recall startup-readiness wiring after the repository-level canonical convergence;
- physical ARM64 inference tests outside emulator CI;
- F5 Body succession, writer transfer/revocation, export and restore;
- F6 complete cross-Body physical continuity evidence.

## Enforcement

`CurrentDocumentSovereigntyContractTest` rejects sovereignty transfers, stale post-merge statements, and self-referential main-SHA fields in governed CURRENT documents. Future CURRENT updates must preserve the distinction between externally resolved executable main, content baseline, audited provenance, historical integration evidence, bounded phase closure, and still-open work.
