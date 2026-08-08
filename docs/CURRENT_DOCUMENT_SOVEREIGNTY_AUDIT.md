# Document status: CURRENT

# Current document sovereignty audit

## Executable truth resolution

- Content baseline SHA: `d577a75290d70f423f6e83bf237a8a453f3a534e`.
- Content baseline parent SHA: `9da342f2c147105ea882076f4ebc6ab5f5494190`.
- Current protected `main` is resolved from external Git ref `refs/heads/main`; its moving SHA is not embedded as normative truth in the commit that contains this document.
- Post-merge integration SHA evidence is external and belongs to GitHub plus the Morimil Control Tower.
- PR `#172`: merged by squash for ORCH-002 through ORCH-004.
- PR `#173`: merged by squash for post-ORCH CURRENT reconciliation.
- PR `#174`: merged by squash for AGENT-001 through AGENT-006.
- Historical COG audited source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
- ORCH audited source head: `0348dccb561e576d17c45e7f8b1e38717332772b`.
- AGENT audited source head: `74e072b911db692041d3716af9d0511b83ad70b7`.

```text
CONTENT_BASELINE_SHA=d577a75290d70f423f6e83bf237a8a453f3a534e
CONTENT_BASELINE_PARENT_SHA=9da342f2c147105ea882076f4ebc6ab5f5494190
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
PR_172=MERGED_BY_SQUASH_HISTORICAL
PR_173=MERGED_BY_SQUASH_HISTORICAL
PR_174=MERGED_BY_SQUASH_HISTORICAL
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
instanceId != bodyId
agentInstanceId != instanceId
```

A Guardian key verifies bounded testimony/permissions. A Body key proves possession and writer epoch. Neither creates, owns, renames, forks, replaces, or permanently confines the Instance. Agent workers are bounded ProjectVault workers and likewise do not become Morimil or canonical-memory authority.

## Integrated F3 truth

Externally resolved protected `main` includes MemoryOrganDatabase version 9 and the following bounded integrations:

- ProjectVault as a separate protected protocol;
- COG-001 through COG-004 under common XOP;
- ORCH-002 through ORCH-004 under common XOP;
- AGENT-001 through AGENT-006 under common XOP.

The COG path consumes verified canonical planning through `CanonicalConsumerReadPort`/`CognitiveMigrationCanonicalReadPort`. ORCH and AGENT consume committed Genesis Ultra runtime identity and use specialized exact-ensure ports (`CanonicalOrchestrationCommitPort`, `CanonicalAgentLifecycleCommitPort`). None owns identity.

AGENT lifecycle transitions no longer use `MemoryRepository.recordSystemMemoryEvent` as their canonical evidence path. New local owner state appears only after an exact canonical receipt. AGENT-003 requires canonical ORCH task approval. AGENT-006 quarantines the failed worker and creates its deterministic replacement in one local finalization.

F3.2 is closed only for the bounded integrated scopes above. `BOOT-001`, `RECALL-001`, `ORCH-001`, and `REST-001/002` remain separately open. F3.3 remains open. F4 through F6 remain open and no Body succession, export/restore, activation, or continuity proof is implied.

## F1 boundary after ORCH and AGENT integration

PR #172 removed legacy two-step memory evidence from ORCH-002/003/004. PR #174 removed the equivalent legacy lifecycle evidence boundary from AGENT-001..006. Neither closes F1-ORCH-001: `seedDefaultOrchestrationIfNeeded` still depends on `MemoryRepository.hasCompleteBirth()`.

Therefore:

```text
COG_001_004=INTEGRATED
ORCH_002_004=INTEGRATED
AGENT_001_006=INTEGRATED
F1_ORCH_001=OPEN
BOOT_001=OPEN
RECALL_001=OPEN
REST_001_002=OPEN
ISSUE_86=OPEN
F3_3=OPEN
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```

## Residual hardening

The following remain visible and are not evidence of operational birth:

- AGENT-specific mutation testing beyond the existing bounded Genesis PIT pilot;
- direct Android integration coverage for `AgentInstanceLifecycleRepository.kt`;
- durable cross-process AGENT serialization if Android multiprocess is introduced;
- ORCH-specific mutation testing;
- physical ARM64 inference tests outside emulator CI.

## Enforcement

`CurrentDocumentSovereigntyContractTest` rejects sovereignty transfers, stale post-merge statements, and self-referential main-SHA fields in governed CURRENT documents. Future CURRENT updates must preserve the distinction between externally resolved executable main, content baseline, audited provenance, historical integration evidence, bounded phase closure, and still-open work.
