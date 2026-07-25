package com.morimil.app.data.genesis.ultra

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
    val authorization = testOnlyAtomicBirthAuthorization(
        verifiedBirth = verifiedBirth,
        birthStateDigest = persistence.birthState.stateDigest,
        receiptDigest = persistence.birthReceipt.receiptDigest,
        authorizedAt = activatedAt,
        expiresAt = Instant.parse(activatedAt).plusSeconds(300).toString()
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
    birthStateDigest: String,
    receiptDigest: String,
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
        digest("candidate"),
        digest("consent"),
        birthStateDigest,
        receiptDigest,
        digest("authorization"),
        authorizedAt,
        expiresAt
    )
}

private fun digest(value: String): String =
    GenesisUltraHashProfile.sha256(value.toByteArray(Charsets.UTF_8))
