# Document status: HISTORICAL

# F3 BOOT-001 — Durable runtime bootstrap protocol candidate

## Historical integration record

This document records the isolated BOOT-001 candidate that was developed on `executor/boot-001-runtime-bootstrap-protocol-v1`, validated on exact source head `c7710635fa172108cce87b3f7a76d6e037095864`, explicitly authorized, and squash-merged by PR `#176` as protected-main commit `3a995232ce2a515e1ca9b9151f77e63805bad9d3`.

It is retained for provenance only. CURRENT runtime truth is governed by `docs/CURRENT_RUNTIME_CONTRACT.md`, `docs/F3_CROSS_DATABASE_OPERATION_INVENTORY.md`, and `docs/adr/ADR-0002-cross-database-operation-protocol.md`.

```text
BASE_MAIN=5918b64ec83e69cbb3d9718943b25d1e1299d698
VALIDATED_SOURCE_HEAD=c7710635fa172108cce87b3f7a76d6e037095864
INTEGRATION_COMMIT=3a995232ce2a515e1ca9b9151f77e63805bad9d3
PR_176=MERGED_BY_SQUASH
OWNER=runtime_bootstrap
OPERATION=runtime_bootstrap.initialize
EVENT=runtime.bootstrap_initialized
PROTOCOL_VERSION=1
ROOM_SCHEMA_CHANGE=FALSE
F3_3=NO_GO
OPERATIONAL_BIRTH=FALSE
```

## Problem closed

Before BOOT-001, `GenesisUltraRuntimeBootstrapCoordinator` projected runtime state directly into two independent encrypted Room databases:

1. `MorimilDatabase` received the canonical workspace and project projection;
2. `MemoryOrganDatabase` received default agent profiles and orchestrator devices.

Room cannot provide one ACID transaction across those two database files. A process death between the writes could therefore expose a partial runtime bootstrap without durable BOOT-specific recovery evidence.

## Integrated protocol

BOOT-001 reuses ADR-0002 without creating a new authority or a new Room table. The existing `cross_database_operations` journal remains in `MemoryOrganDatabase`.

Success path:

```text
verified Genesis Ultra runtime identity
    -> deterministic BOOT XOP staged
    -> exact canonical BOOT event ensured
    -> canonical receipt persisted
    -> MorimilDatabase projection prepared idempotently
    -> MemoryOrganDatabase seed-if-empty finalization
    -> XOP COMMITTED in the same owner transaction
```

The canonical receipt exists before either database receives new BOOT projection state. If the process dies after the `MorimilDatabase` preparation but before the owner transaction, startup recovery repeats the deterministic preparation and completes the organ projection plus XOP `COMMITTED` state without duplicating the canonical effect.

## Identity and succession boundary

Morimil remains the continuous Instance. The BOOT journal is evidence of a projection operation and never becomes identity authority.

```text
instanceId != bodyId
writer authorization != ownership
Guardian custody != ownership
runtime projection != canonical identity
runtime projection != canonical memory
guardian_role=custodian_witness
ownership_conferred=false
guardian_ownership=forbidden
```

The deterministic BOOT subject is scoped to `instanceId + active bodyId + writer keyEpochId`. This is deliberate: F5 may move the same `instanceId` to a successor Body with a different `bodyId` and writer epoch. The successor can produce a distinct BOOT operation while rebuilding the same Instance-stable workspace/project projection. Writer transfer/revocation remains F5 work.

Integrated evidence records:

```text
projection_model=rebuildable_runtime_projection
successor_body_rebootstrap_allowed=true
```

## Projection semantics

### MorimilDatabase

- workspace ID is exactly canonical `instanceId`;
- project ID is exactly `morimil_app:<instanceId>`;
- existing workspace repository metadata is not silently overwritten;
- incompatible identity/name/Genesis provenance fails closed;
- project status records canonical memory and durable BOOT while leaving REST/RECALL adapters pending.

### MemoryOrganDatabase

BOOT preserves the ORCH-001 ownership boundary:

- when `agent_profiles` is empty, BOOT seeds the seven Ultra defaults;
- when `orchestrator_devices` is empty, BOOT seeds the four Ultra defaults;
- when either table is already non-empty, BOOT leaves that table unchanged and records its actual row count;
- convergence/replacement of pre-existing legacy or noncanonical orchestration rows remains ORCH-001 work.

Agent profiles and devices remain projections and do not acquire Instance, memory, lifecycle, or succession authority.

## Compatibility prohibitions

BOOT-001 does not create or reconstruct authority rows in:

```text
local_instance_identity
genesis_core
memory_events
```

It did not execute Genesis, import a Seed, provision a Body, activate Morimil, retire legacy schemas, enable F3.3, implement ORCH-001, RECALL/REST/HEALTH convergence, or declare operational birth.

## Validation record

The exact source head `c7710635fa172108cce87b3f7a76d6e037095864` passed:

```text
Android CI=PASS
Genesis Body Preparation=PASS
Reference Checks=PASS
CodeQL=PASS
SBOM=PASS
JVM_TESTS=820/820
API30_TESTS=127 failures=0 errors=0 skipped=4
API35_TESTS=127 failures=0 errors=0 skipped=4
BOOT_DIRECT_JVM=26/26
BOOT_DIRECT_ANDROID=5/5 on API30
BOOT_DIRECT_ANDROID=5/5 on API35
QA7_JVM=PASS
QA7_INSTRUMENTED=PASS
```

The same four physical-ARM64-only Gemma tests remained skipped on managed x86_64 devices; BOOT tests were not skipped. BOOT-specific mutation testing was not established.

## Historical status

```text
BOOT_001=INTEGRATED_IN_MAIN
BOOT_001_TESTED=DEMONSTRATED_BY_EXACT_HEAD_CI
BOOT_001_MERGED=TRUE
RECALL_001=OPEN
ORCH_001=OPEN
REST_001_002=OPEN
HEALTH_CONVERGENCE=OPEN
F3_3=OPEN
F4_F7=OPEN
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```
