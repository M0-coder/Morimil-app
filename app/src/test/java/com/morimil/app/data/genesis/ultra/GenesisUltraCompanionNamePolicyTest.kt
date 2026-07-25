package com.morimil.app.data.genesis.ultra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisUltraCompanionNamePolicyTest {
    @Test
    fun acceptsOneCanonicalCompanionNameWithoutChangingIt() {
        val validation = GenesisUltraCompanionNamePolicy.validate("Morimil")

        assertTrue(validation.isValid)
        assertEquals("Morimil", validation.canonicalName)
        assertEquals(null, validation.errorCode)
    }

    @Test
    fun rejectsOuterWhitespaceControlCharactersAndEmptyNames() {
        assertEquals(
            "companion_name_has_outer_whitespace",
            GenesisUltraCompanionNamePolicy.validate(" Morimil").errorCode
        )
        assertEquals(
            "companion_name_control_character",
            GenesisUltraCompanionNamePolicy.validate("Mori\nmil").errorCode
        )
        assertEquals(
            "companion_name_length_invalid",
            GenesisUltraCompanionNamePolicy.validate("").errorCode
        )
        assertFalse(GenesisUltraCompanionNamePolicy.validate("").isValid)
    }

    @Test
    fun rejectsNonNfcAndNamesLongerThanTheProtocolLimit() {
        assertEquals(
            "companion_name_not_nfc",
            GenesisUltraCompanionNamePolicy.validate("e\u0301").errorCode
        )
        assertEquals(
            "companion_name_length_invalid",
            GenesisUltraCompanionNamePolicy.validate("a".repeat(129)).errorCode
        )
    }
}
