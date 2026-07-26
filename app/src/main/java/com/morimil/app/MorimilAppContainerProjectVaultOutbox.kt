package com.morimil.app

import com.morimil.app.data.genesis.ultra.CanonicalProjectVaultCommitPort
import com.morimil.app.data.repository.ProjectVaultCommitPort

/** Canonical, idempotent destination for the ProjectVault origin outbox. */
internal val MorimilAppContainer.canonicalProjectVaultCommitPort: ProjectVaultCommitPort
    get() = CanonicalProjectVaultCommitPort(canonicalMemoryRepository)
