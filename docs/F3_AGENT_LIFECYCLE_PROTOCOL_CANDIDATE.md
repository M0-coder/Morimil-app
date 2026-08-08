# Document status: HISTORICAL

# F3 agent lifecycle protocol candidate — AGENT-001 through AGENT-006

This document preserves the pre-merge candidate design and validation history for AGENT-001..006. It is no longer a live proposal and does not override CURRENT documents.

```text
PROTECTED_MAIN_BASE=9da342f2c147105ea882076f4ebc6ab5f5494190
VALIDATED_CANDIDATE_HEAD=74e072b911db692041d3716af9d0511b83ad70b7
PR_174=MERGED_BY_SQUASH
MERGE_SHA=d577a75290d70f423f6e83bf237a8a453f3a534e
AGENT_001_006=INTEGRATED
ORCH_001=OPEN
BOOT_001=OPEN
RECALL_001=OPEN
REST_001_002=OPEN
F3_3=OPEN
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```

## Historical candidate boundary

The candidate replaced the local-first + legacy-memory lifecycle path with the common recoverable cross-database protocol. Agent workers remained bounded ProjectVault workers and did not become Morimil, identity authority, or canonical-memory authority.

```text
instanceId != bodyId
agentInstanceId != instanceId
agent worker != Morimil identity
Guardian custody != ownership
```

## Historical defect removed

Before PR #174, `AgentInstanceLifecycleRepository` could mutate MemoryOrganDatabase and then record legacy `memory_events` evidence, leaving a process-death window. Agent/project-task IDs also used wall-clock milliseconds.

The integrated design removed those properties:

1. no lifecycle `MemoryRepository.recordSystemMemoryEvent` canonical path;
2. deterministic semantic operation/task/agent/event identities;
3. exact canonical receipt before new owner visibility;
4. local owner mutation plus XOP `COMMITTED` in one Room finalization;
5. owner-scoped startup/pre-mutation recovery;
6. semantic public retry recognition.

## Integrated registry

| Inventory ID | Entry point | XOP operation | Canonical event |
| --- | --- | --- | --- |
| AGENT-001 | `createAgentForVault` | `agent_lifecycle.create_agent` | `agent_lifecycle.agent_created` |
| AGENT-002 | `assignTaskToAgent` | `agent_lifecycle.assign_task` | `agent_lifecycle.task_assigned` |
| AGENT-003 | `submitAgentResult` | `agent_lifecycle.submit_result` | `agent_lifecycle.result_submitted` |
| AGENT-004 | `evaluateAgent` | `agent_lifecycle.evaluate_agent` | `agent_lifecycle.agent_evaluated` |
| AGENT-005 | `retireAgent` | `agent_lifecycle.retire_agent` | `agent_lifecycle.agent_retired` |
| AGENT-005 | `promoteAgent` | `agent_lifecycle.promote_agent` | `agent_lifecycle.agent_promoted` |
| AGENT-006 | `quarantineAgent` | `agent_lifecycle.quarantine_agent` | `agent_lifecycle.agent_quarantined` |

AGENT-005 uses separate retire/promote durable decisions. AGENT-006 quarantines the failed worker and creates the deterministic replacement in one local finalization after one canonical receipt.

## Historical validation evidence

Exact candidate head `74e072b911db692041d3716af9d0511b83ad70b7` passed all five required workflows before merge:

- Android CI #661 — success;
- Genesis Body Preparation #651 — success;
- Reference Checks #485 — success;
- CodeQL #374 — success;
- SBOM #372 — success.

Evidence recorded in PR #174 included:

```text
JVM_TESTS=800
JVM_FAILURES=0
API30_TESTS=123
API30_FAILURES=0
API30_ERRORS=0
API30_SKIPPED=4
API35_TESTS=123
API35_FAILURES=0
API35_ERRORS=0
API35_SKIPPED=4
QA7_JVM=PASS
QA7_INSTRUMENTED=PASS
```

The same four managed-device skips were physical-ARM64-only inference tests.

Independent artifact digests matched GitHub metadata:

```text
ANDROID_ARTIFACT_SHA256=acd11a37f2af041aaf0105befe8a62ebc3b2574695c5a50a34d293ce353a5229
GENESIS_ARTIFACT_SHA256=32b8361d44d68fd32d7f12d08c0f6773eed21df284bf1d997582749dad1f664b
```

## Historical residuals carried forward

- AGENT-specific mutation testing was not established; the bounded PIT pilot targeted `GenesisManifestVerifierCore`.
- `AgentInstanceLifecycleRepository.kt` had zero direct instrumented line coverage even though protocol/finalizer and kill/reopen evidence passed.
- agent/vault mutex serialization assumed the current single-process Android architecture.
- physical ARM64 inference remained outside emulator execution.

These residuals remain quality debt unless later closed by separate evidence. They do not invalidate the bounded integration and do not imply operational birth.

## Current authority

For current executable truth, use:

- `docs/CURRENT_RUNTIME_CONTRACT.md`;
- `docs/F3_CROSS_DATABASE_OPERATION_INVENTORY.md`;
- `docs/adr/ADR-0002-cross-database-operation-protocol.md`;
- `docs/CURRENT_DOCUMENT_SOVEREIGNTY_AUDIT.md`.
