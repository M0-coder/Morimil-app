package com.morimil.app.data.genesis.ultra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GenesisUltraInstanceIdProfileTest {
    @Test
    fun instanceIdMatchesBodyIndependentGoldenVector() {
        val instanceId = GenesisUltraInstanceIdProfile.derive(
            releaseRoot = "sha256:" + "1".repeat(64),
            companionName = "Morimil",
            bornAt = "2026-08-14T20:30:00Z",
            entropyRef = "sha256:" + "2".repeat(64)
        )

        assertEquals(
            "inst_d1c20f93ea1b5304e4bee85e8c9a1cd009fad01aeb79af5b2233f5e595451e0a",
            instanceId
        )
    }

    @Test
    fun instanceIdChangesOnlyWhenInstanceGenesisMaterialChanges() {
        val base = GenesisUltraInstanceIdProfile.derive(
            releaseRoot = "sha256:" + "1".repeat(64),
            companionName = "Morimil",
            bornAt = "2026-08-14T20:30:00Z",
            entropyRef = "sha256:" + "2".repeat(64)
        )
        val differentEntropy = GenesisUltraInstanceIdProfile.derive(
            releaseRoot = "sha256:" + "1".repeat(64),
            companionName = "Morimil",
            bornAt = "2026-08-14T20:30:00Z",
            entropyRef = "sha256:" + "3".repeat(64)
        )

        assertNotEquals(base, differentEntropy)
    }
}
