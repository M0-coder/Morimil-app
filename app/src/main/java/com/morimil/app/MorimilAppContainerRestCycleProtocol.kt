package com.morimil.app

import com.morimil.app.data.genesis.ultra.CanonicalRestCycleCommitPort
import com.morimil.app.data.repository.CrossDatabaseOperationCoordinator
import com.morimil.app.data.repository.RestCycleProtocolFinalizer
import com.morimil.app.data.repository.RestCycleProtocolTypes

internal val MorimilAppContainer.canonicalRestCycleCommitPort: CanonicalRestCycleCommitPort
    get() = CanonicalRestCycleCommitPort(canonicalMemoryRepository)

internal val MorimilAppContainer.restCycleProtocolCoordinator: CrossDatabaseOperationCoordinator
    get() = CrossDatabaseOperationCoordinator.production(
        database = organDatabase,
        canonicalEnsurePort = canonicalRestCycleCommitPort,
        finalizers = listOf(RestCycleProtocolFinalizer(database = organDatabase)),
        protocolRegistry = RestCycleProtocolTypes.REGISTRY
    )
