package com.morimil.app.core.runtime

import com.morimil.app.improvements.SelfImprovementRuntimeObserver
import com.morimil.app.improvements.SelfImprovementRuntimeStatus

data class AppRuntimeCapabilities(
    val genesisBundleVerification: Boolean = true,
    val genesisPrivateInstallation: Boolean = true,
    val singleLocalBirth: Boolean = true,
    val hashLinkedMemoryEvents: Boolean = true,
    val memoryHashV1Compatibility: Boolean = true,
    val memoryHashV2MetadataBinding: Boolean = true,
    val livingMemorySnapshot: Boolean = true,
    val configurableMotor: Boolean = true,
    val localMotorEndpoint: Boolean = true,
    val encryptedRuntimeStorage: Boolean = true,
    val memoryOrganDatabase: Boolean = true,
    val autobiographicalSnapshotEntity: Boolean = true,
    val knowledgeCapsuleEntity: Boolean = true,
    val recallSchedule: Boolean = true,
    val restCycle: Boolean = true,
    val migrationRecord: Boolean = true,
    val memoryLink: Boolean = true,
    val agentOrchestration: Boolean = true,
    val multiDeviceAuthorization: Boolean = true,
    val selfImprovementGovernance: Boolean = true,
    val selfImprovementContentBoundObservations: Boolean = true,
    val selfImprovementPatchSafetyPolicy: Boolean = true,
    val selfImprovementSignedAuthorityAttestations: Boolean = true,
    val selfImprovementAuditAntiTruncationAnchor: Boolean = true,
    val selfPatchExecutorConnected: Boolean = false,
    val selfIndependentVerifierConnected: Boolean = false,
    val selfHumanAuthorizerTrustConnected: Boolean = false,
    val selfImprovementExternalAuditWitnessConnected: Boolean = false,
    val selfImprovementRuntimeStatus: SelfImprovementRuntimeStatus =
        SelfImprovementRuntimeStatus.NOT_INITIALIZED,
    val selfImprovementRuntimeSignalAutonomy: Boolean = false,
    val selfImprovementDurableAuditStore: Boolean = false,
    val selfMergeAuthority: Boolean = false,
    val pcHandoffProtocol: Boolean = false,
    val pcCommandExecution: Boolean = false,
    val interactionState: Boolean = false
)

object CurrentRuntimeCapabilities {
    val value: AppRuntimeCapabilities
        get() {
            val runtimeStatus = SelfImprovementRuntimeObserver.runtimeStatus()
            val ready = runtimeStatus == SelfImprovementRuntimeStatus.READY
            return AppRuntimeCapabilities(
                selfImprovementRuntimeStatus = runtimeStatus,
                selfImprovementRuntimeSignalAutonomy = ready,
                selfImprovementDurableAuditStore = ready
            )
        }
}
