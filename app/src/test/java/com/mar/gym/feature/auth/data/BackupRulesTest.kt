package com.mar.gym.feature.auth.data

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupRulesTest {
    @Test
    fun encryptedSessionDirectoryIsExcludedFromLegacyCloudAndDeviceTransfer() {
        val legacy = parseResource("backup_rules.xml")
        val extraction = parseResource("data_extraction_rules.xml")

        val legacyExcludes = legacy.getElementsByTagName("exclude")
        assertEquals(1, legacyExcludes.length)
        assertSessionDirectory(legacyExcludes.item(0))

        val cloud = extraction.getElementsByTagName("cloud-backup").item(0)
        val device = extraction.getElementsByTagName("device-transfer").item(0)
        assertSessionDirectory(cloud.childNodes.onlyExclude())
        assertSessionDirectory(device.childNodes.onlyExclude())
    }

    private fun parseResource(name: String) = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(resourceFile(name))

    private fun resourceFile(name: String): File = sequenceOf(
        File("src/main/res/xml", name),
        File("app/src/main/res/xml", name),
    ).first(File::isFile)

    private fun org.w3c.dom.NodeList.onlyExclude(): org.w3c.dom.Node {
        val excludes = (0 until length)
            .map(::item)
            .filter { it.nodeName == "exclude" }
        assertEquals(1, excludes.size)
        return excludes.single()
    }

    private fun assertSessionDirectory(node: org.w3c.dom.Node) {
        assertEquals("file", node.attributes.getNamedItem("domain").nodeValue)
        assertEquals(
            "${AtomicSessionFileStorage.SESSION_DIRECTORY}/",
            node.attributes.getNamedItem("path").nodeValue,
        )
    }
}
