package com.example.dimanow.widget

import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetReceiverManifestTest {
    @Test
    fun `widget providers are not exported to arbitrary applications`() {
        val manifest = appModuleRoot().resolve("src/main/AndroidManifest.xml")
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifest.toFile())
        val expected = setOf(
            ".widget.ShuttleWidgetProvider",
            ".widget.MealWidgetProvider",
            ".widget.CampusSummaryWidgetProvider",
        )
        val exported = buildMap {
            val receivers = document.getElementsByTagName("receiver")
            for (index in 0 until receivers.length) {
                val receiver = receivers.item(index)
                val name = receiver.attributes.getNamedItemNS(ANDROID_NAMESPACE, "name")?.nodeValue
                if (name in expected) {
                    put(name, receiver.attributes.getNamedItemNS(ANDROID_NAMESPACE, "exported")?.nodeValue)
                }
            }
        }

        assertEquals(expected.associateWith { "false" }, exported)
    }

    private fun appModuleRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (!Files.exists(current.resolve("src/main/AndroidManifest.xml"))) {
            current = current.parent ?: error("app 모듈을 찾지 못했습니다")
        }
        return current
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
