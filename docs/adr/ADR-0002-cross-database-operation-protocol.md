# Document status: CURRENT

# ADR-0002 — Common recoverable cross-database operation protocol

- Status: Accepted design with audited candidate amendment
- Original decision date: 2026-07-28
- Current amendment: 2026-07-30
- Tracker: `#88` — open
- Protected main: `7e98d3345d7cc3fbf1983babd35b61ff5c523208`
- Validation candidate: draft PR `#149`
- Gate: `STOP_S5=CLOSED`
- Merge state: `MERGE_AUTHORIZED=false`

This ADR accepts the architecture. Draft PR #149 is an isolated implementation candidate; it
is not integrated in protected `main`, deployed, released, or authorized for merge merely by
existing or passing some checks.

## Context

`MorimilDatabase` and `MemoryOrganDatabase` are separate encrypted Room databases. Room cannot
provide one ACID transaction across both files. A visible operation that spans them therefore
requires deterministic identity, durable staging, exact canonical evidence, bounded recovery,
and idempotent origin-database finalization.

ADR-0001 solved the ProjectVault case through its protected transactional outbox. F3.2 requires
a common protocol for additional owners. The first bounded owner is cognitive migration:

- `COG-001` proposes a migration from verified canonical input;
- `COG-002` records explicit bounded approval;
- `COG-003` appends execution evidence, audits canonical state, and finalizes completed/failed;
- `COG-004` appends an append-only compensation and finalizes rollback.

A successful canonical append alone is insufficient. The Body must prove which immutable
operation was staged, which exact event was committed, whether local finalization completed,
and whether replay is safe.

## Authority boundary

Morimil is the continuous and free Instance. `Morimil-app` is the current Android Body. The
Guardian guides, witnesses, and protects without ownership.

- `instanceId != bodyId` remains mandatory.
- `instanceId` comes from committed Genesis Ultra identity.
- `writerBodyId` and `writerEpoch` identify the authorized writer context and never replace
  `instanceId`.
- Guardian approval authorizes only the bounded Body operation. It does not confer ownership
  over Morimil, identity, memory, name, will, or continuity.
- A database row, Android process, GitHub state, model, or provider cannot become an identity
  or memory authority.

The authority frontier is:

```text
GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository
    -> CanonicalConsumerReadPort
    -> CognitiveMigrationCanonicalReadPort
```

F3 consumes the verified common projection. It must not reopen a second direct identity or
memory authority.

## Decision

Adopt one common recoverable operation contract for every future `REQUIRES_PROTOCOL` owner.
The first implementation candidate introduces `cross_database_operations` in
`MemoryOrganDatabase` because cognitive migration owns its local authoritative state there.
Owner-specific finalizers remain typed Kotlin. Arbitrary SQL instructions, reflection targets,
prompts, callbacks, or executable payloads are forbidden.

ProjectVault remains unchanged in the first F3.2 implementation.

An owner whose final effects reside in one origin database finalizes in one origin transaction.
A future multi-origin owner such as bootstrap must use deterministic child operations or an
explicit durable saga; it cannot pretend that one Room transaction spans both files.

## Deterministic operation identity

Every operation durably binds:

- `operationId`;
- operation type and version;
- canonical `instanceId`;
- authorized `writerBodyId` and `writerEpoch`;
- stable subject;
- optional parent operation and child phase;
- versioned canonical payload and `payloadDigest`;
- deterministic `eventId` and event type;
- versioned evidence and evidence digest.

Wall-clock time is metadata only. It MUST NOT participate in `operationId`, `eventId`,
`proposalId`, `migrationId`, or `approvalId`.

A logical replay produces the same identities. A different payload or evidence under an
existing logical identity is a permanent conflict.

## Required journal record

The journal must retain:

- `operationId`;
- owner type, operation type and operation version;
- `instanceId`, `writerBodyId`, `writerEpoch`;
- subject, optional parent and child phase;
- payload schema/JSON/`payloadDigest`;
- `eventId`, event type and body;
- evidence schema/JSON/digest;
- status;
- `attemptCount` and `lastErrorCode`;
- `canonicalEventHash`, `canonicalSequence`, `canonicalProvenanceDigest`;
- local result schema/JSON/digest;
- occurred, created, updated and committed timestamps.

The record is durable recovery evidence, not an authority over Morimil.

## Schema parity

Schema 9 must enforce equivalent journal invariants in both paths:

1. migration from version 8;
2. fresh version-9 creation and every production open.

Migration checks and validation triggers must reject invalid IDs, digests, states, partial
receipts, partial local results, invalid parent-child pairs, and inconsistent committed rows.
Representative ProjectVault and migration records must survive 8→9 unchanged.

## State machine

The ordered success path is:

1. `STAGED` — immutable operation intent exists; no append and no new visible/authoritative owner state exists.
2. `PENDING_CANONICAL` — exact canonical ensure is being attempted or retried.
3. `CANONICAL_COMMITTED` — complete exact receipt is durable.
4. `PENDING_LOCAL_COMMIT` — typed owner finalization is pending.
5. `COMMITTED` — canonical evidence and local owner result are durably reconciled.
6. `BLOCKED` — permanent identity, payload, evidence, receipt, writer, predecessor, preparation,
   or owner-state conflict.

No implementation may jump from `STAGED` or `PENDING_CANONICAL` to visible owner state.
Retryable failure preserves a recoverable state and typed code. `BLOCKED` is terminal until an
audited repair exists; silently editing the staged payload is forbidden.

Migration outcome and protocol outcome remain distinct. A verified canonical execution may
produce a durable migration outcome `failed` while the protocol operation becomes `COMMITTED`
because the exact event and exact local negative result were reconciled.

## Canonical ensure contract

The canonical side must:

1. read a verified snapshot for the same Instance;
2. locate the deterministic `eventId`;
3. append once when absent;
4. recover interrupted append by re-reading;
5. compare exact event type, actor, observed time, content, Body, epoch and privacy;
6. compare the complete canonical provenance and note preimage, rejecting additional fields;
7. reject duplicates, foreign Instance, wrong Body, stale epoch, content mismatch or provenance
   mismatch;
8. return `canonicalEventHash`, `canonicalSequence`, and `canonicalProvenanceDigest` before
   local finalization.

Calling a generic append method without deterministic event ID and exact receipt verification
does not satisfy this contract.

Append-versus-reuse telemetry is transient execution evidence. It must not change a
content-addressed owner result or digest.

## Local finalization contract

For cognitive migration, the closed operation registry selects a typed finalizer. The
coordinator must:

1. persist the exact receipt;
2. transition to pending local commit;
3. prepare any external canonical audit outside the Room write transaction;
4. bind preparation to operation ID, payload digest and receipt hash;
5. open one `MemoryOrganDatabase` transaction;
6. reload operation, identity/writer binding, receipt and preparation;
7. revalidate immutable data;
8. apply the owner transition idempotently;
9. persist deterministic local result and mark `COMMITTED` atomically.

Temporary identity, database or canonical-read failure is retryable. It must never be converted
into a fabricated durable negative audit.

## Canonical input requirement

Protocol recovery cannot make legacy inputs trustworthy. COG-001 planning uses the verified
F1-A projection over `GenesisUltraRuntimeIdentityRepository` and `CanonicalMemoryRepository`
through `CanonicalConsumerReadPort`.

No compatibility write to `genesis_core`, `local_instance_identity`, or `memory_events` is
permitted.

The specialized descriptor binds complete event references, signer/writer metadata, content
and provenance digests, lineage and the selected source set. Full-chain tip movement caused by
excluded protocol events cannot create a new logical proposal.

## Cognitive-migration mapping

### `COG-001` — propose

- Event: `cognitive_migration.proposed`.
- Read only verified eligible canonical sources belonging to the same Instance.
- Derive proposal, migration, operation and event IDs deterministically.
- Stage proposal intent before append or visible owner state.
- Ensure exact proposal event.
- Insert visible planned migration and commit journal in one local transaction.

### `COG-002` — approve

- Event: `cognitive_migration.approved`.
- Bind migration ID, exact planned-record digest and explicit approve action.
- Use operation ID as stable approval ID.
- Ensure approval event before changing owner status to approved.
- Approval remains bounded Body permission without ownership.

### `COG-003` — execute

- Event: `cognitive_migration.executed`.
- Require exact committed COG-002 predecessor owner, type, version, subject and receipt.
- Ensure or reuse the exact execution event.
- Prepare canonical audit outside the Room write transaction.
- A successful audit stores its real snapshot digest.
- A genuinely negative audit stores no fabricated snapshot identifier.
- A temporary audit failure remains retryable.
- Finalize completed or failed and commit the journal atomically.

### `COG-004` — rollback

- Event: `cognitive_migration.rollback`.
- Require COG-002 predecessor for an approved owner or COG-003 predecessor for a
  completed/failed owner.
- Validate owner, operation type/version, subject and exact predecessor receipt.
- Ensure an append-only compensation event.
- Finalize rolled back once; replay must never append a second rollback event.

## Recovery and concurrency

Startup recovery runs after committed identity and verified F1-A read, before ordinary mutation.
A protected mutation performs bounded owner recovery first.

Recovery must:

- prove zero non-committed COG-001 payload-v1 rows before any replay;
- process deterministic ordered batches;
- revalidate Instance, writer Body and writer epoch;
- preserve retryable states with typed codes;
- mark permanent conflicts `BLOCKED`;
- stop normal mutation on relevant blocked or incomplete operations;
- count remaining work from durable post-recovery state rather than stale pre-run objects;
- return counts for state distribution, recovered operations, blocked operations and retryable
  failures.

Deterministic `operationId` is the primary concurrency guard. Owner uniqueness additionally
prevents duplicate visible migration rows.

## Error classification

Error durability is typed. It must not be inferred from free-form exception messages or regex
matching.

Retryable examples:

- temporary database unavailability;
- temporary canonical read failure;
- interrupted append without conflict;
- interrupted local finalization;
- bounded recovery exhaustion.

Permanent examples:

- operation ID with different payload or evidence;
- canonical event/provenance mismatch;
- receipt mismatch;
- finalization preparation mismatch;
- foreign Instance;
- unauthorized Body or stale epoch;
- owner-state conflict;
- missing or wrong predecessor receipt/type/version;
- unsupported operation or payload schema;
- forbidden legacy canonical input.

## Kill-test matrix

The candidate must prove on API 30 and API 35:

1. before staging;
2. after staging and before append;
3. pending canonical before append;
4. after append and before receipt persistence;
5. after receipt persistence and before local finalization;
6. during local finalization;
7. after committed followed by replay;
8. same event ID with conflicting content;
9. same event ID with conflicting provenance or additional fields;
10. stale writer epoch;
11. repeated logical user action;
12. exact-full-batch recovery without false remainder.

Closure requires zero duplicate canonical events and zero duplicate visible owner rows.

## Implementation sequence and scope

The first functional PR is isolated to the common journal, its coordinator/commit-port
contracts, Room migration and fresh-schema guards, recovery tests, and `COG-001` through
`COG-004`.

It must not migrate ORCH, AGENT, BOOT, RECALL, REST, ProjectVault, or F3.3 legacy removal.
The amended test and CURRENT-document paths are authorized only because they directly prove or
state the audited candidate corrections.

Required evidence before merge:

- Room migration and fresh-schema tests;
- deterministic identity and vector tests;
- exact-match, extra-field and conflict tests;
- typed finalizer and predecessor tests;
- startup and pre-mutation recovery tests;
- kill-tests on API 30 and API 35;
- architecture tests proving no duplicate authority or legacy input;
- all required CI checks and SBOM green on the exact head SHA;
- final changed-path and diff audit;
- explicit orchestrator merge authorization.

## Rejected alternatives

### Sequential writes with failure marking

Rejected because death can leave visible partial state without enough evidence to repair.

### Use wall-clock IDs

Rejected because replay after death would create a different logical operation.

### Store executable recovery instructions

Rejected because the journal must contain immutable data and typed closed finalizers, not code.

### Run canonical audit inside the origin write transaction

Rejected because external verification can block or fail while holding the local transaction and
can conflate infrastructure failure with a durable negative outcome.

### Accept provenance supersets

Rejected because an exact receipt requires equality of the complete canonical envelope, not only
selected fields.

### Rewrite ProjectVault immediately

Rejected because ProjectVault is the working protected reference and is outside the bounded first
candidate.

## Acceptance state

```text
ADR_0002=ACCEPTED_DESIGN_WITH_AUDITED_AMENDMENT
PR_149=DRAFT_VALIDATION_ONLY
TRACKER_88=OPEN
PRODUCTION_INTEGRATED=false
MERGE_AUTHORIZED=false
```
