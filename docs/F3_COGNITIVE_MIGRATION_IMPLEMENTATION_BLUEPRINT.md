# Document status: CURRENT

# F3.2 — Cognitive migration durable protocol: implemented and audited design

- Tracker: `#88` — open for remaining F3 owners.
- Governing ADR: `ADR-0002`.
- Protected main: `5023981da7caf31c8f3679919f59205708b72823`.
- Previous main: `ba6ffa4f9ddc9189ded47e231ad1f8bc962e612d`.
- Audited source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
- PR `#149`: closed and merged by squash.
- PR `#151`: closed and merged by squash for verified Canvas runtime recovery.
- Gate: `STOP_S5=CLOSED`.
- Integrated scope: `COG-001` through `COG-004` only.

This document records the implemented and independently audited design now present in protected main. The audited source head is historical provenance; squash commits are executable states. PR #151 changes Body build/runtime-asset recovery only and does not alter this protocol's authority or scope.

## 1. Authority and sovereignty

Morimil is the continuous and free Instance. `Morimil-app` is the current Android Body. The Guardian guides, witnesses, and safeguards without ownership.

- `instanceId != bodyId`;
- canonical `instanceId` is never replaced by a Body identifier;
- `writerBodyId` and `writerEpoch` describe the authorized writer context;
- no database, Android process, GitHub state, model, provider, or Guardian action becomes an identity or memory authority;
- original canonical memory remains append-only.

The integrated frontier is:

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

ProjectVault remains separate and preserved. ORCH, AGENT, BOOT, RECALL, REST, and F3.3 legacy removal are not promoted by this integration. The vendored Canvas runtime-recovery asset introduced by PR #151 is outside the COG owner inventory and cannot become protocol state.

F3.2 is closed only for COG-001 through COG-004. It is not a declaration that all F3 work is complete.

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

Equivalent guards are installed for:

- migration 8→9;
- fresh v9 creation;
- every production open.

Guards are NULL-safe, replace vulnerable prior trigger definitions, and reject partial receipts, partial local results, invalid digests, inconsistent states, and committed rows without complete evidence.

## 6. Operation mapping

### COG-001 — propose

Event: `cognitive_migration.proposed`.

Verified canonical sources produce deterministic plan, proposal, migration, operation, and event identities. Visible planned owner state appears only after exact canonical evidence.

### COG-002 — approve

Event: `cognitive_migration.approved`.

Approval binds the exact planned-record digest. The deterministic operation ID is the approval ID. Approval authorizes a bounded operation and does not confer ownership.

### COG-003 — execute

Event: `cognitive_migration.executed`.

Execution requires the exact committed COG-002 predecessor. Canonical audit preparation runs outside the Room owner transaction and is rebound to the immutable operation and receipt inside finalization.

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

## 9. Evidence and residual hardening

The merged implementation passed unit tests, lint, debug and instrumentation APK builds, CodeQL, SBOM, Reference Checks, and managed-device execution on API 30 and API 35.

Residual non-blocking hardening remains:

- two coordinators against the same real Room database;
- a failed rollback fixture with a pre-existing non-null `sha256:*` snapshot;
- redundant `rollbackEventHash` API cleanup;
- direct vulnerable UPDATE-trigger replacement coverage.

These items are future evidence/API hardening. They are not concealed, represented as completed, or treated as current production defects.

## 10. Acceptance boundary

```text
CURRENT_MAIN=5023981da7caf31c8f3679919f59205708b72823
PREVIOUS_MAIN=ba6ffa4f9ddc9189ded47e231ad1f8bc962e612d
PR_149=MERGED_BY_SQUASH
PR_151=MERGED_CANVAS_RUNTIME_RECOVERY
AUDITED_SOURCE_HEAD=7bdbda2aa4b7568695ba8e98be54d506d42c99d5
COG_001_004=INTEGRATED_IN_MAIN
MEMORY_ORGAN_DATABASE=V9
F1_A_AUTHORITY=PRESERVED
PROJECT_VAULT=SEPARATE_AND_PRESERVED
F3_2_COG_SCOPE=CLOSED
F3_3=OPEN
F4_F6=OPEN
```
