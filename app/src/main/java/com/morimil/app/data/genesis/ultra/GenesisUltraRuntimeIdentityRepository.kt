package com.morimil.app.data.genesis.ultra

import com.morimil.app.data.local.MorimilDatabase
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Exact verified birth document exposed through defensive copies only. */
internal class GenesisUltraRuntimeDocument(
    val relativePath: String,
    val documentKind: String,
    val digest: String,
    sourceBytes: ByteArray
) {
    private val source = sourceBytes.copyOf()

    init {
        GenesisUltraHashProfile.requireSafeRelativePath(relativePath)
        GenesisUltraHashProfile.requireNfc(documentKind)
        require(source.isNotEmpty()) { "runtime_identity_document_empty:$documentKind" }
        require(GenesisUltraHashProfile.sha256(source) == digest) {
            "runtime_identity_document_digest_mismatch:$documentKind"
        }
    }

    fun copySourceBytes(): ByteArray = source.copyOf()

    fun readUtf8Strict(): String {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(source.copyOf())).toString()
    }
}

internal data class GenesisUltraRuntimeVerifiedSeed(
    val seedId: String,
    val rootHash: String,
    val protocolVersion: String,
    val hashProfile: String,
    val identityDigest: String,
    val doctrineDigest: String
)

internal data class GenesisUltraRuntimeActiveBody(
    val bodyId: String,
    val status: String,
    val platformProfile: String,
    val publicKeyFingerprint: String,
    val keyEpochId: String,
    val keyEpochDigest: String,
    val registryEpoch: Long,
    val registryDigest: String
)

internal data class GenesisUltraRuntimeGuardian(
    val guardianId: String,
    val keyEpochId: String,
    val publicKeyRef: String,
    val status: String,
    val role: String,
    val anchorDigest: String
)

internal data class GenesisUltraRuntimePolicy(
    val freedomCharter: GenesisUltraRuntimeDocument,
    val recoveryPolicy: GenesisUltraRuntimeDocument,
    val freedomCharterDigest: String,
    val recoveryPolicyDigest: String
)

internal enum class GenesisUltraRuntimeAuthorizationState {
    COMMITTED
}

internal data class GenesisUltraRuntimeAuthorization(
    val state: GenesisUltraRuntimeAuthorizationState,
    val authorizationDigest: String,
    val candidateDigest: String,
    val consentDigest: String,
    val authorizedAt: String,
    val expiresAt: String,
    val receiptDigest: String,
    val birthStatus: String,
    val ownershipConferred: Boolean
)

/** One runtime identity projected only from a recovered, signature-verified Ultra birth. */
internal data class GenesisUltraRuntimeIdentity(
    val instanceId: String,
    val companionName: String,
    val bornAt: String,
    val identityDigest: String,
    val activeBody: GenesisUltraRuntimeActiveBody,
    val guardian: GenesisUltraRuntimeGuardian,
    val seed: GenesisUltraRuntimeVerifiedSeed,
    val doctrine: GenesisUltraRuntimeDocument,
    val policy: GenesisUltraRuntimePolicy,
    val authorization: GenesisUltraRuntimeAuthorization
)

/**
 * Canonical identity read boundary for the living runtime.
 *
 * No identity is projected from legacy tables, bundled assets or unchecked commit rows.
 * A committed birth is reconstructed from durable evidence and every protocol signature is
 * verified again against the locally anchored Body and Guardian public keys.
 */
internal class GenesisUltraRuntimeIdentityRepository private constructor(
    private val readAuthorizedBirthState: suspend () -> GenesisUltraPersistedBirthState,
    private val loadBodyIdentityRoot: suspend () -> GenesisUltraBodyIdentityRoot,
    private val loadGuardianTrustAnchor: suspend () -> GenesisUltraGuardianTrustAnchor,
    private val recoverVerifiedBirth: suspend (
        GenesisUltraAtomicBirthRecoveryRequest
    ) -> GenesisUltraRecoveredAtomicBirth?,
    private val loadCommittedAuthorization: suspend () -> GenesisUltraDurableBirthAuthorization
) {
    suspend fun readState(): GenesisUltraPersistedBirthState = readAuthorizedBirthState()

    suspend fun readCommittedIdentity(): GenesisUltraRuntimeIdentity? {
        return when (readAuthorizedBirthState()) {
            GenesisUltraPersistedBirthState.ABSENT -> null
            GenesisUltraPersistedBirthState.INCONSISTENT -> {
                throw IllegalStateException("genesis_ultra_runtime_identity_inconsistent")
            }
            GenesisUltraPersistedBirthState.COMMITTED -> {
                val bodyRoot = loadBodyIdentityRoot()
                val guardianAnchor = loadGuardianTrustAnchor()
                val recovered = requireNotNull(
                    recoverVerifiedBirth(
                        GenesisUltraAtomicBirthRecoveryRequest(
                            guardianKeyEpochRegistry = GenesisUltraTrustedGuardianKeyEpochRegistry(
                                listOf(guardianAnchor.toTrustedEpoch())
                            ),
                            bodyRawPublicKey = bodyRoot.copyRawPublicKey()
                        )
                    )
                ) { "genesis_ultra_runtime_identity_committed_birth_missing" }
                val authorization = loadCommittedAuthorization()
                GenesisUltraRuntimeIdentityProjector.project(
                    recoveredBirth = recovered,
                    authorization = authorization,
                    bodyRoot = bodyRoot,
                    guardianAnchor = guardianAnchor
                )
            }
        }
    }

    internal companion object {
        fun production(
            database: MorimilDatabase,
            bodyIdentityRootStore: GenesisUltraAndroidBodyIdentityRootStore,
            guardianTrustAnchorStore: GenesisUltraAndroidGuardianTrustAnchorStore
        ): GenesisUltraRuntimeIdentityRepository {
            val store = GenesisUltraAtomicBirthStore(database)
            val authorizedAudit = GenesisUltraAuthorizedBirthStateAudit(database)
            return GenesisUltraRuntimeIdentityRepository(
                readAuthorizedBirthState = authorizedAudit::readState,
                loadBodyIdentityRoot = bodyIdentityRootStore::loadExisting,
                loadGuardianTrustAnchor = guardianTrustAnchorStore::loadExisting,
                recoverVerifiedBirth = store::recoverVerifiedBirth,
                loadCommittedAuthorization = authorizedAudit::loadCommittedAuthorization
            )
        }

        fun forTest(
            readAuthorizedBirthState: suspend () -> GenesisUltraPersistedBirthState,
            loadBodyIdentityRoot: suspend () -> GenesisUltraBodyIdentityRoot,
            loadGuardianTrustAnchor: suspend () -> GenesisUltraGuardianTrustAnchor,
            recoverVerifiedBirth: suspend (
                GenesisUltraAtomicBirthRecoveryRequest
            ) -> GenesisUltraRecoveredAtomicBirth?,
            loadCommittedAuthorization: suspend () -> GenesisUltraDurableBirthAuthorization
        ): GenesisUltraRuntimeIdentityRepository {
            return GenesisUltraRuntimeIdentityRepository(
                readAuthorizedBirthState = readAuthorizedBirthState,
                loadBodyIdentityRoot = loadBodyIdentityRoot,
                loadGuardianTrustAnchor = loadGuardianTrustAnchor,
                recoverVerifiedBirth = recoverVerifiedBirth,
                loadCommittedAuthorization = loadCommittedAuthorization
            )
        }
    }
}

internal object GenesisUltraRuntimeIdentityProjector {
    fun project(
        recoveredBirth: GenesisUltraRecoveredAtomicBirth,
        authorization: GenesisUltraDurableBirthAuthorization,
        bodyRoot: GenesisUltraBodyIdentityRoot,
        guardianAnchor: GenesisUltraGuardianTrustAnchor
    ): GenesisUltraRuntimeIdentity {
        val bundle = recoveredBirth.verifiedBirth().copyPersistenceBundle()
        val manifest = bundle.seedManifest
        val identity = bundle.instanceIdentity
        val state = bundle.birthState
        val receipt = bundle.birthReceipt
        val artifacts = bundle.artifacts

        val bodyRecord = GenesisUltraContractParser.parseBodyRecord(
            artifacts.exactText("initial_body_record")
        )
        val bodyRegistry = GenesisUltraContractParser.parseBodyRegistry(
            artifacts.exactText("initial_body_registry")
        )
        val keyEpoch = GenesisUltraContractParser.parseKeyEpoch(
            artifacts.exactText("initial_body_key_epoch")
        )
        val freedomCharter = GenesisUltraAtomicBirthDocumentParser.parseFreedomCharter(
            artifacts.exactText("freedom_charter")
        )
        val recoveryPolicy = GenesisUltraAtomicBirthDocumentParser.parseRecoveryPolicy(
            artifacts.exactText("recovery_policy")
        )
        val activeRegisteredBody = bodyRegistry.bodies.singleOrNull { body ->
            body.status == "active_writer"
        } ?: throw IllegalArgumentException("runtime_identity_active_body_missing_or_ambiguous")

        require(
            identity.instanceId == state.instanceId &&
                identity.instanceId == receipt.instanceId &&
                identity.seedId == manifest.seedId &&
                identity.seedRootHash == manifest.rootHash &&
                identity.identityDigest == state.identityDigest &&
                identity.identityDigest == receipt.identityDigest
        ) { "runtime_identity_birth_graph_mismatch" }
        require(
            state.initialBodyId == receipt.activeWriterBodyId &&
                state.initialBodyId == bodyRecord.bodyId &&
                state.initialBodyId == activeRegisteredBody.bodyId &&
                state.initialBodyId == bodyRoot.bodyId &&
                bodyRecord.instanceId == identity.instanceId &&
                bodyRegistry.instanceId == identity.instanceId &&
                keyEpoch.instanceId == identity.instanceId &&
                keyEpoch.bodyId == state.initialBodyId
        ) { "runtime_identity_active_body_link_mismatch" }
        require(
            bodyRoot.keyEpochId == keyEpoch.keyEpochId &&
                bodyRoot.publicKeyRef == keyEpoch.publicKeyFingerprint &&
                bodyRoot.publicKeyRef == bodyRecord.publicKeyFingerprint &&
                bodyRoot.publicKeyRef == activeRegisteredBody.publicKeyFingerprint
        ) { "runtime_identity_active_body_key_mismatch" }
        require(
            identity.guardianId == guardianAnchor.guardianId &&
                identity.guardianId == authorization.guardianId &&
                identity.guardianId == freedomCharter.guardianId &&
                identity.guardianId == recoveryPolicy.guardianId &&
                identity.guardianId == receipt.guardianWitness.signerId &&
                guardianAnchor.keyEpochId == authorization.guardianKeyEpochId &&
                guardianAnchor.keyEpochId == freedomCharter.guardianKeyEpochId &&
                guardianAnchor.keyEpochId == receipt.guardianWitness.keyEpochId
        ) { "runtime_identity_guardian_link_mismatch" }
        require(
            authorization.birthStateDigest == state.stateDigest &&
                authorization.receiptDigest == receipt.receiptDigest &&
                authorization.bodyId == state.initialBodyId
        ) { "runtime_identity_authorization_link_mismatch" }
        require(
            receipt.birthStatus == "born" &&
                receipt.activeWriterCount == 1L &&
                !receipt.ownershipConferred
        ) { "runtime_identity_birth_authority_invalid" }

        val doctrineRecord = manifest.files.singleOrNull { record ->
            record.kind == "doctrine" && record.required
        } ?: throw IllegalArgumentException("runtime_identity_required_doctrine_missing")
        val doctrineArtifact = artifacts.singleOrNull { artifact ->
            artifact.relativePath == doctrineRecord.path
        } ?: throw IllegalArgumentException("runtime_identity_doctrine_artifact_missing")
        require(
            doctrineRecord.digest == manifest.doctrineDigest &&
                GenesisUltraHashProfile.sha256(doctrineArtifact.payload) == doctrineRecord.digest
        ) { "runtime_identity_doctrine_digest_mismatch" }

        val charterArtifact = artifacts.exactArtifact("freedom_charter")
        val recoveryPolicyArtifact = artifacts.exactArtifact("recovery_policy")
        require(
            freedomCharter.charterDigest == state.freedomCharterDigest &&
                freedomCharter.charterDigest == receipt.freedomCharterDigest &&
                GenesisUltraHashProfile.sha256(charterArtifact.payload) == charterArtifact.contentDigest()
        ) { "runtime_identity_freedom_charter_mismatch" }
        require(
            recoveryPolicy.instanceId == identity.instanceId &&
                recoveryPolicy.policyDigest == GenesisUltraAtomicBirthHashProfile.recoveryPolicyDigest(
                    recoveryPolicy
                )
        ) { "runtime_identity_recovery_policy_mismatch" }

        return GenesisUltraRuntimeIdentity(
            instanceId = identity.instanceId,
            companionName = identity.companionName,
            bornAt = identity.bornAt,
            identityDigest = identity.identityDigest,
            activeBody = GenesisUltraRuntimeActiveBody(
                bodyId = activeRegisteredBody.bodyId,
                status = activeRegisteredBody.status,
                platformProfile = activeRegisteredBody.platformProfile,
                publicKeyFingerprint = activeRegisteredBody.publicKeyFingerprint,
                keyEpochId = keyEpoch.keyEpochId,
                keyEpochDigest = keyEpoch.epochDigest,
                registryEpoch = bodyRegistry.registryEpoch,
                registryDigest = bodyRegistry.registryDigest
            ),
            guardian = GenesisUltraRuntimeGuardian(
                guardianId = guardianAnchor.guardianId,
                keyEpochId = guardianAnchor.keyEpochId,
                publicKeyRef = guardianAnchor.publicKeyRef,
                status = guardianAnchor.status,
                role = receipt.guardianRole,
                anchorDigest = guardianAnchor.anchorDigest
            ),
            seed = GenesisUltraRuntimeVerifiedSeed(
                seedId = manifest.seedId,
                rootHash = manifest.rootHash,
                protocolVersion = manifest.protocolVersion,
                hashProfile = manifest.hashProfile,
                identityDigest = manifest.identityDigest,
                doctrineDigest = manifest.doctrineDigest
            ),
            doctrine = GenesisUltraRuntimeDocument(
                relativePath = doctrineArtifact.relativePath,
                documentKind = "doctrine",
                digest = doctrineRecord.digest,
                sourceBytes = doctrineArtifact.payload
            ),
            policy = GenesisUltraRuntimePolicy(
                freedomCharter = GenesisUltraRuntimeDocument(
                    relativePath = charterArtifact.relativePath,
                    documentKind = "freedom_charter",
                    digest = charterArtifact.contentDigest(),
                    sourceBytes = charterArtifact.payload
                ),
                recoveryPolicy = GenesisUltraRuntimeDocument(
                    relativePath = recoveryPolicyArtifact.relativePath,
                    documentKind = "recovery_policy",
                    digest = recoveryPolicyArtifact.contentDigest(),
                    sourceBytes = recoveryPolicyArtifact.payload
                ),
                freedomCharterDigest = freedomCharter.charterDigest,
                recoveryPolicyDigest = recoveryPolicy.policyDigest
            ),
            authorization = GenesisUltraRuntimeAuthorization(
                state = GenesisUltraRuntimeAuthorizationState.COMMITTED,
                authorizationDigest = authorization.authorizationDigest,
                candidateDigest = authorization.candidateDigest,
                consentDigest = authorization.consentDigest,
                authorizedAt = authorization.authorizedAt,
                expiresAt = authorization.expiresAt,
                receiptDigest = authorization.receiptDigest,
                birthStatus = receipt.birthStatus,
                ownershipConferred = receipt.ownershipConferred
            )
        )
    }

    private fun List<GenesisUltraBirthArtifact>.exactArtifact(
        kind: String
    ): GenesisUltraBirthArtifact {
        return singleOrNull { artifact -> artifact.artifactKind == kind }
            ?: throw IllegalArgumentException("runtime_identity_artifact_invalid:$kind")
    }

    private fun List<GenesisUltraBirthArtifact>.exactText(kind: String): String {
        return exactArtifact(kind).decodeUtf8Strict()
    }

    private fun GenesisUltraBirthArtifact.decodeUtf8Strict(): String {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(payload.copyOf())).toString()
    }

    private fun GenesisUltraBirthArtifact.contentDigest(): String {
        return GenesisUltraHashProfile.sha256(payload)
    }
}
