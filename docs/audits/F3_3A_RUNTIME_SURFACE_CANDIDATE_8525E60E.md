# Document status: PROPOSAL

# F3.3-A — canonical runtime surface cut

Baseline protected main:

`8525e60e435eb624d5eb06e2c2e41151f8e2299c`

Branch:

`f3-3a/cut-legacy-runtime-surface-v1`

## Scope

F3.3-A removes productive/UI legacy identity and living-memory surfaces without performing irreversible Room schema removal.

This candidate:

- removes `GenesisReader` from production sources and the composition root;
- removes `LocalBirthState` and legacy birth compatibility methods from `MemoryRepository`;
- removes productive `localIdentity`, `genesisCore`, `memory_events` and `memory_snapshots` observation from `MorimilViewModel`;
- adds a read-only canonical presentation adapter over `CanonicalConsumerReadPort`;
- resolves Memory UI, recall targets, reviews and backlinks against verified canonical Genesis Ultra event hashes;
- updates Workspace/Genesis presentation so Android is a Body, not the Instance or identity owner;
- leaves legacy Room entities physically present and `memory_events` frozen for deterministic migration/convergence;
- keeps startup legacy convergence before canonical bootstrap.

## Explicit exclusions

```text
ROOM_SCHEMA_REMOVAL=false
DROP_LEGACY_TABLES=false
F3_3_B=false
F3_3_C=false
F3_3_FULL_CLOSURE=false
F4=false
F5=false
F6=false
OPERATIONAL_BIRTH=false
RELEASE=false
AUTO_MERGE=false
```

## Safety invariant

The legacy archive is not a runtime authority, but it remains migration evidence until a later F3.3-C migration can prove that historical identity/memory state has been irreversibly retired without creating a false clean-install state.

`LegacyMemoryConvergenceCoordinator` remains required for upgrades that still contain frozen legacy memory.

## Required validation before merge consideration

- compile/build;
- JVM tests;
- architecture contract tests;
- lint/static analysis;
- API 30/API 35 managed-device suite;
- coverage ratchets;
- dependency verification/reproducibility checks;
- CodeQL, SBOM and Reference Checks;
- confirm no Room version/schema change;
- confirm no `memory_events` writer reintroduced;
- confirm exact source HEAD for all evidence.

This document is not authorization to merge and does not declare F3.3 complete.
