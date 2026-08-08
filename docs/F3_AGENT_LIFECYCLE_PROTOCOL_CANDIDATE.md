# Document status: PROPOSAL

# F3 agent lifecycle protocol candidate — AGENT-001 through AGENT-006

## Candidate boundary

This document describes an isolated candidate on branch `executor/f3-agent-001-006-v1` based on protected main `9da342f2c147105ea882076f4ebc6ab5f5494190`.

It is not protected-main CURRENT truth until separately validated, authorized, merged, and reconciled.

```text
AGENT_001_006=CANDIDATE
PROTECTED_MAIN_BASE=9da342f2c147105ea882076f4ebc6ab5f5494190
MERGED=FALSE
MERGE_AUTHORIZED=FALSE
ORCH_001=OPEN
BOOT_001=OPEN
RECALL_001=OPEN
REST_001_002=OPEN
F3_3=OPEN
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```

## Authority

Morimil remains the continuous Instance. The Android Body hosts execution and keys but does not become identity authority. Agent instances are bounded workers inside a ProjectVault; they do not become Morimil, own Morimil, create a second canonical-memory authority, or receive autonomous execution rights.

```text
instanceId != bodyId
agentInstanceId != instanceId
agent worker != Morimil identity
agent lifecycle state != canonical memory authority
Guardian custody != ownership
```

The candidate consumes committed Genesis Ultra runtime identity and appends lifecycle decisions only through CanonicalMemoryRepository via a specialized exact-ensure port.

## Defect being removed

The pre-candidate `AgentInstanceLifecycleRepository` mutates `MemoryOrganDatabase` first and then calls `MemoryRepository.recordSystemMemoryEvent`. A process death between those actions can expose owner state without a canonical receipt. Agent and project-task IDs also include wall-clock milliseconds, so a retry can produce a different identity.

The candidate removes both properties:

1. no `MemoryRepository` or `memory_events` write remains in the owner;
2. operation/task/agent identities are content-addressed from semantic inputs and writer epoch, not wall-clock time;
3. canonical receipt is durable before local owner visibility;
4. final owner mutation and journal `COMMITTED` transition share the existing Room transaction;
5. startup and pre-mutation recovery are owner-scoped.

## Closed candidate registry

| Inventory ID | Entry point | XOP operation | Canonical event |
| --- | --- | --- | --- |
| AGENT-001 | `createAgentForVault` | `agent_lifecycle.create_agent` | `agent_lifecycle.agent_created` |
| AGENT-002 | `assignTaskToAgent` | `agent_lifecycle.assign_task` | `agent_lifecycle.task_assigned` |
| AGENT-003 | `submitAgentResult` | `agent_lifecycle.submit_result` | `agent_lifecycle.result_submitted` |
| AGENT-004 | `evaluateAgent` | `agent_lifecycle.evaluate_agent` | `agent_lifecycle.agent_evaluated` |
| AGENT-005 | `retireAgent` | `agent_lifecycle.retire_agent` | `agent_lifecycle.agent_retired` |
| AGENT-005 | `promoteAgent` | `agent_lifecycle.promote_agent` | `agent_lifecycle.agent_promoted` |
| AGENT-006 | `quarantineAgent` | `agent_lifecycle.quarantine_agent` | `agent_lifecycle.agent_quarantined` |

AGENT-005 has two mutually exclusive operation types because retirement and promotion are different durable decisions even though the inventory groups them under one bounded ID.

## Deterministic identity rules

Wall-clock time is metadata only and MUST NOT participate in agent, task, operation, or event identity.

AGENT-001 uses a deterministic vault/template ordinal. Recovery runs before the ordinal is computed. Therefore an interrupted create retries the same logical identity, while a later intentional second worker gets the next ordinal after the first durable owner state exists.

AGENT-002 chains project-task identity from the agent's previous `currentTaskId` plus normalized delegation semantics. A retry before local finalization therefore reuses the same task identity; a later assignment after owner advancement gets a different identity.

AGENT-006 derives its replacement agent from the failed agent identity, writer epoch, vault/template identity, and normalized reason digest.

## Local finalization

All local finalizers run under `RoomCrossDatabaseOperationStore.finalizeCommitted`.

### AGENT-001

After the exact canonical receipt, insert the planned AgentInstance and refresh ProjectVault active-agent projection. Exact replay reuses the same entity.

The legacy pair `project.agent_created` + `project.agent_briefed` is intentionally collapsed into one canonical `agent_lifecycle.agent_created` event whose payload/evidence includes the full briefing digest. This is an observability shape change, not an authority transfer.

### AGENT-002

After the receipt, insert the project delegated task. If immune policy blocks it, the task is durably rejected and the agent is not assigned to it. Otherwise the agent's `currentTaskId` and lifecycle status are advanced in the same Room transaction.

Project tasks remain approval-required.

### AGENT-003

A result is accepted only when the current task belongs to that agent, is `approved`, and carries a non-null canonical ORCH approval ID. The pre-candidate behavior allowed `submitAgentResult` to move an unapproved task to review; the candidate closes that policy hole.

Task result and agent lifecycle state update atomically after the canonical receipt.

### AGENT-004

Evaluation binds the exact semantic pre-state, normalized note digest, bounded score, and normalized review status before local mutation.

### AGENT-005

Retire and promote are distinct canonical decisions. They serialize by `agentInstanceId` before append. Finalization binds exact pre-state and normalized reason.

### AGENT-006

Quarantine and replacement creation are one durable local finalization after one canonical receipt. The candidate does not call AGENT-001 after quarantine, because that would create a second crash window between closing the failed worker and creating its replacement.

The failed agent gains one error, becomes quarantined, and the deterministic replacement begins in `thinking` state inside the same Room transaction.

## Concurrency

Public lifecycle mutations use a process-wide striped mutex by `agentInstanceId`; AGENT-001 uses a vault-scoped striped mutex while allocating its semantic ordinal. This prevents two different operation IDs for the same worker from appending mutually incompatible canonical transitions concurrently in the current single-process Android application.

Room transaction serialization and exact semantic pre-state verification provide a second defense during local finalization.

If the application later introduces more than one Android process, the process-local mutex assumption must be replaced by a cross-process decision lease or equivalent durable serialization primitive before claiming the same guarantee.

## Recovery order

The startup order becomes:

```text
verified canonical planning input
-> COG owner recovery
-> ORCH owner recovery
-> AGENT owner recovery
-> remaining legacy convergence
-> ProjectVault recovery
-> BOOT-001 current path
```

Each XOP recovery call filters by owner type. AGENT cannot consume COG or ORCH rows and vice versa.

## Schema and authority non-changes

```text
MemoryOrganDatabase_VERSION_CHANGE=FALSE
MorimilDatabase_VERSION_CHANGE=FALSE
NEW_IDENTITY_AUTHORITY=FALSE
NEW_MEMORY_AUTHORITY=FALSE
WRITE_TO_memory_events=FALSE
COMPATIBILITY_ROWS=FALSE
BODY_SUCCESSION=NOT_IMPLEMENTED
GENESIS_EXECUTED=FALSE
SEED_IMPORTED=FALSE
GUARDIAN_MODIFIED=FALSE
ACTIVATION_EXECUTED=FALSE
```

## Required candidate evidence

Before merge eligibility:

- deterministic factory unit tests;
- finalizer exact-state and replay tests;
- architecture contract proving removal of legacy memory writes and clock-derived IDs;
- API30/API35 kill/reopen recovery tests for representative AGENT transitions;
- owner-isolation recovery test;
- canonical exact-ensure tests;
- QA-7 JVM and instrumented ratchets;
- Android lint;
- bounded PIT pilot must remain non-regressed;
- Reference Checks, CodeQL, SBOM, and Genesis validation;
- exact-head artifact digest verification.

Mutation testing specific to the AGENT lifecycle protocol is desirable but is not established by the existing bounded Genesis PIT pilot. That debt must remain explicit.

## Non-scope

This candidate does not close ORCH-001, BOOT-001, RECALL-001, REST-001/002, F3.3, F4, F5, or F6. It does not provision a Body, import Seed, modify Guardian authority, execute Genesis, activate Morimil, transfer writer epoch, or prove operational birth.
