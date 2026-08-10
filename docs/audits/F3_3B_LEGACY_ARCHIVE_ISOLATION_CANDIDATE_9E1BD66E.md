# Document status: PROPOSAL

# F3.3-B legacy archive isolation candidate — baseline `9e1bd66e6c0aceccf1c126acfa2e5c960648ffb6`

This document records a bounded F3.3-B implementation candidate. It is not protected-main evidence, does not authorize merge, and does not authorize F3.3-C or physical schema removal.

## Baseline

```text
REPOSITORY=M0-coder/Morimil-app
BASE_MAIN=9e1bd66e6c0aceccf1c126acfa2e5c960648ffb6
PHASE=F3.3-B
ROOM_VERSION=15
DROP_LEGACY_TABLES=false
F3_3_C_AUTHORIZED=false
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```

## Boundary

F3.3-A removed legacy identity and living-memory presentation from normal product runtime. F3.3-B narrows the remaining physical archive capability without deleting the archive needed for upgrade safety.

The normal `MemoryDao` must not expose legacy birth, identity, `memory_events`, or memory-snapshot capabilities. Remaining legacy reads are confined to:

- `LegacyBirthConflictProbe`: count-only anti-double-birth evidence for birth preparation, atomic birth commit, runtime bootstrap, and convergence safety;
- `LegacyMemoryArchiveReadPort`: read-only full-chain input for deterministic one-way convergence into Genesis Ultra;
- `LegacyArchiveReadDao`: SELECT-only Room implementation of those capabilities.

No normal UI, memory presentation, REST, RECALL, Health, reasoning, or project runtime path may depend on these boundaries.

## Preserved migration evidence

F3.3-B intentionally preserves:

- `local_instance_identity` physical rows;
- `genesis_core` physical rows;
- frozen `memory_events` rows;
- legacy memory snapshot schema residue;
- v15 read-only `memory_events` triggers;
- `LegacyMemoryConvergenceEntity` state;
- `LegacyMemoryImportEntity` legacy-to-canonical mappings;
- deterministic convergence before canonical bootstrap.

These are not reclassified as living identity or memory authority.

## Prohibited in this phase

```text
Room version bump
DROP TABLE
DELETE legacy archive rows
new legacy writer
new memory authority
removal of convergence evidence
release
Operational Birth
```

## Required candidate evidence

The candidate is not PASS until the exact final PR HEAD proves:

- compilation/build;
- JVM tests;
- lint/static analysis;
- API30/API35 managed-device tests;
- QA coverage ratchets;
- CodeQL;
- SBOM;
- Reference Checks;
- architecture contract proving normal `MemoryDao` cannot express legacy capabilities;
- architecture contract proving the archive DAO is read-only;
- startup ordering keeps legacy convergence before canonical bootstrap.

```text
F3_3_B_STATUS=VALIDATING
F3_3_FULL_CLOSURE=OPEN
MERGE_AUTHORIZED=false
```
