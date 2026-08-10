package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisProtectedMainExecutionContractTest {
    @Test
    fun genesisValidationRunsForPrProtectedMainPushAndManualDispatch() {
        val source = repositoryFile(".github/workflows/genesis-body-ci.yml").readText()
        val triggers = source.substringBefore("permissions:")

        assertTrue(triggers.contains("pull_request:\n    branches: [main]"))
        assertTrue(triggers.contains("push:\n    branches:\n      - main\n"))
        assertTrue(triggers.contains("workflow_dispatch:"))
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
