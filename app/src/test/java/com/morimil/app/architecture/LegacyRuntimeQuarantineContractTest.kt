package com.morimil.app.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyRuntimeQuarantineContractTest {
    private data class LegacySymbolRule(
        val symbol: String,
        val allowedProductionPaths: Set<String>
    )

    @Test
    fun legacyBirthAndIdentitySymbolsCannotEscapeQuarantine() {
        val productionRoot = productionSourceRoot()
        val productionSources = productionRoot
            .walkTopDown()
            .filter { file ->
                file.isFile && file.extension in setOf("kt", "java")
            }
            .toList()

        val rules = listOf(
            LegacySymbolRule(
                symbol = "birthLocalIdentity",
                allowedProductionPaths = setOf(
                    "com/morimil/app/data/repository/MemoryRepository.kt"
                )
            ),
            LegacySymbolRule(
                symbol = "installGenesisBundle",
                allowedProductionPaths = setOf(
                    "com/morimil/app/data/genesis/GenesisReader.kt"
                )
            ),
            LegacySymbolRule(
                symbol = "insertLocalIdentity",
                allowedProductionPaths = setOf(
                    "com/morimil/app/data/local/MemoryDao.kt",
                    "com/morimil/app/data/repository/MemoryRepository.kt"
                )
            ),
            LegacySymbolRule(
                symbol = "insertGenesisCore",
                allowedProductionPaths = setOf(
                    "com/morimil/app/data/local/MemoryDao.kt",
                    "com/morimil/app/data/repository/MemoryRepository.kt"
                )
            )
        )

        val violations = buildList {
            rules.forEach { rule ->
                productionSources
                    .asSequence()
                    .filter { source -> source.readText().contains(rule.symbol) }
                    .map { source -> source.relativeTo(productionRoot).invariantSeparatorsPath }
                    .filterNot(rule.allowedProductionPaths::contains)
                    .forEach { path ->
                        add("${rule.symbol} escaped legacy quarantine into $path")
                    }
            }
        }

        assertTrue(
            "Legacy runtime symbols must remain confined until removal:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    private fun productionSourceRoot(): File {
        return sequenceOf(
            File("src/main/java"),
            File("app/src/main/java")
        ).firstOrNull(File::isDirectory)
            ?: error("Production source root not found")
    }
}
