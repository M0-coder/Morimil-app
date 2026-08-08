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
    fun currentRuntimeContractRejectsOsTransferAsBodySuccessionAuthority() {
        val contract = repositoryFile("docs/CURRENT_RUNTIME_CONTRACT.md").readText()

        assertTrue(contract.contains("Android 12+ device-to-device transfer", ignoreCase = true))
        assertTrue(contract.contains("F5 sovereign succession protocol", ignoreCase = true))
        assertTrue(contract.contains("OS-managed transfer is not Body succession authority", ignoreCase = true))
    }

    private fun assertDenyAll(root: Element, sectionName: String) {
        val sections = root.getElementsByTagName(sectionName)
        assertEquals("Expected exactly one <$sectionName> section", 1, sections.length)
        val section = sections.item(0) as Element
        assertEquals("<$sectionName> must not contain include rules", 0, section.getElementsByTagName("include").length)
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
            .firstOrNull(File::isFile)
            ?: error("Repository file not found: $relativePath")
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
