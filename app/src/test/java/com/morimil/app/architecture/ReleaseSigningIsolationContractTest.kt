package com.morimil.app.architecture

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
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
        val mutated = workflow.replaceFirst(
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
            "          set -euo pipefail\n          chmod +x ./gradlew",
            "          set -euo pipefail\n" +
                "          printf '%s\\n' 'ordinary secrets text' >/dev/null\n" +
                "          chmod +x ./gradlew"
        )
        assertTrue("ordinaryText rejected", ReleaseWorkflowPolicy.validate(ordinaryText))

        val stringLiteral = workflow.replace(
            "      - name: Build unsigned release input\n        shell: bash",
            "      - name: Build unsigned release input\n" +
                "        env:\n" +
                "          SAFE_LITERAL: ${'$'}{{ format('secrets') }}\n" +
                "        shell: bash"
        )
        assertTrue("stringLiteral rejected", ReleaseWorkflowPolicy.validate(stringLiteral))
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
            "          set -euo pipefail\n          chmod +x ./gradlew",
            "          set -euo pipefail\n" +
                "          printf '%s\\n' 'ordinary secrets SECRETS SeCrEtS text' >/dev/null\n" +
                "          chmod +x ./gradlew"
        )
        assertTrue("ordinaryText rejected", ReleaseWorkflowPolicy.validate(ordinaryText))

        val stringLiteral = workflow.replace(
            "      - name: Build unsigned release input\n        shell: bash",
            "      - name: Build unsigned release input\n" +
                "        env:\n" +
                "          SAFE_LITERAL: ${'$'}{{ format('SECRETS SeCrEtS secrets') }}\n" +
                "        shell: bash"
        )
        assertTrue("stringLiteral rejected", ReleaseWorkflowPolicy.validate(stringLiteral))
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



    @Test
    fun criticalRunBodiesAndExactSigningStepInventoryAreSealed() {
        assertTrue(ReleaseWorkflowPolicy.validate(workflow))
        val structure = WorkflowStructure.parse(workflow)
        assertTrue(
            structure.jobs.getValue("sign-release").steps.map { it.name } ==
                ReleaseWorkflowPolicy.expectedSignStepNames
        )
    }

    @Test
    fun signedApkReplacementAfterClosedInventoryIsRejected() {
        val terminal = "          [[ \"${'$'}(wc -l < \"${'$'}actual_inventory\")\" -eq 4 ]]"
        val mutated = workflow.replace(
            terminal,
            terminal + "\n          cp \"${'$'}unsigned_apk\" release-output/app-release.apk"
        )
        assertTrue(mutated != workflow)
        assertTrue("cp \"${'$'}unsigned_apk\" release-output/app-release.apk" in mutated)
        assertFalse(ReleaseWorkflowPolicy.validate(mutated))
    }

    @Test
    fun unsignedApkMutationAfterInitialDigestIsRejected() {
        val boundary =
            "          if [[ \"${'$'}actual_unsigned_sha256\" != \"${'$'}EXPECTED_UNSIGNED_SHA256\" ]]; then\n" +
                "            echo \"Unsigned APK digest does not match the build job output.\" >&2\n" +
                "            exit 1\n" +
                "          fi"
        val mutation = "          printf 'tampered' >> \"${'$'}unsigned_apk\""
        val mutated = workflow.replace(boundary, boundary + "\n" + mutation)
        assertTrue(mutated != workflow)
        assertTrue(mutation in mutated)
        assertFalse(ReleaseWorkflowPolicy.validate(mutated))
    }

    @Test
    fun bashLineContinuationCannotHideGradleInsideSigningJob() {
        val anchor = "          set -euo pipefail\n          sdkmanager_path="
        val mutation = "          ./gra\\\n          dlew maliciousTask\n"
        val mutated = workflow.replace(anchor, "          set -euo pipefail\n" + mutation + "          sdkmanager_path=")
        assertTrue(mutated != workflow)
        assertTrue("./gra\\\n          dlew maliciousTask" in mutated)
        assertFalse(ReleaseWorkflowPolicy.validate(mutated))
    }

    @Test
    fun finalGradleModelMutationAfterSafeBlockIsRejected() {
        val mutation =
            "\nandroid.buildTypes.getByName(\"releaseUnsigned\") {\n" +
                "    isDebuggable = true\n" +
                "}\n"
        val mutated = gradle + mutation
        assertTrue(mutated != gradle)
        assertTrue(mutation.trim() in mutated)
        assertFalse(ReleaseWorkflowPolicy.gradleBoundaryIsSafe(mutated))
        assertTrue(ReleaseWorkflowPolicy.gradleBoundaryIsSafe(gradle))
    }

    @Test
    fun constructedTaskNamesCannotDisableVerifierOrInjectAssembleDebug() {
        val mutation = """
            val verifierTaskName = listOf("verifyReleaseUnsigned", "Boundary").joinToString("")
            tasks.named(verifierTaskName).configure {
                enabled = false
            }
            val debugTaskName = listOf("assemble", "Debug").joinToString("")
            tasks.named("assembleUnsignedReleaseForSigning").configure {
                dependsOn(debugTaskName)
            }
        """.trimIndent()
        val mutated = gradle + "\n" + mutation + "\n"

        assertTrue(ReleaseWorkflowPolicy.gradleBoundaryIsSafe(gradle))
        assertTrue(mutated != gradle)
        assertTrue(mutation in mutated)
        assertFalse("verifyReleaseUnsignedBoundary" in mutation)
        assertFalse("assembleDebug" in mutation)
        assertFalse(ReleaseWorkflowPolicy.gradleBoundaryIsSafe(mutated))
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

    val expectedSignStepNames = listOf(
        "Install deterministic Android build tools",
        "Download exact unsigned release input",
        "Verify unsigned release input digest",
        "Sign, verify, and close final artifact inventory",
        "Upload verified signed APK"
    )

    private val expectedRunSha256 = mapOf(
        "Install deterministic Android build tools" to
            "7f82921dbf5b99fcc26a09f9e1426555f715d88ce45b968175a63f74c3327ff5",
        "Verify unsigned release input digest" to
            "4d33f1a6f0ecca0445a06c6b370bdb12250dd6dd8977bb552b28a608f5d0bcda",
        "Sign, verify, and close final artifact inventory" to
            "2a0c78c6bca2046f89d7836436c7581c85fdefabe667bb64ee97207be23370a8"
    )

    private const val expectedNormalizedGradleSha256 =
        "f6ed90f22c7a6256db04c4f81f3b17992bb4bc3a33bda1a2bac76f727ecb3d27"

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
        if (structure.document.root.entries.keys.intersect(
                setOf("container", "services", "defaults", "environment")
            ).isNotEmpty()
        ) return false
        if (structure.jobs.values.any {
                it.hasContainer || it.hasServices || it.hasDefaults || it.hasEnvironment
            }
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
        if (build.steps.size != 7) return false
        if (build.steps.none { step -> step.runText()?.contains(":app:assembleUnsignedReleaseForSigning") == true }) {
            return false
        }
        if (build.steps.none { it.usesValue()?.startsWith("actions/checkout@") == true }) return false
        if ("name: morimil-unsigned-release-apk" !in build.text) return false
        if ("path: unsigned-output/app-release-unsigned.apk" !in build.text) return false

        if (sign.runsOn != "ubuntu-latest") return false
        if (sign.steps.map { it.name } != expectedSignStepNames) return false
        if (sign.steps.size != expectedSignStepNames.size) return false

        val install = sign.steps[0]
        val download = sign.steps[1]
        val verify = sign.steps[2]
        val signing = sign.steps[3]
        val upload = sign.steps[4]

        if (install.mapping.entries.keys != setOf("name", "shell", "run")) return false
        if (verify.mapping.entries.keys != setOf("name", "shell", "env", "run")) return false
        if (signing.mapping.entries.keys != setOf("name", "shell", "env", "run")) return false
        if (download.mapping.entries.keys != setOf("name", "uses", "with")) return false
        if (upload.mapping.entries.keys != setOf("name", "uses", "with")) return false
        if (listOf(install, verify, signing).any { it.scalar("shell") != "bash" }) return false

        expectedRunSha256.forEach { (stepName, expectedHash) ->
            val step = sign.steps.singleOrNull { it.name == stepName } ?: return false
            val run = step.runText() ?: return false
            if (sha256(run) != expectedHash) return false
        }

        if (download.usesValue() !=
            "actions/download-artifact@d3f86a106a0bac45b974a628896c90dbdf5c8093"
        ) return false
        if (!download.mappingValueEquals(
                "with",
                mapOf(
                    "name" to "morimil-unsigned-release-apk",
                    "path" to "unsigned-input"
                )
            )
        ) return false

        val expectedDigestExpression =
            "${'$'}{{ needs.build-unsigned-release.outputs.unsigned_apk_sha256 }}"
        if (!verify.mappingValueEquals(
                "env",
                mapOf("EXPECTED_UNSIGNED_SHA256" to expectedDigestExpression),
                requirePlainSource = true
            )
        ) return false

        val signingEnv = signing.mapping.entries["env"] as? YamlMapping ?: return false
        if (signingEnv.entries.keys != releaseSecretNames + "EXPECTED_UNSIGNED_SHA256") return false
        val expectedDigest = signingEnv.entries["EXPECTED_UNSIGNED_SHA256"] as? YamlScalar ?: return false
        if (expectedDigest.value != expectedDigestExpression || expectedDigest.raw != expectedDigestExpression) {
            return false
        }
        releaseSecretNames.forEach { name ->
            val scalar = signingEnv.entries[name] as? YamlScalar ?: return false
            val canonical = "${'$'}{{ secrets.$name }}"
            if (scalar.value != canonical || scalar.raw != canonical) return false
        }

        val contextReferences = structure.secretContextReferences
        if (contextReferences.size != 5) return false
        if (contextReferences.mapNotNull { it.name }.toSet() != releaseSecretNames) return false
        if (contextReferences.any { !it.canonicalDirect }) return false
        val signingIndex = sign.steps.indexOf(signing).toString()
        if (contextReferences.any { reference ->
                val name = reference.name ?: return false
                reference.path != listOf(
                    "jobs", "sign-release", "steps", signingIndex, "env", name
                ) || reference.expression != "${'$'}{{ secrets.$name }}"
            }
        ) return false
        if (contextReferences.any { it.step?.name != signing.name }) return false

        val namedReferences = structure.secretReferences
        if (namedReferences.size != 5 || namedReferences.map { it.name }.toSet() != releaseSecretNames) {
            return false
        }

        if (upload.usesValue() !=
            "actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02"
        ) return false
        val uploadWith = upload.mapping.entries["with"] as? YamlMapping ?: return false
        if (uploadWith.entries.keys != setOf("name", "path", "if-no-files-found", "retention-days")) {
            return false
        }
        if ((uploadWith.entries["name"] as? YamlScalar)?.value !=
            "morimil-signed-release-${'$'}{{ github.sha }}"
        ) return false
        if ((uploadWith.entries["if-no-files-found"] as? YamlScalar)?.value != "error") return false
        if ((uploadWith.entries["retention-days"] as? YamlScalar)?.value != "30") return false
        val uploadPath = uploadWith.entries["path"] as? YamlScalar ?: return false
        if ("*" in uploadPath.value) return false
        val uploaded = uploadPath.value.lines().map { it.trim() }.filter { it.isNotEmpty() }
            .map { line ->
                if (!line.startsWith("release-output/")) return false
                line.removePrefix("release-output/")
            }.toSet()
        if (uploaded != expectedFinalFiles || uploadPath.value.lines().count { it.isNotBlank() } != 4) {
            return false
        }
        return true
    }

    fun gradleBoundaryIsSafe(source: String): Boolean =
        sha256(source) == expectedNormalizedGradleSha256

    fun finalInventoryIsExact(root: File): Boolean {
        val entries = root.listFiles()?.toList() ?: return false
        if (entries.any { Files.isSymbolicLink(it.toPath()) }) return false
        if (entries.any { !it.isFile }) return false
        return entries.map { it.name }.toSet() == expectedFinalFiles && entries.size == expectedFinalFiles.size
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.replace("\r\n", "\n").replace('\r', '\n').toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun WorkflowStep.runText(): String? =
        (mapping.entries["run"] as? YamlScalar)?.value

    private fun WorkflowStep.usesValue(): String? =
        (mapping.entries["uses"] as? YamlScalar)?.value

    private fun WorkflowStep.scalar(key: String): String? =
        (mapping.entries[key] as? YamlScalar)?.value

    private fun WorkflowStep.mappingValueEquals(
        key: String,
        expected: Map<String, String>,
        requirePlainSource: Boolean = false
    ): Boolean {
        val nested = mapping.entries[key] as? YamlMapping ?: return false
        if (nested.entries.keys != expected.keys) return false
        return expected.all { (entryKey, expectedValue) ->
            val scalar = nested.entries[entryKey] as? YamlScalar ?: return false
            scalar.value == expectedValue && (!requirePlainSource || scalar.raw == expectedValue)
        }
    }

    private fun List<String>.startsWithPath(vararg prefix: String): Boolean =
        size >= prefix.size && prefix.indices.all { index -> this[index] == prefix[index] }
}
