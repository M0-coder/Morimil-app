# Document status: CURRENT

# F3.2 — Cognitive migration durable protocol: implemented and audited design

- Tracker: `#88` — open for full F1/F3.2 reaudit and later F3.3 work.
- Governing ADR: `ADR-0002`.
- Content baseline SHA: `c4b192b8f54b2422ce816dc3542d55adfd44510c`.
- Content baseline parent SHA: `9c7325e6f1a21d79b1c3fb58f0b5f81a828fc304`.
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
- Bootstrap-health audited source head: `f1697227241459f316bd562756e15ae3ce02c90d`.
- REST-BOOT-001 audited source head: `dd7a92a011fd4c453775df6ec307638b05313ec9`.
- Health legacy-consumer convergence audited source head: `6735e2d1febccf7da560d026d6ddd88f6ad82845`.
- RECALL-BOOT-001 audited source head: `20d834e1d438fd5883a76e9b45bcf21860e7db42`.
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
- PR `#186`: merged by squash for post-REST-002 CURRENT reconciliation without normative erosion.
- PR `#187`: merged by squash for dependency-derived bootstrap health instead of a static READY assignment.
- PR `#188`: merged by squash for REST boot-readiness canonical probing.
- PR `#189`: merged by squash for post-REST-readiness/bootstrap-Health CURRENT reconciliation.
- PR `#190`: merged by squash for Local Nervous System Health legacy-consumer convergence.
- PR `#191`: merged by squash for RECALL-BOOT-001 canonical read-only startup readiness.
- Gate: `STOP_S5=CLOSED`.
- Integrated scope of this blueprint: `COG-001` through `COG-004`; ADR-0002 also governs integrated ORCH-002..004, AGENT-001..006, BOOT-001, REST-001 and REST-002. RECALL-001 is an integrated canonical `DERIVED_REBUILD`, not an XOP owner. ORCH-001 is integrated F1 seed convergence and does not add a new XOP operation. Bootstrap health derivation, REST-BOOT-001, Local Nervous System read-only Health convergence and RECALL-BOOT-001 are integrated runtime/readiness changes and do not add XOP owners.

This document remains COG-specific. ADR-0002 and the F3 inventory record the broader common-journal scope. The moving protected-main SHA is resolved externally.

```text
CONTENT_BASELINE_SHA=c4b192b8f54b2422ce816dc3542d55adfd44510c
CONTENT_BASELINE_PARENT_SHA=9c7325e6f1a21d79b1c3fb58f0b5f81a828fc304
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
PR_186=MERGED_BY_SQUASH_HISTORICAL
PR_187=MERGED_BY_SQUASH_HISTORICAL
PR_188=MERGED_BY_SQUASH_HISTORICAL
PR_189=MERGED_BY_SQUASH_HISTORICAL
PR_190=MERGED_BY_SQUASH_HISTORICAL
PR_191=MERGED_BY_SQUASH_HISTORICAL
```

## 1. Authority and sovereignty

Morimil is the continuous Instance. `Morimil-app` is the current Android Body. The Guardian guides and safeguards without ownership.

- `instanceId != bodyId`;
- canonical `instanceId` is never replaced by a Body identifier;
- `writerBodyId` and `writerEpoch` describe writer authorization, not ownership;
- no database, Android process, GitHub state, model, provider, agent worker, BOOT projection, ORCH projection, recall projection, REST projection, Health projection, or Guardian action becomes identity or canonical-memory authority;
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

ProjectVault remains separate. PR #172 integrated ORCH-002..004 under ADR-0002, PR #174 integrated AGENT-001..006, PR #176 integrated BOOT-001, PR #178 integrated RECALL-001 as a canonical verified derived rebuild, PR #180 integrated ORCH-001 seed convergence, PR #182 integrated REST-001 canonical planning and durable execution, PR #184 integrated REST-002 proposal-only canonical convergence, PR #187 integrated dependency-derived bootstrap Health, PR #188 integrated REST startup readiness, PR #190 integrated Local Nervous System read-only Health convergence, and PR #191 integrated RECALL startup readiness without changing the COG mapping documented here.

REST-001 participates in the broader common-journal architecture as owner `rest_cycle`; its planning input comes from `CanonicalConsumerReadPort.readRestCyclePlanningInput`, and its exact writer is `CanonicalRestCycleCommitPort`. The local autobiographical snapshot remains a projection rather than a new canonical-memory authority.

REST-002 extends the same closed `rest_cycle` owner registry with deterministic `rest_cycle.propose_repair` -> `memory.repair_proposed` proposal convergence. It persists only a repair proposal requiring approval; automatic repair execution remains unimplemented. REST-002 recovery may finalize an exact proposal receipt but does not execute repair.

PR #187 derives the bootstrap Health report from dependency state and rejects inconsistent forged READY state. REST-BOOT-001 probes canonical REST planning read-only and promotes REST only after the existing Instance/Body/epoch/digest validation succeeds. RECALL-BOOT-001 probes canonical recall candidates read-only and promotes RECALL only after Instance/Body/epoch/snapshot/candidate validation succeeds. These readiness paths do not create a new identity source, canonical writer, or XOP owner.

F1 Local Nervous System legacy-consumer convergence is integrated as a read-only derived boundary. `LocalNervousSystemRepository.observeHealth` consumes `CanonicalConsumerReadPort.readHealthInput`, derives Health from verified canonical living-memory signals, and returns operational telemetry without writing canonical memory or legacy `memory_events`. It is not an ADR-0002 XOP owner and does not become memory authority.

Global `HEALTH_CONVERGENCE` remains open pending the full F1/F3.2 protected-main reaudit, not because RECALL startup readiness or Local Nervous System legacy authority remains open. Runtime Health is `DEPENDENCY_DERIVED`: actual READY/WAITING status depends on verified runtime inputs.

Remaining F3.2/F1 work is the full F1/F3.2 reaudit. F3.3 legacy removal remains open.

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

COG remains covered by unit, lint, APK, CodeQL, SBOM, Reference Checks and managed-device evidence. Broader ADR evidence now also includes ORCH, AGENT, BOOT, RECALL, REST-001, REST-002, bootstrap Health derivation, REST-BOOT-001, Local Nervous System Health convergence and RECALL-BOOT-001 validation, but that does not alter the COG mapping.

REST-001 validation on source head `3661450325237fcadb86098ec16ee45cd039bc0b` passed Android CI #717, Genesis Body Preparation #699, Reference Checks #541, CodeQL #430 and SBOM #428. It also passed unit, lint, QA-7 JVM, fail-closed release signing, API30/API35 compatibility and canonical API30 instrumented coverage. Its Room process-death test demonstrated recovery from a persisted exact canonical receipt without canonical writer replay.

REST-002 source head `2ecca3f48d5e0ef27bd927da3986292daf7f7e2c` passed Android CI #723, Genesis Body Preparation #703, Reference Checks #547, CodeQL #436 and SBOM #434. Android validation included unit tests, lint, debug/instrumentation build, fail-closed release signing, ephemeral signed release, API30/API35 compatibility and the process-death test proving exactly-once proposal recovery without repair execution.

PR #187 source head `f1697227241459f316bd562756e15ae3ce02c90d` passed Android CI #732, Genesis Body Preparation #710, Reference Checks #556, CodeQL #445 and SBOM #443. REST-BOOT-001 source head `dd7a92a011fd4c453775df6ec307638b05313ec9` passed Android CI #738, Genesis Body Preparation #715, Reference Checks #562, CodeQL #451 and SBOM #449. Those PR #187 results prove bootstrap Health derivation, not the later Local Nervous System convergence.

Health legacy-consumer convergence source head `6735e2d1febccf7da560d026d6ddd88f6ad82845` passed Android CI #757, Genesis Body Preparation #732, Reference Checks #581, CodeQL #470 and SBOM #468. RECALL-BOOT-001 source head `20d834e1d438fd5883a76e9b45bcf21860e7db42` passed Android CI #759, Genesis Body Preparation #733, Reference Checks #583, CodeQL #472 and SBOM #470. RECALL managed-device evidence re-read canonical state after repository recreation without creating schedule/link projections.

The global mutation pilot remained report-only. REST-specific, Health-specific and RECALL-specific mutation testing are not established.

Residual COG hardening includes Room-backed multi-coordinator concurrency, stronger rollback snapshot fixtures, redundant rollback parameter cleanup, and direct vulnerable UPDATE-trigger replacement coverage. Health-specific residuals include the full F1/F3.2 protected-main reaudit and the intentionally excluded ambiguous `CanonicalHealthInput.recentVerifiedEventCount` signal.

## 9. Acceptance boundary

```text
CONTENT_BASELINE_SHA=c4b192b8f54b2422ce816dc3542d55adfd44510c
CONTENT_BASELINE_PARENT_SHA=9c7325e6f1a21d79b1c3fb58f0b5f81a828fc304
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
REST_BOOT_READINESS=INTEGRATED
RECALL_BOOT_READINESS=INTEGRATED
MEMORY_ORGAN_DATABASE=V9
F1_A_AUTHORITY=PRESERVED
PROJECT_VAULT=SEPARATE_AND_PRESERVED
F3_2_BOUNDED_SCOPE=CLOSED_FOR_PROJECTVAULT_COG_ORCH_AGENT_BOOT_RECALL_DERIVED_REST001_REST002_PROPOSAL_REST_READINESS_RECALL_READINESS_AND_HEALTH_LEGACY_CONSUMER
BOOTSTRAP_HEALTH_DERIVATION=INTEGRATED
HEALTH_LEGACY_CONSUMER_CONVERGENCE=INTEGRATED
HEALTH_CAN_READ_CANONICAL_MEMORY=true
HEALTH_CAN_WRITE_CANONICAL_MEMORY=false
HEALTH_CAN_WRITE_LEGACY_MEMORY_EVENTS=false
HEALTH_CONVERGENCE=OPEN
HEALTH_CONVERGED=false
HEALTH_STATE=DEPENDENCY_DERIVED
F1_F3_2_FULL_REAUDIT=REQUIRED
F3_3=OPEN
F4_F6=OPEN
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```