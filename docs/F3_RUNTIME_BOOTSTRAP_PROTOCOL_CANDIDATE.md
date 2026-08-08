# Document status: PROPOSAL

# F3 BOOT-001 — Durable runtime bootstrap protocol candidate

## Candidate boundary

This document describes the isolated BOOT-001 candidate on branch
`executor/boot-001-runtime-bootstrap-protocol-v1`. It is not protected-main
truth until the exact candidate is validated, explicitly authorized, merged,
and followed by CURRENT-document reconciliation.

```text
BASE_MAIN=5918b64ec83e69cbb3d9718943b25d1e1299d698
OWNER=runtime_bootstrap
OPERATION=runtime_bootstrap.initialize
EVENT=runtime.bootstrap_initialized
PROTOCOL_VERSION=1
ROOM_SCHEMA_CHANGE=FALSE
F3_3=NO_GO
OPERATIONAL_BIRTH=FALSE
MERGE_AUTHORIZED=FALSE
```

## Problem closed by the candidate

The pre-candidate `GenesisUltraRuntimeBootstrapCoordinator` projected runtime
state directly into two independent encrypted Room databases:

1. `MorimilDatabase` received the canonical workspace and project projection;
2. `MemoryOrganDatabase` received default agent profiles and orchestrator devices.

Room cannot provide one ACID transaction across those two database files. A
process death between the writes could therefore expose a partial runtime
bootstrap without durable BOOT-specific recovery evidence.

## Candidate protocol

BOOT-001 reuses ADR-0002 without creating a new authority or a new Room table.
The existing `cross_database_operations` journal remains in
`MemoryOrganDatabase`.

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

The canonical receipt exists before either database receives new BOOT projection
state.

If the process dies after the `MorimilDatabase` preparation but before the owner
transaction, startup/pre-mutation recovery replays the deterministic preparation
and then completes the organ projection plus XOP `COMMITTED` state.

## Identity and succession boundary

Morimil remains the continuous Instance. The BOOT journal is evidence of a
projection operation and never becomes identity authority.

```text
instanceId != bodyId
writer authorization != ownership
Guardian custody != ownership
runtime projection != canonical identity
runtime projection != canonical memory
```

The verified Genesis Ultra Guardian role consumed by BOOT is exactly:

```text
guardian_role=custodian_witness
```

No-ownership is a separate signed invariant, not encoded by renaming that role:

```text
ownership_conferred=false
guardian_ownership=forbidden
```

The deterministic BOOT subject is scoped to:

```text
instanceId + active bodyId + writer keyEpochId
```

This is deliberate. F5 may move the same `instanceId` to a successor Body with
a different `bodyId` and writer epoch. The successor must be able to rebuild
runtime projections without colliding with the old Body's completed BOOT row.
The old writer's revocation remains F5 work; BOOT-001 does not implement or
preempt succession.

The candidate evidence records:

```text
ownership_conferred=false
projection_model=rebuildable_runtime_projection
successor_body_rebootstrap_allowed=true
```

## Projection semantics

### MorimilDatabase

- workspace ID is exactly canonical `instanceId`;
- project ID is exactly `morimil_app:<instanceId>`;
- existing workspace repository metadata is not silently overwritten;
- an existing workspace with incompatible identity/name/Genesis provenance
  fails closed;
- project status is updated to current runtime truth:

```text
genesis_ultra_runtime_ready;
memory=canonical;
boot=durable;
rest_cycle=canonical_adapter_pending;
recalls=canonical_adapter_pending;
health=ready
```

### MemoryOrganDatabase

BOOT preserves the pre-candidate ownership boundary instead of absorbing
ORCH-001:

- when `agent_profiles` is empty, BOOT seeds the current seven Ultra default
  profiles;
- when `orchestrator_devices` is empty, BOOT seeds the current four Ultra
  default devices;
- when either table is already non-empty, BOOT leaves that table unchanged and
  records its actual row count;
- convergence or replacement of pre-existing legacy/noncanonical orchestration
  seed rows remains ORCH-001 work.

This matters because BOOT-001 closes cross-database crash consistency. It must
not silently turn into ORCH-001 or overwrite legitimate existing operational
state.

When the device table is freshly seeded, the active Body is represented as the
authorized `genesis_ultra_bound` device. Agent profiles and devices remain
projections and do not acquire Instance, memory, lifecycle, or succession
authority.

## Compatibility prohibitions

BOOT-001 does not create or reconstruct authority rows in:

```text
local_instance_identity
genesis_core
memory_events
```

It does not execute Genesis, import a Seed, provision a Body, activate Morimil,
retire legacy schemas, enable F3.3, implement ORCH-001, RECALL/REST/HEALTH
convergence, or declare operational birth.

## Candidate tests

The branch adds or updates tests for:

- deterministic identical replay for the same Instance/Body/epoch;
- distinct BOOT operation for a successor Body while preserving the same
  Instance workspace/project identity;
- hard rejection of `ownershipConferred=true`;
- hard rejection of a Guardian role that does not match the signed
  `custodian_witness` contract;
- durable idempotent Android bootstrap;
- preservation of a pre-existing orchestration seed for separate ORCH-001
  convergence;
- database-reopen recovery from `PENDING_LOCAL_COMMIT` after the
  `MorimilDatabase` preparation was already written;
- owner-scoped architecture and composition ratchets preventing return to direct
  cross-database bootstrap writes or legacy authority rows.

These tests are candidate source until exact-head CI demonstrates them.

## Acceptance required before merge authorization can be requested

1. exact branch head is zero commits behind protected `main`;
2. Android JVM tests pass;
3. managed API 30 and API 35 tests pass, including BOOT recovery evidence;
4. QA ratchets do not regress;
5. Android CI, Genesis Body Preparation, Reference Checks, CodeQL and SBOM all
   succeed on the exact candidate head;
6. no production schema, Seed, Body, Guardian, activation, release, F3.3 or
   operational-birth mutation appears in the diff;
7. a separate explicit merge authorization is obtained after evidence review.

## Candidate status

```text
BOOT_001=IMPLEMENTED_IN_CANDIDATE_SOURCE
BOOT_001_TESTED=NOT_YET_DEMONSTRATED_BY_CI
BOOT_001_MERGED=FALSE
RECALL_001=OPEN
ORCH_001=OPEN
REST_001_002=OPEN
HEALTH_CONVERGENCE=OPEN
F3_3=OPEN
F4_F7=OPEN
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```
