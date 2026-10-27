package io.github.leenban.clash.lucid

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.util.startClashService
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SmartModeVerificationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun yamlAndUiOverrideFollowTheCurrentProfile(): Unit = runBlocking {
        val vm = ViewModelProvider(compose.activity)[ClientViewModel::class.java]
        val repo = vm.repository
        val original = repo.active()
        val override = repo.overrideText()
        val demand = repo.service.onDemandEnabled
        val server = ServerSocket(0)
        val config = """
            proxies: [{name: Offline, type: socks5, server: 127.0.0.1, port: 9}, {name: Backup, type: socks5, server: 127.0.0.1, port: 10}]
            proxy-groups:
              - {name: UI-Smart, type: url-test, proxies: [Offline, Backup]}
              - {name: Other-Auto, type: url-test, proxies: [Offline, Backup]}
              - {name: Manual, type: select, proxies: [Offline, Backup]}
              - {name: Failover, type: fallback, proxies: [Offline, Backup]}
              - {name: YAML-Smart, type: smart, proxies: [Offline]}
            rules: ["MATCH,DIRECT"]
        """.trimIndent()
        thread(isDaemon = true) { while (!server.isClosed) runCatching {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                while (!reader.readLine().isNullOrEmpty()) {}
                val data = config.toByteArray()
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: ${data.size}\r\nConnection: close\r\n\r\n".toByteArray()+data)
            }
        } }
        try {
            repo.restoreSelection(compose.activity, original)
            repo.service.onDemandEnabled = false
            repo.saveOverride("{}")
            val id = repo.importUrl("Smart verification", "http://127.0.0.1:${server.localPort}/config", null)
            val profile = repo.profiles().first { it.uuid == id }
            repo.select(profile)
            assertEquals("config", repo.smartMode("UI-Smart"))
            repo.select("Manual", "Backup")
            assertFalse(repo.groupSmart("Manual").supported)
            assertFalse(repo.groupSmart("Failover").supported)
            assertFalse(repo.groupSmart("YAML-Smart").supported)
            assertEquals("smart", repo.group("YAML-Smart").type.lowercase())
            compose.activityRule.scenario.onActivity { vm.changed(); vm.openRoute("nodes/UI-Smart") }
            compose.waitUntil(15000) { vm.state.value.smartSupported }
            compose.onNodeWithContentDescription("Smart 独立开关").assertIsOff().performClick()
            compose.waitUntil(15000) { vm.state.value.smartMode == "smart" }
            assertEquals("smart", repo.group("UI-Smart").type.lowercase())
            assertEquals(SmartSummary(2,1), repo.smartAll())
            compose.activityRule.scenario.onActivity { vm.openRoute("tools") }
            compose.waitUntil(15000) { vm.state.value.smartSummary == SmartSummary(2,1) }
            compose.onNodeWithText("所有 URLTest 组").performClick()
            compose.waitUntil(15000) { vm.state.value.smartSummary == SmartSummary(2,2) }
            assertEquals("select", repo.group("Manual").type.lowercase())
            assertEquals("fallback", repo.group("Failover").type.lowercase())
            // Subscription update keeps the separate override.
            repo.reload(profile)
            assertEquals("smart", repo.smartMode("UI-Smart"))
            assertEquals("smart", repo.group("UI-Smart").type.lowercase())
            assertNull(compose.activity.startClashService())
            awaitRunningProfile(compose.activity)
            assertEquals("smart", repo.group("UI-Smart").type.lowercase())
            repo.health("UI-Smart")
            val runtime = io.github.leenban.clash.core.model.DiagnosticJson.format
                .parseToJsonElement(repo.diagnostic("runtime")).jsonObject
            assertTrue("Manual UI tests must update Smart statistics through its probe budget",
                runtime.getValue("smart").jsonObject.getValue("nodeRecords").jsonPrimitive.int > 0)
            repo.smartMode("UI-Smart", "config")
            withTimeout(15000) { while (repo.group("UI-Smart").type.lowercase() !in listOf("url-test","urltest")) delay(100) }
            assertEquals("Backup", repo.group("Manual").now)
            assertEquals(SmartSummary(2,1), repo.smartAll())
            repo.smartAll("config")
            withTimeout(15000) { while (repo.group("Other-Auto").type.lowercase() !in listOf("url-test","urltest")) delay(100) }
            assertEquals(SmartSummary(2,0), repo.smartAll())
            assertEquals("smart", repo.group("YAML-Smart").type.lowercase())
        } finally {
            server.close()
            repo.restoreSelection(compose.activity, original)
            repo.saveOverride(override)
            repo.service.onDemandEnabled = demand
        }
    }
}
