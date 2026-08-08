package com.morimil.app.architecture

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class BodyDataTransferSovereigntyContractTest {
    @Test
    fun manifestBindsFailClosedBackupAndD2dRules() {
        val document = parse(repositoryFile("app/src/main/AndroidManifest.xml"), namespaceAware = true)
        val applications = document.getElementsByTagName("application")
        assertEquals(1, applications.length)
        val application = applications.item(0) as Element

        assertEquals("false", application.getAttributeNS(ANDROID_NAMESPACE, "allowBackup"))
        assertEquals(
            "@xml/morimil_full_backup_content",
            application.getAttributeNS(ANDROID_NAMESPACE, "fullBackupContent")
        )
        assertEquals(
            "@xml/morimil_data_extraction_rules",
            application.getAttributeNS(ANDROID_NAMESPACE, "dataExtractionRules")
        )
        assertFalse(application.hasAttributeNS(ANDROID_NAMESPACE, "backupAgent"))
    }

    @Test
    fun releaseSourceSetsCannotOverrideTheTransferBoundary() {
        val sourceRoot = repositoryFile("app/src")
        val manifests = sourceRoot
            .walkTopDown()
            .filter { file -> file.isFile && file.name == "AndroidManifest.xml" }
            .filterNot { file -> file.invariantSeparatorsPath.endsWith("/main/AndroidManifest.xml") }
            .toList()

        manifests.forEach { manifest ->
            val applicationNodes = parse(manifest, namespaceAware = true).getElementsByTagName("application")
            for (index in 0 until applicationNodes.length) {
                val application = applicationNodes.item(index) as Element
                listOf(
                    "allowBackup",
                    "fullBackupContent",
                    "dataExtractionRules",
                    "backupAgent"
                ).forEach { attribute ->
                    assertFalse(
                        "${manifest.invariantSeparatorsPath} must not override android:$attribute",
                        application.hasAttributeNS(ANDROID_NAMESPACE, attribute)
                    )
                }
            }
        }
    }

    @Test
    fun android12AndLaterRulesDenyCloudAndDeviceTransferForEveryDomain() {
        val document = parse(repositoryFile(DATA_EXTRACTION_RULES))
        assertEquals("data-extraction-rules", document.documentElement.tagName)

        assertDenyAll(document.documentElement, "cloud-backup")
        assertDenyAll(document.documentElement, "device-transfer")
    }

    @Test
    fun android11AndEarlierRulesDenyEveryBackupDomain() {
        val document = parse(repositoryFile(FULL_BACKUP_CONTENT))
        val root = document.documentElement
        assertEquals("full-backup-content", root.tagName)
        assertEquals(0, root.getElementsByTagName("include").length)
        assertExactExclusions(root)
    }

    @Test
    fun currentPolicyRejectsOsTransferAsBodySuccessionAuthority() {
        val policy = repositoryFile("docs/security/BODY_DATA_TRANSFER_SOVEREIGNTY.md").readText()

        assertTrue(policy.contains("# Document status: CURRENT"))
        assertTrue(policy.contains("OS_MANAGED_D2D_TRANSFER=DENIED"))
        assertTrue(policy.contains("OS_MANAGED_TRANSFER_IS_BODY_SUCCESSION_AUTHORITY=FALSE"))
        assertTrue(policy.contains("F5_SOVEREIGN_SUCCESSION_PROTOCOL=REQUIRED"))
        assertTrue(policy.contains("MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED"))
    }

    private fun assertDenyAll(root: Element, sectionName: String) {
        val sections = root.getElementsByTagName(sectionName)
        assertEquals("Expected exactly one <$sectionName> section", 1, sections.length)
        val section = sections.item(0) as Element
        assertEquals(
            "<$sectionName> must not contain include rules",
            0,
            section.getElementsByTagName("include").length
        )
        assertExactExclusions(section)
    }

    private fun assertExactExclusions(section: Element) {
        val excludes = section.getElementsByTagName("exclude")
        assertEquals(EXPECTED_DOMAINS.size, excludes.length)

        val actual = buildSet {
            for (index in 0 until excludes.length) {
                val element = excludes.item(index) as Element
                add(element.getAttribute("domain") to element.getAttribute("path"))
            }
        }
        val expected = EXPECTED_DOMAINS.map { domain -> domain to "." }.toSet()
        assertEquals(expected, actual)
    }

    private fun parse(file: File, namespaceAware: Boolean = false): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = namespaceAware
        }
        return factory.newDocumentBuilder().parse(file)
    }

    private fun repositoryFile(relativePath: String): File {
        return sequenceOf(File(relativePath), File("../$relativePath"))
            .firstOrNull { candidate -> candidate.exists() }
            ?: error("Repository path not found: $relativePath")
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val DATA_EXTRACTION_RULES = "app/src/main/res/xml/morimil_data_extraction_rules.xml"
        const val FULL_BACKUP_CONTENT = "app/src/main/res/xml/morimil_full_backup_content.xml"

        val EXPECTED_DOMAINS = setOf(
            "root",
            "file",
            "database",
            "sharedpref",
            "external",
            "device_root",
            "device_file",
            "device_database",
            "device_sharedpref"
        )
    }
}
