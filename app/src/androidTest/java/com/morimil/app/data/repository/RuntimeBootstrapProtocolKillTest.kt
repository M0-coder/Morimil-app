package com.morimil.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.morimil.app.data.genesis.ultra.GenesisUltraHashProfile
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeActiveBody
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeAuthorization
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeAuthorizationState
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeDocument
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeGuardian
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimePolicy
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeVerifiedSeed
import com.morimil.app.data.local.CrossDatabaseOperationStatus
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.MorimilDatabase
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeBootstrapProtocolKillTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun memoryProjectionAfterReceiptRecoversOwnerProjectionAfterDatabaseReopen() = runBlocking {
        val memoryName = testDatabaseName("memory")
        val organName = testDatabaseName("organ")
        context.deleteDatabase(memoryName)
        context.deleteDatabase(organName)
        var memoryDatabase = openMemoryDatabase(memoryName)
        var organDatabase = openOrganDatabase(organName)
        try {
            val identity = identity()
            val command = RuntimeBootstrapOperationFactory.initialize(identity)
            val receipt = receipt(command, sequence = 501L)
            stagePendingLocalCommit(organDatabase, command, receipt)

            val operation = requireNotNull(
                organDatabase.crossDatabaseOperationDao().loadOperation(command.operationId)
            )
            val finalizer = RuntimeBootstrapProtocolFinalizer(
                memoryDatabase = memoryDatabase,
                organDatabase = organDatabase
            )

            // Simulate process interruption after the MorimilDatabase saga preparation
            // but before MemoryOrganDatabase + XOP COMMITTED finalization.
            finalizer.prepareOutsideTransaction(operation, receipt)

            assertEquals(identity.instanceId, memoryDatabase.memoryDao().observeActiveWorkspace().first()?.workspaceId)
            assertEquals(1, memoryDatabase.memoryDao().observeProjects().first().size)
            assertEquals(0, organDatabase.memoryOrganDao().countAgentProfiles())
            assertEquals(0, organDatabase.memoryOrganDao().countOrchestratorDevices())
            assertEquals(
                CrossDatabaseOperationStatus.PENDING_LOCAL_COMMIT,
                organDatabase.crossDatabaseOperationDao().loadOperation(command.operationId)?.status
            )

            memoryDatabase.close()
            organDatabase.close()
            memoryDatabase = openMemoryDatabase(memoryName)
            organDatabase = openOrganDatabase(organName)

            val report = coordinator(memoryDatabase, organDatabase).recoverAtStartup(identity, 20)
            val recovered = requireNotNull(
                organDatabase.crossDatabaseOperationDao().loadOperation(command.operationId)
            )
            val workspace = memoryDatabase.memoryDao().observeActiveWorkspace().first()
            val project = memoryDatabase.memoryDao().observeProjects().first().single()
            val devices = organDatabase.memoryOrganDao().observeOrchestratorDevices().first()
            val legacyArchiveDao = memoryDatabase.legacyArchiveReadDao()

            assertEquals(1, report.recoveredCount)
            assertEquals(CrossDatabaseOperationStatus.COMMITTED, recovered.status)
            assertEquals(RuntimeBootstrapProtocolSchemas.BOOT_001_LOCAL_RESULT, recovered.localResultSchema)
            assertEquals(identity.instanceId, workspace?.workspaceId)
            assertTrue(project.status.contains("memory=canonical"))
            assertTrue(project.status.contains("boot=durable"))
            assertEquals(7, organDatabase.memoryOrganDao().countAgentProfiles())
            assertEquals(4, organDatabase.memoryOrganDao().countOrchestratorDevices())
            assertTrue(devices.any { device ->
                device.deviceId == identity.activeBody.bodyId &&
                    device.authorizationStatus == "authorized" &&
                    device.pairingState == "genesis_ultra_bound"
            })
            assertEquals(0, legacyArchiveDao.countLocalIdentity())
            assertEquals(0, legacyArchiveDao.countGenesisCore())
        } finally {
            if (memoryDatabase.isOpen) memoryDatabase.close()
            if (organDatabase.isOpen) organDatabase.close()
            context.deleteDatabase(memoryName)
            context.deleteDatabase(organName)
        }
    }

    private fun coordinator(
        memoryDatabase: MorimilDatabase,
        organDatabase: MemoryOrganDatabase
    ): CrossDatabaseOperationCoordinator {
        return CrossDatabaseOperationCoordinator.production(
            database = organDatabase,
            canonicalEnsurePort = canonicalMustNotRun(),
            finalizers = listOf(
                RuntimeBootstrapProtocolFinalizer(
                    memoryDatabase = memoryDatabase,
                    organDatabase = organDatabase
                )
            ),
            protocolRegistry = RuntimeBootstrapProtocolTypes.REGISTRY,
            clockMillis = IncrementingClock()
        )
    }

    private suspend fun stagePendingLocalCommit(
        database: MemoryOrganDatabase,
        command: CrossDatabaseStageCommand,
        receipt: CrossDatabaseCanonicalReceipt
    ) {
        val stagingCoordinator = CrossDatabaseOperationCoordinator.production(
            database = database,
            canonicalEnsurePort = canonicalMustNotRun(),
            finalizers = listOf(
                object : CrossDatabaseTypedFinalizer {
                    override val supportedOperationTypes =
                        RuntimeBootstrapProtocolTypes.CLOSED_REGISTRY.keys

                    override suspend fun finalizeInsideTransaction(
                        operation: CrossDatabaseOperationRecord,
                        receipt: CrossDatabaseCanonicalReceipt
                    ): CrossDatabaseLocalResult = error("finalizer_must_not_run")
                }
            ),
            protocolRegistry = RuntimeBootstrapProtocolTypes.REGISTRY,
            clockMillis = IncrementingClock()
        )
        stagingCoordinator.stageExact(command)
        val dao = database.crossDatabaseOperationDao()
        assertEquals(1, dao.transitionStagedToPendingCanonical(command.operationId, 3_001L))
        assertEquals(
            1,
            dao.persistCanonicalReceipt(
                operationId = command.operationId,
                canonicalEventHash = receipt.eventHash,
                canonicalSequence = receipt.sequence,
                canonicalProvenanceDigest = receipt.provenanceDigest,
                updatedAtMillis = 3_002L
            )
        )
        assertEquals(
            1,
            dao.transitionCanonicalCommittedToPendingLocalCommit(command.operationId, 3_003L)
        )
    }

    private fun canonicalMustNotRun(): CrossDatabaseCanonicalEnsurePort =
        object : CrossDatabaseCanonicalEnsurePort {
            override suspend fun ensureCommitted(
                command: CrossDatabaseCanonicalCommand
            ): CrossDatabaseCanonicalReceipt = error("canonical_must_not_replay")
        }

    private fun receipt(
        command: CrossDatabaseStageCommand,
        sequence: Long
    ): CrossDatabaseCanonicalReceipt {
        return CrossDatabaseCanonicalReceipt(
            eventId = command.eventId,
            eventHash = "evsha256:" + digest("event:${command.eventId}").removePrefix("sha256:"),
            sequence = sequence,
            provenanceDigest = digest("provenance:${command.eventId}"),
            reusedExistingEvent = true
        )
    }

    private fun openMemoryDatabase(name: String): MorimilDatabase =
        Room.databaseBuilder(context, MorimilDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()

    private fun openOrganDatabase(name: String): MemoryOrganDatabase =
        Room.databaseBuilder(context, MemoryOrganDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()

    private fun identity(): GenesisUltraRuntimeIdentity {
        val doctrine = document("doctrine/test.md", "doctrine", "doctrine")
        val charter = document("policy/charter.json", "freedom_charter", "{}")
        val recovery = document("policy/recovery.json", "recovery_policy", "{}")
        return GenesisUltraRuntimeIdentity(
            instanceId = "instance_boot_protocol_recovery_test",
            companionName = "Morimil",
            bornAt = "2026-08-08T00:00:00Z",
            identityDigest = digest("identity"),
            activeBody = GenesisUltraRuntimeActiveBody(
                bodyId = "body_boot_protocol_recovery_test",
                status = "active_writer",
                platformProfile = "android",
                publicKeyFingerprint = digest("body_key"),
                keyEpochId = "epoch_boot_protocol_recovery_test",
                keyEpochDigest = digest("epoch"),
                registryEpoch = 1,
                registryDigest = digest("registry")
            ),
            guardian = GenesisUltraRuntimeGuardian(
                guardianId = "guardian_test",
                keyEpochId = "guardian_epoch",
                publicKeyRef = digest("guardian_key"),
                status = "active",
                role = "custodian_witness",
                anchorDigest = digest("guardian_anchor")
            ),
            seed = GenesisUltraRuntimeVerifiedSeed(
                seedId = "seed_test",
                rootHash = digest("seed"),
                protocolVersion = "genesis-ultra-v1",
                hashProfile = GenesisUltraHashProfile.FIELD_PROFILE,
                identityDigest = digest("identity"),
                doctrineDigest = doctrine.digest
            ),
            doctrine = doctrine,
            policy = GenesisUltraRuntimePolicy(
                freedomCharter = charter,
                recoveryPolicy = recovery,
                freedomCharterDigest = charter.digest,
                recoveryPolicyDigest = recovery.digest
            ),
            authorization = GenesisUltraRuntimeAuthorization(
                state = GenesisUltraRuntimeAuthorizationState.COMMITTED,
                authorizationDigest = digest("authorization"),
                candidateDigest = digest("candidate"),
                consentDigest = digest("consent"),
                authorizedAt = "2026-08-08T00:00:00Z",
                expiresAt = "2026-08-08T01:00:00Z",
                receiptDigest = digest("receipt"),
                birthStatus = "born",
                ownershipConferred = false
            )
        )
    }

    private fun document(path: String, kind: String, text: String): GenesisUltraRuntimeDocument {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        return GenesisUltraRuntimeDocument(
            relativePath = path,
            documentKind = kind,
            digest = GenesisUltraHashProfile.sha256(bytes),
            sourceBytes = bytes
        )
    }

    private fun digest(value: String): String =
        GenesisUltraHashProfile.sha256(value.toByteArray(StandardCharsets.UTF_8))

    private fun testDatabaseName(suffix: String): String =
        "$TEST_DATABASE_PREFIX-$suffix.db"

    private class IncrementingClock : () -> Long {
        private var value = 2_000L
        override fun invoke(): Long = value++
    }

    private companion object {
        const val TEST_DATABASE_PREFIX = "runtime-bootstrap-protocol-recovery"
    }
}
