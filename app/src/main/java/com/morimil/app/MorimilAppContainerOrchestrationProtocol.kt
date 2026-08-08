package com.morimil.app

import com.morimil.app.data.genesis.ultra.CanonicalOrchestrationCommitPort
import com.morimil.app.data.repository.CrossDatabaseOperationCoordinator
import com.morimil.app.data.repository.OrchestrationProtocolFinalizer
import com.morimil.app.data.repository.OrchestrationProtocolTypes

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
