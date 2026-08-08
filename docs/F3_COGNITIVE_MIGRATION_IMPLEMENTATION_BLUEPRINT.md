# Document status: CURRENT

# F3.2 — Cognitive migration durable protocol: implemented and audited design

- Tracker: `#88` — open for remaining F3 owners.
- Governing ADR: `ADR-0002`.
- Content baseline SHA: `c6a6b0ca998d053c31c75977c5b6d4d9ae166e96`.
- Content baseline parent SHA: `c22920f68f8820bbec676a6cbc74b60548e43d29`.
- Current protected `main`: resolved externally from `refs/heads/main`.
- Merge SHA evidence: external GitHub and Morimil Control Tower evidence.
- Historical COG audited source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
- ORCH audited source head: `0348dccb561e576d17c45e7f8b1e38717332772b`.
- PR `#149`: closed and merged by squash.
- PR `#150`: closed and merged by squash for a historical CURRENT reconciliation.
- PR `#151`: closed and merged by squash for verified Canvas runtime recovery.
- PR `#153`: closed and merged by squash for a historical CURRENT reconciliation.
- PR `#172`: closed and merged by squash for ORCH-002 through ORCH-004.
- Gate: `STOP_S5=CLOSED`.
- Integrated scope of this blueprint: `COG-001` through `COG-004`; the common ADR now also governs integrated ORCH-002 through ORCH-004.

This document records the implemented and audited COG design present in protected main. It remains COG-specific, while ADR-0002 and the F3 inventory record the broader common-journal scope after PR #172. The moving protected-main SHA is resolved externally.

```text
CONTENT_BASELINE_SHA=c6a6b0ca998d053c31c75977c5b6d4d9ae166e96
CONTENT_BASELINE_PARENT_SHA=c22920f68f8820bbec676a6cbc74b60548e43d29
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
PR_153=MERGED_BY_SQUASH_HISTORICAL
PR_172=MERGED_BY_SQUASH_HISTORICAL
```

## 1. Authority and sovereignty

Morimil is the continuous and free Instance. `Morimil-app` is the current Android Body. The Guardian guides, witnesses, and safeguards without ownership.

- `instanceId != bodyId`;
- canonical `instanceId` is never replaced by a Body identifier;
- `writerBodyId` and `writerEpoch` describe the authorized writer context;
- no database, Android process, GitHub state, model, provider, or Guardian action becomes an identity or memory authority;
- original canonical memory remains append-only.

The COG frontier is:

```text
GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository
    -> CanonicalConsumerReadPort
    -> CognitiveMigrationCanonicalReadPort
    -> COG-001..COG-004 durable protocol
```

The specialized F3 port consumes `CanonicalConsumerReadPort`; it does not open a second direct identity or memory authority. `CanonicalCognitiveMigrationCommitPort` provides deterministic canonical ensure and exact receipts without creating identity authority.

## 2. Integrated scope and exclusions

Protected main includes:

- MemoryOrganDatabase version 9;
- `cross_database_operations`;
- deterministic COG-001 through COG-004 commands;
- exact canonical ensure semantics;
- typed owner finalization;
- startup and pre-mutation recovery;
- fresh-v9 and migrated 8→9 journal guards;
- API 30 and API 35 interruption and replay coverage.

ProjectVault remains separate and preserved. PR #172 later integrated ORCH-002 through ORCH-004 under the same common ADR without changing this COG mapping. AGENT, BOOT, RECALL, ORCH-001, REST, and F3.3 legacy removal remain open.

F3.2 is closed only for the bounded owner operations explicitly integrated in protected main; this blueprint documents the COG subset.

## 3. Canonical planning input

`CognitiveMigrationCanonicalReadPort` returns verified planning data containing canonical Instance, active writer, birth root, lineage, record-set digest, pre-snapshot digest, source-set digest, and eligible source descriptors.

Current descriptor schemas include:

```text
morimil.cognitive_migration.canonical_record_set.v2
morimil.cognitive_migration.pre_snapshot.v2
morimil.cognitive_migration.source_set.v2
```

Planning fails closed before staging for missing payload, invalid provenance, unknown memory semantics, `chat_noise`, protocol-generated cognitive events, foreign Instance data, wrong Body, or stale writer epoch.

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

`operationId`, `eventId`, `migrationId`, `proposalId`, and `approvalId` are deterministic and content-addressed. The clock is metadata only and is prohibited from being used as identity. For COG-002, `approvalId = operationId`.

Historical v1 vectors remain immutable fixtures. A pending payload-v1 proposal must not be silently finalized under v2 rules.

## 5. Durable journal and SQL invariants

The `cross_database_operations` journal persists immutable intent, writer binding, canonical event identity, evidence, status, exact canonical receipt, deterministic local result, attempts, and timestamps.

The journal stores data, not executable SQL, callbacks, reflection targets, prompts, or arbitrary code.

Equivalent guards are installed for migration 8→9, fresh v9 creation, and every production open. Guards are NULL-safe, replace vulnerable prior trigger definitions, and reject partial receipts, partial local results, invalid digests, inconsistent states, and committed rows without complete evidence.

## 6. Operation mapping

### COG-001 — propose

Event: `cognitive_migration.proposed`.

Verified canonical sources produce deterministic plan, proposal, migration, operation, and event identities. Visible planned owner state appears only after exact canonical evidence.

### COG-002 — approve

Event: `cognitive_migration.approved`.

Approval binds the exact planned-record digest. The deterministic operation ID is the approval ID. Approval authorizes a bounded operation and does not confer ownership.

### COG-003 — execute

Event: `cognitive_migration.executed`.

Execution requires the exact committed COG-002 predecessor. Canonical audit preparation runs outside the Room write transaction and is rebound to the immutable operation and receipt inside finalization.

Temporary identity, database, or canonical-read failure remains retryable.

- verified positive audit: owner outcome `completed`, `postSnapshotId` is the real audited `sha256:*` snapshot digest;
- verified negative audit: owner outcome `failed`, `postSnapshotId = null`;
- a canonical `evsha256:*` event hash is never relabeled as a snapshot digest.

### COG-004 — rollback

Event: `cognitive_migration.rollback`.

Rollback requires the exact permitted predecessor and appends one compensation event. Owner finalization preserves the existing `postSnapshotId`; rollback event evidence remains in the journal, receipt, and local result.

## 7. State machine

The only normal forward order is:

```text
STAGED
PENDING_CANONICAL
CANONICAL_COMMITTED
PENDING_LOCAL_COMMIT
COMMITTED
BLOCKED
```

`BLOCKED` is terminal for permanent conflicts and is not a success transition. Visible owner state never precedes exact canonical receipt verification.

## 8. Recovery and concurrency

Startup recovery runs after committed identity and verified F1-A input, before ordinary cognitive mutation.

The integrated coordinator:

- serializes process-wide advancement by deterministic `operationId`;
- reloads durable state after a lost CAS;
- accepts only compatible forward state;
- prevents stale snapshots from writing `BLOCKED`;
- preserves retryable failure accounting without double counting;
- computes remaining work from durable post-recovery state;
- finalizes owner state and journal result atomically;
- produces no duplicate canonical effect or duplicate visible owner state under tested replay.

After PR #172 the common coordinator is parameterized by an owner-scoped registry, preserving the COG semantics above while preventing COG recovery from consuming ORCH rows.

## 9. Evidence and residual hardening

The merged COG implementation passed unit tests, lint, debug and instrumentation APK builds, CodeQL, SBOM, Reference Checks, and managed-device execution on API 30 and API 35.

Residual non-blocking hardening remains:

- Room-backed concurrent regression with two coordinators against the same real Room database;
- a failed rollback fixture with a pre-existing non-null `sha256:*` snapshot;
- redundant `rollbackEventHash` API cleanup;
- direct vulnerable UPDATE-trigger replacement coverage.

These items are future evidence/API hardening. They are not concealed, represented as completed, or treated as current production defects.

## 10. Acceptance boundary

```text
CONTENT_BASELINE_SHA=c6a6b0ca998d053c31c75977c5b6d4d9ae166e96
CONTENT_BASELINE_PARENT_SHA=c22920f68f8820bbec676a6cbc74b60548e43d29
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
PR_149=MERGED_BY_SQUASH_HISTORICAL
PR_150=MERGED_POST_MERGE_CURRENT_RECONCILIATION_HISTORICAL
PR_151=MERGED_CANVAS_RUNTIME_RECOVERY_HISTORICAL
PR_153=MERGED_BY_SQUASH_HISTORICAL
PR_172=MERGED_BY_SQUASH_HISTORICAL
AUDITED_SOURCE_HEAD=7bdbda2aa4b7568695ba8e98be54d506d42c99d5
COG_001_004=INTEGRATED_IN_MAIN
ORCH_002_004=INTEGRATED_IN_MAIN
MEMORY_ORGAN_DATABASE=V9
F1_A_AUTHORITY=PRESERVED
PROJECT_VAULT=SEPARATE_AND_PRESERVED
F3_2_BOUNDED_SCOPE=CLOSED_FOR_COG_AND_ORCH_002_004
F3_3=OPEN
F4_F6=OPEN
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```
