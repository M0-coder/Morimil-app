package com.morimil.app.improvements

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/** Request sent to an external sandboxed code executor. */
internal data class SelfPatchGenerationRequest(
    val changeId: String,
    val observationDigest: String,
    val problem: String,
    val proposal: String,
    val surfaces: Set<SelfChangeSurface>,
    val baseCommitSha: String
) {
    init {
        require(changeId.isNotBlank()) { "self_patch_request_change_id_blank" }
        require(SHA256_REF.matches(observationDigest)) { "self_patch_request_observation_digest_invalid" }
        require(problem.isNotBlank()) { "self_patch_request_problem_blank" }
        require(proposal.isNotBlank()) { "self_patch_request_proposal_blank" }
        require(surfaces.isNotEmpty()) { "self_patch_request_surfaces_empty" }
        require(COMMIT_SHA.matches(baseCommitSha)) { "self_patch_request_base_sha_invalid" }
    }

    private companion object {
        val SHA256_REF = Regex("^sha256:[a-f0-9]{64}$")
        val COMMIT_SHA = Regex("^[a-f0-9]{40}$")
    }
}

/**
 * Immutable patch artifact derived from the exact unified-diff bytes.
 *
 * Security-relevant metadata is never accepted from the executor. Digest, byte
 * count, changed paths and path-derived surfaces are recomputed from the bytes.
 */
internal class SelfPatchArtifact private constructor(
    val candidateDigest: String,
    val baseCommitSha: String,
    val changedPaths: List<String>,
    val derivedSurfaces: Set<SelfChangeSurface>,
    val patchByteCount: Long,
    val patchRef: String,
    private val patchBytes: ByteArray
) {
    fun copyPatchBytes(): ByteArray = patchBytes.copyOf()

    fun recomputeCandidateDigest(): String {
        return SelfPatchContentProfile.candidateDigest(baseCommitSha, patchBytes)
    }

    internal companion object {
        fun fromPatchBytes(
            baseCommitSha: String,
            patchRef: String,
            patchBytes: ByteArray
        ): SelfPatchArtifact {
            require(COMMIT_SHA.matches(baseCommitSha)) { "self_patch_base_sha_invalid" }
            require(patchRef.isNotBlank()) { "self_patch_ref_blank" }
            require(patchBytes.isNotEmpty()) { "self_patch_bytes_empty" }
            require(patchBytes.size.toLong() <= SelfPatchSafetyPolicy.MAX_PATCH_BYTES) {
                "self_patch_size_out_of_bounds"
            }
            val snapshot = patchBytes.copyOf()
            val changedPaths = SelfPatchSafetyPolicy.extractAndValidateChangedPaths(snapshot)
            return SelfPatchArtifact(
                candidateDigest = SelfPatchContentProfile.candidateDigest(baseCommitSha, snapshot),
                baseCommitSha = baseCommitSha,
                changedPaths = changedPaths,
                derivedSurfaces = SelfPatchSafetyPolicy.inferSurfaces(changedPaths),
                patchByteCount = snapshot.size.toLong(),
                patchRef = patchRef,
                patchBytes = snapshot
            )
        }

        private val COMMIT_SHA = Regex("^[a-f0-9]{40}$")
    }
}

/** Fail-closed blast-radius policy for generated code patches. */
internal object SelfPatchSafetyPolicy {
    const val MAX_CHANGED_PATHS = 128
    const val MAX_PATCH_BYTES = 2L * 1024L * 1024L

    private val diffHeader = Regex("^diff --git a/([^\\s]+) b/([^\\s]+)$")
    private val forbiddenBasenames = setOf(
        ".env",
        ".gitmodules",
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

    fun extractAndValidateChangedPaths(patchBytes: ByteArray): List<String> {
        val text = decodeStrictUtf8(patchBytes)
        require('\u0000' !in text) { "self_patch_nul_forbidden" }
        require('\r' !in text) { "self_patch_noncanonical_line_endings" }
        require(!text.contains("GIT binary patch")) { "self_patch_binary_diff_forbidden" }
        require(!text.contains("Binary files ")) { "self_patch_binary_diff_forbidden" }
        require(!text.contains("new file mode 120000")) { "self_patch_symlink_forbidden" }
        require(!text.contains("new mode 120000")) { "self_patch_symlink_forbidden" }
        require(text.startsWith("diff --git ")) { "self_patch_missing_diff_header" }

        val changed = linkedSetOf<String>()
        var currentOld: String? = null
        var currentNew: String? = null
        var sawOldMarker = false
        var sawNewMarker = false
        var sectionCount = 0

        fun closeSection() {
            if (currentOld != null || currentNew != null) {
                require(sawOldMarker && sawNewMarker) { "self_patch_noncanonical_file_section" }
            }
        }

        text.split('\n').forEach { line ->
            if (line.startsWith("diff --git ")) {
                closeSection()
                val match = diffHeader.matchEntire(line)
                    ?: error("self_patch_diff_header_noncanonical")
                val oldPath = match.groupValues[1]
                val newPath = match.groupValues[2]
                requireSafeRelativePath(oldPath)
                requireSafeRelativePath(newPath)
                changed += oldPath
                changed += newPath
                currentOld = oldPath
                currentNew = newPath
                sawOldMarker = false
                sawNewMarker = false
                sectionCount += 1
            } else if (line.startsWith("--- ")) {
                val expected = currentOld ?: error("self_patch_old_marker_without_header")
                val marker = line.removePrefix("--- ")
                require(marker == "a/$expected" || marker == "/dev/null") {
                    "self_patch_old_marker_mismatch"
                }
                sawOldMarker = true
            } else if (line.startsWith("+++ ")) {
                val expected = currentNew ?: error("self_patch_new_marker_without_header")
                val marker = line.removePrefix("+++ ")
                require(marker == "b/$expected" || marker == "/dev/null") {
                    "self_patch_new_marker_mismatch"
                }
                sawNewMarker = true
            }
        }
        closeSection()

        require(sectionCount > 0) { "self_patch_file_section_missing" }
        val paths = changed.toList().distinct().sorted()
        require(paths.isNotEmpty()) { "self_patch_changed_paths_empty" }
        require(paths.size <= MAX_CHANGED_PATHS) { "self_patch_changed_path_count_exceeded" }
        requireSafePaths(paths)
        return paths
    }

    fun inferSurfaces(paths: List<String>): Set<SelfChangeSurface> {
        val surfaces = linkedSetOf<SelfChangeSurface>()
        paths.forEach { rawPath ->
            val path = rawPath.lowercase(Locale.ROOT)
            if (
                path.startsWith(".github/") ||
                path == "app/build.gradle.kts" ||
                path.startsWith("gradle/") ||
                path.startsWith("tools/quality/")
            ) {
                surfaces += SelfChangeSurface.BUILD_AND_SUPPLY_CHAIN
            }
            if (
                "/security/" in path ||
                "signed-release" in path ||
                "/improvements/" in path
            ) {
                surfaces += SelfChangeSurface.SECURITY_BOUNDARY
            }
            if ("genesis" in path || "birth" in path) {
                surfaces += SelfChangeSurface.GENESIS
                surfaces += SelfChangeSurface.INSTANCE_IDENTITY
            }
            if ("memory" in path || "recall" in path || "restcycle" in path || "migration" in path) {
                surfaces += SelfChangeSurface.CANONICAL_MEMORY
            }
            if ("body" in path || "writer" in path || "epoch" in path) {
                surfaces += SelfChangeSurface.WRITER_AUTHORITY
                surfaces += SelfChangeSurface.BODY_SUCCESSION
            }
            if ("recovery" in path) {
                surfaces += SelfChangeSurface.RECOVERY
            }
            if ("/reasoning/" in path || "/ai/" in path) {
                surfaces += SelfChangeSurface.REASONING_RUNTIME
            }
            if ("/ui/" in path) {
                surfaces += SelfChangeSurface.PRESENTATION
            }
            if (path.startsWith("app/src/main/") && surfaces.isEmpty()) {
                surfaces += SelfChangeSurface.CORE_IMPLEMENTATION
            }
        }
        return surfaces
    }

    fun requireSafePaths(paths: List<String>) {
        require(paths.size <= MAX_CHANGED_PATHS) { "self_patch_changed_path_count_exceeded" }
        require(paths.none { path ->
            val normalized = path.lowercase(Locale.ROOT)
            val basename = normalized.substringAfterLast('/')
            normalized.startsWith(".git/") ||
                basename in forbiddenBasenames ||
                basename.startsWith(".env.") ||
                forbiddenSuffixes.any { suffix -> basename.endsWith(suffix) }
        }) { "self_patch_credential_or_git_path_forbidden" }
    }

    private fun requireSafeRelativePath(path: String) {
        require(
            path.isNotBlank() &&
                !path.startsWith('/') &&
                '\\' !in path &&
                path.split('/').none { segment -> segment.isBlank() || segment == "." || segment == ".." }
        ) { "self_patch_changed_path_invalid" }
    }

    private fun decodeStrictUtf8(bytes: ByteArray): String {
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (error: Exception) {
            throw IllegalArgumentException("self_patch_utf8_invalid", error)
        }
    }
}

/** Domain-separated digest of the exact patch bytes plus the exact base commit. */
internal object SelfPatchContentProfile {
    private const val DOMAIN = "morimil.self_improvement.patch.v1"

    fun candidateDigest(baseCommitSha: String, patchBytes: ByteArray): String {
        val preimage = ByteArrayOutputStream(patchBytes.size + 128).use { output ->
            output.write(frame(DOMAIN.toByteArray(StandardCharsets.UTF_8)))
            output.write(frame(baseCommitSha.toByteArray(StandardCharsets.US_ASCII)))
            output.write(frame(patchBytes))
            output.toByteArray()
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(preimage)
        return "sha256:" + digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun frame(bytes: ByteArray): ByteArray {
        return ByteArrayOutputStream(bytes.size + 24).use { output ->
            output.write(bytes.size.toString().toByteArray(StandardCharsets.US_ASCII))
            output.write(':'.code)
            output.write(bytes)
            output.write('\n'.code)
            output.toByteArray()
        }
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

/** Independent signer of evidence for one exact patch artifact. */
internal interface SelfIndependentVerifierPort {
    val verifierId: String

    suspend fun verify(
        observation: SelfChangeObservation,
        patch: SelfPatchArtifact
    ): SelfSignedAuthorityAttestation
}

/**
 * Coordinates self-improvement up to VERIFIED. It deliberately cannot authorize
 * or merge. External evidence is cryptographically verified before the stage can
 * advance.
 */
internal class SelfImprovementOrchestrator(
    private val patchExecutor: SelfPatchExecutorPort,
    private val independentVerifier: SelfIndependentVerifierPort,
    private val authorityVerifier: SelfImprovementAuthorityVerifier
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
        require(patch.candidateDigest == patch.recomputeCandidateDigest()) {
            "self_patch_digest_recompute_mismatch"
        }
        val widenedSurfaces = patch.derivedSurfaces - observation.surfaces
        require(widenedSurfaces.isEmpty()) {
            "self_patch_surface_expansion_requires_new_observation:$widenedSurfaces"
        }

        candidate = SelfImprovementProtocol.registerPatchCandidate(
            candidate = candidate,
            candidateDigest = patch.candidateDigest,
            baseCommitSha = patch.baseCommitSha,
            actor = SelfChangeActor.EXTERNAL_EXECUTOR
        )

        val attestation = independentVerifier.verify(observation, patch)
        require(attestation.signerId == independentVerifier.verifierId) {
            "self_verifier_attestation_identity_mismatch"
        }
        require(attestation.signerId != patchExecutor.executorId) {
            "self_change_executor_verifier_separation_required"
        }
        return SelfImprovementProtocol.verify(
            candidate = candidate,
            attestation = attestation,
            authorityVerifier = authorityVerifier
        )
    }
}
