package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CognitiveMigrationImplementationBlueprintContractTest {
    @Test
    fun blueprintIsCurrentTrackedAndStopGated() {
        val blueprint = blueprintFile(repositoryRoot()).readText()

        assertTrue(blueprint.startsWith("# Document status: CURRENT"))
        assertTrue(blueprint.contains("ADR-0002"))
        assertTrue(blueprint.contains("`#88`"))
        assertTrue(blueprint.contains("STOP S5 remains open"))
        assertTrue(blueprint.contains("This blueprint does not authorize runtime changes."))
        assertTrue(
            blueprint.contains(
                "The `cross_database_operations` table and the common cognitive-migration " +
                    "protocol do not exist in production at this baseline."
            )
        )
        assertFalse(blueprint.contains("STOP S5 is closed"))
        assertFalse(blueprint.contains("Status: implemented"))
        assertFalse(blueprint.contains("protocol is active in production"))
    }

    @Test
    fun allCognitiveOperationsAndCanonicalEventsAreSpecified() {
        val blueprint = blueprintFile(repositoryRoot()).readText()

        listOf("`COG-001`", "`COG-002`", "`COG-003`", "`COG-004`").forEach { operation ->
            assertTrue("Missing cognitive operation $operation", blueprint.contains(operation))
        }
        listOf(
            "cognitive_migration.proposed",
            "cognitive_migration.approved",
            "cognitive_migration.executed",
            "cognitive_migration.rollback"
        ).forEach { eventType ->
            assertTrue("Missing canonical event type $eventType", blueprint.contains(eventType))
        }
    }

    @Test
    fun protocolStatesStayCompleteAndOrdered() {
        val blueprint = blueprintFile(repositoryRoot()).readText()
        val section = blueprint.substringAfter("## 10. State machine")
        val states = listOf(
            "STAGED",
            "PENDING_CANONICAL",
            "CANONICAL_COMMITTED",
            "PENDING_LOCAL_COMMIT",
            "COMMITTED",
            "BLOCKED"
        )
        val orderedBlock = section.substringAfter("The only normal forward order is:")
            .substringBefore("Interpretation:")
        val positions = states.map { state ->
            Regex("(?m)^${Regex.escape(state)}$").find(orderedBlock)?.range?.first ?: -1
        }

        positions.forEach { position ->
            assertTrue("Missing protocol state", position >= 0)
        }
        positions.zipWithNext().forEach { (left, right) ->
            assertTrue("Protocol states are out of order", left < right)
        }
    }

    @Test
    fun deterministicIdentitiesExcludeTheClock() {
        val blueprint = blueprintFile(repositoryRoot()).readText()

        listOf(
            "operationId",
            "eventId",
            "migrationId",
            "proposalId",
            "approvalId"
        ).forEach { identity ->
            assertTrue("Missing deterministic identity $identity", blueprint.contains(identity))
        }
        assertTrue(
            blueprint.contains(
                "The clock is metadata only and is prohibited from being used as identity."
            )
        )
        assertTrue(blueprint.contains("approvalId = operationId"))
    }

    @Test
    fun cog001UsesACompleteVerifiedCanonicalPlanningContract() {
        val blueprint = blueprintFile(repositoryRoot()).readText()

        listOf(
            "CognitiveMigrationCanonicalReadPort",
            "VerifiedCognitiveMigrationPlanningInput",
            "morimil.cognitive_migration.snapshot_descriptor.v1",
            "canonicalRecordSetDigest",
            "canonicalPreSnapshotHash",
            "planCoreJson",
            "planCoreDigest",
            "plan_core_digest"
        ).forEach { requirement ->
            assertTrue("Missing COG-001 planning requirement $requirement", blueprint.contains(requirement))
        }
        assertTrue(blueprint.contains("The descriptor covers the complete verified snapshot"))
        assertTrue(blueprint.contains("fails closed before staging"))
    }

    @Test
    fun implementationEvidenceRequiresMigrationConflictRecoveryAndKillTests() {
        val blueprint = blueprintFile(repositoryRoot()).readText()

        listOf(
            "Room migration",
            "API 30 and API 35",
            "exact-match",
            "payload conflict",
            "provenance conflict",
            "recovery",
            "zero duplicate canonical events",
            "zero duplicate visible MigrationRecord rows"
        ).forEach { requirement ->
            assertTrue("Missing implementation requirement $requirement", blueprint.contains(requirement))
        }
        assertTrue(blueprint.contains("After append, before persisting receipt"))
        assertTrue(blueprint.contains("Repeated same user action"))
        assertTrue(blueprint.contains("stale writer epoch", ignoreCase = true))
    }

    @Test
    fun firstFunctionalPrKeepsProjectVaultAndOtherOwnersOutOfScope() {
        val blueprint = blueprintFile(repositoryRoot()).readText()

        assertTrue(
            blueprint.contains(
                "`ProjectVault` remains unchanged in the first functional PR."
            )
        )
        assertTrue(blueprint.contains("It must not modify or migrate:"))
        listOf("ORCH", "AGENT", "BOOT", "RECALL", "REST", "ProjectVault").forEach { excluded ->
            assertTrue("Missing excluded owner $excluded", blueprint.contains(excluded))
        }
        assertFalse(
            blueprint.contains(
                "ProjectVault will be rewritten in the first functional PR"
            )
        )
    }

    @Test
    fun blueprintForbidsLegacyCompatibilityForCog001() {
        val blueprint = blueprintFile(repositoryRoot()).readText()

        assertTrue(blueprint.contains("GenesisUltraRuntimeIdentityRepository"))
        assertTrue(blueprint.contains("CanonicalMemoryRepository"))
        assertTrue(
            blueprint.contains(
                "`COG-001` must not read or create compatibility rows in " +
                    "`memory_events`, `genesis_core`, or `local_instance_identity`."
            )
        )
        assertTrue(blueprint.contains("instanceId != bodyId"))
    }

    private fun blueprintFile(root: File): File {
        val file = File(root, BLUEPRINT_PATH)
        assertTrue("Missing cognitive migration implementation blueprint", file.isFile)
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
        const val BLUEPRINT_PATH =
            "docs/F3_COGNITIVE_MIGRATION_IMPLEMENTATION_BLUEPRINT.md"
    }
}
