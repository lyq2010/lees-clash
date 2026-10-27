package io.github.leenban.clash.lucid

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.util.startClashService
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ExternalResourceVerificationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun manualUpdateDirectWhenStoppedAndRoutedWhenRunning(): Unit = runBlocking {
        val vm = ViewModelProvider(compose.activity)[ClientViewModel::class.java]
        val repo = vm.repository
        val original = repo.active()
        val override = repo.overrideText()
        val demand = repo.service.onDemandEnabled
        val content = ServerSocket(0)
        val proxy = ServerSocket(0)
        val routed = AtomicInteger()
        val downloaded = AtomicInteger()
        val config = """
            proxies: [{name: Fetch, type: http, server: 127.0.0.1, port: ${proxy.localPort}}]
            proxy-providers:
              External:
                type: http
                url: http://127.0.0.1:${content.localPort}/nodes
                path: external.yaml
                interval: 86400
                proxy: REJECT
            proxy-groups: [{name: Resources, type: select, proxies: [DIRECT], use: [External]}]
            rules: ["IP-CIDR,127.0.0.1/32,Fetch,no-resolve", "MATCH,DIRECT"]
        """.trimIndent()
        thread(isDaemon = true) {
            while (!content.isClosed) runCatching {
                val socket = content.accept()
                thread(isDaemon = true) { runCatching { socket.use {
                    val reader = it.getInputStream().bufferedReader()
                    val request = reader.readLine().orEmpty()
                    while (!reader.readLine().isNullOrEmpty()) {}
                    if (!request.contains("/config")) downloaded.incrementAndGet()
                    val data = (if (request.contains("/config")) config else "proxies: [{name: Provider-Direct, type: direct}]").toByteArray()
                    it.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: ${data.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    it.getOutputStream().write(data)
                } } }
            }
        }
        thread(isDaemon = true) {
            while (!proxy.isClosed) runCatching {
                val socket = proxy.accept()
                thread(isDaemon = true) { runCatching { socket.use {
                    val reader = it.getInputStream().bufferedReader()
                    val request = reader.readLine().orEmpty()
                    while (!reader.readLine().isNullOrEmpty()) {}
                    check(request.startsWith("CONNECT "))
                    routed.incrementAndGet()
                    Socket("127.0.0.1", content.localPort).use { upstream ->
                        it.getOutputStream().write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray())
                        val client = it
                        thread(isDaemon = true) { runCatching { client.getInputStream().copyTo(upstream.getOutputStream()) } }
                        upstream.getInputStream().copyTo(it.getOutputStream())
                    }
                } } }
            }
        }
        try {
            repo.restoreSelection(compose.activity, original)
            repo.service.onDemandEnabled = false
            repo.saveOverride("{}")
            val id = repo.importUrl("External resource fixture", "http://127.0.0.1:${content.localPort}/config", null)
            repo.select(repo.profiles().first { it.uuid == id })
            compose.activityRule.scenario.onActivity { vm.changed(); vm.openRoute("providers") }
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("更新External").fetchSemanticsNodes().isNotEmpty() }
            val beforeDirect = downloaded.get()
            val routeBefore = routed.get()
            val firstNotice = vm.state.value.notice?.id
            compose.onNodeWithContentDescription("更新External").performClick()
            compose.waitUntil(15000) { vm.state.value.notice?.text == "外部资源已更新" && vm.state.value.notice?.id != firstNotice }
            assertTrue("Stopped update must download without starting the service", downloaded.get() > beforeDirect)
            assertEquals(routeBefore, routed.get())
            assertFalse(vm.state.value.running)
            assertNull(compose.activity.startClashService())
            awaitRunningProfile(compose.activity)
            compose.activityRule.scenario.onActivity { vm.changed(); vm.openRoute("providers") }
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("更新External").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("外部资源").assertIsDisplayed()
            val before = routed.get()
            val previousNotice = vm.state.value.notice?.id
            compose.onNodeWithContentDescription("更新External").performClick()
            compose.waitUntil(15000) { vm.state.value.notice?.text == "外部资源已更新" && vm.state.value.notice?.id != previousNotice }
            assertTrue("Provider update must traverse Fetch selected by the routing rule", routed.get() > before)
            assertTrue(repo.readDocument("$id/config.yaml").contains("interval: 86400"))
            compose.onNodeWithText("全部更新").assertIsEnabled()
        } finally {
            repo.restoreSelection(compose.activity, original)
            repo.saveOverride(override)
            repo.service.onDemandEnabled = demand
            content.close(); proxy.close()
        }
    }
}
