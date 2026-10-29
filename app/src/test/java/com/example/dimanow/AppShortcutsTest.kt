package com.example.dimanow

import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

// D-128(2): static shortcuts carry a literal targetPackage (resource XML gets no ${applicationId}), so this pins
// it to the built applicationId; the tile service must stay bindable only by the system.
class AppShortcutsTest {
    private val root = appModuleRoot()

    @Test
    fun `every shortcut opens MainActivity of this application with a routed page`() {
        val shortcuts = parse("src/main/res/xml/shortcuts.xml")
        val intents = shortcuts.elements("intent")

        assertEquals(4, intents.size)
        intents.forEach { intent ->
            assertEquals(BuildConfig.APPLICATION_ID, intent.android("targetPackage"))
            assertEquals(MainActivity::class.java.name, intent.android("targetClass"))
            assertEquals("android.intent.action.VIEW", intent.android("action"))
        }
        val pages = intents.map { intent ->
            val extra = (intent.getElementsByTagName("extra").item(0) as Element)
            assertEquals("TARGET_PAGE", extra.android("name"))
            extra.android("value")
        }
        assertEquals(listOf("SHUTTLE", "MEAL", "OUTING", "LIBRARY_CARD"), pages)
    }

    @Test
    fun `shortcut short labels fit the launcher limit`() {
        val strings = parse("src/main/res/values/strings.xml").elements("string")
            .associate { it.getAttribute("name") to it.textContent }
        parse("src/main/res/xml/shortcuts.xml").elements("shortcut").forEach { shortcut ->
            val short = strings.getValue(shortcut.android("shortcutShortLabel").removePrefix("@string/"))
            val long = strings.getValue(shortcut.android("shortcutLongLabel").removePrefix("@string/"))
            assertTrue(short, short.length <= 10)
            assertTrue(long, long.length <= 25)
        }
    }

    @Test
    fun `the launcher activity declares the shortcuts and the tile service is system-bound`() {
        val manifest = parse("src/main/AndroidManifest.xml")
        val main = manifest.elements("activity").single { it.android("name") == ".MainActivity" }
        val shortcutMeta = main.elements("meta-data").single { it.android("name") == "android.app.shortcuts" }
        assertEquals("@xml/shortcuts", shortcutMeta.android("resource"))

        val tile = manifest.elements("service").single { it.android("name") == ".widget.ShuttleTileService" }
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", tile.android("permission"))
        assertEquals(
            listOf("android.service.quicksettings.action.QS_TILE"),
            tile.elements("action").map { it.android("name") },
        )
    }

    private fun parse(relative: String): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(root.resolve(relative).toFile())

    private fun Document.elements(tag: String): List<Element> = documentElement.elements(tag)

    private fun Element.elements(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.android(attribute: String): String = getAttributeNS(ANDROID_NAMESPACE, attribute)

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
