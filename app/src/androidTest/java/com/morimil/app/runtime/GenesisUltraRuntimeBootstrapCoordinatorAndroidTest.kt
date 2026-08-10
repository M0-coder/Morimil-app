package com.morimil.app.runtime

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
import com.morimil.app.data.local.AgentProfileEntity
import com.morimil.app.data.local.CrossDatabaseOperationStatus
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.local.OrchestratorDeviceEntity
import com.morimil.app.data.repository.CrossDatabaseCanonicalCommand
import com.morimil.app.data.repository.CrossDatabaseCanonicalEnsurePort
import com.morimil.app.data.repository.CrossDatabaseCanonicalReceipt
import com.morimil.app.data.repository.CrossDatabaseOperationCoordinator
import com.morimil.app.data.repository.RuntimeBootstrapOperationFactory
import com.morimil.app.data.repository.RuntimeBootstrapProtocolFinalizer
import com.morimil.app.data.repository.RuntimeBootstrapProtocolTypes
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenesisUltraRuntimeBootstrapCoordinatorAndroidTest {
    private lateinit var memoryDatabase: MorimilDatabase
    private lateinit var organDatabase: MemoryOrganDatabase

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        memoryDatabase = Room.inMemoryDatabaseBuilder(context, MorimilDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        organDatabase = Room.inMemoryDatabaseBuilder(context, MemoryOrganDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        organDatabase.close()
        memoryDatabase.close()
    }

    @Test
    fun cleanUltraBootstrapIsDurableIdempotentAndNeverCreatesLegacyBirthRows() = runBlocking {
        val identity = validIdentity()
        var canonicalEnsureCalls = 0
        val protocol = bootstrapProtocol {
            canonicalEnsureCalls += 1
            receipt(it, sequence = 301L)
        }
        val coordinator = GenesisUltraRuntimeBootstrapCoordinator.production(
            memoryDatabase = memoryDatabase,
            organDatabase = organDatabase,
            protocol = protocol
        )

        val first = coordinator.bootstrap(identity, nowMillis = 10_000L)
        val second = coordinator.bootstrap(identity, nowMillis = 20_000L)

        val memoryDao = memoryDatabase.memoryDao()
        val legacyArchiveDao = memoryDatabase.legacyArchiveReadDao()
        val organDao = organDatabase.memoryOrganDao()
        val workspace = memoryDao.observeActiveWorkspace().first()
        val projects = memoryDao.observeProjects().first()
        val agents = organDao.observeAgentProfiles().first()
        val devices = organDao.observeOrchestratorDevices().first()
        val command = RuntimeBootstrapOperationFactory.initialize(identity)
        val operation = requireNotNull(
            organDatabase.crossDatabaseOperationDao().loadOperation(command.operationId)
        )

        assertEquals(0, legacyArchiveDao.countLocalIdentity())
        assertEquals(0, legacyArchiveDao.countGenesisCore())
        assertEquals(1, memoryDao.countWorkspaces())
        assertEquals(identity.instanceId, workspace?.workspaceId)
        assertEquals(identity.companionName, workspace?.displayName)
        assertTrue(workspace?.genesisSource?.startsWith("genesis-ultra:") == true)
        assertEquals(1, projects.size)
        assertEquals("morimil_app:${identity.instanceId}", projects.single().projectId)
        assertTrue(projects.single().status.contains("memory=canonical"))
        assertTrue(projects.single().status.contains("boot=durable"))
        assertEquals(7, agents.size)
        assertEquals(4, devices.size)
        assertTrue(devices.any { device ->
            device.deviceId == identity.activeBody.bodyId &&
                device.pairingState == "genesis_ultra_bound" &&
                device.authorizationStatus == "authorized"
        })
        assertEquals(first.workspaceId, second.workspaceId)
        assertEquals(first.projectId, second.projectId)
        assertEquals(7, second.agentProfileCount)
        assertEquals(4, second.orchestratorDeviceCount)
        assertTrue(second.legacyCounts.isEmpty)
        assertEquals(1, canonicalEnsureCalls)
        assertEquals(CrossDatabaseOperationStatus.COMMITTED, operation.status)
    }

    @Test
    fun existingOrchestrationSeedIsPreservedForSeparateOrch001Convergence() = runBlocking {
        val legacyAgent = AgentProfileEntity(
            agentId = "legacy_agent",
            displayName = "Legacy Agent",
            role = "legacy_role",
            description = "Legacy seed that BOOT must preserve for ORCH-001 convergence.",
            capabilitySetJson = "[]",
            allowedToolsetJson = "[]",
            allowedTransportsJson = "[]",
            riskLevel = "low",
            requiresHumanApproval = true,
            status = "active",
            createdAtMillis = 1_111L,
            updatedAtMillis = 1_222L
        )
        val legacyDevice = OrchestratorDeviceEntity(
            deviceId = "legacy_android_body",
            displayName = "Legacy Android Body",
            deviceType = "android_phone",
            ownershipScope = "user_owned",
            trustedOwner = "legacy_owner_metadata",
            allowedTransportsJson = "[]",
            authorizationStatus = "authorized",
            authorizationRequired = false,
            riskLevel = "low",
            pairingState = "paired_local",
            lastSeenAtMillis = 1_333L,
            createdAtMillis = 1_111L,
            updatedAtMillis = 1_333L
        )
        val organDao = organDatabase.memoryOrganDao()
        organDao.insertAgentProfiles(listOf(legacyAgent))
        organDao.insertOrchestratorDevices(listOf(legacyDevice))

        val identity = validIdentity()
        var canonicalEnsureCalls = 0
        val coordinator = GenesisUltraRuntimeBootstrapCoordinator.production(
            memoryDatabase = memoryDatabase,
            organDatabase = organDatabase,
            protocol = bootstrapProtocol {
                canonicalEnsureCalls += 1
                receipt(it, sequence = 302L)
            }
        )

        val report = coordinator.bootstrap(identity, nowMillis = 30_000L)
        val agents = organDao.observeAgentProfiles().first()
        val devices = organDao.observeOrchestratorDevices().first()
        val legacyArchiveDao = memoryDatabase.legacyArchiveReadDao()

        assertEquals(1, agents.size)
        assertEquals(legacyAgent, agents.single())
        assertEquals(1, devices.size)
        assertEquals(legacyDevice, devices.single())
        assertEquals(1, report.agentProfileCount)
        assertEquals(1, report.orchestratorDeviceCount)
        assertEquals(1, canonicalEnsureCalls)
        assertEquals(0, legacyArchiveDao.countLocalIdentity())
        assertEquals(0, legacyArchiveDao.countGenesisCore())
    }

    private fun bootstrapProtocol(
        ensure: suspend (CrossDatabaseCanonicalCommand) -> CrossDatabaseCanonicalReceipt
    ): CrossDatabaseOperationCoordinator {
        return CrossDatabaseOperationCoordinator.production(
            database = organDatabase,
            canonicalEnsurePort = object : CrossDatabaseCanonicalEnsurePort {
                override suspend fun ensureCommitted(
                    command: CrossDatabaseCanonicalCommand
                ): CrossDatabaseCanonicalReceipt = ensure(command)
            },
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

    private fun receipt(
        command: CrossDatabaseCanonicalCommand,
        sequence: Long
    ): CrossDatabaseCanonicalReceipt {
        return CrossDatabaseCanonicalReceipt(
            eventId = command.eventId,
            eventHash = "evsha256:" + digest("event:${command.eventId}").removePrefix("sha256:"),
            sequence = sequence,
            provenanceDigest = digest("provenance:${command.eventId}"),
            reusedExistingEvent = false
        )
    }

    private fun validIdentity(): GenesisUltraRuntimeIdentity {
        val doctrine = document("doctrine/free-birth.md", "doctrine", "free birth doctrine")
        val charter = document("policy/freedom-charter.json", "freedom_charter", "{}")
        val recovery = document("policy/recovery-policy.json", "recovery_policy", "{}")
        return GenesisUltraRuntimeIdentity(
            instanceId = "instance_android_test",
            companionName = "Morimil",
            bornAt = "2026-07-25T00:00:00Z",
            identityDigest = GenesisUltraHashProfile.sha256("identity".utf8()),
            activeBody = GenesisUltraRuntimeActiveBody(
                bodyId = "body_android_test",
                status = "active_writer",
                platformProfile = "android",
                publicKeyFingerprint = GenesisUltraHashProfile.sha256("body-key".utf8()),
                keyEpochId = "body_key_epoch_1",
                keyEpochDigest = GenesisUltraHashProfile.sha256("body-epoch".utf8()),
                registryEpoch = 1L,
                registryDigest = GenesisUltraHashProfile.sha256("registry".utf8())
            ),
            guardian = GenesisUltraRuntimeGuardian(
                guardianId = "guardian_test",
                keyEpochId = "guardian_key_epoch_1",
                publicKeyRef = GenesisUltraHashProfile.sha256("guardian-key".utf8()),
                status = "active",
                role = "custodian_witness",
                anchorDigest = GenesisUltraHashProfile.sha256("guardian-anchor".utf8())
            ),
            seed = GenesisUltraRuntimeVerifiedSeed(
                seedId = "seed_test",
                rootHash = GenesisUltraHashProfile.sha256("seed-root".utf8()),
                protocolVersion = "genesis-ultra-v1",
                hashProfile = "sha256",
                identityDigest = GenesisUltraHashProfile.sha256("seed-identity".utf8()),
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
                authorizationDigest = GenesisUltraHashProfile.sha256("authorization".utf8()),
                candidateDigest = GenesisUltraHashProfile.sha256("candidate".utf8()),
                consentDigest = GenesisUltraHashProfile.sha256("consent".utf8()),
                authorizedAt = "2026-07-25T00:00:00Z",
                expiresAt = "2026-07-25T00:05:00Z",
                receiptDigest = GenesisUltraHashProfile.sha256("receipt".utf8()),
                birthStatus = "born",
                ownershipConferred = false
            )
        )
    }

    private fun document(
        relativePath: String,
        kind: String,
        text: String
    ): GenesisUltraRuntimeDocument {
        val bytes = text.utf8()
        return GenesisUltraRuntimeDocument(
            relativePath = relativePath,
            documentKind = kind,
            digest = GenesisUltraHashProfile.sha256(bytes),
            sourceBytes = bytes
        )
    }

    private fun digest(value: String): String =
        GenesisUltraHashProfile.sha256(value.toByteArray(StandardCharsets.UTF_8))

    private fun String.utf8(): ByteArray = toByteArray(StandardCharsets.UTF_8)

    private class IncrementingClock : () -> Long {
        private var value = 10_000L
        override fun invoke(): Long = value++
    }
}
