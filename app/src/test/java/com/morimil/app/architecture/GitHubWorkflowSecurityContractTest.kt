package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubWorkflowSecurityContractTest {
    private val workflowPaths = listOf(
        ".github/workflows/android-ci.yml",
        ".github/workflows/codeql.yml",
        ".github/workflows/sbom.yml",
        ".github/workflows/signed-release-apk.yml"
    )

    @Test
    fun externalActionsRemainPinnedToFullCommitShas() {
        val actionRef = Regex("""(?m)^\s*uses:\s*([^./\s][^@\s]*)@([^\s#]+)""")
        workflowPaths.forEach { path ->
            val source = repositoryFile(path).readText()
            actionRef.findAll(source).forEach { match ->
                val action = match.groupValues[1]
                val ref = match.groupValues[2]
                assertTrue(
                    "$path must pin $action to a full lowercase 40-character SHA, found @$ref",
                    ref.matches(Regex("[0-9a-f]{40}"))
                )
            }
        }
    }

    @Test
    fun verifiedActionVersionCommentsRemainAccurate() {
        val expected = mapOf(
            "3d3c42e5aac5ba805825da76410c181273ba90b1" to "# v7.0.1",
            "043fb46d1a93c77aae656e7c1c64a875d1fc6a0a" to "# v7.0.1"
        )
        val sources = workflowPaths.associateWith { path ->
            repositoryFile(path).readLines()
        }

        expected.forEach { (sha, comment) ->
            val matchingLines = sources.values.flatten().filter { "@$sha" in it }
            assertTrue("Verified action SHA $sha must remain present", matchingLines.isNotEmpty())
            assertTrue(
                "Every use of verified action SHA $sha must retain human version $comment",
                matchingLines.all { it.trimEnd().endsWith(comment) }
            )
        }
    }

    @Test
    fun releaseSecretsNeverLiveAtJobScope() {
        val lines = repositoryFile(".github/workflows/signed-release-apk.yml").readLines()
        val stepsIndex = lines.indexOfFirst { it == "    steps:" }
        assertTrue("signed release job must contain steps", stepsIndex >= 0)
        assertFalse(
            "MORIMIL_RELEASE_* secrets must not exist in jobs.<job>.env",
            lines.take(stepsIndex).any { "MORIMIL_RELEASE_" in it }
        )
    }

    @Test
    fun androidCiUsesMinimumReadOnlyPermissions() {
        val source = repositoryFile(".github/workflows/android-ci.yml").readText()
        assertTrue(
            "Android CI must declare top-level contents: read",
            Regex("(?m)^permissions:\n  contents: read$").containsMatchIn(source)
        )
        assertFalse("Android CI must not request write permissions", Regex("(?m)^  [a-z-]+: write$").containsMatchIn(source))
    }

    @Test
    fun releaseSecretsAreScopedToOneFailClosedStep() {
        val source = repositoryFile(".github/workflows/signed-release-apk.yml").readText()
        val names = listOf(
            "MORIMIL_RELEASE_KEYSTORE_BASE64",
            "MORIMIL_RELEASE_STORE_PASSWORD",
            "MORIMIL_RELEASE_KEY_ALIAS",
            "MORIMIL_RELEASE_KEY_PASSWORD",
            "MORIMIL_RELEASE_CERT_SHA256"
        )
        names.forEach { name ->
            assertTrue(
                "$name must be sourced exactly once from GitHub secrets",
                source.split("${'$'}{{ secrets.$name }}").size == 2
            )
        }
        assertFalse("Signing material must never be persisted through GITHUB_ENV", "GITHUB_ENV" in source)
        assertTrue("Signing must remain release-only", ":app:assembleRelease" in source)
        assertFalse("Signing must never fall back to debug", "assembleDebug" in source)
        assertTrue("Keystore deletion must be fail-closed through an EXIT trap", "trap cleanup EXIT" in source)
        assertTrue(
            "Certificate mismatch must terminate the workflow",
            "Release certificate fingerprint does not match the pinned production fingerprint." in source
        )
    }

    @Test
    fun postSigningUploadRemainsIsolatedFromReleaseSecrets() {
        val source = repositoryFile(".github/workflows/signed-release-apk.yml").readText()
        val signingStep = "      - name: Build, verify, package, and destroy signing material"
        val uploadStep = "      - name: Upload verified signed APK"
        val signingIndex = source.indexOf(signingStep)
        val uploadIndex = source.indexOf(uploadStep)

        assertTrue("signed release workflow must retain the signing step", signingIndex >= 0)
        assertTrue("signed release workflow must retain the upload step", uploadIndex > signingIndex)

        val signingBlock = source.substring(signingIndex, uploadIndex)
        val postSigning = source.substring(uploadIndex)
        val externalAction = Regex("""(?m)^\s*uses:\s*([^./\s][^@\s]*)@([^\s#]+)""")
        val postSigningActions = externalAction.findAll(postSigning).toList()

        assertTrue(
            "Signing cleanup must execute on every exit path",
            "trap cleanup EXIT" in signingBlock
        )
        assertFalse(
            "Signing material must never cross steps through GITHUB_ENV",
            "GITHUB_ENV" in source
        )
        assertFalse(
            "No release secret expression may exist after the signing step",
            "${'$'}{{ secrets.MORIMIL_RELEASE_" in postSigning
        )
        assertTrue(
            "Exactly one external action is allowed after signing",
            postSigningActions.size == 1
        )
        assertTrue(
            "The only post-signing action must be the pinned artifact upload",
            postSigningActions.single().groupValues[1] == "actions/upload-artifact" &&
                postSigningActions.single().groupValues[2] ==
                "ea165f8d65b6e75b540449e92b4886f43607fa02"
        )
        assertTrue(
            "Post-signing upload path must be exactly release-output/**",
            Regex("""(?m)^          path: release-output/\*\*$""").containsMatchIn(postSigning)
        )
        assertFalse(
            "Post-signing upload must not reference keystore material",
            Regex("""(?i)(keystore|\.jks|RUNNER_TEMP|MORIMIL_RELEASE_)""").containsMatchIn(postSigning)
        )
    }

    private fun repositoryFile(path: String): File = File(repositoryRoot(), path)

    private fun repositoryRoot(): File {
        return sequenceOf(File("."), File(".."))
            .map(File::getCanonicalFile)
            .firstOrNull { candidate ->
                File(candidate, "README.md").isFile &&
                    File(candidate, "app/build.gradle.kts").isFile
            }
            ?: error("Repository root not found")
    }
}
