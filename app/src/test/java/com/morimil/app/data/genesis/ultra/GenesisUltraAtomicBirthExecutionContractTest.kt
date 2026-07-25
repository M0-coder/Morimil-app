package com.morimil.app.data.genesis.ultra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraAtomicBirthExecutionContractTest {
    @Test
    fun executionEntryPointAcceptsNoCallerInjectedSignerOrTrustRegistry() {
        val methods = GenesisUltraAtomicBirthExecutionCoordinator::class.java.declaredMethods
            .filter { method -> method.name == "execute" }

        assertEquals(1, methods.size)
        val parameterTypes = methods.single().parameterTypes.toList()
        assertTrue(parameterTypes.contains(GenesisUltraAuthorizedAtomicBirth::class.java))
        assertTrue(parameterTypes.contains(GenesisUltraCanonicalMemoryAppendRequest::class.java))
        assertFalse(parameterTypes.contains(GenesisUltraBodyMemorySigner::class.java))
        assertFalse(parameterTypes.contains(GenesisUltraAtomicBirthRecoveryRequest::class.java))
        assertFalse(parameterTypes.contains(GenesisUltraTrustedGuardianKeyEpochRegistry::class.java))
        assertFalse(parameterTypes.contains(GenesisUltraBodyMemoryKey::class.java))
    }
}
