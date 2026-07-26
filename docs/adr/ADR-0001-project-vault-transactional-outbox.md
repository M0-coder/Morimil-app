# ADR-0001 — Transactional outbox for ProjectVault

- Status: Accepted
- Date: 2026-07-26
- Scope: Plan V2, Fase 3.1

## Context

`ProjectVaultRepository` owns visible project state in `MemoryOrganDatabase` but also records each create, complete, or archive transition in the canonical Genesis Ultra memory stored by `MorimilDatabase`.

Room cannot provide one ACID transaction across these two encrypted database files. The previous sequence wrote the visible vault first and the canonical memory event second. A process death or second-write failure could therefore leave a project state visible without its required canonical evidence.

The long-term preferred architecture remains one Room database for all durable domains. Performing that merger in Fase 3.1 would combine schema ownership, cryptographic memory constraints, organ replacement semantics, and every cross-database caller in one change.

## Decision

Use a transactional outbox in the origin database, `MemoryOrganDatabase`, as the transitional protocol.

For every ProjectVault transition:

1. build a deterministic operation payload and SHA-256 digest;
2. persist only an outbox row in one origin-database transaction;
3. do not insert or mutate the visible `project_vaults` row yet;
4. ensure a canonical Genesis Ultra event exists using a deterministic `eventId`;
5. verify existing or newly appended content and provenance exactly;
6. in one origin-database transaction, apply the local vault state and mark the outbox row committed;
7. recover pending rows at runtime startup and before new ProjectVault mutations.

The active states are:

- `pending`: retryable and not locally visible;
- `committed`: canonical event verified and local state applied;
- `blocked`: a permanent local invariant conflict was detected; the prior visible state remains unchanged.

A transient canonical or database failure increments `attemptCount`, records `lastError`, and leaves the operation pending. A permanent finalization conflict is compensated locally by preserving the previous visible state and recording `blocked`.

## Idempotency

`operationId`, `eventId`, and `payloadDigest` are deterministic. Recovery first reads the fully verified canonical chain:

- no matching event: append once through `CanonicalMemoryRepository`;
- exact matching event: reuse its receipt and finalize locally;
- same `eventId` with different content or provenance: fail closed.

This covers death after canonical append but before local commit without duplicating memory.

## Visibility invariant

A new vault does not exist in `project_vaults` until its canonical event is verified. Completion and archive operations leave the previous committed vault state visible until their canonical event is verified and the local transition commits.

Therefore killing the process at any boundary cannot expose a new orphaned ProjectVault state.

## Recovery

Startup recovery runs after verified Genesis Ultra identity and memory convergence, before normal runtime bootstrap. New ProjectVault mutations also retry older pending rows first. Blocked rows stop the startup integrity gate; transient pending rows remain hidden and retryable.

## Consequences

Positive:

- no visible partial ProjectVault state;
- deterministic recovery after process death;
- one active canonical memory writer;
- no distributed lock or cross-database transaction illusion;
- protocol can be reused during the Fase 3.2 inventory.

Costs:

- temporary duplication of operation metadata;
- additional migration and recovery logic;
- `MemoryOrganDatabase` and `MorimilDatabase` remain separate until a future unification ADR.

## Rejected alternatives

### Keep the two sequential writes

Rejected because failure between them violates the visibility invariant.

### Compensate by deleting the visible vault

Rejected because compensating deletes can also fail and erase user-visible state after it was observed.

### Merge both databases in this PR

Deferred. It remains the preferred long-term direction, but it is too broad for the isolated 3.1 acceptance test and would mix all Fase 3.2 operations into one migration.
