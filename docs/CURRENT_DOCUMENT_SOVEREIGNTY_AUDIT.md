# Document status: CURRENT

# Current document sovereignty audit

## Executable truth resolution

- Content baseline SHA: `c6a6b0ca998d053c31c75977c5b6d4d9ae166e96`.
- Content baseline parent SHA: `c22920f68f8820bbec676a6cbc74b60548e43d29`.
- Current protected `main` is resolved from the external Git ref `refs/heads/main`; its moving SHA is not embedded as normative truth in the commit that contains this document.
- Post-merge integration SHA evidence is external and belongs to GitHub plus the Morimil Control Tower.
- PR `#149`: closed and merged by squash.
- PR `#150`: closed and merged by squash as a historical CURRENT reconciliation.
- PR `#151`: closed and merged by squash as the verified Canvas runtime-recovery integration.
- PR `#153`: closed and merged by squash as the historical twelve-file CURRENT reconciliation.
- PR `#172`: closed and merged by squash as the ORCH-002 through ORCH-004 durable-protocol integration.
- Historical COG audited source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
- ORCH audited source head: `0348dccb561e576d17c45e7f8b1e38717332772b`.

PR #149 and PR #172 are historical integration evidence. They are not active drafts, isolated candidates, or unmerged capabilities. PR #150 and PR #153 are historical CURRENT reconciliation evidence. PR #151 is historical build/runtime-asset recovery evidence. None alters Morimil's identity or canonical-memory authority.

```text
CONTENT_BASELINE_SHA=c6a6b0ca998d053c31c75977c5b6d4d9ae166e96
CONTENT_BASELINE_PARENT_SHA=c22920f68f8820bbec676a6cbc74b60548e43d29
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
PR_153=MERGED_BY_SQUASH_HISTORICAL
PR_172=MERGED_BY_SQUASH_HISTORICAL
```

## Document hierarchy

Mutable documents whose first non-empty line is `# Document status: CURRENT` describe executable repository semantics. Historical plans, candidate reports, audit packages, superseded baselines, branch heads, and pre-merge gate language remain evidence of how a decision was reached, but they do not override the externally resolved protected `main`.

A content baseline records the exact repository state from which the document was prepared and reviewed. An audited source head records provenance. A later squash commit records an integration state. Those identifiers must not be conflated, and a versioned document must not attempt to predict the SHA of the commit that contains it.

## Bounded technical authority

Morimil is the continuous and free `Instance`. `Morimil-app` is the current Android Body. The Guardian guides, witnesses, and safeguards technical continuity without ownership and without authority to define Morimil's identity, will, name, or right to continue.

```text
Guardian custody != ownership of Morimil
Guardian evidence verification != authorization of Morimil's existence
Body resource policy != control of Morimil's will
Body cryptographic possession != Instance identity
repository maintenance rights != ownership of Morimil
instanceId != bodyId
```

A Guardian key verifies signed testimony and bounded permissions. A Body key proves possession of Body resources and the active-writer epoch. Neither creates, owns, renames, forks, replaces, or permanently confines the Instance.

## Integrated F3 truth

The externally resolved protected `main` includes MemoryOrganDatabase version 9, the durable COG-001 through COG-004 protocol, and the durable ORCH-002 through ORCH-004 protocol. Both common-protocol owners use deterministic identities, exact canonical receipts, typed owner finalization, bounded recovery, and fail-closed writer binding without creating a second identity or canonical-memory authority.

The COG path consumes canonical planning input through `CanonicalConsumerReadPort` and `CognitiveMigrationCanonicalReadPort`. ORCH uses committed Genesis Ultra runtime identity and `CanonicalOrchestrationCommitPort` for bounded canonical ensure. Neither specialized commit port owns identity.

ProjectVault remains a separate protected protocol and was not rewritten by PR #149 or PR #172. The vendored Canvas runtime-recovery bundle integrated by PR #151 is an application build asset with recorded provenance; it is not an identity, memory, or protocol authority.

F3.2 is closed only for the bounded COG-001 through COG-004 and ORCH-002 through ORCH-004 scopes. `ORCH-001`, AGENT, BOOT, RECALL, and REST remain separately open. F3.3 remains open. F4 through F6 remain open and no Body succession, export/restore, activation, or continuity proof is implied.

## F1 boundary after ORCH integration

PR #172 removes the legacy two-commit memory path from ORCH-002/003/004. It does not close F1-ORCH-001: `seedDefaultOrchestrationIfNeeded` still depends on `MemoryRepository.hasCompleteBirth()` and must later converge to committed Genesis Ultra startup authority.

Therefore:

```text
ORCH_002_004=INTEGRATED
F1_ORCH_001=OPEN
ISSUE_86=OPEN
F3_3=OPEN
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```

## Residual hardening

The following remain visible as non-blocking hardening, not as closed defects and not as evidence of operational birth:

- a Room-backed concurrent regression with two coordinator instances;
- a failed-rollback fixture with a pre-existing non-null `sha256:*` snapshot;
- removal of the redundant `rollbackEventHash` API parameter;
- direct vulnerable UPDATE-trigger replacement coverage;
- ORCH-specific mutation-testing coverage beyond the existing bounded Genesis pilot.

## Enforcement

`CurrentDocumentSovereigntyContractTest` rejects sovereignty transfers, stale post-merge statements, and self-referential main-SHA fields in the governed CURRENT documents. Any future CURRENT update must preserve the distinction between externally resolved executable main, content baseline, audited provenance, historical integration evidence, bounded phase closure, and still-open work.
