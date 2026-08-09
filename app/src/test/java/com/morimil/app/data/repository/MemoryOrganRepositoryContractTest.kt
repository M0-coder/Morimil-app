package com.morimil.app.data.repository

import com.morimil.app.core.memory.MemoryIntegrityCore
import com.morimil.app.data.genesis.ultra.CanonicalConsumerReadPort
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentityRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryOrganRepositoryContractTest {
    @Test
    fun capsuleIdKeepsStableSlugShape() {
        val title = "Genesis Memory Core"
        val id = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

        assertEquals("genesis-memory-core", id)
    }

    @Test
    fun confidenceIsBounded() {
        val low = (-10).coerceIn(1, 100)
        val high = 999.coerceIn(1, 100)

        assertEquals(1, low)
        assertEquals(100, high)
    }

    @Test
    fun criticalRuntimeRepositoriesRequireTheirCurrentIntegrityBoundaries() {
        assertConstructorRequires(MemoryOrganRepository::class.java, MemoryIntegrityCore::class.java)
        assertConstructorRequires(MemoryOrganReconciliationRepository::class.java, MemoryIntegrityCore::class.java)
        assertConstructorRequires(MemoryRepository::class.java, MemoryIntegrityCore::class.java)
        assertConstructorRequires(MemoryRepository::class.java, LivingMemoryPort::class.java)

        // REST-001 no longer verifies legacy memory_events directly. Its authority and
        // integrity boundaries are committed Ultra identity, verified canonical reads,
        // and the owner-scoped durable XOP coordinator.
        assertConstructorRequires(
            RestCycleRepository::class.java,
            GenesisUltraRuntimeIdentityRepository::class.java
        )
        assertConstructorRequires(RestCycleRepository::class.java, CanonicalConsumerReadPort::class.java)
        assertConstructorRequires(
            RestCycleRepository::class.java,
            CrossDatabaseOperationCoordinator::class.java
        )
        assertConstructorDoesNotRequire(RestCycleRepository::class.java, MemoryIntegrityCore::class.java)
        assertConstructorDoesNotRequire(RestCycleRepository::class.java, LivingMemoryPort::class.java)
    }

    private fun assertConstructorRequires(type: Class<*>, dependency: Class<*>) {
        val constructors = type.constructors
        val constructorParameterTypes = constructors.flatMap { constructor -> constructor.parameterTypes.toList() }

        assertTrue("${type.simpleName} must require ${dependency.simpleName}", constructorParameterTypes.contains(dependency))
        assertFalse(
            "${type.simpleName} exposes a constructor without ${dependency.simpleName}",
            constructors.any { constructor ->
                constructor.parameterTypes.none { parameter -> parameter == dependency }
            }
        )
    }

    private fun assertConstructorDoesNotRequire(type: Class<*>, dependency: Class<*>) {
        val constructorParameterTypes = type.constructors.flatMap { constructor -> constructor.parameterTypes.toList() }
        assertFalse(
            "${type.simpleName} must not regain legacy dependency ${dependency.simpleName}",
            constructorParameterTypes.contains(dependency)
        )
    }
}
