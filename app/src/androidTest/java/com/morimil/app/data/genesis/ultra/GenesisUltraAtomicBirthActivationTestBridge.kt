package com.morimil.app.data.genesis.ultra

import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * Test-APK-only compatibility bridge for store transaction tests that isolate
 * persistence mechanics from the production consent/evidence ceremony.
 * Application sources have no equivalent evidence-only activation overload.
 */
internal suspend fun GenesisUltraAtomicBirthActivationCoordinator.activate(
    verifiedBirth: GenesisUltraVerifiedAtomicBirth,
    persistedAtMillis: Long,
    recoveryRequest: GenesisUltraAtomicBirthRecoveryRequest,
    signer: GenesisUltraBodyMemorySigner,
    firstPostBirthRequest: GenesisUltraCanonicalMemoryAppendRequest
): GenesisUltraAtomicBirthActivationResult {
    val activatedAt = firstPostBirthRequest.observedAt
    val persistence = verifiedBirth.copyPersistenceBundle()
    val expiresAt = Instant.parse(activatedAt).plusSeconds(300).toString()
    val signature = GenesisUltraContractParser.parseSignatureEnvelope(
        persistence.artifacts.single { artifact -> artifact.artifactKind == "seed_signature" }
            .payload.toString(StandardCharsets.UTF_8)
    )
    val candidateDigest = digest("candidate")
    val consentDigest = digest("consent")
    val authorizationDigest = GenesisUltraHashProfile.hashFields(
        "genesis.atomic.birth.authorization.v0.1",
        listOf(
            candidateDigest,
            consentDigest,
            persistence.birthState.stateDigest,
            persistence.birthReceipt.receiptDigest,
            persistence.birthState.initialBodyId,
            signature.signerId,
            signature.keyEpochId,
            activatedAt,
            expiresAt
        )
    )
    val authorization = testOnlyAtomicBirthAuthorization(
        verifiedBirth = verifiedBirth,
        candidateDigest = candidateDigest,
        consentDigest = consentDigest,
        birthStateDigest = persistence.birthState.stateDigest,
        receiptDigest = persistence.birthReceipt.receiptDigest,
        authorizationDigest = authorizationDigest,
        authorizedAt = activatedAt,
        expiresAt = expiresAt
    )
    return activate(
        authorization = authorization,
        activatedAt = activatedAt,
        persistedAtMillis = persistedAtMillis,
        recoveryRequest = recoveryRequest,
        signer = signer,
        firstPostBirthRequest = firstPostBirthRequest
    )
}

private fun testOnlyAtomicBirthAuthorization(
    verifiedBirth: GenesisUltraVerifiedAtomicBirth,
    candidateDigest: String,
    consentDigest: String,
    birthStateDigest: String,
    receiptDigest: String,
    authorizationDigest: String,
    authorizedAt: String,
    expiresAt: String
): GenesisUltraAuthorizedAtomicBirth {
    val constructor = GenesisUltraAuthorizedAtomicBirth::class.java.getDeclaredConstructor(
        GenesisUltraVerifiedAtomicBirth::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java
    )
    constructor.isAccessible = true
    return constructor.newInstance(
        verifiedBirth,
        candidateDigest,
        consentDigest,
        birthStateDigest,
        receiptDigest,
        authorizationDigest,
        authorizedAt,
        expiresAt
    )
}

private fun digest(value: String): String =
    GenesisUltraHashProfile.sha256(value.toByteArray(Charsets.UTF_8))
