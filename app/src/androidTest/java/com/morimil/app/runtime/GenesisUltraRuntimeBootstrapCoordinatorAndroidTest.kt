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
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.MorimilDatabase
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
    fun cleanUltraBootstrapIsIdempotentAndNeverCreatesLegacyBirthRows() = runBlocking {
        val identity = validIdentity()
        val coordinator = GenesisUltraRuntimeBootstrapCoordinator.production(
            memoryDatabase = memoryDatabase,
            organDatabase = organDatabase
        )

        val first = coordinator.bootstrap(identity, nowMillis = 10_000L)
        val second = coordinator.bootstrap(identity, nowMillis = 20_000L)

        val memoryDao = memoryDatabase.memoryDao()
        val organDao = organDatabase.memoryOrganDao()
        val workspace = memoryDao.observeActiveWorkspace().first()
        val projects = memoryDao.observeProjects().first()
        val agents = organDao.observeAgentProfiles().first()
        val devices = organDao.observeOrchestratorDevices().first()

        assertEquals(0, memoryDao.countLocalIdentity())
        assertEquals(0, memoryDao.countGenesisCore())
        assertEquals(1, memoryDao.countWorkspaces())
        assertEquals(identity.instanceId, workspace?.workspaceId)
        assertEquals(identity.companionName, workspace?.displayName)
        assertTrue(workspace?.genesisSource?.startsWith("genesis-ultra:") == true)
        assertEquals(1, projects.size)
        assertEquals("morimil_app:${identity.instanceId}", projects.single().projectId)
        assertTrue(projects.single().status.contains("memory=phase_2_pending"))
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
                role = "custodian_without_ownership",
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

    private fun String.utf8(): ByteArray = toByteArray(StandardCharsets.UTF_8)
}
