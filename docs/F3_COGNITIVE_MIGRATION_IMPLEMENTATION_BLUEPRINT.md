# Document status: CURRENT

# F3.2 — Cognitive migration durable protocol: implemented and audited design

- Tracker: `#88` — open for remaining F3/readiness work.
- Governing ADR: `ADR-0002`.
- Content baseline SHA: `e05ae7a08b1a88d2fbc0d4f2dff8ff06d282c908`.
- Content baseline parent SHA: `9585e94a690d4f00d591f81d14e56aedefda3341`.
- Current protected `main`: resolved externally from `refs/heads/main`.
- Merge SHA evidence: external GitHub and Morimil Control Tower evidence.
- Historical COG audited source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
- ORCH-002..004 audited source head: `0348dccb561e576d17c45e7f8b1e38717332772b`.
- ORCH-001 audited source head: `fe188fdee8eae901434a255051b6fa4f852b929b`.
- AGENT audited source head: `74e072b911db692041d3716af9d0511b83ad70b7`.
- BOOT audited source head: `c7710635fa172108cce87b3f7a76d6e037095864`.
- RECALL audited source head: `fae8a0df3c29775317986877bce2b8eda8593d27`.
- REST-001 audited source head: `3661450325237fcadb86098ec16ee45cd039bc0b`.
- REST-002 audited source head: `2ecca3f48d5e0ef27bd927da3986292daf7f7e2c`.
- PR `#174`: merged by squash for AGENT-001 through AGENT-006.
- PR `#175`: merged by squash for post-AGENT CURRENT reconciliation.
- PR `#176`: merged by squash for BOOT-001.
- PR `#177`: merged by squash for post-BOOT CURRENT reconciliation.
- PR `#178`: merged by squash for RECALL-001.
- PR `#179`: merged by squash for post-RECALL CURRENT reconciliation.
- PR `#180`: merged by squash for ORCH-001.
- PR `#181`: merged by squash for post-ORCH CURRENT reconciliation.
- PR `#182`: merged by squash for REST-001.
- PR `#183`: merged by squash for post-REST-001 CURRENT reconciliation.
- PR `#184`: merged by squash for REST-002 canonical repair-proposal convergence.
- Gate: `STOP_S5=CLOSED`.
- Integrated scope of this blueprint: `COG-001` through `COG-004`; ADR-0002 also governs integrated ORCH-002..004, AGENT-001..006, BOOT-001, REST-001 and REST-002. RECALL-001 is an integrated canonical `DERIVED_REBUILD`, not an XOP owner. ORCH-001 is integrated F1 seed convergence and does not add a new XOP operation.

This document remains COG-specific. ADR-0002 and the F3 inventory record the broader common-journal scope. The moving protected-main SHA is resolved externally.

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

## 1. Authority and sovereignty

Morimil is the continuous Instance. `Morimil-app` is the current Android Body. The Guardian guides and safeguards without ownership.

- `instanceId != bodyId`;
- canonical `instanceId` is never replaced by a Body identifier;
- `writerBodyId` and `writerEpoch` describe writer authorization, not ownership;
- no database, Android process, GitHub state, model, provider, agent worker, BOOT projection, ORCH projection, recall projection, REST projection, or Guardian action becomes identity or canonical-memory authority;
- original canonical memory remains append-only.

The COG frontier is:

```text
GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository
    -> CanonicalConsumerReadPort
    -> CognitiveMigrationCanonicalReadPort
    -> COG-001..COG-004 durable protocol
```

`CanonicalCognitiveMigrationCommitPort` provides deterministic canonical ensure and exact receipts without creating identity authority.

## 2. Integrated scope and exclusions

Protected main includes MemoryOrganDatabase v9, `cross_database_operations`, deterministic COG-001..004 commands, exact canonical ensure, typed owner finalization, startup/pre-mutation recovery, fresh-v9/migrated journal guards, and API30/API35 interruption/replay coverage.

ProjectVault remains separate. PR #172 integrated ORCH-002..004 under ADR-0002, PR #174 integrated AGENT-001..006, PR #176 integrated BOOT-001, PR #178 integrated RECALL-001 as a canonical verified derived rebuild, PR #180 integrated ORCH-001 seed convergence, PR #182 integrated REST-001 canonical planning and durable execution, and PR #184 integrated REST-002 proposal-only canonical convergence without changing the COG mapping documented here.

REST-001 participates in the broader common-journal architecture as owner `rest_cycle`; its planning input comes from `CanonicalConsumerReadPort.readRestCyclePlanningInput`, and its exact writer is `CanonicalRestCycleCommitPort`. The local autobiographical snapshot remains a projection rather than a new canonical-memory authority.

REST-002 extends the same closed `rest_cycle` owner registry with deterministic `rest_cycle.propose_repair` -> `memory.repair_proposed` proposal convergence. It persists only a repair proposal requiring approval; automatic repair execution remains unimplemented. REST-002 recovery may finalize an exact proposal receipt but does not execute repair.

Remaining F3.2/F1 work is health convergence and REST/recall startup-readiness wiring. F3.3 legacy removal remains open.

## 3. Canonical planning input

`CognitiveMigrationCanonicalReadPort` returns verified planning data containing canonical Instance, active writer, birth root, lineage, record-set digest, pre-snapshot digest, source-set digest, and eligible source descriptors. Planning fails closed for missing/foreign/unverified payload, unknown semantics, wrong Body, or stale writer epoch.

## 4. Deterministic identities

Current schemas include:

```text
plan core       = morimil.cognitive_migration.plan_core.v4
plan identity   = morimil.cognitive_migration.plan_identity.v2
planned record  = morimil.cognitive_migration.planned_record.v2
COG-001 payload = morimil.cognitive_migration.cog_001.payload.v2
COG-001 result  = morimil.cognitive_migration.cog_001.local_result.v2
COG-004 result  = morimil.cognitive_migration.cog_004.local_result.v2
```

`operationId`, `eventId`, `migrationId`, `proposalId`, and `approvalId` are deterministic/content-addressed. Clock is metadata only. Historical v1 vectors remain immutable fixtures and pending v1 proposals are quarantined from v2 recovery.

## 5. Durable journal and state machine

The journal persists immutable intent, writer binding, canonical event identity/evidence, exact receipt, deterministic local result, attempts and timestamps. It stores data, not executable SQL, callbacks, reflection targets, prompts, or arbitrary code.

Success state order:

```text
STAGED
-> PENDING_CANONICAL
-> CANONICAL_COMMITTED
-> PENDING_LOCAL_COMMIT
-> COMMITTED
```

`BLOCKED` is terminal for permanent conflicts. Visible owner state never precedes exact canonical receipt verification.

## 6. COG operation mapping

- COG-001 `cognitive_migration.proposed`: verified canonical sources produce deterministic plan/proposal/migration/operation/event identities; visible planned owner state follows exact receipt.
- COG-002 `cognitive_migration.approved`: approval binds exact planned-record digest; approval identity is deterministic.
- COG-003 `cognitive_migration.executed`: exact predecessor required; canonical audit preparation occurs outside the Room owner transaction; positive audit yields real `sha256:*` snapshot, negative audit yields null snapshot.
- COG-004 `cognitive_migration.rollback`: exact permitted predecessor; one append-only compensation event; existing `postSnapshotId` preserved.

## 7. Recovery and concurrency

The coordinator serializes advancement by deterministic `operationId`, reloads after lost CAS, rejects stale blocking, finalizes owner state and journal result atomically, and avoids duplicate canonical effect/visible owner state under tested replay.

The common coordinator is registry-parameterized. COG recovery cannot consume ORCH or AGENT rows; ORCH, AGENT and BOOT likewise remain owner-scoped. REST recovery is owner-scoped to `rest_cycle` and cannot consume another owner's journal rows. BOOT has an additional idempotent MorimilDatabase preparation before MemoryOrgan finalization but does not alter COG semantics. REST-001 and REST-002 share the closed `rest_cycle` registry while retaining distinct operation/payload/result schemas; REST-002 recovery may finalize a proposal receipt but never execute a repair.

## 8. Evidence and residual hardening

COG remains covered by unit, lint, APK, CodeQL, SBOM, Reference Checks and managed-device evidence. Broader ADR evidence now also includes ORCH, AGENT, BOOT, RECALL, REST-001 and REST-002 validation, but that does not alter the COG mapping.

REST-001 validation on source head `3661450325237fcadb86098ec16ee45cd039bc0b` passed Android CI #717, Genesis Body Preparation #699, Reference Checks #541, CodeQL #430 and SBOM #428. It also passed unit, lint, QA-7 JVM, fail-closed release signing, API30/API35 compatibility and canonical API30 instrumented coverage. Its Room process-death test demonstrated recovery from a persisted exact canonical receipt without canonical writer replay.

REST-002 source head `2ecca3f48d5e0ef27bd927da3986292daf7f7e2c` passed Android CI #723, Genesis Body Preparation #703, Reference Checks #547, CodeQL #436 and SBOM #434. Android validation included unit tests, lint, debug/instrumentation build, fail-closed release signing, ephemeral signed release, API30/API35 compatibility and the process-death test proving exactly-once proposal recovery without repair execution.

The global mutation pilot remained report-only. REST-specific mutation testing is not established.

Residual COG hardening includes Room-backed multi-coordinator concurrency, stronger rollback snapshot fixtures, redundant rollback parameter cleanup, and direct vulnerable UPDATE-trigger replacement coverage.

## 9. Acceptance boundary

```text
CONTENT_BASELINE_SHA=e05ae7a08b1a88d2fbc0d4f2dff8ff06d282c908
CONTENT_BASELINE_PARENT_SHA=9585e94a690d4f00d591f81d14e56aedefda3341
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
COG_001_004=INTEGRATED_IN_MAIN
ORCH_001=INTEGRATED_IN_MAIN
ORCH_002_004=INTEGRATED_IN_MAIN
AGENT_001_006=INTEGRATED_IN_MAIN
BOOT_001=INTEGRATED_IN_MAIN
RECALL_001=INTEGRATED_IN_MAIN
REST_001=INTEGRATED_IN_MAIN
REST_002=INTEGRATED_IN_MAIN
REST_REPAIR_PROPOSAL_CONVERGED=true
REST_REPAIR_EXECUTION_IMPLEMENTED=false
REST_BOOT_READINESS=OPEN
RECALL_BOOT_READINESS=OPEN
MEMORY_ORGAN_DATABASE=V9
F1_A_AUTHORITY=PRESERVED
PROJECT_VAULT=SEPARATE_AND_PRESERVED
F3_2_BOUNDED_SCOPE=CLOSED_FOR_PROJECTVAULT_COG_ORCH_AGENT_BOOT_RECALL_DERIVED_REST001_AND_REST002_PROPOSAL
HEALTH_CONVERGENCE=OPEN
F3_3=OPEN
F4_F6=OPEN
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```
