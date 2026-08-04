package com.morimil.app.data.genesis.ultra

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Explicit local-presence request for creating the first Body identity root. */
internal data class GenesisUltraBodyProvisioningCeremonyRequest(
    val confirmationPurpose: String,
    val userPresenceConfirmed: Boolean
) {
    init {
        require(confirmationPurpose == CONFIRMATION_PURPOSE) {
            "body_provisioning_confirmation_purpose_invalid"
        }
        require(userPresenceConfirmed) { "body_provisioning_user_presence_required" }
    }

    internal companion object {
        const val CONFIRMATION_PURPOSE = "prepare_first_android_body_for_genesis"
    }
}

/**
 * Process-local preview of one exact RAW Ed25519 Guardian public key.
 *
 * The bytes are public but intentionally stay outside UI state. The preview is
 * discarded on process death and cannot pin trust without a second explicit
 * ceremony containing an independently confirmed fingerprint.
 */
internal class GenesisUltraGuardianPublicKeyPreview(
    rawPublicKey: ByteArray
) {
    private val publicKey = rawPublicKey.copyOf()

    val publicKeyRef: String = GenesisUltraHashProfile.sha256(publicKey)

    init {
        require(publicKey.size == RAW_ED25519_PUBLIC_KEY_BYTES) {
            "guardian_public_key_raw_size_invalid"
        }
    }

    internal fun ceremonyRequest(
        guardianId: String,
        keyEpochId: String,
        confirmedPublicKeyRef: String,
        independentConfirmationAcknowledged: Boolean,
        userPresenceConfirmed: Boolean
    ): GenesisUltraGuardianProvisioningCeremonyRequest {
        return GenesisUltraGuardianProvisioningCeremonyRequest(
            guardianId = guardianId,
            keyEpochId = keyEpochId,
            confirmedPublicKeyRef = confirmedPublicKeyRef,
            confirmationPurpose =
                GenesisUltraGuardianTrustAnchorProvisioningRequest.CONFIRMATION_PURPOSE,
            independentConfirmationAcknowledged = independentConfirmationAcknowledged,
            userPresenceConfirmed = userPresenceConfirmed,
            rawPublicKey = publicKey
        )
    }

    internal companion object {
        const val RAW_ED25519_PUBLIC_KEY_BYTES = 32
    }
}

/** Explicit, out-of-band-confirmed request for the irreversible pre-birth pin. */
internal class GenesisUltraGuardianProvisioningCeremonyRequest(
    val guardianId: String,
    val keyEpochId: String,
    val confirmedPublicKeyRef: String,
    val confirmationPurpose: String,
    val independentConfirmationAcknowledged: Boolean,
    val userPresenceConfirmed: Boolean,
    rawPublicKey: ByteArray
) {
    private val publicKey = rawPublicKey.copyOf()

    init {
        require(independentConfirmationAcknowledged) {
            "guardian_provisioning_independent_confirmation_required"
        }
        require(userPresenceConfirmed) { "guardian_provisioning_user_presence_required" }
        anchorRequest()
    }

    internal fun anchorRequest(): GenesisUltraGuardianTrustAnchorProvisioningRequest {
        return GenesisUltraGuardianTrustAnchorProvisioningRequest(
            guardianId = guardianId,
            keyEpochId = keyEpochId,
            confirmedPublicKeyRef = confirmedPublicKeyRef,
            confirmationPurpose = confirmationPurpose,
            rawPublicKey = publicKey
        )
    }
}

/** Reconstructable, non-secret receipt for the provisioned Body root. */
internal data class GenesisUltraBodyProvisioningReceipt(
    val bodyId: String,
    val keyEpochId: String,
    val publicKeyRef: String,
    val protectionProfile: String,
    val receiptDigest: String
) {
    internal companion object {
        private const val RECEIPT_DOMAIN = "genesis.body.provisioning.receipt.v0.1"

        fun from(root: GenesisUltraBodyIdentityRoot): GenesisUltraBodyProvisioningReceipt {
            val fields = listOf(
                root.bodyId,
                root.keyEpochId,
                root.publicKeyRef,
                root.protectionProfile
            )
            return GenesisUltraBodyProvisioningReceipt(
                bodyId = root.bodyId,
                keyEpochId = root.keyEpochId,
                publicKeyRef = root.publicKeyRef,
                protectionProfile = root.protectionProfile,
                receiptDigest = GenesisUltraHashProfile.hashFields(RECEIPT_DOMAIN, fields)
            )
        }
    }
}

/** Reconstructable, non-secret receipt for the pinned Guardian epoch. */
internal data class GenesisUltraGuardianProvisioningReceipt(
    val guardianId: String,
    val keyEpochId: String,
    val publicKeyRef: String,
    val anchorDigest: String,
    val pinnedAtMillis: Long,
    val receiptDigest: String
) {
    internal companion object {
        private const val RECEIPT_DOMAIN = "genesis.guardian.provisioning.receipt.v0.1"

        fun from(
            anchor: GenesisUltraGuardianTrustAnchor
        ): GenesisUltraGuardianProvisioningReceipt {
            val fields = listOf(
                anchor.guardianId,
                anchor.keyEpochId,
                anchor.publicKeyRef,
                anchor.anchorDigest,
                anchor.pinnedAtMillis.toString()
            )
            return GenesisUltraGuardianProvisioningReceipt(
                guardianId = anchor.guardianId,
                keyEpochId = anchor.keyEpochId,
                publicKeyRef = anchor.publicKeyRef,
                anchorDigest = anchor.anchorDigest,
                pinnedAtMillis = anchor.pinnedAtMillis,
                receiptDigest = GenesisUltraHashProfile.hashFields(RECEIPT_DOMAIN, fields)
            )
        }
    }
}

internal data class GenesisUltraPreBirthProvisioningSnapshot(
    val assessment: GenesisUltraBirthPreparationAssessment,
    val bodyReceipt: GenesisUltraBodyProvisioningReceipt?,
    val guardianReceipt: GenesisUltraGuardianProvisioningReceipt?
) {
    init {
        require(
            bodyReceipt == null ||
                assessment.facts.bodyIdentityRootState == GenesisUltraBodyIdentityRootState.READY
        ) { "pre_birth_snapshot_body_receipt_state_mismatch" }
        require(
            guardianReceipt == null ||
                assessment.facts.guardianTrustAnchorState ==
                GenesisUltraGuardianTrustAnchorState.READY
        ) { "pre_birth_snapshot_guardian_receipt_state_mismatch" }
    }
}

/**
 * The only production facade allowed to provision the two local pre-birth
 * roots. It re-inspects durable state immediately before and after every
 * mutation and never constructs, authorizes, or commits a Genesis birth.
 */
internal class GenesisUltraPreBirthProvisioningCoordinator(
    private val inspectPreparation: suspend () -> GenesisUltraBirthPreparationAssessment,
    private val provisionBodyRoot: suspend () -> GenesisUltraBodyIdentityRoot,
    private val loadBodyRoot: suspend () -> GenesisUltraBodyIdentityRoot,
    private val provisionGuardianAnchor:
        suspend (GenesisUltraGuardianTrustAnchorProvisioningRequest) ->
            GenesisUltraGuardianTrustAnchor,
    private val loadGuardianAnchor: suspend () -> GenesisUltraGuardianTrustAnchor
) {
    suspend fun inspect(): GenesisUltraPreBirthProvisioningSnapshot {
        val assessment = inspectPreparation()
        val receiptsAllowed =
            assessment.status != GenesisUltraBirthPreparationStatus.INCONSISTENT
        val bodyReceipt = when {
            !receiptsAllowed -> null
            assessment.facts.bodyIdentityRootState == GenesisUltraBodyIdentityRootState.READY ->
                GenesisUltraBodyProvisioningReceipt.from(loadBodyRoot())
            else -> null
        }
        val guardianReceipt = when {
            !receiptsAllowed -> null
            assessment.facts.guardianTrustAnchorState ==
                GenesisUltraGuardianTrustAnchorState.READY ->
                GenesisUltraGuardianProvisioningReceipt.from(loadGuardianAnchor())
            else -> null
        }
        return GenesisUltraPreBirthProvisioningSnapshot(
            assessment = assessment,
            bodyReceipt = bodyReceipt,
            guardianReceipt = guardianReceipt
        )
    }

    suspend fun provisionBody(
        request: GenesisUltraBodyProvisioningCeremonyRequest
    ): GenesisUltraPreBirthProvisioningSnapshot {
        require(request.userPresenceConfirmed) { "body_provisioning_user_presence_required" }
        val before = inspect()
        require(before.assessment.status == GenesisUltraBirthPreparationStatus.BODY_IDENTITY_REQUIRED) {
            "body_provisioning_state_not_allowed:${before.assessment.status}"
        }
        provisionBodyRoot()
        val after = inspect()
        check(after.bodyReceipt != null) { "body_provisioning_receipt_missing" }
        check(
            after.assessment.status == GenesisUltraBirthPreparationStatus.GUARDIAN_TRUST_REQUIRED ||
                after.assessment.status ==
                    GenesisUltraBirthPreparationStatus.READY_FOR_SIGNED_CANDIDATE
        ) { "body_provisioning_post_state_invalid:${after.assessment.status}" }
        return after
    }

    suspend fun provisionGuardian(
        request: GenesisUltraGuardianProvisioningCeremonyRequest
    ): GenesisUltraPreBirthProvisioningSnapshot {
        require(request.independentConfirmationAcknowledged) {
            "guardian_provisioning_independent_confirmation_required"
        }
        require(request.userPresenceConfirmed) {
            "guardian_provisioning_user_presence_required"
        }
        val before = inspect()
        require(before.assessment.status == GenesisUltraBirthPreparationStatus.GUARDIAN_TRUST_REQUIRED) {
            "guardian_provisioning_state_not_allowed:${before.assessment.status}"
        }
        provisionGuardianAnchor(request.anchorRequest())
        val after = inspect()
        check(after.guardianReceipt != null) { "guardian_provisioning_receipt_missing" }
        check(
            after.assessment.status == GenesisUltraBirthPreparationStatus.READY_FOR_SIGNED_CANDIDATE
        ) { "guardian_provisioning_post_state_invalid:${after.assessment.status}" }
        return after
    }

    internal companion object {
        fun production(
            preparationCoordinator: GenesisUltraBirthPreparationCoordinator,
            bodyIdentityRootStore: GenesisUltraAndroidBodyIdentityRootStore,
            guardianTrustAnchorStore: GenesisUltraAndroidGuardianTrustAnchorStore
        ): GenesisUltraPreBirthProvisioningCoordinator {
            return GenesisUltraPreBirthProvisioningCoordinator(
                inspectPreparation = preparationCoordinator::inspect,
                provisionBodyRoot = bodyIdentityRootStore::provisionBeforeBirth,
                loadBodyRoot = bodyIdentityRootStore::loadExisting,
                provisionGuardianAnchor = guardianTrustAnchorStore::provisionBeforeBirth,
                loadGuardianAnchor = guardianTrustAnchorStore::loadExisting
            )
        }
    }
}

/** Imports exactly one 32-byte RAW Ed25519 public key from a user-selected document. */
internal class GenesisUltraGuardianPublicKeyImportCoordinator(
    context: Context
) {
    private val contentResolver = context.applicationContext.contentResolver

    fun preview(uri: Uri): GenesisUltraGuardianPublicKeyPreview {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT) {
            "guardian_public_key_content_uri_required"
        }
        val rawPublicKey = contentResolver.openInputStream(uri)?.use(::readExactGuardianPublicKey)
            ?: error("guardian_public_key_unreadable")
        return GenesisUltraGuardianPublicKeyPreview(rawPublicKey)
    }
}

internal fun readExactGuardianPublicKey(input: InputStream): ByteArray {
    val output = ByteArrayOutputStream(GenesisUltraGuardianPublicKeyPreview.RAW_ED25519_PUBLIC_KEY_BYTES)
    val buffer = ByteArray(64)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) {
            val singleByte = input.read()
            if (singleByte < 0) break
            total += 1
            require(total <= GenesisUltraGuardianPublicKeyPreview.RAW_ED25519_PUBLIC_KEY_BYTES) {
                "guardian_public_key_raw_size_invalid"
            }
            output.write(singleByte)
            continue
        }
        total += count
        require(total <= GenesisUltraGuardianPublicKeyPreview.RAW_ED25519_PUBLIC_KEY_BYTES) {
            "guardian_public_key_raw_size_invalid"
        }
        output.write(buffer, 0, count)
    }
    require(total == GenesisUltraGuardianPublicKeyPreview.RAW_ED25519_PUBLIC_KEY_BYTES) {
        "guardian_public_key_raw_size_invalid"
    }
    return output.toByteArray()
}
