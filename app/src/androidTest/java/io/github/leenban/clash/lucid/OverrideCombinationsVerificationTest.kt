@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.github.leenban.clash.lucid

import android.app.ActivityManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.core.Clash
import io.github.leenban.clash.core.model.ConfigurationOverride
import io.github.leenban.clash.remote.StatusClient
import io.github.leenban.clash.util.*
import kotlinx.coroutines.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverrideCombinationsVerificationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(compose.activity)[ClientViewModel::class.java]
    private val repo get() = model.repository
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun fixture() = InstrumentationRegistry.getInstrumentation().context.assets
        .open("override-full.json").bufferedReader().use { it.readText() }

    private fun leaves(schema: SerialDescriptor, path: List<String> = emptyList()): List<Pair<List<String>, SerialDescriptor>> =
        (0 until schema.elementsCount).flatMap { index ->
            val child = schema.getElementDescriptor(index)
            val key = path + schema.getElementName(index)
            if (child.kind == StructureKind.CLASS) leaves(child, key) else listOf(key to child)
        }

    @Test(timeout = 180000)
    fun everySchemaLeafRoundTripsThroughBinderAndSurvivesServiceDeath() = runBlocking {
        val original = repo.active()
        val running = StatusClient(context).currentProfile() != null
        val previous = repo.overrideText()
        val session = withClash { queryOverride(Clash.OverrideSlot.Session) }
        try {
            repo.restoreSelection(context, original)
            val full = repo.configurationJson.decodeFromString(ConfigurationOverride.serializer(), fixture())
            val root = Json.parseToJsonElement(fixture())
            val paths = leaves(ConfigurationOverride.serializer().descriptor)
            for ((path, _) in paths) {
                val value = path.fold(root) { node, key -> node.jsonObject.getValue(key) }
                assertNotEquals("Missing shared fixture leaf: $path", JsonNull, value)
            }
            android.util.Log.i("OverrideVerification", "Schema leaves covered: ${paths.size}")
            val empty = full.copy(authentication = emptyList(), hosts = emptyMap(), allowLan = false,
                dns = full.dns.copy(nameServer = emptyList(), nameserverPolicy = emptyMap()),
                sniffer = full.sniffer.copy(forceDomain = emptyList(), skipSrcAddress = emptyList()))
            for (value in listOf(full, empty, ConfigurationOverride())) {
                repo.saveOverride(repo.configurationJson.encodeToString(ConfigurationOverride.serializer(), value))
                assertEquals(value, repo.configurationJson.decodeFromString(ConfigurationOverride.serializer(), repo.overrideText()))
            }
            repo.saveOverride(fixture())
            val pid = context.getSystemService(ActivityManager::class.java).runningAppProcesses
                .first { it.processName == "${context.packageName}:background" }.pid
            android.os.Process.killProcess(pid)
            delay(1500)
            withTimeout(30000) {
                while (runCatching { repo.configurationJson.decodeFromString(ConfigurationOverride.serializer(), repo.overrideText()) }.getOrNull() != full) delay(100)
            }
        } finally {
            withContext(NonCancellable) {
                repo.saveOverride(previous)
                withClash { patchOverride(Clash.OverrideSlot.Session, session) }
                repo.restoreSelection(context, original)
                if (running) { context.startClashService(); awaitRunningProfile(context) }
            }
        }
    }

    @Test(timeout = 240000)
    fun runtimeDnsHostsAuthenticationControllerAndSessionPriority() = runBlocking {
        val original = repo.active()
        val running = StatusClient(context).currentProfile() != null
        val previous = repo.overrideText()
        val session = withClash { queryOverride(Clash.OverrideSlot.Session) }
        val vpn = repo.ui.enableVpn
        val ntp = java.net.DatagramSocket(13123, java.net.InetAddress.getByName("127.0.0.1")).apply { soTimeout = 500 }
        val requests = java.util.concurrent.atomic.AtomicInteger()
        val responder = launch(Dispatchers.IO) {
            val packet = java.net.DatagramPacket(ByteArray(512), 512)
            while (isActive) {
                try {
                    ntp.receive(packet)
                    if (packet.length < 48) continue
                    val answer = ByteArray(48)
                    answer[0] = 0x24; answer[1] = 1; answer[2] = 4; answer[3] = (-20).toByte()
                    packet.data.copyInto(answer, 24, 40, 48)
                    val now = System.currentTimeMillis()
                    for (offset in listOf(16, 32, 40)) java.nio.ByteBuffer.wrap(answer, offset, 8)
                        .putInt((now / 1000 + 2208988800L).toInt()).putInt(((now % 1000) * 4294967296L / 1000).toInt())
                    ntp.send(java.net.DatagramPacket(answer, answer.size, packet.socketAddress))
                    requests.incrementAndGet()
                } catch (_: java.net.SocketTimeoutException) { }
                catch (error: java.net.SocketException) { if (isActive) throw error }
            }
        }
        try {
            repo.restoreSelection(context, original)
            val profile = repo.profiles().first { it.name == "Local integration test" }
            require(profile.source == "http://127.0.0.1:18080/config")
            repo.select(profile)
            repo.ui.enableVpn = false
            val full = repo.configurationJson.decodeFromString(ConfigurationOverride.serializer(), fixture())
            val value = full.copy(redirectPort = 0, tproxyPort = 0,
                globalClientFingerprint = null, sniffer = full.sniffer.copy(enable = false),
                ntp = full.ntp.copy(enable = true, writeToSystem = false),
                dns = full.dns.copy(fallback = emptyList(), respectRules = false))
            repo.saveOverride(repo.configurationJson.encodeToString(ConfigurationOverride.serializer(), value))
            withClash { patchOverride(Clash.OverrideSlot.Session, ConfigurationOverride(mode = io.github.leenban.clash.core.model.TunnelState.Mode.Direct)) }
            assertNull(context.startClashService())
            awaitRunningProfile(context)
            // A session belongs to one service run; startup clears the previous session.
            assertEquals(io.github.leenban.clash.core.model.TunnelState.Mode.Rule, repo.mode())
            withClash { patchOverride(Clash.OverrideSlot.Session, ConfigurationOverride(mode = io.github.leenban.clash.core.model.TunnelState.Mode.Direct)) }
            withTimeout(15000) { while (repo.mode() != io.github.leenban.clash.core.model.TunnelState.Mode.Direct) delay(100) }
            assertTrue(repo.diagnostic("dns", domain = "override.test", recordType = "A").contains("127.0.0.1"))
            withTimeout(15000) { while (requests.get() == 0) delay(100) }
            fun get(path: String, token: Boolean): Pair<Int, String> {
                val connection = java.net.URL("http://127.0.0.1:19090$path").openConnection() as HttpURLConnection
                connection.connectTimeout = 5000; connection.readTimeout = 5000
                if (token) connection.setRequestProperty("Authorization", "Bearer verification-local-secret")
                return try { val code = connection.responseCode; code to (if (code < 400) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() } }
                finally { connection.disconnect() }
            }
            withTimeout(20000) { while (runCatching { get("/configs", true).first }.getOrNull() != 200) delay(100) }
            assertEquals(401, get("/configs", false).first)
            val effective = Json.parseToJsonElement(get("/configs", true).second).jsonObject
            assertEquals(18094, effective.getValue("mixed-port").jsonPrimitive.int)
            val auth = "Basic " + android.util.Base64.encodeToString("verification:local-only".toByteArray(), android.util.Base64.NO_WRAP)
            for ((header, status) in listOf(null to 407, auth to 200)) {
                val connection = java.net.URL("http://127.0.0.1:18080/payload").openConnection(
                    Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 18094))) as HttpURLConnection
                connection.connectTimeout = 5000; connection.readTimeout = 5000
                if (header != null) connection.setRequestProperty("Proxy-Authorization", header)
                try { assertEquals(status, connection.responseCode) } finally { connection.disconnect() }
            }
            val cors = java.net.URL("http://127.0.0.1:19090/configs").openConnection() as HttpURLConnection
            cors.requestMethod = "OPTIONS"
            cors.setRequestProperty("Origin", "http://127.0.0.1:18080")
            cors.setRequestProperty("Access-Control-Request-Method", "GET")
            try { assertEquals(200, cors.responseCode); assertEquals("http://127.0.0.1:18080", cors.getHeaderField("Access-Control-Allow-Origin")) }
            finally { cors.disconnect() }
            // The core generates an ephemeral self-signed certificate when no TLS key
            // is supplied. Trust is scoped to this loopback test connection only.
            val tls = javax.net.ssl.SSLContext.getInstance("TLS")
            tls.init(null, arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                override fun getAcceptedIssuers() = emptyArray<java.security.cert.X509Certificate>()
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, type: String) = Unit
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, type: String) { assertTrue(chain.isNotEmpty()) }
            }), null)
            val secure = java.net.URL("https://127.0.0.1:19443/configs").openConnection() as javax.net.ssl.HttpsURLConnection
            secure.sslSocketFactory = tls.socketFactory
            secure.hostnameVerifier = javax.net.ssl.HostnameVerifier { hostname, _ -> hostname == "127.0.0.1" }
            secure.connectTimeout = 5000; secure.readTimeout = 5000
            secure.setRequestProperty("Authorization", "Bearer verification-local-secret")
            try { assertEquals(200, secure.responseCode); assertTrue(secure.serverCertificates.isNotEmpty()) }
            finally { secure.disconnect() }
        } finally {
            withContext(NonCancellable) {
                repo.restoreSelection(context, original)
                repo.saveOverride(previous)
                withClash { patchOverride(Clash.OverrideSlot.Session, session) }
                repo.ui.enableVpn = vpn
                if (running) { context.startClashService(); awaitRunningProfile(context) }
                responder.cancel()
                ntp.close()
                responder.join()
            }
        }
    }

    @Test(timeout = 180000)
    fun nestedEditorTriStateEnumsListsMapsAndDeprecatedFingerprint() {
        val previous = runBlocking { repo.overrideText() }
        fun row(key: String) = compose.onNode(hasText(key) and hasClickAction())
        fun edit(key: String, text: String) {
            row(key).performScrollTo().performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement(text)
            compose.onNodeWithText("确定").performClick()
        }
        try {
            runBlocking { repo.saveOverride("{}") }
            compose.activityRule.scenario.onActivity { model.openRoute("override") }
            compose.waitUntil(15000) { model.state.value.overrideJson != null }
            edit("hosts", """{"ui-override.test":"127.0.0.1"}""")
            row("unified-delay").performScrollTo().performClick()
            row("unified-delay").performClick()
            row("mode").performScrollTo().performClick()
            row("dns").performScrollTo().performClick()
            edit("nameserver-policy", """{"+.ui.test":"tcp://127.0.0.1:13053"}""")
            row("fallback-filter").performScrollTo().performClick()
            edit("ipcidr", """["192.0.2.0/24"]""")
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithContentDescription("返回").performClick()
            row("sniffer").performScrollTo().performClick()
            row("sniff").performScrollTo().performClick()
            row("HTTP").performScrollTo().performClick()
            edit("ports", """["80","18080-18081"]""")
            repeat(3) { compose.onNodeWithContentDescription("返回").performClick() }
            edit("global-client-fingerprint", "chrome")
            compose.onNodeWithText("当前内核已停用全局指纹，请在节点配置中设置 client-fingerprint；此处仅可清空旧值。").assertIsDisplayed()
            compose.onNode(hasSetTextAction()).performTextReplacement("")
            compose.onNodeWithText("确定").performClick()
            compose.onNodeWithText("完成").performClick()
            compose.waitUntil(15000) { !model.state.value.busy && model.state.value.notice?.text == "覆写已保存" }
            val saved = runBlocking { repo.configurationJson.decodeFromString(ConfigurationOverride.serializer(), repo.overrideText()) }
            assertEquals(mapOf("ui-override.test" to "127.0.0.1"), saved.hosts)
            assertEquals(false, saved.unifiedDelay)
            assertNotNull(saved.mode)
            assertEquals(listOf("80", "18080-18081"), saved.sniffer.sniff.http.ports)
            assertEquals(listOf("192.0.2.0/24"), saved.dns.fallbackFilter.ipcidr)
            assertNull(saved.globalClientFingerprint)
        } finally { runBlocking { repo.saveOverride(previous) } }
    }
}
