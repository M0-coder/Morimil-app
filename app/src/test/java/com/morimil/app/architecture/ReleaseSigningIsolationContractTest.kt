package com.morimil.app.architecture

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSigningIsolationContractTest {
    private val workflow: String
        get() = repositoryFile(".github/workflows/signed-release-apk.yml").readText()
    private val gradle: String
        get() = repositoryFile("app/build.gradle.kts").readText()

    @Test
    fun buildAndSigningJobsRemainStronglySeparated() {
        assertTrue(ReleaseWorkflowPolicy.validate(workflow))
    }

    @Test
    fun normalReleaseRemainsFailClosedWhileUnsignedInputIsExplicitAndAlwaysFresh() {
        assertTrue(ReleaseWorkflowPolicy.gradleBoundaryIsSafe(gradle))
        assertFalse(":app:assembleDebug" in workflow)
        assertTrue(":app:assembleUnsignedReleaseForSigning" in workflow)
        assertTrue("outputs.upToDateWhen { false }" in gradle)
    }

    @Test
    fun secretMovedToAnotherStepIsRejected() {
        val mutated = workflow.replace(
            "      - name: Verify unsigned release input digest\n        shell: bash",
            "      - name: Verify unsigned release input digest\n        env:\n          LEAK: ${'$'}{{ secrets.MORIMIL_RELEASE_KEY_PASSWORD }}\n        shell: bash"
        )
        assertFalse(ReleaseWorkflowPolicy.validate(mutated))
    }

    @Test
    fun unnamedRunStepReceivingMovedSecretIsRejectedWithGlobalCountStillFive() {
        val removed = workflow.replace(
            "          MORIMIL_RELEASE_KEY_PASSWORD: ${'$'}{{ secrets.MORIMIL_RELEASE_KEY_PASSWORD }}\n",
            ""
        )
        val mutated = removed.replace(
            "      - name: Sign, verify, and close final artifact inventory",
            "      - run: echo blocked >/dev/null\n" +
                "        env:\n" +
                "          LEAK: ${'$'}{{ secrets.MORIMIL_RELEASE_KEY_PASSWORD }}\n\n" +
                "      - name: Sign, verify, and close final artifact inventory"
        )
        assertTrue(mutated.split("secrets.MORIMIL_RELEASE_").size - 1 == 5)
        assertFalse(ReleaseWorkflowPolicy.validate(mutated))
    }

    @Test
    fun bracketSecretNotationOutsideAuthorizedStepIsRejected() {
        val mutated = workflow.replace(
            "        env:\n          EXPECTED_UNSIGNED_SHA256:",
            "        env:\n          LEAK: ${'$'}{{ secrets['MORIMIL_RELEASE_KEY_PASSWORD'] }}\n          EXPECTED_UNSIGNED_SHA256:"
        )
        assertFalse(ReleaseWorkflowPolicy.validate(mutated))
    }

    @Test
    fun wholeSecretsObjectInsideAnotherStepIsRejectedWhileNamedCountRemainsFive() {
        val mutated = workflow.replace(
            "        env:\n          EXPECTED_UNSIGNED_SHA256:",
            "        env:\n          LEAK: ${'$'}{{ toJSON(secrets) }}\n          EXPECTED_UNSIGNED_SHA256:"
        )
        val structure = WorkflowStructure.parse(mutated)
        assertTrue(structure.secretReferences.size == 5)
        assertTrue(structure.secretContextReferences.size == 6)
        assertFalse(ReleaseWorkflowPolicy.validate(mutated))
    }

    @Test
    fun nestedFunctionAndComputedSecretsAccessAreRejected() {
        val nested = workflow.replace(
            "        env:\n          EXPECTED_UNSIGNED_SHA256:",
            "        env:\n          LEAK: ${'$'}{{ fromJSON(toJSON(secrets)) }}\n          EXPECTED_UNSIGNED_SHA256:"
        )
        assertFalse(ReleaseWorkflowPolicy.validate(nested))

        val computed = workflow.replace(
            "        env:\n          EXPECTED_UNSIGNED_SHA256:",
            "        env:\n          LEAK: ${'$'}{{ secrets[format('MORIMIL_RELEASE_{0}', 'KEY_PASSWORD')] }}\n" +
                "          EXPECTED_UNSIGNED_SHA256:"
        )
        assertFalse(ReleaseWorkflowPolicy.validate(computed))
    }

    @Test
    fun multilineSecretsContextExpressionIsRejected() {
        val mutated = workflow.replace(
            "        env:\n          EXPECTED_UNSIGNED_SHA256:",
            "        env:\n" +
                "          LEAK: >-\n" +
                "            ${'$'}{{\n" +
                "              toJSON(\n" +
                "                secrets\n" +
                "              )\n" +
                "            }}\n" +
                "          EXPECTED_UNSIGNED_SHA256:"
        )
        assertFalse(ReleaseWorkflowPolicy.validate(mutated))
    }

    @Test
    fun ordinarySecretsTextAndExpressionStringLiteralsDoNotCauseFalsePositives() {
        val ordinaryText = workflow.replace(
            "          set -euo pipefail\n          unsigned_apk=\"unsigned-input/app-release-unsigned.apk\"",
            "          set -euo pipefail\n" +
                "          printf '%s\\n' 'ordinary secrets text' >/dev/null\n" +
                "          unsigned_apk=\"unsigned-input/app-release-unsigned.apk\""
        )
        assertTrue(ReleaseWorkflowPolicy.validate(ordinaryText))

        val stringLiteral = workflow.replace(
            "        env:\n          EXPECTED_UNSIGNED_SHA256:",
            "        env:\n          SAFE_LITERAL: ${'$'}{{ format('secrets') }}\n          EXPECTED_UNSIGNED_SHA256:"
        )
        assertTrue(ReleaseWorkflowPolicy.validate(stringLiteral))
    }

    @Test
    fun mixedCaseSecretsContextOutsideAuthorizedStepIsRejectedWithCanonicalCountStillFive() {
        val mutated = workflow.replace(
            "        env:\n          EXPECTED_UNSIGNED_SHA256:",
            "        env:\n" +
                "          LEAK: ${'$'}{{ SeCrEtS.MORIMIL_RELEASE_KEY_PASSWORD }}\n" +
                "          EXPECTED_UNSIGNED_SHA256:"
        )
        assertTrue(mutated.split("${'$'}{{ secrets.MORIMIL_RELEASE_").size - 1 == 5)
        assertFalse(ReleaseWorkflowPolicy.validate(mutated))
    }

    @Test
    fun uppercaseObjectAndMixedComputedSecretsContextsAreRejected() {
        val upperObject = workflow.replace(
            "        env:\n          EXPECTED_UNSIGNED_SHA256:",
            "        env:\n          LEAK: ${'$'}{{ TOJSON(SECRETS) }}\n          EXPECTED_UNSIGNED_SHA256:"
        )
        assertFalse(ReleaseWorkflowPolicy.validate(upperObject))

        val mixedComputed = workflow.replace(
            "        env:\n          EXPECTED_UNSIGNED_SHA256:",
            "        env:\n" +
                "          LEAK: ${'$'}{{ SeCrEtS[format('MORIMIL_RELEASE_{0}', 'KEY_PASSWORD')] }}\n" +
                "          EXPECTED_UNSIGNED_SHA256:"
        )
        assertFalse(ReleaseWorkflowPolicy.validate(mixedComputed))
    }

    @Test
    fun mixedCaseContextCannotReplaceCanonicalAuthorizedExpression() {
        val mutated = workflow.replace(
            "${'$'}{{ secrets.MORIMIL_RELEASE_KEY_PASSWORD }}",
            "${'$'}{{ SeCrEtS.MORIMIL_RELEASE_KEY_PASSWORD }}"
        )
        assertTrue(mutated.split("${'$'}{{ secrets.MORIMIL_RELEASE_").size - 1 == 4)
        assertFalse(ReleaseWorkflowPolicy.validate(mutated))
        assertTrue(ReleaseWorkflowPolicy.validate(workflow))
    }

    @Test
    fun caseVariantsInOrdinaryTextAndExpressionStringLiteralsRemainSafe() {
        val ordinaryText = workflow.replace(
            "          set -euo pipefail\n          unsigned_apk=\"unsigned-input/app-release-unsigned.apk\"",
            "          set -euo pipefail\n" +
                "          printf '%s\\n' 'ordinary secrets SECRETS SeCrEtS text' >/dev/null\n" +
                "          unsigned_apk=\"unsigned-input/app-release-unsigned.apk\""
        )
        assertTrue(ReleaseWorkflowPolicy.validate(ordinaryText))

        val stringLiteral = workflow.replace(
            "        env:\n          EXPECTED_UNSIGNED_SHA256:",
            "        env:\n" +
                "          SAFE_LITERAL: ${'$'}{{ format('SECRETS SeCrEtS secrets') }}\n" +
                "          EXPECTED_UNSIGNED_SHA256:"
        )
        assertTrue(ReleaseWorkflowPolicy.validate(stringLiteral))
    }

    @Test
    fun escapedSecretsContextsOutsideAuthorizedStepAreRejectedWithFivePlainReferencesRemaining() {
        val slash = '\\'
        listOf(
            "${slash}x73ecrets",
            "${slash}u0073ecrets",
            "${slash}U00000073ecrets"
        ).forEach { escaped ->
            val mutated = workflow.replace(
                "        env:\n          EXPECTED_UNSIGNED_SHA256:",
                "        env:\n" +
                    "          LEAK: \"${'$'}{{ $escaped.MORIMIL_RELEASE_KEY_PASSWORD }}\"\n" +
                    "          EXPECTED_UNSIGNED_SHA256:"
            )
            assertTrue(mutated.split("${'$'}{{ secrets.MORIMIL_RELEASE_").size - 1 == 5)
            assertFalse(ReleaseWorkflowPolicy.validate(mutated))
        }
    }

    @Test
    fun escapedContextCannotReplaceCanonicalAuthorizedScalar() {
        val slash = '\\'
        val canonical =
            "          MORIMIL_RELEASE_KEY_PASSWORD: ${'$'}{{ secrets.MORIMIL_RELEASE_KEY_PASSWORD }}"
        val escaped =
            "          MORIMIL_RELEASE_KEY_PASSWORD: \"${'$'}{{ ${slash}u0073ecrets.MORIMIL_RELEASE_KEY_PASSWORD }}\""
        val mutated = workflow.replace(canonical, escaped)
        assertTrue(mutated != workflow)
        assertTrue(mutated.split("${'$'}{{ secrets.MORIMIL_RELEASE_").size - 1 == 4)
        assertFalse(ReleaseWorkflowPolicy.validate(mutated))
    }

    @Test
    fun invalidOrTruncatedYamlEscapeFailsClosed() {
        val slash = '\\'
        val mutated = workflow.replace(
            "        env:\n          EXPECTED_UNSIGNED_SHA256:",
            "        env:\n" +
                "          LEAK: \"${'$'}{{ ${slash}u007.MORIMIL_RELEASE_KEY_PASSWORD }}\"\n" +
                "          EXPECTED_UNSIGNED_SHA256:"
        )
        assertFalse(ReleaseWorkflowPolicy.validate(mutated))
    }

    @Test
    fun ordinaryEscapedTextIsRejectedByTheStrictGovernedYamlSubset() {
        val slash = '\\'
        val mutated = workflow.replace(
            "        env:\n          EXPECTED_UNSIGNED_SHA256:",
            "        env:\n" +
                "          SAFE_TEXT: \"ordinary ${slash}u0073ecrets text\"\n" +
                "          EXPECTED_UNSIGNED_SHA256:"
        )
        assertFalse(ReleaseWorkflowPolicy.validate(mutated))
        assertTrue(ReleaseWorkflowPolicy.validate(workflow))
    }

    @Test
    fun secondJobWithAReleaseSecretIsRejected() {
        val mutated = workflow.replace(
            "  build-unsigned-release:\n    runs-on: ubuntu-latest",
            "  build-unsigned-release:\n    env:\n      LEAK: ${'$'}{{ secrets.MORIMIL_RELEASE_KEY_ALIAS }}\n    runs-on: ubuntu-latest"
        )
        assertFalse(ReleaseWorkflowPolicy.validate(mutated))
    }

    @Test
    fun checkoutOrGradleInsideSigningJobIsRejected() {
        val checkoutMutation = workflow.replace(
            "    steps:\n      - name: Install deterministic Android build tools",
            "    steps:\n      - name: Forbidden checkout\n        uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1\n\n      - name: Install deterministic Android build tools",
            ignoreCase = false
        )
        assertFalse(ReleaseWorkflowPolicy.validate(checkoutMutation))

        val gradleMutation = workflow.replace(
            "      - name: Install deterministic Android build tools\n        shell: bash\n        run: |",
            "      - name: Install deterministic Android build tools\n        shell: bash\n        run: |\n          ./gradlew maliciousTask"
        )
        assertFalse(ReleaseWorkflowPolicy.validate(gradleMutation))
    }

    @Test
    fun containerServicesAndUngovernedSigningRunnerAreRejected() {
        val containerMutation = workflow.replace(
            "  sign-release:\n    needs: build-unsigned-release",
            "  sign-release:\n    container: ubuntu:latest\n    needs: build-unsigned-release"
        )
        assertFalse(ReleaseWorkflowPolicy.validate(containerMutation))

        val servicesMutation = workflow.replace(
            "  sign-release:\n    needs: build-unsigned-release",
            "  sign-release:\n    services:\n      helper:\n        image: alpine:latest\n    needs: build-unsigned-release"
        )
        assertFalse(ReleaseWorkflowPolicy.validate(servicesMutation))

        val runnerMutation = workflow.replace(
            "  sign-release:\n    needs: build-unsigned-release\n    runs-on: ubuntu-latest",
            "  sign-release:\n    needs: build-unsigned-release\n    runs-on: windows-latest"
        )
        assertFalse(ReleaseWorkflowPolicy.validate(runnerMutation))
    }

    @Test
    fun globUploadIsRejected() {
        val explicit = """          path: |
            release-output/app-release.apk
            release-output/app-release.apk.sha256
            release-output/app-release-signature.txt
            release-output/release-signing-manifest.txt"""
        val mutated = workflow.replace(explicit, "          path: release-output/**")
        assertFalse(ReleaseWorkflowPolicy.validate(mutated))
    }

    @Test
    fun fifthFileAndSymlinkAreRejectedByInventoryPolicy() {
        val root = Files.createTempDirectory("morimil-release-inventory").toFile()
        try {
            ReleaseWorkflowPolicy.expectedFinalFiles.forEach { name -> File(root, name).writeText(name) }
            assertTrue(ReleaseWorkflowPolicy.finalInventoryIsExact(root))
            File(root, "unexpected.txt").writeText("blocked")
            assertFalse(ReleaseWorkflowPolicy.finalInventoryIsExact(root))
            File(root, "unexpected.txt").delete()

            val link = File(root, "linked.apk").toPath()
            try {
                Files.createSymbolicLink(link, File(root, "app-release.apk").toPath())
                assertFalse(ReleaseWorkflowPolicy.finalInventoryIsExact(root))
            } catch (_: UnsupportedOperationException) {
                assertTrue("find release-output -mindepth 1 -type l" in workflow)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun weakeningUnsignedVariantReleaseGateOrFreshStagingIsRejected() {
        assertFalse(
            ReleaseWorkflowPolicy.gradleBoundaryIsSafe(
                gradle.replace("isDebuggable = false", "isDebuggable = true")
            )
        )
        assertFalse(
            ReleaseWorkflowPolicy.gradleBoundaryIsSafe(
                gradle.replace("dependsOn(validateReleaseSigning)", "// gate removed")
            )
        )
        assertFalse(
            ReleaseWorkflowPolicy.gradleBoundaryIsSafe(
                gradle + "\ntasks.register(\"fallback\") { dependsOn(\"assembleDebug\") }\n"
            )
        )
        assertFalse(
            ReleaseWorkflowPolicy.gradleBoundaryIsSafe(
                gradle.replace("    outputs.upToDateWhen { false }\n", "")
            )
        )
        assertFalse(
            ReleaseWorkflowPolicy.gradleBoundaryIsSafe(
                gradle.replace("outputs.upToDateWhen { false }", "outputs.upToDateWhen { true }")
            )
        )
    }

    private fun repositoryFile(path: String): File = File(repositoryRoot(), path)

    private fun repositoryRoot(): File {
        return sequenceOf(File("."), File(".."))
            .map(File::getCanonicalFile)
            .firstOrNull { candidate ->
                File(candidate, "README.md").isFile && File(candidate, "app/build.gradle.kts").isFile
            }
            ?: error("Repository root not found")
    }
}

internal object ReleaseWorkflowPolicy {
    val releaseSecretNames = setOf(
        "MORIMIL_RELEASE_KEYSTORE_BASE64",
        "MORIMIL_RELEASE_STORE_PASSWORD",
        "MORIMIL_RELEASE_KEY_ALIAS",
        "MORIMIL_RELEASE_KEY_PASSWORD",
        "MORIMIL_RELEASE_CERT_SHA256"
    )

    val expectedFinalFiles = setOf(
        "app-release.apk",
        "app-release.apk.sha256",
        "app-release-signature.txt",
        "release-signing-manifest.txt"
    )

    fun validate(source: String): Boolean = runCatching {
        validateOrThrow(source)
    }.getOrDefault(false)

    private fun validateOrThrow(source: String): Boolean {
        val structure = WorkflowStructure.parse(source)
        val build = structure.jobs["build-unsigned-release"] ?: return false
        val sign = structure.jobs["sign-release"] ?: return false
        if (structure.jobs.keys != setOf("build-unsigned-release", "sign-release")) return false
        if (structure.topLevelPermissions != WorkflowMapping.emptyFlow()) return false
        if (build.permissions != WorkflowMapping.block(mapOf("contents" to "read"))) return false
        if (sign.permissions != WorkflowMapping.emptyFlow()) return false
        if (structure.topLevelEnv != null || structure.jobs.values.any { it.env != null }) return false
        if (listOfNotNull(structure.topLevelPermissions, build.permissions, sign.permissions)
                .flatMap { it.values.values }
                .any { it == "write" || it == "write-all" }
        ) return false

        val releaseUses = GovernedActionInventory.parse(
            ".github/workflows/signed-release-apk.yml",
            structure.document
        )
        if (releaseUses != GovernedActionInventory.expected.filter {
                it.path == ".github/workflows/signed-release-apk.yml"
            }
        ) return false

        if (structure.secretContextReferences.any {
                it.path.startsWithPath("jobs", "build-unsigned-release")
            }
        ) return false
        if (build.steps.none { step -> step.runText()?.contains("./gradlew") == true }) return false
        if (build.steps.none { step -> step.runText()?.contains(":app:assembleUnsignedReleaseForSigning") == true }) return false
        if (build.steps.none { it.usesValue()?.startsWith("actions/checkout@") == true }) return false
        if ("name: morimil-unsigned-release-apk" !in build.text) return false
        if ("path: unsigned-output/app-release-unsigned.apk" !in build.text) return false

        if (sign.runsOn != "ubuntu-latest" || sign.hasContainer || sign.hasServices) return false
        if (sign.steps.any { it.usesValue()?.startsWith("actions/checkout@") == true }) return false
        if (sign.steps.any { it.usesValue()?.startsWith("gradle/actions/") == true }) return false
        if (sign.steps.mapNotNull { it.runText() }.any { run ->
                "./gradlew" in run || Regex("(?i)(^|\\s)gradle(\\s|$)").containsMatchIn(run)
            }
        ) return false
        if (sign.steps.none {
                it.usesValue() ==
                    "actions/download-artifact@d3f86a106a0bac45b974a628896c90dbdf5c8093"
            }
        ) return false
        if ("name: morimil-unsigned-release-apk" !in sign.text || "path: unsigned-input" !in sign.text) return false
        if ("needs.build-unsigned-release.outputs.unsigned_apk_sha256" !in sign.text) return false
        if ("actual_unsigned_sha256" !in sign.text || "Unsigned APK digest does not match" !in sign.text) return false

        val signingStep = sign.steps.singleOrNull {
            it.name == "Sign, verify, and close final artifact inventory"
        } ?: return false
        val signingIndex = sign.steps.indexOf(signingStep).toString()
        val signingEnv = signingStep.mapping.entries["env"] as? YamlMapping ?: return false
        if (signingEnv.entries.keys != releaseSecretNames) return false
        releaseSecretNames.forEach { name ->
            val scalar = signingEnv.entries[name] as? YamlScalar ?: return false
            val canonical = "${'$'}{{ secrets.$name }}"
            if (scalar.value != canonical || scalar.raw != canonical) return false
        }

        val contextReferences = structure.secretContextReferences
        if (contextReferences.size != 5) return false
        if (contextReferences.mapNotNull { it.name }.toSet() != releaseSecretNames) return false
        if (contextReferences.any { !it.canonicalDirect }) return false
        if (contextReferences.any { reference ->
                val name = reference.name ?: return false
                reference.path != listOf(
                    "jobs", "sign-release", "steps", signingIndex, "env", name
                ) || reference.expression != "${'$'}{{ secrets.$name }}"
            }
        ) return false
        if (contextReferences.any { it.step?.name != signingStep.name }) return false

        val namedReferences = structure.secretReferences
        if (namedReferences.size != 5 || namedReferences.map { it.name }.toSet() != releaseSecretNames) {
            return false
        }

        val signingRun = signingStep.runText() ?: return false
        if ("\"${'$'}apksigner\" sign" !in signingRun) return false
        if ("trap cleanup EXIT" !in signingRun || "${'$'}RUNNER_TEMP/morimil-release.jks" !in signingRun) return false
        if ("chmod 600 \"${'$'}keystore\"" !in signingRun || "umask 077" !in signingRun) return false
        if ("rm -rf release-output" !in signingRun || "install -d -m 0700 release-output" !in signingRun) return false
        if ("find release-output -mindepth 1 -type l" !in signingRun) return false
        if ("wc -l < \"${'$'}actual_inventory\"" !in signingRun) return false

        val upload = sign.steps.singleOrNull { it.name == "Upload verified signed APK" } ?: return false
        val with = upload.mapping.entries["with"] as? YamlMapping ?: return false
        val uploadPath = with.entries["path"] as? YamlScalar ?: return false
        if ("*" in uploadPath.value) return false
        val uploaded = uploadPath.value.lines().map { it.trim() }.filter { it.isNotEmpty() }
            .map { line ->
                if (!line.startsWith("release-output/")) return false
                line.removePrefix("release-output/")
            }.toSet()
        if (uploaded != expectedFinalFiles || uploadPath.value.lines().filter { it.isNotBlank() }.size != 4) return false
        return true
    }

    fun gradleBoundaryIsSafe(source: String): Boolean {
        val releaseBlock = Regex(
            """getByName\("release"\)\s*\{[\s\S]*?signingConfig\s*=\s*signingConfigs\.getByName\("release"\)[\s\S]*?\}"""
        ).containsMatchIn(source)
        val unsignedBlock = Regex(
            """create\("releaseUnsigned"\)\s*\{[\s\S]*?initWith\(getByName\("release"\)\)[\s\S]*?signingConfig\s*=\s*null[\s\S]*?isDebuggable\s*=\s*false[\s\S]*?matchingFallbacks\s*\+=\s*listOf\("release"\)[\s\S]*?\}"""
        ).containsMatchIn(source)
        val releaseGate = Regex(
            """tasks\.matching\s*\{\s*task\s*->\s*task\.name\s*==\s*"preReleaseBuild"\s*\}[\s\S]*?dependsOn\(validateReleaseSigning\)"""
        ).containsMatchIn(source)
        val explicitTask = Regex(
            """tasks\.register\("assembleUnsignedReleaseForSigning"\)[\s\S]*?dependsOn\("assembleReleaseUnsigned"\)[\s\S]*?outputs\.file\(isolatedUnsignedApk\)[\s\S]*?outputs\.upToDateWhen\s*\{\s*false\s*\}"""
        ).containsMatchIn(source)
        return releaseBlock && unsignedBlock && releaseGate && explicitTask && "assembleDebug" !in source
    }

    fun finalInventoryIsExact(root: File): Boolean {
        val entries = root.listFiles()?.toList() ?: return false
        if (entries.any { Files.isSymbolicLink(it.toPath()) }) return false
        if (entries.any { !it.isFile }) return false
        return entries.map { it.name }.toSet() == expectedFinalFiles && entries.size == expectedFinalFiles.size
    }

    private fun WorkflowStep.runText(): String? =
        (mapping.entries["run"] as? YamlScalar)?.value

    private fun WorkflowStep.usesValue(): String? =
        (mapping.entries["uses"] as? YamlScalar)?.value

    private fun List<String>.startsWithPath(vararg prefix: String): Boolean =
        size >= prefix.size && prefix.indices.all { index -> this[index] == prefix[index] }
}
