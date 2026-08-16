package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseUnsignedBoundaryTaskClassificationTest {
    private val allowedReleaseUnsignedDebugMetadataTasks = setOf(
        "mergeReleaseUnsignedNativeDebugMetadata",
        "stripReleaseUnsignedDebugSymbols"
    )

    @Test
    fun onlyKnownReleaseUnsignedDebugMetadataTasksAreAllowed() {
        assertFalse(isForbiddenDebugTask("mergeReleaseUnsignedNativeDebugMetadata"))
        assertFalse(isForbiddenDebugTask("stripReleaseUnsignedDebugSymbols"))
    }

    @Test
    fun realDebugVariantTasksRemainForbidden() {
        listOf(
            "assembleDebug",
            "compileDebugKotlin",
            "packageDebug",
            "mergeDebugResources",
            "testDebugUnitTest",
            "connectedDebugAndroidTest"
        ).forEach { taskName ->
            assertTrue("Expected debug variant task to remain forbidden: $taskName", isForbiddenDebugTask(taskName))
        }
    }

    @Test
    fun similarlyNamedReleaseUnsignedTaskCannotExpandTheAllowlist() {
        assertTrue(isForbiddenDebugTask("assembleReleaseUnsignedDebug"))
        assertTrue(isForbiddenDebugTask("releaseUnsignedDebugBackdoor"))
    }

    @Test
    fun gradleSourceUsesExactAllowlistRatherThanBroadReleaseUnsignedExemption() {
        val gradle = repositoryFile("app/build.gradle.kts").readText()
        assertTrue(gradle.contains("val allowedReleaseUnsignedDebugMetadataTasks = setOf("))
        assertTrue(gradle.contains("\"mergeReleaseUnsignedNativeDebugMetadata\""))
        assertTrue(gradle.contains("\"stripReleaseUnsignedDebugSymbols\""))
        assertTrue(gradle.contains("task.name !in allowedReleaseUnsignedDebugMetadataTasks"))
        assertFalse(gradle.contains("!task.name.contains(\"releaseUnsigned\""))
    }

    private fun isForbiddenDebugTask(taskName: String): Boolean =
        taskName.contains("debug", ignoreCase = true) &&
            taskName !in allowedReleaseUnsignedDebugMetadataTasks

    private fun repositoryFile(path: String): File = File(repositoryRoot(), path)

    private fun repositoryRoot(): File =
        sequenceOf(File("."), File(".."))
            .map(File::getCanonicalFile)
            .firstOrNull { candidate ->
                File(candidate, "README.md").isFile && File(candidate, "app/build.gradle.kts").isFile
            }
            ?: error("Repository root not found")
}
