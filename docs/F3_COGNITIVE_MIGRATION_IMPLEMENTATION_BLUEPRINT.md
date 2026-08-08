# Document status: CURRENT

# F3.2 — Cognitive migration durable protocol: implemented and audited design

- Tracker: `#88` — open for remaining F3 owners.
- Governing ADR: `ADR-0002`.
- Content baseline SHA: `d577a75290d70f423f6e83bf237a8a453f3a534e`.
- Content baseline parent SHA: `9da342f2c147105ea882076f4ebc6ab5f5494190`.
- Current protected `main`: resolved externally from `refs/heads/main`.
- Merge SHA evidence: external GitHub and Morimil Control Tower evidence.
- Historical COG audited source head: `7bdbda2aa4b7568695ba8e98be54d506d42c99d5`.
- ORCH audited source head: `0348dccb561e576d17c45e7f8b1e38717332772b`.
- AGENT audited source head: `74e072b911db692041d3716af9d0511b83ad70b7`.
- PR `#172`: merged by squash for ORCH-002 through ORCH-004.
- PR `#173`: merged by squash for post-ORCH CURRENT reconciliation.
- PR `#174`: merged by squash for AGENT-001 through AGENT-006.
- Gate: `STOP_S5=CLOSED`.
- Integrated scope of this blueprint: `COG-001` through `COG-004`; ADR-0002 also governs integrated ORCH-002..004 and AGENT-001..006.

This document remains COG-specific. ADR-0002 and the F3 inventory record the broader common-journal scope. The moving protected-main SHA is resolved externally.

```text
CONTENT_BASELINE_SHA=d577a75290d70f423f6e83bf237a8a453f3a534e
CONTENT_BASELINE_PARENT_SHA=9da342f2c147105ea882076f4ebc6ab5f5494190
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
PR_172=MERGED_BY_SQUASH_HISTORICAL
PR_173=MERGED_BY_SQUASH_HISTORICAL
PR_174=MERGED_BY_SQUASH_HISTORICAL
```

## 1. Authority and sovereignty

Morimil is the continuous Instance. `Morimil-app` is the current Android Body. The Guardian guides and safeguards without ownership.

- `instanceId != bodyId`;
- canonical `instanceId` is never replaced by a Body identifier;
- `writerBodyId` and `writerEpoch` describe writer authorization;
- no database, Android process, GitHub state, model, provider, agent worker, or Guardian action becomes identity or canonical-memory authority;
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

ProjectVault remains separate. PR #172 integrated ORCH-002..004 under ADR-0002 and PR #174 integrated AGENT-001..006 under the same ADR without changing the COG mapping documented here.

Remaining F3.2 work is `BOOT-001`, `RECALL-001`, `ORCH-001`, and `REST-001/002`. F3.3 legacy removal remains open.

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

The common coordinator is registry-parameterized. COG recovery cannot consume ORCH or AGENT rows; ORCH and AGENT likewise remain owner-scoped.

## 8. Evidence and residual hardening

COG remains covered by unit, lint, APK, CodeQL, SBOM, Reference Checks and managed-device evidence. Broader ADR evidence now also includes ORCH and AGENT exact-head validation, but that does not alter the COG mapping.

Residual COG hardening includes Room-backed multi-coordinator concurrency, stronger rollback snapshot fixtures, redundant rollback parameter cleanup, and direct vulnerable UPDATE-trigger replacement coverage.

## 9. Acceptance boundary

```text
CONTENT_BASELINE_SHA=d577a75290d70f423f6e83bf237a8a453f3a534e
CONTENT_BASELINE_PARENT_SHA=9da342f2c147105ea882076f4ebc6ab5f5494190
CURRENT_MAIN_RESOLUTION=EXTERNAL_GIT_REF
MERGE_SHA_EVIDENCE=EXTERNAL
COG_001_004=INTEGRATED_IN_MAIN
ORCH_002_004=INTEGRATED_IN_MAIN
AGENT_001_006=INTEGRATED_IN_MAIN
MEMORY_ORGAN_DATABASE=V9
F1_A_AUTHORITY=PRESERVED
PROJECT_VAULT=SEPARATE_AND_PRESERVED
F3_2_BOUNDED_SCOPE=CLOSED_FOR_PROJECTVAULT_COG_ORCH_AND_AGENT_ONLY
BOOT_001=OPEN
RECALL_001=OPEN
ORCH_001=OPEN
REST_001_002=OPEN
F3_3=OPEN
F4_F6=OPEN
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```
