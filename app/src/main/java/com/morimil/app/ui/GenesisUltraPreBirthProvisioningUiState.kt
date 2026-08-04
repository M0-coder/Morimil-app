package com.morimil.app.ui

import com.morimil.app.data.genesis.ultra.GenesisUltraBodyProvisioningReceipt
import com.morimil.app.data.genesis.ultra.GenesisUltraGuardianProvisioningReceipt
import com.morimil.app.data.genesis.ultra.GenesisUltraPreBirthProvisioningSnapshot

internal data class GenesisUltraPreBirthProvisioningUiState(
    val bodyProvisioning: Boolean = false,
    val guardianKeyImporting: Boolean = false,
    val guardianPinning: Boolean = false,
    val guardianPublicKeyRef: String? = null,
    val bodyReceipt: GenesisUltraBodyProvisioningReceipt? = null,
    val guardianReceipt: GenesisUltraGuardianProvisioningReceipt? = null,
    val errorMessage: String? = null
) {
    val busy: Boolean
        get() = bodyProvisioning || guardianKeyImporting || guardianPinning

    init {
        require(listOf(bodyProvisioning, guardianKeyImporting, guardianPinning).count { it } <= 1) {
            "pre_birth_provisioning_multiple_actions_active"
        }
        require(guardianPublicKeyRef == null || SHA256_REF.matches(guardianPublicKeyRef)) {
            "pre_birth_provisioning_guardian_fingerprint_invalid"
        }
    }

    internal companion object {
        private val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")

        fun from(
            snapshot: GenesisUltraPreBirthProvisioningSnapshot,
            guardianPublicKeyRef: String? = null
        ): GenesisUltraPreBirthProvisioningUiState {
            return GenesisUltraPreBirthProvisioningUiState(
                guardianPublicKeyRef = guardianPublicKeyRef,
                bodyReceipt = snapshot.bodyReceipt,
                guardianReceipt = snapshot.guardianReceipt
            )
        }
    }
}
