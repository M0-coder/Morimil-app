package com.morimil.app

import com.morimil.app.data.genesis.ultra.CanonicalCognitiveMigrationCommitPort
import com.morimil.app.data.genesis.ultra.CanonicalCognitiveMigrationReadPort
import com.morimil.app.data.genesis.ultra.GenesisUltraCanonicalConsumerReadAdapter
import com.morimil.app.data.repository.CognitiveMigrationProtocolFinalizer
import com.morimil.app.data.repository.CrossDatabaseOperationCoordinator

internal val MorimilAppContainer.canonicalCognitiveMigrationReadPort:
    CanonicalCognitiveMigrationReadPort
    get() = CanonicalCognitiveMigrationReadPort.production(
        identityRepository = genesisUltraRuntimeIdentityRepository,
        consumerReadPort = GenesisUltraCanonicalConsumerReadAdapter.production(
            identityRepository = genesisUltraRuntimeIdentityRepository,
            memoryRepository = canonicalMemoryRepository
        )
    )

internal val MorimilAppContainer.canonicalCognitiveMigrationCommitPort:
    CanonicalCognitiveMigrationCommitPort
    get() = CanonicalCognitiveMigrationCommitPort(canonicalMemoryRepository)

internal val MorimilAppContainer.cognitiveMigrationProtocolCoordinator:
    CrossDatabaseOperationCoordinator
    get() {
        val readPort = canonicalCognitiveMigrationReadPort
        return CrossDatabaseOperationCoordinator.production(
            database = organDatabase,
            canonicalEnsurePort = canonicalCognitiveMigrationCommitPort,
            finalizers = listOf(
                CognitiveMigrationProtocolFinalizer(
                    database = organDatabase,
                    canonicalAuditPort = readPort
                )
            )
        )
    }
