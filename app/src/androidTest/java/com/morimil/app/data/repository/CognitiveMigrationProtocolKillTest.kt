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
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CognitiveMigrationProtocolKillTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun interruptedCanonicalAppendRecoversOnceAndFinalizesOnce() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            context,
            MemoryOrganDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val identity = identity()
            val command = command(identity)
            var interruptedCalls = 0
            val interruptedCoordinator = coordinator(
                database = database,
                canonical = object : CrossDatabaseCanonicalEnsurePort {
                    override suspend fun ensureCommitted(
                        command: CrossDatabaseCanonicalCommand
                    ): CrossDatabaseCanonicalReceipt {
                        interruptedCalls += 1
                        throw IOException("simulated_append_interruption")
                    }
                }
            )
            interruptedCoordinator.stageExact(command)

            val interruptedReport = interruptedCoordinator.recoverAtStartup(identity, 20)
            val pending = database.crossDatabaseOperationDao()
                .loadOperation(command.operationId)
            assertEquals(1, interruptedCalls)
            assertEquals(1, interruptedReport.retryableFailureCount)
            assertEquals(CrossDatabaseOperationStatus.PENDING_CANONICAL, pending?.status)
            assertEquals(1, pending?.attemptCount)

            var recoveredCanonicalCalls = 0
            var finalizerCalls = 0
            val recoveredCoordinator = coordinator(
                database = database,
                canonical = object : CrossDatabaseCanonicalEnsurePort {
                    override suspend fun ensureCommitted(
                        command: CrossDatabaseCanonicalCommand
                    ): CrossDatabaseCanonicalReceipt {
                        recoveredCanonicalCalls += 1
                        return receipt(command.eventId)
                    }
                },
                onFinalize = {
                    finalizerCalls += 1
                }
            )
            val recoveredReport = recoveredCoordinator.recoverAtStartup(identity, 20)
            val committed = database.crossDatabaseOperationDao()
                .loadOperation(command.operationId)

            assertEquals(1, recoveredReport.recoveredCount)
            assertEquals(1, recoveredCanonicalCalls)
            assertEquals(1, finalizerCalls)
            assertEquals(CrossDatabaseOperationStatus.COMMITTED, committed?.status)
            assertNotNull(committed?.canonicalEventHash)
            assertNotNull(committed?.localResultDigest)

            val replayReport = recoveredCoordinator.recoverAtStartup(identity, 20)
            assertEquals(0, replayReport.recoveredCount)
            assertEquals(1, recoveredCanonicalCalls)
            assertEquals(1, finalizerCalls)
        } finally {
            database.close()
        }
    }

    private fun coordinator(
        database: MemoryOrganDatabase,
        canonical: CrossDatabaseCanonicalEnsurePort,
        onFinalize: () -> Unit = {}
    ): CrossDatabaseOperationCoordinator {
        return CrossDatabaseOperationCoordinator.production(
            database = database,
            canonicalEnsurePort = canonical,
            finalizers = listOf(
                object : CrossDatabaseTypedFinalizer {
                    override val supportedOperationTypes =
                        setOf(CognitiveMigrationProtocolTypes.PROPOSE)

                    override suspend fun finalizeInsideTransaction(
                        operation: CrossDatabaseOperationRecord,
                        receipt: CrossDatabaseCanonicalReceipt
                    ): CrossDatabaseLocalResult {
                        onFinalize()
                        val json = CrossDatabaseOperationIdentity.canonicalJson(
                            mapOf(
                                "owner_status" to "planned",
                                "schema" to "test.kill.local_result.v1"
                            )
                        )
                        return CrossDatabaseLocalResult(
                            schema = "test.kill.local_result.v1",
                            json = json,
                            digest =
                                CrossDatabaseOperationIdentity.digestCanonicalJson(json),
                            ownerStatus = "planned"
                        )
                    }
                }
            ),
            clockMillis = IncrementingClock()
        )
    }

    private fun command(identity: GenesisUltraRuntimeIdentity): CrossDatabaseStageCommand {
        val payload = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf("schema" to "test.kill.payload.v1", "subject" to MIGRATION_ID)
        )
        val payloadDigest = CrossDatabaseOperationIdentity.digestCanonicalJson(payload)
        val operationId = CrossDatabaseOperationIdentity.operationId(
            operationType = CognitiveMigrationProtocolTypes.PROPOSE,
            operationVersion = 1,
            instanceId = identity.instanceId,
            writerBodyId = identity.activeBody.bodyId,
            writerEpoch = identity.activeBody.keyEpochId,
            subjectId = MIGRATION_ID,
            parentOperationId = null,
            childPhase = null,
            payloadDigest = payloadDigest
        )
        val eventId = CrossDatabaseOperationIdentity.eventId(
            operationId,
            CognitiveMigrationProtocolTypes.PROPOSED_EVENT
        )
        val evidence = CrossDatabaseOperationIdentity.canonicalJson(
            mapOf(
                "event_id" to eventId,
                "operation_id" to operationId,
                "schema" to "test.kill.evidence.v1"
            )
        )
        return CrossDatabaseStageCommand(
            operationId = operationId,
            ownerType = CognitiveMigrationProtocolTypes.OWNER_TYPE,
            operationType = CognitiveMigrationProtocolTypes.PROPOSE,
            operationVersion = 1,
            instanceId = identity.instanceId,
            writerBodyId = identity.activeBody.bodyId,
            writerEpoch = identity.activeBody.keyEpochId,
            subjectId = MIGRATION_ID,
            parentOperationId = null,
            childPhase = null,
            payloadSchema = "test.kill.payload.v1",
            payloadJson = payload,
            payloadDigest = payloadDigest,
            eventId = eventId,
            eventType = CognitiveMigrationProtocolTypes.PROPOSED_EVENT,
            eventBody = "deterministic kill test body",
            evidenceSchema = "test.kill.evidence.v1",
            evidenceJson = evidence,
            evidenceDigest = CrossDatabaseOperationIdentity.digestCanonicalJson(evidence)
        )
    }

    private fun receipt(eventId: String): CrossDatabaseCanonicalReceipt {
        return CrossDatabaseCanonicalReceipt(
            eventId = eventId,
            eventHash = "evsha256:" + "1".repeat(64),
            sequence = 9,
            provenanceDigest = "sha256:" + "2".repeat(64),
            reusedExistingEvent = true
        )
    }

    private fun identity(): GenesisUltraRuntimeIdentity {
        val doctrine = document("doctrine/test.md", "doctrine", "doctrine")
        val charter = document("policy/charter.json", "freedom_charter", "{}")
        val recovery = document("policy/recovery.json", "recovery_policy", "{}")
        return GenesisUltraRuntimeIdentity(
            instanceId = "instance_test",
            companionName = "Morimil",
            bornAt = "2026-07-29T00:00:00Z",
            identityDigest = digest("identity"),
            activeBody = GenesisUltraRuntimeActiveBody(
                bodyId = "body_test",
                status = "active_writer",
                platformProfile = "android",
                publicKeyFingerprint = digest("body_key"),
                keyEpochId = "epoch_test",
                keyEpochDigest = digest("epoch"),
                registryEpoch = 1,
                registryDigest = digest("registry")
            ),
            guardian = GenesisUltraRuntimeGuardian(
                guardianId = "guardian_test",
                keyEpochId = "guardian_epoch",
                publicKeyRef = digest("guardian_key"),
                status = "active",
                role = "custodian_without_ownership",
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
                authorizedAt = "2026-07-29T00:00:00Z",
                expiresAt = "2026-07-29T01:00:00Z",
                receiptDigest = digest("receipt"),
                birthStatus = "born",
                ownershipConferred = false
            )
        )
    }

    private fun document(
        path: String,
        kind: String,
        text: String
    ): GenesisUltraRuntimeDocument {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        return GenesisUltraRuntimeDocument(
            relativePath = path,
            documentKind = kind,
            digest = GenesisUltraHashProfile.sha256(bytes),
            sourceBytes = bytes
        )
    }

    private fun digest(value: String): String {
        return GenesisUltraHashProfile.sha256(value.toByteArray(StandardCharsets.UTF_8))
    }

    private class IncrementingClock : () -> Long {
        private var value = 1000L
        override fun invoke(): Long = value++
    }

    private companion object {
        const val MIGRATION_ID =
            "cog_migration_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
