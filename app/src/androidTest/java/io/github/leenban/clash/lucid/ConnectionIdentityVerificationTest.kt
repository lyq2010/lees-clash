package io.github.leenban.clash.lucid

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.core.model.*
import io.github.leenban.clash.util.startClashService
import io.github.leenban.clash.util.withClash
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ConnectionIdentityVerificationTest {
    @Test fun strictModeResolvesApplicationForRealActiveSocket() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = ClientRepository(context)
        val json = DiagnosticJson.format
        val server = ServerSocket(0)
        val release = CountDownLatch(1)
        val icon = "https://example.test/identity.png"
        val config = """
            mixed-port: 17898
            find-process-mode: strict
            proxies: [{name: Fixture-Direct, type: direct}]
            proxy-groups: [{name: Identity, type: select, proxies: [Fixture-Direct], icon: "$icon"}]
            rules: ["MATCH,DIRECT"]
        """.trimIndent()
        thread(isDaemon = true) {
            while (!server.isClosed) runCatching {
                val socket = server.accept()
                thread(isDaemon = true) { socket.use {
                    val input = it.getInputStream().bufferedReader()
                    val request = input.readLine().orEmpty()
                    while (!input.readLine().isNullOrEmpty()) {}
                    val output = it.getOutputStream()
                    if (request.contains("/config")) {
                        val data = config.toByteArray()
                        output.write("HTTP/1.1 200 OK\r\nContent-Length: ${data.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        output.write(data)
                    } else if (request.contains("/ip")) {
                        val data = """{"ip":"203.0.113.12","isp":{"isp":"Fixture ISP","asn":"AS64500"}}""".toByteArray()
                        output.write("HTTP/1.1 200 OK\r\nContent-Length: ${data.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        output.write(data)
                    } else {
                        output.write("HTTP/1.1 200 OK\r\nContent-Length: 10000\r\n\r\nx".toByteArray())
                        output.flush()
                        release.await(30, TimeUnit.SECONDS)
                    }
                } }
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            val original = repo.active()
            val override = repo.overrideText()
            val demand = repo.service.onDemandEnabled
            var held: Socket? = null
            try {
                repo.restoreSelection(context, original)
                repo.service.onDemandEnabled = false
                repo.saveOverride("{}")
                val id = repo.importUrl("Connection identity fixture", "http://127.0.0.1:${server.localPort}/config", null)
                repo.select(repo.profiles().first { it.uuid == id })
                assertEquals(icon, repo.group("Identity").icon)
                assertNull("VPN permission must be granted by the harness", context.startClashService())
                awaitRunningProfile(context)
                assertEquals(icon, repo.group("Identity").icon)
                val exit = json.decodeFromString(NodeExitInfo.serializer(), withClash {
                    queryDiagnostics("nodeExit", """{"query":"Identity","id":"Fixture-Direct","url":"http://127.0.0.1:${server.localPort}/ip"}""")
                })
                assertEquals("203.0.113.12", exit.ip)
                assertEquals("Fixture ISP", exit.isp)
                assertEquals("AS64500", exit.asn)
                assertEquals("Fixture-Direct", repo.group("Identity").now)
                withContext(Dispatchers.IO) {
                    held = Socket("127.0.0.1", 17898).apply {
                        soTimeout = 5000
                        getOutputStream().write("GET http://127.0.0.1:${server.localPort}/held HTTP/1.1\r\nHost: 127.0.0.1:${server.localPort}\r\n\r\n".toByteArray())
                        assertTrue(getInputStream().read() >= 0)
                    }
                }
                val connection = withTimeout(10000) {
                    var found: ConnectionInfo? = null
                    while (found == null) {
                        val page = json.decodeFromString(DiagnosticPage.serializer(ConnectionInfo.serializer()), repo.diagnostic("connections"))
                        found = page.items.firstOrNull { c -> c.destination.endsWith(":${server.localPort}") && c.uid == android.os.Process.myUid().toLong() }
                        if (found == null) delay(100)
                    }
                    found
                }
                assertEquals(context.packageName, connection.process)
                assertEquals(context.applicationInfo.loadLabel(context.packageManager).toString(), connection.appName)
                val detail = json.decodeFromString(ConnectionInfo.serializer(), repo.diagnostic("connection", id = connection.id))
                assertEquals(connection.appName, detail.appName)
                assertEquals(connection.uid, detail.uid)
            } finally {
                held?.close()
                release.countDown()
                server.close()
                repo.restoreSelection(context, original)
                repo.saveOverride(override)
                repo.service.onDemandEnabled = demand
            }
        }
    }
}
