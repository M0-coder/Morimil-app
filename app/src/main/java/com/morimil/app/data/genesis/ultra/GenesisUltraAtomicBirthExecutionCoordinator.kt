package com.morimil.app.data.genesis.ultra

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * Production composition boundary for the atomic Genesis Ultra commit.
 *
 * The caller supplies only the already-authorized type-state, the canonical
 * activation time and the first post-birth memory content. Body signing and
 * Guardian recovery trust are always loaded from authenticated local stores.
 */
internal class GenesisUltraAtomicBirthExecutionCoordinator(
    private val activationCoordinator: GenesisUltraAtomicBirthActivationCoordinator,
    private val bodyIdentityRootStore: GenesisUltraAndroidBodyIdentityRootStore,
    private val guardianTrustAnchorStore: GenesisUltraAndroidGuardianTrustAnchorStore
) {
    suspend fun execute(
        authorization: GenesisUltraAuthorizedAtomicBirth,
        activatedAt: String,
        firstPostBirthRequest: GenesisUltraCanonicalMemoryAppendRequest
    ): GenesisUltraAtomicBirthActivationResult {
        authorization.requireUsableAt(activatedAt)
        require(firstPostBirthRequest.observedAt == activatedAt) {
            "birth_execution_memory_time_mismatch"
        }
        val activationInstant = canonicalTimestamp(activatedAt)
        val bundle = authorization.copyVerifiedBirth().copyPersistenceBundle()
        val identity = bundle.instanceIdentity
        val state = bundle.birthState

        val bodyRoot = bodyIdentityRootStore.loadExisting()
        val signer = bodyIdentityRootStore.signerForInstance(identity.instanceId)
        require(
            signer.key.bodyId == bodyRoot.bodyId &&
                signer.key.keyEpochId == bodyRoot.keyEpochId &&
                signer.key.publicKeyRef == bodyRoot.publicKeyRef &&
                signer.key.copyRawPublicKey().contentEquals(bodyRoot.copyRawPublicKey()) &&
                state.initialBodyId == bodyRoot.bodyId &&
                state.activeWriterCount == 1L &&
                bundle.birthReceipt.activeWriterBodyId == bodyRoot.bodyId
        ) { "birth_execution_local_body_mismatch" }

        val guardianRegistry = guardianTrustAnchorStore.loadExistingRegistry()
        val seedSignature = GenesisUltraContractParser.parseSignatureEnvelope(
            decodeUtf8Strict(
                bundle.artifacts.singleOrNull { artifact ->
                    artifact.artifactKind == "seed_signature"
                }?.payload ?: throw IllegalArgumentException("birth_execution_seed_signature_missing")
            )
        )
        require(guardianRegistry.trusts(seedSignature)) {
            "birth_execution_local_guardian_mismatch"
        }
        require(
            seedSignature.signerId == identity.guardianId &&
                seedSignature.signedDigest == bundle.seedManifest.rootHash
        ) { "birth_execution_guardian_identity_mismatch" }

        return activationCoordinator.activate(
            authorization = authorization,
            activatedAt = activatedAt,
            persistedAtMillis = activationInstant.toEpochMilli(),
            recoveryRequest = GenesisUltraAtomicBirthRecoveryRequest(
                guardianKeyEpochRegistry = guardianRegistry,
                bodyRawPublicKey = bodyRoot.copyRawPublicKey()
            ),
            signer = signer,
            firstPostBirthRequest = firstPostBirthRequest
        )
    }

    private fun canonicalTimestamp(value: String): Instant {
        val parsed = runCatching { Instant.parse(value) }
            .getOrElse { failure ->
                throw IllegalArgumentException("birth_execution_activation_time_invalid", failure)
            }
        require(parsed.toString() == value) { "birth_execution_activation_time_invalid" }
        return parsed
    }

    private fun decodeUtf8Strict(bytes: ByteArray): String {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }
}
