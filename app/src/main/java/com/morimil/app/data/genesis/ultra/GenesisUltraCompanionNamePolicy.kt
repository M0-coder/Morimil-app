package com.morimil.app.data.genesis.ultra

import java.text.Normalizer

internal data class GenesisUltraCompanionNameValidation(
    val canonicalName: String?,
    val errorCode: String?
) {
    val isValid: Boolean
        get() = canonicalName != null && errorCode == null

    init {
        require((canonicalName == null) != (errorCode == null)) {
            "companion_name_validation_state_invalid"
        }
    }
}

/** Shared UI-facing policy matching the candidate-construction contract. */
internal object GenesisUltraCompanionNamePolicy {
    const val MAX_LENGTH = 128

    fun validate(value: String): GenesisUltraCompanionNameValidation {
        if (!Normalizer.isNormalized(value, Normalizer.Form.NFC)) {
            return invalid("companion_name_not_nfc")
        }
        if (value != value.trim()) {
            return invalid("companion_name_has_outer_whitespace")
        }
        if (value.length !in 1..MAX_LENGTH) {
            return invalid("companion_name_length_invalid")
        }
        if (value.any(Char::isISOControl)) {
            return invalid("companion_name_control_character")
        }
        return GenesisUltraCompanionNameValidation(
            canonicalName = value,
            errorCode = null
        )
    }

    fun requireCanonical(value: String): String {
        val validation = validate(value)
        return requireNotNull(validation.canonicalName) {
            validation.errorCode ?: "companion_name_invalid"
        }
    }

    private fun invalid(errorCode: String): GenesisUltraCompanionNameValidation {
        return GenesisUltraCompanionNameValidation(
            canonicalName = null,
            errorCode = errorCode
        )
    }
}
