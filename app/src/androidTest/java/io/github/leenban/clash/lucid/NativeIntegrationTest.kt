package io.github.leenban.clash.lucid

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.core.model.*
import io.github.leenban.clash.util.startClashService
import io.github.leenban.clash.util.stopClashService
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeIntegrationTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val json = DiagnosticJson.format

    @Test
    fun prepareLocalProfile() = runBlocking {
        ActivityScenario.launch(MainActivity::class.java).use {
            withTimeout(30000) {
                val repository = ClientRepository(context)
                val existing =
                    repository.profiles().find { profile ->
                        profile.name == "Local integration test"
                    }
                val profile =
                    existing
                        ?: repository.profile(
                            repository
                                .importUrl(
                                    "Local integration test",
                                    "http://127.0.0.1:18080/config",
                                    null,
                                )
                                .toString()
                        )!!
                repository.select(profile)
                assertEquals("Local integration test", repository.active()?.name)
                assertTrue(repository.coreVersion().contains("3f27ef3"))
            }
        }
    }

    @Test
    fun runningCoreRoutesAndDiagnostics() = runBlocking {
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                val repository = ClientRepository(context)
                withTimeout(45000) {
                    assertNull(
                        "VPN permission must be granted by the test harness",
                        context.startClashService(),
                    )
                    while (repository.group("Local-Test").proxies.isEmpty()) delay(100)
                    repository.select("Local-Test", "Local-A")
                    val group = repository.group("Local-Test")
                    assertEquals("Local-A", group.now)
                    repository.select("Local-Test", "Local-B")
                    assertEquals("Local-B", repository.group("Local-Test").now)
                    val rules =
                        json.decodeFromString(
                            DiagnosticPage.serializer(RuleInfo.serializer()),
                            repository.diagnostic("rules", "lees-clash.test"),
                        )
                    assertEquals(1, rules.total)
                    assertEquals("Local-Test", rules.items.first().proxy)
                    val dns =
                        json.decodeFromString(
                            DnsResult.serializer(),
                            repository.diagnostic(
                                "dns",
                                domain = "lees-clash.test",
                                recordType = "A",
                            ),
                        )
                    assertEquals("NOERROR", dns.status)
                    assertTrue(
                        dns.answers.any { answer -> answer.data == "127.0.0.1" && answer.ttl > 0 }
                    )
                    val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 17890))
                    val response =
                        withContext(Dispatchers.IO) {
                            (URL("http://lees-clash.test:18080/echo").openConnection(proxy)
                                    as HttpURLConnection)
                                .run {
                                    connectTimeout = 5000
                                    readTimeout = 5000
                                    try {
                                        inputStream.bufferedReader().use { reader ->
                                            reader.readText()
                                        }
                                    } finally {
                                        disconnect()
                                    }
                                }
                        }
                    assertEquals("lees-clash-local-fixture", response)
                    val request =
                        async(Dispatchers.IO) {
                            runCatching {
                                (URL("http://lees-clash.test:18080/slow").openConnection(proxy)
                                        as HttpURLConnection)
                                    .run {
                                        connectTimeout = 5000
                                        readTimeout = 8000
                                        try {
                                            inputStream.use { stream -> stream.readBytes() }
                                        } finally {
                                            disconnect()
                                        }
                                    }
                            }
                        }
                    delay(500)
                    val connections =
                        json.decodeFromString(
                            DiagnosticPage.serializer(ConnectionInfo.serializer()),
                            repository.diagnostic("connections", "lees-clash.test"),
                        )
                    assertTrue("Expected a live tracked connection", connections.items.isNotEmpty())
                    val connection = connections.items.first()
                    assertEquals("Domain", connection.rule)
                    assertTrue(connection.chains.contains("Local-B"))
                    repository.diagnostic("close", id = connection.id)
                    request.await()
                    val traffic =
                        json.decodeFromString(
                            TrafficStats.serializer(),
                            repository.diagnostic("traffic"),
                        )
                    assertTrue(traffic.downloadTotal > 0)
                    assertTrue(traffic.uploadTotal > 0)
                }
            }
        } finally {
            context.stopClashService()
        }
    }
}
