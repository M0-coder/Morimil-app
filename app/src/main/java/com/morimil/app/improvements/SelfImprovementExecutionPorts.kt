package com.morimil.app.improvements

/** Request sent to an external sandboxed code executor. */
internal data class SelfPatchGenerationRequest(
    val changeId: String,
    val observationDigest: String,
    val problem: String,
    val proposal: String,
    val surfaces: Set<SelfChangeSurface>,
    val baseCommitSha: String
)

/** Exact patch artifact returned by an external executor. */
internal data class SelfPatchArtifact(
    val candidateDigest: String,
    val baseCommitSha: String,
    val changedPaths: List<String>,
    val patchByteCount: Long,
    val patchRef: String
) {
    init {
        require(SHA256_REF.matches(candidateDigest)) { "self_patch_digest_invalid" }
        require(COMMIT_SHA.matches(baseCommitSha)) { "self_patch_base_sha_invalid" }
        require(changedPaths.isNotEmpty()) { "self_patch_changed_paths_empty" }
        require(changedPaths == changedPaths.distinct().sorted()) {
            "self_patch_changed_paths_not_canonical"
        }
        require(changedPaths.none { path ->
            path.isBlank() || path.startsWith("/") || path.contains("\\") ||
                path.split('/').any { segment -> segment.isBlank() || segment == "." || segment == ".." }
        }) { "self_patch_changed_path_invalid" }
        require(patchByteCount in 1..SelfPatchSafetyPolicy.MAX_PATCH_BYTES) {
            "self_patch_size_out_of_bounds"
        }
        require(patchRef.isNotBlank()) { "self_patch_ref_blank" }
        SelfPatchSafetyPolicy.requireSafePaths(changedPaths)
    }

    private companion object {
        val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
        val COMMIT_SHA = Regex("^[a-f0-9]{40}$")
    }
}

/** Fail-closed blast-radius policy for generated code patches. */
internal object SelfPatchSafetyPolicy {
    const val MAX_CHANGED_PATHS = 128
    const val MAX_PATCH_BYTES = 2L * 1024L * 1024L

    private val forbiddenExactPaths = setOf(
        ".env",
        "local.properties"
    )

    private val forbiddenSuffixes = setOf(
        ".jks",
        ".keystore",
        ".p12",
        ".pfx",
        ".pem",
        ".key"
    )

    fun requireSafePaths(paths: List<String>) {
        require(paths.size <= MAX_CHANGED_PATHS) { "self_patch_changed_path_count_exceeded" }
        require(paths.none { path ->
            path in forbiddenExactPaths ||
                path.startsWith(".git/") ||
                path.substringAfterLast('/').startsWith(".env.") ||
                forbiddenSuffixes.any { suffix -> path.endsWith(suffix) }
        }) { "self_patch_credential_or_git_path_forbidden" }
    }
}

/**
 * Sandboxed patch-generation boundary.
 *
 * Implementations may write only to an isolated workspace/branch. This port has
 * no merge, release, install, production-signing or protected-main mutation API.
 */
internal interface SelfPatchExecutorPort {
    val executorId: String

    suspend fun generatePatch(request: SelfPatchGenerationRequest): SelfPatchArtifact
}

/** Independent evidence producer for one exact patch artifact. */
internal interface SelfIndependentVerifierPort {
    val verifierId: String

    suspend fun verify(
        observation: SelfChangeObservation,
        patch: SelfPatchArtifact
    ): SelfChangeEvidence
}

/**
 * Coordinates self-improvement up to VERIFIED. It deliberately cannot authorize
 * or merge. Those transitions remain separate from patch generation and
 * independent verification.
 */
internal class SelfImprovementOrchestrator(
    private val patchExecutor: SelfPatchExecutorPort,
    private val independentVerifier: SelfIndependentVerifierPort
) {
    init {
        require(patchExecutor.executorId.isNotBlank()) { "self_patch_executor_id_blank" }
        require(independentVerifier.verifierId.isNotBlank()) { "self_verifier_id_blank" }
        require(patchExecutor.executorId != independentVerifier.verifierId) {
            "self_change_executor_verifier_separation_required"
        }
    }

    suspend fun prepareVerifiedCandidate(
        observation: SelfChangeObservation,
        baseCommitSha: String
    ): SelfChangeCandidate {
        var candidate = SelfImprovementProtocol.detect(observation)
        candidate = SelfImprovementProtocol.diagnose(candidate, SelfChangeActor.MORIMIL)
        candidate = SelfImprovementProtocol.propose(candidate, SelfChangeActor.MORIMIL)

        val patch = patchExecutor.generatePatch(
            SelfPatchGenerationRequest(
                changeId = observation.changeId,
                observationDigest = observation.observationDigest,
                problem = observation.problem,
                proposal = observation.proposal,
                surfaces = observation.surfaces,
                baseCommitSha = baseCommitSha
            )
        )
        require(patch.baseCommitSha == baseCommitSha) { "self_patch_executor_base_mismatch" }

        candidate = SelfImprovementProtocol.registerPatchCandidate(
            candidate = candidate,
            candidateDigest = patch.candidateDigest,
            baseCommitSha = patch.baseCommitSha,
            actor = SelfChangeActor.EXTERNAL_EXECUTOR
        )

        val evidence = independentVerifier.verify(observation, patch)
        return SelfImprovementProtocol.verify(
            candidate = candidate,
            evidence = evidence,
            actor = SelfChangeActor.INDEPENDENT_VERIFIER
        )
    }
}
