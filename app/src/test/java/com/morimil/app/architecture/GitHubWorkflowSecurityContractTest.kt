package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertEquals
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
    fun everyUsesEntryMatchesTheCompleteGovernedAstInventory() {
        val sources = workflowPaths.associateWith { path -> repositoryFile(path).readText() }
        assertTrue(GovernedActionInventory.validate(sources))
        assertEquals(GovernedActionInventory.expected, GovernedActionInventory.parseAll(sources))
    }

    @Test
    fun everyStepIsCapturedWhetherOrNotItHasAName() {
        val source = repositoryFile(".github/workflows/signed-release-apk.yml").readText()
        val structure = WorkflowStructure.parse(source)
        assertEquals(7, structure.jobs.getValue("build-unsigned-release").steps.size)
        assertEquals(5, structure.jobs.getValue("sign-release").steps.size)
        assertTrue(structure.jobs.values.flatMap { it.steps }.all { it.mapping.entries.isNotEmpty() })
    }

    @Test
    fun unnamedFlowAndQuotedUsesBypassesAreRejected() {
        val path = ".github/workflows/android-ci.yml"
        val original = repositoryFile(path).readText()
        val governedStep =
            "      - name: Set up Python\n" +
                "        uses: actions/setup-python@5fda3b95a4ea91299a34e894583c3862153e4b97 # v7.0.0\n" +
                "        with:\n" +
                "          python-version: '3.12'"
        val replacements = listOf(
            "      - uses: docker://alpine:latest",
            "      - {uses: docker://alpine:latest}",
            "      - name: Quoted bypass\n        \"uses\": attacker/action@1111111111111111111111111111111111111111 # v1"
        )

        replacements.forEach { replacement ->
            val mutated = workflowPaths.associateWith { workflowPath ->
                if (workflowPath == path) original.replace(governedStep, replacement)
                else repositoryFile(workflowPath).readText()
            }
            assertFalse("Unexpectedly accepted AST uses entry: $replacement", GovernedActionInventory.validate(mutated))
        }
    }

    @Test
    fun dockerMissingRefMovingRefDynamicRefUnknownAndLocalUsesAreRejected() {
        val path = ".github/workflows/android-ci.yml"
        val original = repositoryFile(path).readText()
        val governedLine =
            "        uses: actions/setup-python@5fda3b95a4ea91299a34e894583c3862153e4b97 # v7.0.0"
        val replacements = listOf(
            "        uses: docker://alpine:latest",
            "        uses: actions/setup-python # v7.0.0",
            "        uses: actions/setup-python@v7 # v7.0.0",
            "        uses: actions/setup-python@${'$'}{{ github.ref }} # dynamic",
            "        uses: attacker/setup-python@5fda3b95a4ea91299a34e894583c3862153e4b97 # v7.0.0",
            "        uses: ./unreviewed-local-action"
        )

        replacements.forEach { replacement ->
            val mutated = workflowPaths.associateWith { workflowPath ->
                if (workflowPath == path) original.replace(governedLine, replacement)
                else repositoryFile(workflowPath).readText()
            }
            assertFalse("Unexpectedly accepted uses entry: $replacement", GovernedActionInventory.validate(mutated))
        }
    }

    @Test
    fun governedWorkflowPermissionsAndEnvironmentScopesUseTheYamlAst() {
        val android = WorkflowStructure.parse(repositoryFile(".github/workflows/android-ci.yml").readText())
        assertEquals(
            WorkflowMapping.block(mapOf("contents" to "read")),
            android.topLevelPermissions
        )
        assertTrue(android.jobs.values.all { job ->
            job.permissions?.values?.values?.none { value -> value == "write" } != false
        })

        val release = WorkflowStructure.parse(repositoryFile(".github/workflows/signed-release-apk.yml").readText())
        assertEquals(WorkflowMapping.emptyFlow(), release.topLevelPermissions)
        assertEquals(
            WorkflowMapping.block(mapOf("contents" to "read")),
            release.jobs.getValue("build-unsigned-release").permissions
        )
        assertEquals(WorkflowMapping.emptyFlow(), release.jobs.getValue("sign-release").permissions)
        assertTrue(release.topLevelEnv == null)
        assertTrue(release.jobs.values.all { it.env == null })
    }

    @Test
    fun scalarAndFlowWritePermissionsAreRejectedAtRootAndJobScope() {
        val source = repositoryFile(".github/workflows/signed-release-apk.yml").readText()
        assertTrue(ReleaseWorkflowPolicy.validate(source))

        val mutations = listOf(
            source.replaceFirst("permissions: {}", "permissions: write-all"),
            source.replaceFirst("    permissions: {}", "    permissions: write-all"),
            source.replaceFirst("permissions: {}", "permissions: {contents: write}"),
            source.replaceFirst("    permissions: {}", "    permissions: {contents: write}"),
            source.replaceFirst(
                "    permissions: {}",
                "    permissions:\n      contents: write"
            )
        )
        mutations.forEach { mutated -> assertFalse(ReleaseWorkflowPolicy.validate(mutated)) }
    }

    @Test
    fun releaseSecretsExistOnlyInTheExactDirectApksignerStepEnv() {
        val source = repositoryFile(".github/workflows/signed-release-apk.yml").readText()
        val structure = WorkflowStructure.parse(source)
        val signingStep = structure.jobs.getValue("sign-release").steps.single {
            it.name == "Sign, verify, and close final artifact inventory"
        }
        assertEquals(ReleaseWorkflowPolicy.releaseSecretNames, signingStep.secretReferences.map { it.name }.toSet())
        assertEquals(5, signingStep.secretReferences.size)
        assertTrue(signingStep.secretReferences.all { it.path.takeLast(2).first() == "env" })
        assertTrue("\"${'$'}apksigner\" sign" in signingStep.text)
        assertFalse("./gradlew" in signingStep.text)
        assertFalse("actions/checkout@" in structure.jobs.getValue("sign-release").text)

        val allReferences = structure.secretReferences
        assertEquals(5, allReferences.size)
        assertTrue(allReferences.all { it.step === signingStep })

        val contextReferences = structure.secretContextReferences
        assertEquals(5, contextReferences.size)
        assertTrue(contextReferences.all { it.step === signingStep })
        assertTrue(contextReferences.all { it.canonicalDirect })
        assertEquals(ReleaseWorkflowPolicy.releaseSecretNames, contextReferences.mapNotNull { it.name }.toSet())
    }

    @Test
    fun githubExpressionScannerDistinguishesSecretsContextFromTextAndStringLiterals() {
        assertTrue(GitHubExpressionSecurity.secretUses("ordinary secrets text").isEmpty())
        assertTrue(GitHubExpressionSecurity.secretUses("${'$'}{{ 'secrets' }}").isEmpty())
        assertTrue(GitHubExpressionSecurity.secretUses("${'$'}{{ format('secrets') }}").isEmpty())

        val objectUse = GitHubExpressionSecurity.secretUses("${'$'}{{ toJSON(secrets) }}")
        assertEquals(1, objectUse.size)
        assertEquals(null, objectUse.single().name)
        assertFalse(objectUse.single().canonicalDirect)

        val computedUse = GitHubExpressionSecurity.secretUses(
            "${'$'}{{ secrets[format('MORIMIL_RELEASE_{0}', 'KEY_PASSWORD')] }}"
        )
        assertEquals(1, computedUse.size)
        assertEquals(null, computedUse.single().name)
        assertFalse(computedUse.single().canonicalDirect)
    }


    @Test
    fun githubExpressionScannerTreatsSecretsContextCaseInsensitivelyButCanonicalFormStrictly() {
        val mixedDirect = GitHubExpressionSecurity.secretUses(
            "${'$'}{{ SeCrEtS.MORIMIL_RELEASE_KEY_PASSWORD }}"
        )
        assertEquals(1, mixedDirect.size)
        assertEquals("MORIMIL_RELEASE_KEY_PASSWORD", mixedDirect.single().name)
        assertFalse(mixedDirect.single().canonicalDirect)

        val upperObject = GitHubExpressionSecurity.secretUses("${'$'}{{ TOJSON(SECRETS) }}")
        assertEquals(1, upperObject.size)
        assertEquals(null, upperObject.single().name)
        assertFalse(upperObject.single().canonicalDirect)

        val mixedComputed = GitHubExpressionSecurity.secretUses(
            "${'$'}{{ SeCrEtS[format('MORIMIL_RELEASE_{0}', 'KEY_PASSWORD')] }}"
        )
        assertEquals(1, mixedComputed.size)
        assertEquals(null, mixedComputed.single().name)
        assertFalse(mixedComputed.single().canonicalDirect)

        val canonical = GitHubExpressionSecurity.secretUses(
            "${'$'}{{ secrets.MORIMIL_RELEASE_KEY_PASSWORD }}"
        )
        assertEquals(1, canonical.size)
        assertTrue(canonical.single().canonicalDirect)

        assertTrue(GitHubExpressionSecurity.secretUses("ordinary SECRETS and SeCrEtS text").isEmpty())
        assertTrue(GitHubExpressionSecurity.secretUses("${'$'}{{ 'SECRETS SeCrEtS secrets' }}").isEmpty())
        assertTrue(GitHubExpressionSecurity.secretUses("${'$'}{{ format('SeCrEtS') }}").isEmpty())
    }

    @Test
    fun yamlDoubleQuotedEscapesFailClosedBeforeExpressionAnalysis() {
        val slash = '\\'
        val escapedContexts = listOf(
            "${slash}x73ecrets",
            "${slash}u0073ecrets",
            "${slash}U00000073ecrets",
            "${slash}u007"
        )
        escapedContexts.forEach { escaped ->
            val source = "root: \"${'$'}{{ $escaped.MORIMIL_RELEASE_KEY_PASSWORD }}\"\n"
            assertTrue(
                "Governed YAML must reject double-quoted escape form: $escaped",
                runCatching { WorkflowYamlAstParser.parse(source) }.isFailure
            )
        }
    }

    @Test
    fun canonicalSecretReferenceRequiresExactPlainScalarSource() {
        val source = repositoryFile(".github/workflows/signed-release-apk.yml").readText()
        val canonical = "MORIMIL_RELEASE_KEY_PASSWORD: ${'$'}{{ secrets.MORIMIL_RELEASE_KEY_PASSWORD }}"
        val quoted = "MORIMIL_RELEASE_KEY_PASSWORD: \"${'$'}{{ secrets.MORIMIL_RELEASE_KEY_PASSWORD }}\""
        val mutated = source.replace(canonical, quoted)
        assertTrue(mutated != source)
        assertFalse(ReleaseWorkflowPolicy.validate(mutated))
        assertTrue(ReleaseWorkflowPolicy.validate(source))
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

internal data class ActionUse(
    val path: String,
    val action: String,
    val sha: String,
    val version: String
)

internal object GovernedActionInventory {
    val expected = listOf(
        ActionUse(".github/workflows/android-ci.yml", "actions/checkout", "3d3c42e5aac5ba805825da76410c181273ba90b1", "v7.0.1"),
        ActionUse(".github/workflows/android-ci.yml", "actions/setup-java", "c1e323688fd81a25caa38c78aa6df2d33d3e20d9", "v4.8.0"),
        ActionUse(".github/workflows/android-ci.yml", "actions/setup-python", "5fda3b95a4ea91299a34e894583c3862153e4b97", "v7.0.0"),
        ActionUse(".github/workflows/android-ci.yml", "actions/upload-artifact", "043fb46d1a93c77aae656e7c1c64a875d1fc6a0a", "v7.0.1"),
        ActionUse(".github/workflows/codeql.yml", "actions/checkout", "3d3c42e5aac5ba805825da76410c181273ba90b1", "v7.0.1"),
        ActionUse(".github/workflows/codeql.yml", "actions/setup-java", "d7793b545071e98d581d3bf084a51c3213318a07", "v4"),
        ActionUse(".github/workflows/codeql.yml", "gradle/actions/setup-gradle", "017a9effdb900e5b5b2fddfb590a105619dca3c3", "v4.4.2"),
        ActionUse(".github/workflows/codeql.yml", "github/codeql-action/init", "f205ea1c3313d32999d8d6a48b4f6530d4437b38", "v4"),
        ActionUse(".github/workflows/codeql.yml", "github/codeql-action/analyze", "f205ea1c3313d32999d8d6a48b4f6530d4437b38", "v4"),
        ActionUse(".github/workflows/sbom.yml", "actions/checkout", "3d3c42e5aac5ba805825da76410c181273ba90b1", "v7.0.1"),
        ActionUse(".github/workflows/sbom.yml", "anchore/sbom-action", "e22c389904149dbc22b58101806040fa8d37a610", "v0.24.0"),
        ActionUse(".github/workflows/sbom.yml", "actions/upload-artifact", "043fb46d1a93c77aae656e7c1c64a875d1fc6a0a", "v7.0.1"),
        ActionUse(".github/workflows/signed-release-apk.yml", "actions/checkout", "3d3c42e5aac5ba805825da76410c181273ba90b1", "v7.0.1"),
        ActionUse(".github/workflows/signed-release-apk.yml", "actions/setup-java", "c1e323688fd81a25caa38c78aa6df2d33d3e20d9", "v4.8.0"),
        ActionUse(".github/workflows/signed-release-apk.yml", "gradle/actions/setup-gradle", "ed408507eac070d1f99cc633dbcf757c94c7933a", "v4"),
        ActionUse(".github/workflows/signed-release-apk.yml", "actions/upload-artifact", "ea165f8d65b6e75b540449e92b4886f43607fa02", "v4.6.2"),
        ActionUse(".github/workflows/signed-release-apk.yml", "actions/download-artifact", "d3f86a106a0bac45b974a628896c90dbdf5c8093", "v4.3.0"),
        ActionUse(".github/workflows/signed-release-apk.yml", "actions/upload-artifact", "ea165f8d65b6e75b540449e92b4886f43607fa02", "v4.6.2")
    )

    private val external = Regex(
        """^([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)?)@([0-9a-f]{40})$"""
    )
    private val version = Regex("""^v?[0-9]+(?:\.[0-9]+){0,2}(?:[-+][A-Za-z0-9_.-]+)?$""")
    private val localInventory: Map<String, Set<String>> = emptyMap()

    fun validate(sources: Map<String, String>): Boolean = runCatching {
        parseAll(sources) == expected
    }.getOrDefault(false)

    fun parseAll(sources: Map<String, String>): List<ActionUse> {
        val orderedPaths = expected.map { it.path }.distinct()
        require(sources.keys == orderedPaths.toSet()) {
            "Governed workflow set is incomplete or contains an unexpected path"
        }
        return orderedPaths.flatMap { path -> parse(path, WorkflowYamlAstParser.parse(sources.getValue(path))) }
    }

    fun parse(path: String, document: YamlDocument): List<ActionUse> {
        val result = mutableListOf<ActionUse>()
        document.walkMappings { nodePath, mapping ->
            mapping.entries.forEach { (key, value) ->
                if (key != "uses") return@forEach
                val fullPath = nodePath + key
                require(fullPath.size == 5 && fullPath[0] == "jobs" && fullPath[2] == "steps") {
                    "$path:${value.startLine} uses is outside jobs.<job>.steps[*]"
                }
                val scalar = value as? YamlScalar
                    ?: error("$path:${value.startLine} uses must be a scalar")
                val actionValue = scalar.value.trim()
                require(actionValue.isNotEmpty()) { "$path:${scalar.startLine} has an empty uses value" }
                require("${'$'}{{" !in actionValue && "}}" !in actionValue) {
                    "$path:${scalar.startLine} uses a dynamic action reference"
                }
                require(!actionValue.startsWith("docker://")) {
                    "$path:${scalar.startLine} uses a Docker action, which is not governed"
                }
                if (actionValue.startsWith("./")) {
                    require(scalar.comment.isNullOrBlank()) {
                        "$path:${scalar.startLine} local action comment is unsupported"
                    }
                    require(actionValue in localInventory.getOrDefault(path, emptySet())) {
                        "$path:${scalar.startLine} local action is not explicitly governed: $actionValue"
                    }
                    return@forEach
                }
                val comment = scalar.comment?.trim().orEmpty()
                require(comment.isNotEmpty() && version.matches(comment)) {
                    "$path:${scalar.startLine} external action requires an exact version comment"
                }
                val match = external.matchEntire(actionValue)
                    ?: error("$path:${scalar.startLine} external action must use a full lowercase SHA: $actionValue")
                result += ActionUse(path, match.groupValues[1], match.groupValues[2], comment)
            }
        }
        return result
    }
}

internal enum class WorkflowMappingSyntax {
    EMPTY_FLOW,
    BLOCK
}

internal data class WorkflowMapping(
    val values: Map<String, String>,
    val syntax: WorkflowMappingSyntax
) {
    companion object {
        fun emptyFlow(): WorkflowMapping = WorkflowMapping(emptyMap(), WorkflowMappingSyntax.EMPTY_FLOW)
        fun block(values: Map<String, String>): WorkflowMapping = WorkflowMapping(values, WorkflowMappingSyntax.BLOCK)
    }
}

internal data class SecretReference(
    val name: String,
    val path: List<String>,
    val line: Int,
    val step: WorkflowStep?
)

internal data class SecretContextReference(
    val name: String?,
    val path: List<String>,
    val line: Int,
    val step: WorkflowStep?,
    val expression: String,
    val canonicalDirect: Boolean
)

internal enum class GitHubExpressionTokenKind {
    IDENTIFIER,
    STRING,
    NUMBER,
    SYMBOL
}

internal data class GitHubExpressionToken(
    val kind: GitHubExpressionTokenKind,
    val value: String
)

internal data class GitHubExpression(
    val raw: String,
    val body: String,
    val tokens: List<GitHubExpressionToken>
)

internal data class GitHubSecretUse(
    val expression: String,
    val name: String?,
    val canonicalDirect: Boolean
)

internal object GitHubExpressionSecurity {
    fun secretUses(scalarValue: String): List<GitHubSecretUse> =
        expressions(scalarValue).flatMap { expression ->
            expression.tokens.mapIndexedNotNull { index, token ->
                if (token.kind != GitHubExpressionTokenKind.IDENTIFIER ||
                    !token.value.equals("secrets", ignoreCase = true)
                ) {
                    return@mapIndexedNotNull null
                }
                val name = directSecretName(expression.tokens, index)
                val canonical = name != null &&
                    expression.tokens.size == 3 &&
                    expression.tokens[0] == GitHubExpressionToken(
                        GitHubExpressionTokenKind.IDENTIFIER,
                        "secrets"
                    ) &&
                    expression.tokens[1] == GitHubExpressionToken(
                        GitHubExpressionTokenKind.SYMBOL,
                        "."
                    ) &&
                    expression.tokens[2] == GitHubExpressionToken(
                        GitHubExpressionTokenKind.IDENTIFIER,
                        name
                    ) &&
                    expression.raw == "${'$'}{{ secrets.$name }}"
                GitHubSecretUse(expression.raw, name, canonical)
            }
        }

    fun expressions(scalarValue: String): List<GitHubExpression> {
        val result = mutableListOf<GitHubExpression>()
        var cursor = 0
        while (cursor < scalarValue.length) {
            val start = scalarValue.indexOf("${'$'}{{", cursor)
            if (start < 0) break
            var index = start + 3
            var singleQuoted = false
            var doubleQuoted = false
            var closed = false
            while (index < scalarValue.length - 1) {
                val char = scalarValue[index]
                if (singleQuoted) {
                    if (char == '\'') {
                        if (index + 1 < scalarValue.length && scalarValue[index + 1] == '\'') {
                            index += 2
                            continue
                        }
                        singleQuoted = false
                    }
                    index++
                    continue
                }
                if (doubleQuoted) {
                    if (char == '\\' && index + 1 < scalarValue.length) {
                        index += 2
                        continue
                    }
                    if (char == '"') doubleQuoted = false
                    index++
                    continue
                }
                when (char) {
                    '\'' -> singleQuoted = true
                    '"' -> doubleQuoted = true
                    '}' -> if (scalarValue[index + 1] == '}') {
                        val end = index + 2
                        val raw = scalarValue.substring(start, end)
                        val body = scalarValue.substring(start + 3, index)
                        result += GitHubExpression(raw, body, tokenize(body))
                        cursor = end
                        closed = true
                        break
                    }
                }
                index++
            }
            require(closed) { "Unterminated GitHub Actions expression" }
        }
        return result
    }

    private fun directSecretName(tokens: List<GitHubExpressionToken>, index: Int): String? {
        if (index + 2 < tokens.size &&
            tokens[index + 1] == GitHubExpressionToken(GitHubExpressionTokenKind.SYMBOL, ".") &&
            tokens[index + 2].kind == GitHubExpressionTokenKind.IDENTIFIER
        ) {
            return tokens[index + 2].value
        }
        if (index + 3 < tokens.size &&
            tokens[index + 1] == GitHubExpressionToken(GitHubExpressionTokenKind.SYMBOL, "[") &&
            tokens[index + 2].kind == GitHubExpressionTokenKind.STRING &&
            tokens[index + 3] == GitHubExpressionToken(GitHubExpressionTokenKind.SYMBOL, "]")
        ) {
            return tokens[index + 2].value
        }
        return null
    }

    private fun tokenize(body: String): List<GitHubExpressionToken> {
        val result = mutableListOf<GitHubExpressionToken>()
        var index = 0
        while (index < body.length) {
            val char = body[index]
            when {
                char.isWhitespace() -> index++
                char.isLetter() || char == '_' -> {
                    val start = index++
                    while (index < body.length &&
                        (body[index].isLetterOrDigit() || body[index] == '_' || body[index] == '-')
                    ) {
                        index++
                    }
                    result += GitHubExpressionToken(
                        GitHubExpressionTokenKind.IDENTIFIER,
                        body.substring(start, index)
                    )
                }
                char.isDigit() -> {
                    val start = index++
                    while (index < body.length &&
                        (body[index].isLetterOrDigit() || body[index] == '.' || body[index] == '_')
                    ) {
                        index++
                    }
                    result += GitHubExpressionToken(
                        GitHubExpressionTokenKind.NUMBER,
                        body.substring(start, index)
                    )
                }
                char == '\'' -> {
                    val parsed = parseQuoted(body, index, '\'')
                    result += GitHubExpressionToken(GitHubExpressionTokenKind.STRING, parsed.first)
                    index = parsed.second
                }
                char == '"' -> {
                    val parsed = parseQuoted(body, index, '"')
                    result += GitHubExpressionToken(GitHubExpressionTokenKind.STRING, parsed.first)
                    index = parsed.second
                }
                else -> {
                    result += GitHubExpressionToken(GitHubExpressionTokenKind.SYMBOL, char.toString())
                    index++
                }
            }
        }
        return result
    }

    private fun parseQuoted(source: String, start: Int, quote: Char): Pair<String, Int> {
        val value = StringBuilder()
        var index = start + 1
        while (index < source.length) {
            val char = source[index]
            if (quote == '\'' && char == '\'' && index + 1 < source.length && source[index + 1] == '\'') {
                value.append('\'')
                index += 2
                continue
            }
            if (quote == '"' && char == '\\' && index + 1 < source.length) {
                value.append(source[index + 1])
                index += 2
                continue
            }
            if (char == quote) return value.toString() to (index + 1)
            value.append(char)
            index++
        }
        error("Unterminated string literal in GitHub Actions expression")
    }
}

internal data class WorkflowStep(
    val name: String?,
    val text: String,
    val mapping: YamlMapping,
    val path: List<String>,
    val secretReferences: List<SecretReference> = emptyList(),
    val secretContextReferences: List<SecretContextReference> = emptyList()
)

internal data class WorkflowJob(
    val name: String,
    val text: String,
    val mapping: YamlMapping,
    val permissions: WorkflowMapping?,
    val env: WorkflowMapping?,
    val steps: List<WorkflowStep>,
    val runsOn: String?,
    val hasContainer: Boolean,
    val hasServices: Boolean
)

internal data class WorkflowStructure(
    val document: YamlDocument,
    val topLevelPermissions: WorkflowMapping?,
    val topLevelEnv: WorkflowMapping?,
    val jobs: Map<String, WorkflowJob>,
    val secretReferences: List<SecretReference>,
    val secretContextReferences: List<SecretContextReference>
) {
    companion object {
        fun parse(source: String): WorkflowStructure {
            val document = WorkflowYamlAstParser.parse(source)
            val root = document.root
            val jobsNode = root.entries["jobs"] as? YamlMapping
                ?: error("Workflow jobs must be a mapping")
            val jobs = linkedMapOf<String, WorkflowJob>()

            jobsNode.entries.forEach { (jobName, jobNode) ->
                val jobMapping = jobNode as? YamlMapping ?: error("Job $jobName must be a mapping")
                val stepsNode = jobMapping.entries["steps"] as? YamlSequence
                    ?: error("Job $jobName must contain a steps sequence")
                val steps = stepsNode.items.mapIndexed { index, item ->
                    val stepMapping = item as? YamlMapping
                        ?: error("jobs.$jobName.steps[$index] must be a mapping")
                    val name = (stepMapping.entries["name"] as? YamlScalar)?.value
                    WorkflowStep(
                        name = name,
                        text = document.source(item),
                        mapping = stepMapping,
                        path = listOf("jobs", jobName, "steps", index.toString())
                    )
                }.toMutableList()

                val rebuiltSteps = steps.map { step ->
                    val contextReferences = collectSecretContexts(step.mapping, step.path, step)
                    step.copy(
                        secretReferences = contextReferences.mapNotNull { reference ->
                            reference.name?.let { name ->
                                SecretReference(name, reference.path, reference.line, step)
                            }
                        },
                        secretContextReferences = contextReferences
                    )
                }
                jobs[jobName] = WorkflowJob(
                    name = jobName,
                    text = document.source(jobNode),
                    mapping = jobMapping,
                    permissions = workflowMapping(jobMapping.entries["permissions"]),
                    env = workflowMapping(jobMapping.entries["env"]),
                    steps = rebuiltSteps,
                    runsOn = (jobMapping.entries["runs-on"] as? YamlScalar)?.value,
                    hasContainer = "container" in jobMapping.entries,
                    hasServices = "services" in jobMapping.entries
                )
            }

            val stepPaths = jobs.values.flatMap { it.steps }.associateBy { it.path }
            val allContextReferences = collectSecretContexts(root, emptyList(), null).map { reference ->
                val stepPath = reference.path.take(4)
                reference.copy(step = stepPaths[stepPath])
            }
            val allNamedReferences = allContextReferences.mapNotNull { reference ->
                reference.name?.let { name ->
                    SecretReference(name, reference.path, reference.line, reference.step)
                }
            }
            return WorkflowStructure(
                document = document,
                topLevelPermissions = workflowMapping(root.entries["permissions"]),
                topLevelEnv = workflowMapping(root.entries["env"]),
                jobs = jobs,
                secretReferences = allNamedReferences,
                secretContextReferences = allContextReferences
            )
        }

        private fun collectSecretContexts(
            node: YamlNode,
            path: List<String>,
            step: WorkflowStep?
        ): List<SecretContextReference> {
            val result = mutableListOf<SecretContextReference>()
            when (node) {
                is YamlScalar -> GitHubExpressionSecurity.secretUses(node.value).forEach { use ->
                    result += SecretContextReference(
                        name = use.name,
                        path = path,
                        line = node.startLine,
                        step = step,
                        expression = use.expression,
                        canonicalDirect = use.canonicalDirect && node.raw == use.expression
                    )
                }
                is YamlMapping -> node.entries.forEach { (key, value) ->
                    result += collectSecretContexts(value, path + key, step)
                }
                is YamlSequence -> node.items.forEachIndexed { index, value ->
                    result += collectSecretContexts(value, path + index.toString(), step)
                }
            }
            return result
        }

        private fun workflowMapping(node: YamlNode?): WorkflowMapping? {
            if (node == null) return null
            val mapping = node as? YamlMapping ?: error("Governed mapping must be YAML mapping")
            val values = linkedMapOf<String, String>()
            mapping.entries.forEach { (key, value) ->
                val scalar = value as? YamlScalar ?: error("Governed mapping values must be scalar")
                require(values.put(key, scalar.value) == null) { "Duplicate governed mapping key: $key" }
            }
            return if (mapping.style == YamlMappingStyle.FLOW && values.isEmpty()) {
                WorkflowMapping.emptyFlow()
            } else {
                WorkflowMapping.block(values)
            }
        }
    }
}

internal enum class YamlMappingStyle {
    BLOCK,
    FLOW
}

internal sealed class YamlNode(
    open val startLine: Int,
    open val endLine: Int
)

internal data class YamlScalar(
    val value: String,
    val raw: String,
    val comment: String?,
    override val startLine: Int,
    override val endLine: Int = startLine
) : YamlNode(startLine, endLine)

internal data class YamlMapping(
    val entries: LinkedHashMap<String, YamlNode>,
    val style: YamlMappingStyle,
    override val startLine: Int,
    override val endLine: Int
) : YamlNode(startLine, endLine)

internal data class YamlSequence(
    val items: List<YamlNode>,
    override val startLine: Int,
    override val endLine: Int
) : YamlNode(startLine, endLine)

internal data class YamlDocument(
    val root: YamlMapping,
    val lines: List<String>
) {
    fun source(node: YamlNode): String =
        lines.subList(node.startLine - 1, node.endLine).joinToString("\n")

    fun walkMappings(visitor: (List<String>, YamlMapping) -> Unit) {
        fun walk(node: YamlNode, path: List<String>) {
            when (node) {
                is YamlScalar -> Unit
                is YamlMapping -> {
                    visitor(path, node)
                    node.entries.forEach { (key, value) -> walk(value, path + key) }
                }
                is YamlSequence -> node.items.forEachIndexed { index, value ->
                    walk(value, path + index.toString())
                }
            }
        }
        walk(root, emptyList())
    }
}

/**
 * Semantic YAML AST parser for the GitHub workflow surface governed by these tests.
 * It parses mappings, sequences, quoted keys, flow mappings and block scalars, and
 * rejects duplicate keys or indentation that cannot be represented unambiguously.
 */
internal object WorkflowYamlAstParser {
    fun parse(source: String): YamlDocument {
        require('\t' !in source) { "Tabs are not accepted in governed workflow YAML" }
        val normalized = source.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.lines()
        val parser = Parser(lines)
        val first = parser.nextSignificant(0)
        require(first < lines.size) { "YAML document is empty" }
        require(parser.indent(lines[first]) == 0) { "YAML document must begin at root indentation" }
        val parsed = parser.parseBlock(first, 0)
        require(parser.nextSignificant(parsed.nextIndex) >= lines.size) {
            "Trailing YAML content was not parsed"
        }
        val root = parsed.node as? YamlMapping ?: error("Workflow root must be a mapping")
        return YamlDocument(root, lines)
    }

    private data class Parsed(val node: YamlNode, val nextIndex: Int)
    private data class EntryHead(val key: String, val remainder: String)
    private data class Inline(val value: String, val comment: String?)

    private class Parser(private val lines: List<String>) {
        fun parseBlock(startIndex: Int, expectedIndent: Int): Parsed {
            val start = nextSignificant(startIndex)
            require(start < lines.size) { "Expected YAML node" }
            require(indent(lines[start]) == expectedIndent) {
                "Unexpected indentation at line ${start + 1}"
            }
            val content = lines[start].substring(expectedIndent)
            return if (isSequenceLine(content)) {
                parseSequence(start, expectedIndent)
            } else {
                parseMapping(start, expectedIndent)
            }
        }

        private fun parseMapping(startIndex: Int, expectedIndent: Int): Parsed {
            val entries = linkedMapOf<String, YamlNode>()
            var index = startIndex
            var lastLine = startIndex + 1
            while (true) {
                index = nextSignificant(index)
                if (index >= lines.size) break
                val currentIndent = indent(lines[index])
                if (currentIndent < expectedIndent) break
                require(currentIndent == expectedIndent) {
                    "Unexpected nested YAML at line ${index + 1}"
                }
                val content = lines[index].substring(expectedIndent)
                require(!isSequenceLine(content)) {
                    "Cannot mix mapping and sequence entries at line ${index + 1}"
                }
                val head = parseEntryHead(content, index + 1)
                require(head.key !in entries) { "Duplicate YAML key '${head.key}' at line ${index + 1}" }
                val value = parseEntryValue(head.remainder, index, expectedIndent)
                entries[head.key] = value.node
                index = value.nextIndex
                lastLine = maxOf(lastLine, value.node.endLine)
            }
            return Parsed(
                YamlMapping(entries, YamlMappingStyle.BLOCK, startIndex + 1, lastLine),
                index
            )
        }

        private fun parseSequence(startIndex: Int, expectedIndent: Int): Parsed {
            val items = mutableListOf<YamlNode>()
            var index = startIndex
            var lastLine = startIndex + 1
            while (true) {
                index = nextSignificant(index)
                if (index >= lines.size) break
                val currentIndent = indent(lines[index])
                if (currentIndent < expectedIndent) break
                require(currentIndent == expectedIndent) {
                    "Unexpected sequence indentation at line ${index + 1}"
                }
                val content = lines[index].substring(expectedIndent)
                if (!isSequenceLine(content)) break
                val remainder = content.removePrefix("-").trimStart()
                require(remainder.isNotEmpty()) { "Empty sequence item at line ${index + 1}" }

                val parsedItem = when {
                    remainder.startsWith("{") -> {
                        val inline = splitInlineComment(remainder)
                        val mapping = parseFlowMapping(inline.value, index + 1)
                        require(inline.comment.isNullOrBlank()) {
                            "Flow mapping sequence comments are not governed at line ${index + 1}"
                        }
                        val next = nextSignificant(index + 1)
                        require(next >= lines.size || indent(lines[next]) <= expectedIndent) {
                            "Flow mapping sequence item cannot have block continuation at line ${index + 1}"
                        }
                        Parsed(mapping, index + 1)
                    }
                    hasMappingColon(remainder) -> parseSequenceMappingItem(remainder, index, expectedIndent)
                    else -> {
                        val scalar = parseInlineScalar(remainder, index + 1)
                        Parsed(scalar, index + 1)
                    }
                }
                items += parsedItem.node
                index = parsedItem.nextIndex
                lastLine = maxOf(lastLine, parsedItem.node.endLine)
            }
            return Parsed(YamlSequence(items, startIndex + 1, lastLine), index)
        }

        private fun parseSequenceMappingItem(
            remainder: String,
            lineIndex: Int,
            sequenceIndent: Int
        ): Parsed {
            val entries = linkedMapOf<String, YamlNode>()
            val head = parseEntryHead(remainder, lineIndex + 1)
            val first = parseEntryValue(head.remainder, lineIndex, sequenceIndent + 2)
            entries[head.key] = first.node
            var next = first.nextIndex
            var endLine = maxOf(lineIndex + 1, first.node.endLine)

            val continuation = nextSignificant(next)
            if (continuation < lines.size && indent(lines[continuation]) == sequenceIndent + 2) {
                val mapping = parseMapping(continuation, sequenceIndent + 2)
                mapping.node as YamlMapping
                mapping.node.entries.forEach { (key, value) ->
                    require(key !in entries) { "Duplicate sequence mapping key '$key'" }
                    entries[key] = value
                }
                next = mapping.nextIndex
                endLine = maxOf(endLine, mapping.node.endLine)
            }
            return Parsed(
                YamlMapping(entries, YamlMappingStyle.BLOCK, lineIndex + 1, endLine),
                next
            )
        }

        private fun parseEntryValue(remainder: String, lineIndex: Int, parentIndent: Int): Parsed {
            val inline = splitInlineComment(remainder)
            val value = inline.value.trim()
            if (value.isEmpty()) {
                val child = nextSignificant(lineIndex + 1)
                return if (child < lines.size && indent(lines[child]) > parentIndent) {
                    require(indent(lines[child]) == parentIndent + 2) {
                        "Nested YAML must use two-space indentation at line ${child + 1}"
                    }
                    parseBlock(child, parentIndent + 2)
                } else {
                    Parsed(YamlScalar("", "", inline.comment, lineIndex + 1), lineIndex + 1)
                }
            }
            if (value == "|" || value == ">" || value.startsWith("|-") || value.startsWith(">-")) {
                return parseBlockScalar(lineIndex, parentIndent, value)
            }
            val node = when {
                value.startsWith("{") -> parseFlowMapping(value, lineIndex + 1)
                value.startsWith("[") -> parseFlowSequence(value, lineIndex + 1)
                else -> parseInlineScalar(remainder, lineIndex + 1)
            }
            return Parsed(node, lineIndex + 1)
        }

        private fun parseBlockScalar(lineIndex: Int, parentIndent: Int, marker: String): Parsed {
            var index = lineIndex + 1
            var last = lineIndex
            val contentLines = mutableListOf<String>()
            var contentIndent: Int? = null
            while (index < lines.size) {
                val line = lines[index]
                if (line.isNotBlank() && indent(line) <= parentIndent) break
                if (line.isNotBlank()) {
                    val lineIndent = indent(line)
                    if (contentIndent == null) contentIndent = lineIndent
                    require(lineIndent >= requireNotNull(contentIndent)) {
                        "Block scalar indentation decreased at line ${index + 1}"
                    }
                    contentLines += line.substring(requireNotNull(contentIndent))
                } else {
                    contentLines += ""
                }
                last = index
                index++
            }
            require(last >= lineIndex + 1) { "Block scalar at line ${lineIndex + 1} is empty" }
            return Parsed(
                YamlScalar(
                    value = contentLines.joinToString("\n"),
                    raw = marker,
                    comment = null,
                    startLine = lineIndex + 1,
                    endLine = last + 1
                ),
                index
            )
        }

        private fun parseFlowMapping(token: String, lineNumber: Int): YamlMapping {
            val inline = splitInlineComment(token)
            val value = inline.value.trim()
            require(value.startsWith("{") && value.endsWith("}")) {
                "Malformed flow mapping at line $lineNumber"
            }
            val body = value.substring(1, value.length - 1).trim()
            val entries = linkedMapOf<String, YamlNode>()
            if (body.isNotEmpty()) {
                splitTopLevel(body, ',').forEach { part ->
                    val head = parseEntryHead(part.trim(), lineNumber)
                    require(head.remainder.isNotBlank()) {
                        "Flow mapping values must be explicit at line $lineNumber"
                    }
                    require(head.key !in entries) { "Duplicate flow mapping key '${head.key}'" }
                    entries[head.key] = parseInlineScalar(head.remainder, lineNumber)
                }
            }
            return YamlMapping(entries, YamlMappingStyle.FLOW, lineNumber, lineNumber)
        }

        private fun parseFlowSequence(token: String, lineNumber: Int): YamlSequence {
            val inline = splitInlineComment(token)
            val value = inline.value.trim()
            require(value.startsWith("[") && value.endsWith("]")) {
                "Malformed flow sequence at line $lineNumber"
            }
            val body = value.substring(1, value.length - 1).trim()
            val items = if (body.isEmpty()) emptyList() else splitTopLevel(body, ',').map { part ->
                parseInlineScalar(part.trim(), lineNumber)
            }
            return YamlSequence(items, lineNumber, lineNumber)
        }

        private fun parseInlineScalar(token: String, lineNumber: Int): YamlScalar {
            val inline = splitInlineComment(token)
            val raw = inline.value.trim()
            require(raw.isNotEmpty()) { "Empty scalar at line $lineNumber" }
            return YamlScalar(
                value = unquote(raw),
                raw = raw,
                comment = inline.comment,
                startLine = lineNumber
            )
        }

        private fun parseEntryHead(content: String, lineNumber: Int): EntryHead {
            val colon = findTopLevel(content, ':')
            require(colon >= 0) { "Expected mapping entry at line $lineNumber" }
            val rawKey = content.substring(0, colon).trim()
            require(rawKey.isNotEmpty()) { "Empty YAML key at line $lineNumber" }
            val key = unquote(rawKey)
            require(key.isNotEmpty()) { "Empty YAML key at line $lineNumber" }
            return EntryHead(key, content.substring(colon + 1).trimStart())
        }

        private fun hasMappingColon(content: String): Boolean = findTopLevel(content, ':') >= 0

        private fun isSequenceLine(content: String): Boolean =
            content == "-" || content.startsWith("- ")

        fun nextSignificant(start: Int): Int {
            var index = start
            while (index < lines.size) {
                val trimmed = lines[index].trimStart()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) return index
                index++
            }
            return index
        }

        fun indent(line: String): Int = line.indexOfFirst { !it.isWhitespace() }
            .let { if (it < 0) line.length else it }

        private fun splitInlineComment(token: String): Inline {
            var single = false
            var double = false
            var escaped = false
            var braces = 0
            var brackets = 0
            token.forEachIndexed { index, char ->
                if (escaped) {
                    escaped = false
                    return@forEachIndexed
                }
                if (double && char == '\\') {
                    escaped = true
                    return@forEachIndexed
                }
                if (!double && char == '\'') single = !single
                else if (!single && char == '"') double = !double
                else if (!single && !double) {
                    when (char) {
                        '{' -> braces++
                        '}' -> braces--
                        '[' -> brackets++
                        ']' -> brackets--
                        '#' -> if (braces == 0 && brackets == 0 &&
                            (index == 0 || token[index - 1].isWhitespace())
                        ) {
                            return Inline(
                                token.substring(0, index).trimEnd(),
                                token.substring(index + 1).trim()
                            )
                        }
                    }
                }
            }
            require(!single && !double && braces == 0 && brackets == 0) {
                "Unbalanced inline YAML token: $token"
            }
            return Inline(token.trimEnd(), null)
        }

        private fun findTopLevel(token: String, target: Char): Int {
            var single = false
            var double = false
            var escaped = false
            var braces = 0
            var brackets = 0
            token.forEachIndexed { index, char ->
                if (escaped) {
                    escaped = false
                    return@forEachIndexed
                }
                if (double && char == '\\') {
                    escaped = true
                    return@forEachIndexed
                }
                if (!double && char == '\'') single = !single
                else if (!single && char == '"') double = !double
                else if (!single && !double) {
                    if (char == target && braces == 0 && brackets == 0) return index
                    when (char) {
                        '{' -> braces++
                        '}' -> braces--
                        '[' -> brackets++
                        ']' -> brackets--
                    }
                }
            }
            return -1
        }

        private fun splitTopLevel(token: String, separator: Char): List<String> {
            val result = mutableListOf<String>()
            var start = 0
            var single = false
            var double = false
            var escaped = false
            var braces = 0
            var brackets = 0
            token.forEachIndexed { index, char ->
                if (escaped) {
                    escaped = false
                    return@forEachIndexed
                }
                if (double && char == '\\') {
                    escaped = true
                    return@forEachIndexed
                }
                if (!double && char == '\'') single = !single
                else if (!single && char == '"') double = !double
                else if (!single && !double) {
                    when (char) {
                        '{' -> braces++
                        '}' -> braces--
                        '[' -> brackets++
                        ']' -> brackets--
                        separator -> if (braces == 0 && brackets == 0) {
                            result += token.substring(start, index)
                            start = index + 1
                        }
                    }
                }
            }
            result += token.substring(start)
            return result
        }

        private fun unquote(value: String): String {
            if (value.length < 2) return value
            return when {
                value.first() == '\'' && value.last() == '\'' ->
                    value.substring(1, value.length - 1).replace("''", "'")
                value.first() == '"' && value.last() == '"' -> {
                    val body = value.substring(1, value.length - 1)
                    require('\\' !in body) {
                        "Escapes are not accepted in governed double-quoted YAML scalars"
                    }
                    body
                }
                else -> value
            }
        }
    }
}
