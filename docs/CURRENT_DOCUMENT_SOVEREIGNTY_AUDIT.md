# Document status: CURRENT

# Current document sovereignty audit

## Executable truth

- Protected `main`: `ba6ffa4f9ddc9189ded47e231ad1f8bc962e612d`.
- Previous protected main: `7e98d3345d7cc3fbf1983babd35b61ff5c523208`.
- PR `#149`: closed and merged by squash.
- Audited source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
- Merge commit: `ba6ffa4f9ddc9189ded47e231ad1f8bc962e612d`.

PR #149 is historical integration evidence. It is not an active draft, an isolated candidate, or an unmerged capability.

## Document hierarchy

Mutable documents whose first non-empty line is `# Document status: CURRENT` describe the executable repository truth. Historical plans, candidate reports, audit packages, superseded baselines, branch heads, and pre-merge gate language remain evidence of how a decision was reached, but they do not override protected `main`.

The audited source head records provenance. The squash merge commit records the executable state. Those identifiers must not be conflated.

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

Protected `main` now includes MemoryOrganDatabase version 9 and the durable COG-001 through COG-004 protocol. The protocol consumes canonical identity and memory only through `CanonicalConsumerReadPort`, uses `CanonicalCognitiveMigrationCommitPort` for its specialized canonical writer, and does not create a second identity authority.

ProjectVault remains a separate protected protocol and was not rewritten by PR #149.

F3.2 is closed only for the bounded COG-001 through COG-004 scope. F3.3 remains open. F4 through F6 remain open and no Body succession, export/restore, production release, or continuity proof is implied.

## Residual hardening

The following remain visible as non-blocking hardening, not as closed defects and not as merge blockers:

- a Room-backed concurrent regression with two coordinator instances;
- a failed-rollback fixture with a pre-existing non-null `sha256:*` snapshot;
- removal of the redundant `rollbackEventHash` API parameter;
- direct vulnerable UPDATE-trigger replacement coverage.

## Enforcement

`CurrentDocumentSovereigntyContractTest` rejects sovereignty transfers and materially stale post-merge statements in this audit. Any future CURRENT update must preserve the distinction between executable main, audited provenance, historical preparation, bounded phase closure, and still-open work.
