package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class F32CrossDatabaseKillCoverageContractTest {
    @Test
    fun everyCrossDatabaseOperationHasDurableReopenEvidence() {
        val projectVault = androidTest("data/repository/ProjectVaultOutboxRecoveryTest.kt") +
            androidTest("data/repository/ProjectVaultArchiveOutboxRecoveryTest.kt")
        val cognitive = androidTest("data/repository/CognitiveMigrationProtocolKillTest.kt") +
            androidTest("data/repository/CognitiveMigrationRemainingOperationKillTest.kt")
        val orchestration = androidTest("data/repository/OrchestrationProtocolKillTest.kt")
        val agents = androidTest("data/repository/AgentLifecycleProtocolKillTest.kt") +
            androidTest("data/repository/AgentLifecycleRemainingOperationKillTest.kt")
        val bootstrap = androidTest("data/repository/RuntimeBootstrapProtocolKillTest.kt")
        val rest = androidTest("data/repository/RestCycleProtocolKillTest.kt")

        listOf(
            "createProjectVaultFromIntent" to projectVault,
            "completeProjectVault" to projectVault,
            "archiveProjectVault" to projectVault,
            "CognitiveMigrationProtocolTypes.PROPOSE" to cognitive,
            "CognitiveMigrationOperationFactory.approve" to cognitive,
            "CognitiveMigrationOperationFactory.execute" to cognitive,
            "CognitiveMigrationOperationFactory.rollback" to cognitive,
            "OrchestrationOperationFactory.propose" to orchestration,
            "OrchestrationOperationFactory.approve" to orchestration,
            "OrchestrationOperationFactory.reject" to orchestration,
            "AgentLifecycleOperationFactory.create" to agents,
            "AgentLifecycleOperationFactory.assign" to agents,
            "AgentLifecycleOperationFactory.submitResult" to agents,
            "AgentLifecycleOperationFactory.evaluate" to agents,
            "AgentLifecycleOperationFactory.promote" to agents,
            "AgentLifecycleOperationFactory.retire" to agents,
            "AgentLifecycleOperationFactory.quarantine" to agents,
            "RuntimeBootstrapOperationFactory.initialize" to bootstrap,
            "RestCycleOperationFactory.execute" to rest,
            "RestCycleOperationFactory.proposeRepair" to rest
        ).forEach { (token, source) ->
            assertTrue("Missing F3.2 durable reopen evidence for $token", source.contains(token))
        }

        listOf(projectVault, cognitive, orchestration, agents, bootstrap, rest).forEach { source ->
            assertTrue(source.contains("database.close()") || source.contains("db.close()"))
            assertTrue(source.contains("recoverAtStartup") || source.contains("recoverPendingOperations"))
        }
    }

    private fun androidTest(relativePath: String): String =
        repositoryFile("app/src/androidTest/java/com/morimil/app/$relativePath").readText()

    private fun repositoryFile(relativePath: String): File =
        sequenceOf(File(relativePath), File("../$relativePath")).firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")
}
