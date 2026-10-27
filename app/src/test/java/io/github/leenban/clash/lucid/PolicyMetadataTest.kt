package io.github.leenban.clash.lucid

import io.github.leenban.clash.core.model.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class PolicyMetadataTest {
    @Test fun iconUrlAndLegacyGroupDefaults() {
        assertEquals("https://example.test/a.svg", remotePolicyIcon(" https://example.test/a.svg "))
        for (url in listOf("", "file:///private/key", "content://images/1", "https://user:pass@example.test/a", "garbage"))
            assertNull(remotePolicyIcon(url))
        val old = DiagnosticJson.format.decodeFromString(ProxyGroup.serializer(), """{"type":"select","now":"DIRECT","proxies":[]}""")
        assertEquals("", old.icon)
        val configured = DiagnosticJson.format.decodeFromString(ProxyGroup.serializer(), """{"type":"select","now":"DIRECT","proxies":[],"icon":"https://example.test/a.svg"}""")
        assertEquals("https://example.test/a.svg", configured.icon)
    }

    @Test fun exitSubtitleKeepsIpWhenIspMissing() {
        assertEquals("203.0.113.8\n运营商未知 · AS64500", NodeExitInfo("203.0.113.8", asn = "AS64500").subtitle())
    }

    @Test fun connectionApplicationUsesLabelPackageThenUid() {
        assertEquals("Browser", ConnectionInfo("1", appName = "Browser", process = "example.browser", uid = 10123).applicationTitle())
        assertEquals("example.browser", ConnectionInfo("1", process = "example.browser").applicationTitle())
        assertEquals("UID 10123", ConnectionInfo("1", uid = 10123).applicationTitle())
        assertEquals("未知应用", ConnectionInfo("1").applicationTitle())
    }

    @Test fun batchLimitsConcurrencySkipsGroupsAndCanRetryAndCancel() = runBlocking {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        var fail = true
        var block = false
        val lookup = NodeExitLookup(this) { name ->
            val count = active.incrementAndGet()
            peak.updateAndGet { maxOf(it, count) }
            try {
                if (block) awaitCancellation()
                delay(15)
                if (fail && name == "1") error("fixture failure")
                NodeExitInfo("203.0.113.$name", "Fixture ISP")
            } finally { active.decrementAndGet() }
        }
        val nodes = (1..7).map { Proxy("$it", "$it", "", "Http", 0, false) } +
            Proxy("nested", "nested", "", "Selector", 0, true)
        lookup.start(nodes)
        withTimeout(5000) { while (lookup.running) delay(5) }
        assertEquals(7, lookup.completed)
        assertTrue(peak.get() <= 3)
        assertNull(lookup.subtitles["nested"])
        assertTrue(lookup.subtitles.getValue("1").contains("fixture failure"))
        assertTrue(lookup.subtitles.getValue("2").contains("203.0.113.2"))
        fail = false
        lookup.start(nodes)
        withTimeout(5000) { while (lookup.running) delay(5) }
        assertTrue(lookup.subtitles.getValue("1").contains("203.0.113.1"))
        block = true
        lookup.start(nodes)
        yield()
        lookup.cancel()
        withTimeout(5000) { while (lookup.running) delay(5) }
        assertTrue(lookup.subtitles.values.all { it == "落地检测已取消" })
    }
}
