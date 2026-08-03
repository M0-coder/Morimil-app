package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupGradleActionGovernanceContractTest {
    private val workflowPaths = SetupGradleActionPolicy.expected.keys.toList()

    @Test
    fun setupGradle620RemainsImmutableAndUsesOnlyBasicCaching() {
        val sources = workflowPaths.associateWith { path -> repositoryFile(path).readText() }
        assertTrue(SetupGradleActionPolicy.validate(sources))
        assertEquals(
            SetupGradleActionPolicy.expected.values.toList(),
            SetupGradleActionPolicy.parseAll(sources)
        )
    }

    @Test
    fun movingRefWrongShaEnhancedCacheMissingProviderAndDuplicateJavaCacheAreRejected() {
        val sources = workflowPaths.associateWith { path -> repositoryFile(path).readText() }
        val codeqlPath = ".github/workflows/codeql.yml"
        val genesisPath = ".github/workflows/genesis-body-ci.yml"
        val releasePath = ".github/workflows/signed-release-apk.yml"
        val exactUse =
            "gradle/actions/setup-gradle@${SetupGradleActionPolicy.expectedSha} # v6.2.0"

        val mutations = listOf(
            sources + (codeqlPath to sources.getValue(codeqlPath).replace(
                exactUse,
                "gradle/actions/setup-gradle@v6.2.0 # v6.2.0"
            )),
            sources + (genesisPath to sources.getValue(genesisPath).replace(
                SetupGradleActionPolicy.expectedSha,
                "0000000000000000000000000000000000000000"
            )),
            sources + (releasePath to sources.getValue(releasePath).replace(
                "cache-provider: basic",
                "cache-provider: enhanced"
            )),
            sources + (codeqlPath to sources.getValue(codeqlPath).replace(
                "        with:\n          cache-provider: basic\n",
                ""
            )),
            sources + (codeqlPath to sources.getValue(codeqlPath).replace(
                "          java-version: \"17\"",
                "          java-version: \"17\"\n          cache: gradle"
            ))
        )

        mutations.forEachIndexed { index, mutated ->
            assertFalse("Kill-test ${index + 1} was accepted", SetupGradleActionPolicy.validate(mutated))
        }
        assertTrue(SetupGradleActionPolicy.validate(sources))
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

internal data class SetupGradleUse(
    val path: String,
    val job: String,
    val sha: String,
    val version: String,
    val cacheProvider: String
)

internal object SetupGradleActionPolicy {
    const val expectedSha = "3f131e8634966bd73d06cc69884922b02e6faf92"
    private const val expectedVersion = "v6.2.0"
    private const val actionName = "gradle/actions/setup-gradle"

    val expected = linkedMapOf(
        ".github/workflows/codeql.yml" to SetupGradleUse(
            ".github/workflows/codeql.yml",
            "analyze",
            expectedSha,
            expectedVersion,
            "basic"
        ),
        ".github/workflows/genesis-body-ci.yml" to SetupGradleUse(
            ".github/workflows/genesis-body-ci.yml",
            "android-validation",
            expectedSha,
            expectedVersion,
            "basic"
        ),
        ".github/workflows/signed-release-apk.yml" to SetupGradleUse(
            ".github/workflows/signed-release-apk.yml",
            "build-unsigned-release",
            expectedSha,
            expectedVersion,
            "basic"
        )
    )

    fun validate(sources: Map<String, String>): Boolean = runCatching {
        parseAll(sources) == expected.values.toList()
    }.getOrDefault(false)

    fun parseAll(sources: Map<String, String>): List<SetupGradleUse> {
        require(sources.keys == expected.keys) {
            "The setup-gradle governed workflow set diverged"
        }
        val parsed = expected.map { (path, contract) ->
            parse(path, contract.job, sources.getValue(path))
        }
        validateCodeQlJavaCache(sources.getValue(".github/workflows/codeql.yml"))
        return parsed
    }

    private fun parse(path: String, jobName: String, source: String): SetupGradleUse {
        val document = WorkflowYamlAstParser.parse(source)
        val actionUse = GovernedActionInventory.parse(path, document)
            .single { use -> use.action == actionName }
        require(actionUse.sha == expectedSha)
        require(actionUse.version == expectedVersion)

        val structure = WorkflowStructure.parse(source)
        val step = structure.jobs.getValue(jobName).steps.single { candidate ->
            val uses = candidate.mapping.entries["uses"] as? YamlScalar
            uses?.value == "$actionName@$expectedSha"
        }
        require(step.name == "Set up Gradle")
        require(step.mapping.entries.keys == setOf("name", "uses", "with"))

        val with = step.mapping.entries["with"] as? YamlMapping
            ?: error("setup-gradle must declare an exact with mapping")
        require(with.style == YamlMappingStyle.BLOCK)
        require(with.entries.keys == setOf("cache-provider"))
        val provider = with.entries.getValue("cache-provider") as? YamlScalar
            ?: error("cache-provider must be a scalar")
        require(provider.value == "basic")
        require(provider.raw == "basic")
        require(provider.comment.isNullOrBlank())

        return SetupGradleUse(path, jobName, actionUse.sha, actionUse.version, provider.value)
    }

    private fun validateCodeQlJavaCache(source: String) {
        val structure = WorkflowStructure.parse(source)
        val setupJava = structure.jobs.getValue("analyze").steps.single { candidate ->
            val uses = candidate.mapping.entries["uses"] as? YamlScalar
            uses?.value?.startsWith("actions/setup-java@") == true
        }
        val with = setupJava.mapping.entries["with"] as? YamlMapping
            ?: error("CodeQL setup-java must declare its exact inputs")
        require(with.entries.keys == setOf("distribution", "java-version")) {
            "CodeQL must not combine setup-java Gradle caching with setup-gradle"
        }
    }
}
