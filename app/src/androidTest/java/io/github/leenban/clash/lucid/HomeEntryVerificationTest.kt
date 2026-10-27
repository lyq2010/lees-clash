package io.github.leenban.clash.lucid

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.core.model.*
import io.github.leenban.clash.service.model.Profile
import io.github.leenban.clash.util.*
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeEntryVerificationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(compose.activity)[ClientViewModel::class.java]
    private val json = DiagnosticJson.format

    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(compose.activity.getExternalFilesDir(null), "verification/home-entry-$name.png").apply {
            parentFile!!.mkdirs()
            outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        bitmap.recycle()
    }

    private fun open(route: String, title: String) {
        compose.activityRule.scenario.onActivity { model.openRoute(route) }
        compose.waitUntil(15000) { !model.state.value.pageLoading && compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    @Test(timeout = 180000)
    fun homeEntriesShowRealSubscriptionAndReachManualChecksDemandAndSharing() {
        val repo = model.repository
        val original = runBlocking { repo.active() }
        val previous = runBlocking { repo.overrideText() }
        val demand = repo.service.onDemandEnabled
        try {
            runBlocking {
                repo.restoreSelection(compose.activity, original)
                repo.saveOverride("{\"log-level\":\"warning\"}")
                val url = "http://127.0.0.1:18080/config-subscription/full"
                val existing = repo.profiles().firstOrNull { it.name == "Verification home subscription" }
                val profile = if (existing != null) { require(existing.source == url); existing }
                    else repo.profile(repo.importUrl("Verification home subscription", url, null).toString())!!
                repo.select(profile)
            }
            repo.service.onDemandEnabled = true
            compose.activityRule.scenario.onActivity { model.changed() }
            open("home", "出站模式")
            compose.waitUntil(15000) { model.state.value.active?.name == "Verification home subscription" }
            compose.onNodeWithText("订阅概览").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("剩余 ${formatBytes(1048576 - 3072)} / ${formatBytes(1048576)}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("更新订阅").performScrollTo()
            screenshot("subscription")
            val before = runBlocking { repo.active()!!.updatedAt }
            compose.onNodeWithText("更新订阅").performScrollTo().performClick()
            compose.waitUntil(20000) { model.state.value.active!!.updatedAt > before && model.state.value.updatingProfiles.isEmpty() }
            compose.onNodeWithText("配置详情").performScrollTo().performClick()
            compose.onNodeWithText("当前配置详情").assertIsDisplayed()
            compose.onNodeWithText("剩余").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithText("网络").performClick()
            compose.onNodeWithText("DNS 查询").assertDoesNotExist()
            compose.onNodeWithText("实时连接").assertDoesNotExist()
            compose.onNodeWithText("实时流量").assertDoesNotExist()
            screenshot("network")
            compose.onNodeWithText("IP 与网络检查").performScrollTo().performClick()
            compose.onNodeWithContentDescription("服务地址").performTextReplacement("http://127.0.0.1:18080/ip-info")
            compose.onNodeWithText("检查").performClick()
            compose.waitUntil(15000) { model.state.value.notice?.text == "网络检查完成" }
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("203.0.113.5"))
            compose.onNodeWithText("203.0.113.5").assertIsDisplayed()
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithText("按需网络").performScrollTo().performClick()
            compose.onNodeWithText("启用按需网络").performScrollTo().performClick()
            compose.onNodeWithText("保存").performClick()
            compose.waitUntil(10000) { !repo.service.onDemandEnabled && !model.state.value.onDemandEnabled }
            compose.onNodeWithText("局域网信息与共享").performScrollTo().performClick()
            compose.onNodeWithText("服务未启动").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("复制 HTTP 地址").assertDoesNotExist()
            compose.onNodeWithText("局域网访问").performScrollTo().performClick()
            compose.onNodeWithContentDescription("混合代理端口").performScrollTo().performTextReplacement("17896")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithContentDescription("混合代理端口").performScrollTo().assertTextContains("17896")
            compose.onNodeWithText("允许共享").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("保存").performClick()
            compose.waitUntil(10000) { !model.state.value.busy }
            val saved = runBlocking { withClash { queryOverride(io.github.leenban.clash.core.Clash.OverrideSlot.Persist) } }
            assertEquals(true, saved.allowLan)
            assertEquals("*", saved.bindAddress)
            assertEquals(17896, saved.mixedPort)
            assertEquals(LogMessage.Level.Warning, saved.logLevel)
            assertTrue(runBlocking { runCatching { repo.saveLanSharing(true, 65536) }.isFailure })
            open("memory", "内存详情")
            compose.waitUntil(10000) { compose.onAllNodesWithText("正在读取").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("后台进程 PSS").assertIsDisplayed()
            compose.onNodeWithText("Go 协程数").performScrollTo().assertIsDisplayed()
            screenshot("memory")
            assertNull(model.state.value.error)
        } finally {
            runBlocking { repo.restoreSelection(compose.activity, original); repo.saveOverride(previous) }
            repo.service.onDemandEnabled = demand
        }
    }

    @Test(timeout = 120000)
    fun absentSubscriptionInfoAndLocalProfilesDoNotShowInventedQuota() {
        val repo = model.repository
        val original = runBlocking { repo.active() }
        try {
            for (kind in listOf("no-info", "over-quota")) {
                val profile = runBlocking {
                    repo.restoreSelection(compose.activity, original)
                    val name = "Verification home $kind"
                    val url = "http://127.0.0.1:18080/config-subscription/$kind"
                    val value = repo.profiles().firstOrNull { it.name == name }
                        ?: repo.profile(repo.importUrl(name, url, null).toString())!!
                    require(value.source == url)
                    repo.select(value)
                    value
                }
                compose.activityRule.scenario.onActivity { model.changed() }
                open("home", "出站模式")
                compose.waitUntil(10000) { model.state.value.active?.uuid == profile.uuid }
                if (kind == "no-info") {
                    compose.onNodeWithText("订阅未提供总额度").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithText("订阅未提供到期时间").performScrollTo().assertIsDisplayed()
                } else {
                    compose.onNodeWithText("剩余 0 B / 1 B").performScrollTo().assertIsDisplayed()
                    compose.onNode(hasText("已到期", substring = true)).performScrollTo().assertIsDisplayed()
                }
            }
            val local = runBlocking {
                val profile = repo.profiles().firstOrNull { it.type == Profile.Type.File }
                    ?: repo.cloneProfile(repo.active()!!)
                repo.select(profile)
                profile
            }
            compose.activityRule.scenario.onActivity { model.changed() }
            open("home", "出站模式")
            compose.waitUntil(10000) { model.state.value.active?.uuid == local.uuid }
            compose.onNodeWithText("当前配置").performScrollTo().assertIsDisplayed()
            compose.onNode(hasText("本地配置", substring = true)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("订阅未提供总额度").assertDoesNotExist()
            compose.onNodeWithText("更新订阅").assertDoesNotExist()
        } finally {
            runBlocking { repo.restoreSelection(compose.activity, original) }
        }
    }

    @Test(timeout = 180000)
    fun sharingUsesActualInterfaceAndDnsClearInvalidatesCachedAnswersWithoutResettingSession() = runBlocking {
        val repo = model.repository
        val context = compose.activity
        val original = repo.active()
        val previous = repo.overrideText()
        val demand = repo.service.onDemandEnabled
        try {
            repo.restoreSelection(context, original)
            repo.service.onDemandEnabled = false
            repo.saveOverride("{}")
            repo.select(repo.profiles().first { it.name == "Local integration test" })
            repo.saveLanSharing(true, 17896)
            assertNull(context.startClashService())
            awaitRunningProfile(context)
            val status = json.decodeFromString(LanStatus.serializer(), repo.diagnostic("lan"))
            assertTrue(status.allowLan)
            assertEquals("*", status.bindAddress)
            assertEquals(17896, status.mixedPort)
            val address = withContext(Dispatchers.IO) { localLanAddresses(context).first { ':' !in it } }
            val response = withContext(Dispatchers.IO) {
                (URL("http://lees-clash.test:18080/echo").openConnection(Proxy(Proxy.Type.HTTP, InetSocketAddress(address, status.mixedPort))) as HttpURLConnection).run {
                    connectTimeout = 5000; readTimeout = 5000
                    try { assertEquals(200, responseCode); inputStream.bufferedReader().use { it.readText() } }
                    finally { disconnect() }
                }
            }
            assertEquals("lees-clash-local-fixture", response)
            compose.activityRule.scenario.onActivity { model.changed() }
            open("lan", "局域网信息与共享")
            compose.waitUntil(10000) { compose.onAllNodesWithText("复制 HTTP 地址").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("复制 HTTP 地址") and hasText(lanProxyAddress(address, 17896))).performScrollTo().performClick()
            assertEquals(lanProxyAddress(address, 17896), context.getSystemService(android.content.ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString())
            screenshot("lan")
            compose.onNodeWithText("保存").assertIsNotEnabled()
            val domain = "cache-verification-${System.nanoTime()}.test"
            suspend fun answer() = json.decodeFromString(DnsResult.serializer(), repo.diagnostic("dns", domain = domain)).answers.first().data
            val first = answer()
            assertEquals("second lookup must hit cache", first, answer())
            open("tools", "清理 DNS 缓存")
            compose.onNodeWithText("清理 DNS 缓存").performScrollTo().performClick()
            compose.onNodeWithText("确认").performClick()
            compose.waitUntil(10000) { model.state.value.notice?.text == "DNS 缓存已清理" }
            assertNotEquals("lookup after clear must reach fixture", first, answer())
            val before = json.decodeFromString(TrafficStats.serializer(), repo.diagnostic("traffic"))
            assertTrue(before.uptimeMs > 0 && before.downloadTotal > 0 && before.uploadTotal > 0)
            compose.activityRule.scenario.recreate()
            delay(500)
            val after = json.decodeFromString(TrafficStats.serializer(), repo.diagnostic("traffic"))
            assertTrue(after.uptimeMs >= before.uptimeMs)
            assertTrue(after.downloadTotal >= before.downloadTotal)
            assertTrue(json.parseToJsonElement(repo.diagnostic("runtime")).toString().contains("servicePssKiB"))
            repo.restoreSelection(context, original)
            assertEquals(0L, json.decodeFromString(TrafficStats.serializer(), repo.diagnostic("traffic")).uptimeMs)
            assertTrue(runCatching { repo.diagnostic("clearDnsCache") }.isFailure)
        } finally {
            repo.restoreSelection(context, original)
            repo.saveOverride(previous)
            repo.service.onDemandEnabled = demand
        }
    }
}
