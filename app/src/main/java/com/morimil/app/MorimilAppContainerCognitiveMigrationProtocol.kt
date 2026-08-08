package com.morimil.app

import com.morimil.app.data.genesis.ultra.CanonicalCognitiveMigrationCommitPort
import com.morimil.app.data.genesis.ultra.CanonicalCognitiveMigrationReadPort
import com.morimil.app.data.genesis.ultra.CanonicalOrchestrationCommitPort
import com.morimil.app.data.repository.CognitiveMigrationProtocolFinalizer
import com.morimil.app.data.repository.CrossDatabaseOperationCoordinator
import com.morimil.app.data.repository.OrchestrationProtocolFinalizer
import com.morimil.app.data.repository.OrchestrationProtocolTypes

internal val MorimilAppContainer.canonicalCognitiveMigrationReadPort:
    CanonicalCognitiveMigrationReadPort
    get() = CanonicalCognitiveMigrationReadPort.production(
        consumerReadPort = canonicalConsumerReadPort
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

internal val MorimilAppContainer.canonicalOrchestrationCommitPort:
    CanonicalOrchestrationCommitPort
    get() = CanonicalOrchestrationCommitPort(canonicalMemoryRepository)

internal val MorimilAppContainer.orchestrationProtocolCoordinator:
    CrossDatabaseOperationCoordinator
    get() = CrossDatabaseOperationCoordinator.production(
        database = organDatabase,
        canonicalEnsurePort = canonicalOrchestrationCommitPort,
        finalizers = listOf(
            OrchestrationProtocolFinalizer(database = organDatabase)
        ),
        protocolRegistry = OrchestrationProtocolTypes.REGISTRY
    )
