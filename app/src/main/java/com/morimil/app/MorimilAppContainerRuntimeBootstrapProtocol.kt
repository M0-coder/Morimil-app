package com.morimil.app

import com.morimil.app.data.genesis.ultra.CanonicalRuntimeBootstrapCommitPort
import com.morimil.app.data.repository.CrossDatabaseOperationCoordinator
import com.morimil.app.data.repository.RuntimeBootstrapProtocolFinalizer
import com.morimil.app.data.repository.RuntimeBootstrapProtocolTypes

internal val MorimilAppContainer.canonicalRuntimeBootstrapCommitPort:
    CanonicalRuntimeBootstrapCommitPort
    get() = CanonicalRuntimeBootstrapCommitPort(canonicalMemoryRepository)

internal val MorimilAppContainer.runtimeBootstrapProtocolCoordinator:
    CrossDatabaseOperationCoordinator
    get() = CrossDatabaseOperationCoordinator.production(
        database = organDatabase,
        canonicalEnsurePort = canonicalRuntimeBootstrapCommitPort,
        finalizers = listOf(
            RuntimeBootstrapProtocolFinalizer(
                memoryDatabase = memoryDatabase,
                organDatabase = organDatabase
            )
        ),
        protocolRegistry = RuntimeBootstrapProtocolTypes.REGISTRY
    )
