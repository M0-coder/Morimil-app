package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossDatabaseOperationProtocolAdrContractTest {
    @Test
    fun adrIsCurrentAcceptedAndDraftMergeGated() {
        val adr = adrFile(repositoryRoot()).readText()

        assertTrue(adr.startsWith("# Document status: CURRENT"))
        assertTrue(adr.contains("# ADR-0002 — Common recoverable cross-database operation protocol"))
        assertTrue(adr.contains("Status: Accepted design with audited candidate amendment"))
        assertTrue(adr.contains("`STOP_S5=CLOSED`"))
        assertTrue(adr.contains("draft PR `#149`"))
        assertTrue(adr.contains("`MERGE_AUTHORIZED=false`"))
        assertTrue(adr.contains("ProjectVault remains unchanged in the first F3.2 implementation."))
        assertFalse(adr.contains("STOP S5 remains open"))
    }

    @Test
    fun deterministicIdentityRejectsWallClockIds() {
        val adr = adrFile(repositoryRoot()).readText()

        listOf(
            "`operationId`",
            "`eventId`",
            "`payloadDigest`",
            "`instanceId`",
            "`writerBodyId`",
            "`writerEpoch`",
            "`canonicalEventHash`",
            "`canonicalSequence`",
            "`canonicalProvenanceDigest`",
            "`attemptCount`",
            "`lastErrorCode`"
        ).forEach { field ->
            assertTrue("ADR is missing required field $field", adr.contains(field))
        }

        assertTrue(adr.contains("Wall-clock time is metadata only."))
        assertTrue(adr.contains("MUST NOT participate in `operationId`, `eventId`"))
        assertTrue(adr.contains("`proposalId`, `migrationId`, or `approvalId`."))
    }

    @Test
    fun authorityFrontierUsesTheF1AProjectionOnly() {
        val adr = adrFile(repositoryRoot()).readText()
        val identityAuthority = adr.indexOf(
            "GenesisUltraRuntimeIdentityRepository + CanonicalMemoryRepository"
        )
        val commonBoundary = adr.indexOf("-> CanonicalConsumerReadPort", identityAuthority)
        val specializedBoundary = adr.indexOf(
            "-> CognitiveMigrationCanonicalReadPort",
            commonBoundary
        )

        assertTrue(identityAuthority >= 0)
        assertTrue(commonBoundary > identityAuthority)
        assertTrue(specializedBoundary > commonBoundary)
        assertTrue(adr.contains("must not reopen a second direct identity or\nmemory authority"))
        assertTrue(adr.contains("No compatibility write to `genesis_core`"))
        assertTrue(adr.contains("Guardian approval authorizes only the bounded Body operation"))
    }

    @Test
    fun protocolStateMachineStaysOrderedAndFailClosed() {
        val adr = adrFile(repositoryRoot()).readText()
        val stateSection = adr.substringAfter("## State machine")
        val states = listOf(
            "`STAGED`",
            "`PENDING_CANONICAL`",
            "`CANONICAL_COMMITTED`",
            "`PENDING_LOCAL_COMMIT`",
            "`COMMITTED`",
            "`BLOCKED`"
        )
        val positions = states.map { state -> stateSection.indexOf(state) }

        positions.forEach { position ->
            assertTrue("ADR is missing a protocol state", position >= 0)
        }
        positions.zipWithNext().forEach { (left, right) ->
            assertTrue("Protocol states are out of order", left < right)
        }
        assertTrue(adr.contains("no new visible/authoritative owner state exists"))
        assertTrue(adr.contains("silently editing the staged payload is forbidden"))
    }

    @Test
    fun cognitiveMigrationMappingIsCompleteAndCanonical() {
        val adr = adrFile(repositoryRoot()).readText()

        listOf("`COG-001`", "`COG-002`", "`COG-003`", "`COG-004`").forEach { id ->
            assertTrue("ADR is missing cognitive operation $id", adr.contains(id))
        }
        listOf(
            "cognitive_migration.proposed",
            "cognitive_migration.approved",
            "cognitive_migration.executed",
            "cognitive_migration.rollback"
        ).forEach { eventType ->
            assertTrue("ADR is missing canonical event $eventType", adr.contains(eventType))
        }

        assertTrue(adr.contains("prepare canonical audit outside the Room write transaction", ignoreCase = true))
        assertTrue(adr.contains("real snapshot digest", ignoreCase = true))
        assertTrue(adr.contains("no fabricated snapshot identifier", ignoreCase = true))
        assertTrue(adr.contains("temporary audit failure remains retryable", ignoreCase = true))
        assertTrue(adr.contains("complete canonical provenance and note preimage", ignoreCase = true))
    }

    @Test
    fun recoveryUsesTypedErrorsAndDurableRemainder() {
        val adr = adrFile(repositoryRoot()).readText()

        assertTrue(adr.contains("Error durability is typed"))
        assertTrue(adr.contains("must not be inferred from free-form exception messages"))
        assertTrue(adr.contains("durable post-recovery state"))
        assertTrue(adr.contains("zero non-committed COG-001 payload-v1 rows"))
        assertTrue(adr.contains("exact-full-batch recovery without false remainder"))
    }

    @Test
    fun firstFunctionalPrRemainsNarrowAndKillTested() {
        val adr = adrFile(repositoryRoot()).readText()
        val scope = adr.substringAfter("## Implementation sequence and scope")

        assertTrue(scope.contains("The first functional PR is isolated to the common journal"))
        assertTrue(scope.contains("coordinator/commit-port"))
        assertTrue(scope.contains("Room migration and fresh-schema guards"))
        assertTrue(scope.contains("`COG-001` through"))
        assertTrue(scope.contains("`COG-004`"))
        assertTrue(adr.contains("API 30 and API 35"))
        assertTrue(adr.contains("zero duplicate canonical events"))
        assertTrue(adr.contains("zero duplicate visible owner rows"))
        assertTrue(adr.contains("all required CI checks and SBOM green on the exact head SHA"))
        listOf("ORCH", "AGENT", "BOOT", "RECALL", "REST", "ProjectVault").forEach { excluded ->
            assertTrue("ADR is missing excluded scope $excluded", adr.contains(excluded))
        }
    }

    private fun adrFile(root: File): File {
        val file = File(root, ADR_PATH)
        assertTrue("Missing ADR-0002 common protocol", file.isFile)
        return file
    }

    private fun repositoryRoot(): File {
        return sequenceOf(File("."), File(".."))
            .map(File::getCanonicalFile)
            .firstOrNull { candidate ->
                File(candidate, "README.md").isFile &&
                    File(candidate, "app/build.gradle.kts").isFile
            }
            ?: error("Repository root not found")
    }

    private companion object {
        const val ADR_PATH = "docs/adr/ADR-0002-cross-database-operation-protocol.md"
    }
}
