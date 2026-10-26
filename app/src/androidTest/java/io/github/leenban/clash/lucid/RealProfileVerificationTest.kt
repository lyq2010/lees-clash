package io.github.leenban.clash.lucid

import android.content.ComponentName
import android.content.pm.ShortcutManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.core.Clash
import io.github.leenban.clash.core.model.*
import io.github.leenban.clash.remote.Broadcasts
import io.github.leenban.clash.remote.Remote
import io.github.leenban.clash.remote.StatusClient
import io.github.leenban.clash.service.remote.ILogObserver
import io.github.leenban.clash.util.*
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in: exercises the currently selected real subscription, without logging secrets or node
 * names.
 */
@RunWith(AndroidJUnit4::class)
class RealProfileVerificationTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val json = DiagnosticJson.format
    private val results = mutableListOf<JsonObject>()
    private val metrics = linkedMapOf<String, Int>()

    private suspend fun check(
        name: String,
        timeout: Long = 60000,
        block: suspend () -> Unit,
    ): Boolean {
        val start = System.currentTimeMillis()
        return try {
            withTimeout(timeout) { block() }
            results += buildJsonObject {
                put("name", name)
                put("status", "passed")
                put("durationMs", System.currentTimeMillis() - start)
            }
            true
        } catch (e: Exception) {
            results += buildJsonObject {
                put("name", name)
                put("status", "failed")
                put("errorType", e.javaClass.simpleName)
                put(
                    "reason",
                    e.message.orEmpty().replace(Regex("https?://[^\\s]+"), "[URL]").take(240),
                )
                put("durationMs", System.currentTimeMillis() - start)
            }
            false
        } catch (e: AssertionError) {
            results += buildJsonObject {
                put("name", name)
                put("status", "failed")
                put("errorType", "AssertionError")
                put("durationMs", System.currentTimeMillis() - start)
            }
            false
        } finally {
            android.util.Log.i("LeesVerification", "$name: ${results.lastOrNull()?.get("status")}")
            File(context.getExternalFilesDir(null), "verification/real-profile-progress.json")
                .apply {
                    parentFile!!.mkdirs()
                    writeText(JsonArray(results).toString())
                }
        }
    }

    private suspend fun request(url: String): Int =
        withContext(Dispatchers.IO) {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 17891))
            val connection = URL(url).openConnection(proxy) as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            try {
                val status = connection.responseCode
                if (status in 200..299)
                    connection.inputStream.use { it.copyTo(java.io.ByteArrayOutputStream()) }
                status
            } finally {
                connection.disconnect()
            }
        }

    @Test(timeout = 1200000)
    fun selectedRealProfileFullRuntimeVerification() = runBlocking {
        org.junit.Assume.assumeTrue(
            InstrumentationRegistry.getArguments().getString("realProfile") == "true"
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val repo = ClientRepository(context)
            val original =
                checkNotNull(repo.active()) {
                    "Select the real profile before running this opt-in test"
                }
            require(
                original.type != io.github.leenban.clash.service.model.Profile.Type.File &&
                    !original.source.startsWith("http://127.0.0.1")
            ) {
                "A real subscription must be selected"
            }
            val wasRunning = StatusClient(context).currentProfile() != null
            val previousSort = repo.ui.proxySort
            val previousExclude = repo.ui.proxyExcludeNotSelectable
            val previousVpn = repo.ui.enableVpn
            val session = withClash { queryOverride(Clash.OverrideSlot.Session) }
            val persist = withClash { queryOverride(Clash.OverrideSlot.Persist) }
            val selected = mutableMapOf<String, String>()
            try {
                repo.restoreSelection(context, original)
                repo.ui.proxyExcludeNotSelectable = false
                check("offline groups and cached nodes") {
                    val groups = repo.groups()
                    assertTrue(groups.isNotEmpty())
                    metrics["offlineGroups"] = groups.size
                    metrics["offlineUniqueNodes"] =
                        groups.flatMap { it.group.proxies }.map { it.name }.distinct().size
                }
                check("offline paginated rules") {
                    val page =
                        json.decodeFromString(
                            DiagnosticPage.serializer(RuleInfo.serializer()),
                            repo.diagnostic("rules"),
                        )
                    assertTrue(page.total > 0)
                    metrics["offlineRules"] = page.total
                }
                check("persistent override round trip and restore") {
                    val temporary =
                        persist.copy(
                            mixedPort = 17891,
                            allowLan = false,
                            ipv6 = false,
                            unifiedDelay = true,
                            tcpConcurrent = true,
                        )
                    val encoded =
                        repo.configurationJson.encodeToString(
                            ConfigurationOverride.serializer(),
                            temporary,
                        )
                    repo.saveOverride(encoded)
                    assertEquals(
                        temporary,
                        repo.configurationJson.decodeFromString(
                            ConfigurationOverride.serializer(),
                            repo.overrideText(),
                        ),
                    )
                    repo.saveOverride(
                        repo.configurationJson.encodeToString(
                            ConfigurationOverride.serializer(),
                            persist,
                        )
                    )
                }
                withClash {
                    patchOverride(
                        Clash.OverrideSlot.Persist,
                        persist.copy(
                            mixedPort = 17891,
                            authentication = emptyList(),
                            allowLan = false,
                        ),
                    )
                }
                repo.ui.enableVpn = true
                val started =
                    check("VPN start with real configuration", 120000) {
                        assertNull(context.startClashService())
                        awaitRunningProfile(context)
                    }
                if (started) {
                    check("running groups, automatic groups and sort modes") {
                        val groups = repo.groups()
                        assertTrue(groups.isNotEmpty())
                        metrics["runningGroups"] = groups.size
                        metrics["automaticGroups"] =
                            groups.count {
                                it.group.type.lowercase() !in listOf("selector", "select")
                            }
                        val name = groups.first().name
                        val expected = groups.first().group.proxies.map { it.name }.toSet()
                        for (sort in ProxySort.values()) {
                            repo.ui.proxySort = sort
                            assertEquals(expected, repo.group(name).proxies.map { it.name }.toSet())
                        }
                    }
                    check("all running rule pages and search", 120000) {
                        var offset = 0
                        var count = 0
                        val indices = hashSetOf<Int>()
                        var total = 0
                        do {
                            val page =
                                json.decodeFromString(
                                    DiagnosticPage.serializer(RuleInfo.serializer()),
                                    repo.diagnostic("rules", offset = offset),
                                )
                            total = page.total
                            for (rule in page.items) assertTrue(indices.add(rule.index))
                            count += page.items.size
                            offset = page.nextOffset
                        } while (offset >= 0)
                        assertEquals(total, count)
                        metrics["runningRules"] = total
                        val empty =
                            json.decodeFromString(
                                DiagnosticPage.serializer(RuleInfo.serializer()),
                                repo.diagnostic("rules", "lees-clash-no-match-${System.nanoTime()}"),
                            )
                        assertEquals(0, empty.total)
                    }
                    for (type in listOf("A", "AAAA")) check("external DNS $type") {
                        val answer =
                            json.decodeFromString(
                                DnsResult.serializer(),
                                repo.diagnostic("dns", domain = "example.com", recordType = type),
                            )
                        assertEquals("NOERROR", answer.status)
                        assertTrue(answer.answers.isNotEmpty())
                    }
                    check("invalid DNS input is rejected") {
                        assertTrue(
                            runCatching {
                                    repo.diagnostic("dns", domain = "", recordType = "INVALID")
                                }
                                .isFailure
                        )
                    }
                    check("real HTTPS through the embedded proxy") {
                        assertEquals(204, request("https://www.gstatic.com/generate_204"))
                    }
                    val group =
                        repo.groups().firstOrNull {
                            it.group.type.lowercase() in listOf("selector", "select") &&
                                it.group.proxies.count { p -> !p.isGroup } > 1
                        }
                    if (group != null) {
                        selected[group.name] = group.group.now
                        val logCount = AtomicInteger()
                        check("log observation and selector change") {
                            withClash {
                                setLogObserver(
                                    object : ILogObserver {
                                        override fun newItem(message: LogMessage) {
                                            logCount.incrementAndGet()
                                        }
                                    }
                                )
                            }
                            try {
                                val node =
                                    group.group.proxies.first {
                                        !it.isGroup && it.name != group.group.now
                                    }
                                repo.select(group.name, node.name)
                                assertEquals(node.name, repo.group(group.name).now)
                                withTimeout(5000) { while (logCount.get() == 0) delay(100) }
                                repo.select(group.name, group.group.now)
                            } finally {
                                withClash { setLogObserver(null) }
                            }
                        }
                        check("real node latency test", 180000) {
                            repo.health(group.name)
                            val nodes = repo.group(group.name).proxies
                            metrics["latencyResults"] =
                                nodes.count { it.delay > 0 && it.delay < 65535 }
                            metrics["latencyTimeouts"] = nodes.count { it.delay >= 65535 }
                            assertTrue(nodes.any { it.delay > 0 })
                        }
                    }
                    check("real download and bidirectional traffic counters") {
                        assertEquals(200, request("https://example.com/"))
                        val stats =
                            json.decodeFromString(
                                TrafficStats.serializer(),
                                repo.diagnostic("traffic"),
                            )
                        assertTrue(stats.downloadTotal > 0)
                        assertTrue(stats.uploadTotal > 0)
                    }
                    val providers = repo.providers()
                    metrics["providers"] = providers.size
                    for ((index, provider) in providers.withIndex()) check(
                        "provider update ${index+1}",
                        120000,
                    ) {
                        repo.update(provider)
                    }
                    if (
                        InstrumentationRegistry.getArguments().getString("realSubscription") ==
                            "true"
                    ) {
                        check("real subscription update", 180000) {
                            repo.reload(original)
                            val updated = repo.profile(original.uuid.toString())!!
                            assertTrue(updated.imported && updated.updatedAt >= original.updatedAt)
                            assertEquals(original.source, updated.source)
                        }
                        check("background subscription worker", 180000) {
                            val previous = repo.profile(original.uuid.toString())!!.updatedAt
                            val completed = CompletableDeferred<String?>()
                            val observer =
                                object : Broadcasts.Observer {
                                    override fun onProfileUpdateCompleted(uuid: UUID?) {
                                        if (uuid == original.uuid) completed.complete(null)
                                    }

                                    override fun onProfileUpdateFailed(
                                        uuid: UUID?,
                                        reason: String?,
                                    ) {
                                        if (uuid == original.uuid)
                                            completed.complete(reason ?: "后台更新失败")
                                    }

                                    override fun onServiceRecreated() {}

                                    override fun onStarted() {}

                                    override fun onStopped(cause: String?) {}

                                    override fun onProfileChanged() {}

                                    override fun onProfileLoaded() {}
                                }
                            withContext(Dispatchers.Main) {
                                Remote.broadcasts.addObserver(observer)
                            }
                            try {
                                withProfile { update(original.uuid) }
                                val reason = completed.await()
                                check(reason == null) { reason.orEmpty() }
                                assertTrue(
                                    repo.profile(original.uuid.toString())!!.updatedAt >= previous
                                )
                            } finally {
                                withContext(Dispatchers.Main + NonCancellable) {
                                    Remote.broadcasts.removeObserver(observer)
                                }
                            }
                        }
                    }
                    for (asset in repo.geoAssets()) check("Geo update ${asset.id}", 150000) {
                        repo.updateGeo(asset.id)
                        assertTrue(repo.geoAssets().first { it.id == asset.id }.size > 0)
                    }
                    check("foreground/background lifecycle with real configuration") {
                        scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                        delay(2500)
                        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                        assertNotNull(StatusClient(context).currentProfile())
                        assertEquals(original.uuid, repo.active()?.uuid)
                    }
                    check("notification and system entry registration") {
                        assertTrue(
                            context
                                .getSystemService(ShortcutManager::class.java)
                                .dynamicShortcuts
                                .isNotEmpty()
                        )
                        val tile = ComponentName(context, "io.github.leenban.clash.TileService")
                        assertTrue(context.packageManager.getServiceInfo(tile, 0).exported)
                    }
                }
                check("stop and offline browsing after real update") {
                    repo.restoreSelection(context, original)
                    assertTrue(repo.groups().isNotEmpty())
                    assertNull(StatusClient(context).currentProfile())
                }
            } finally {
                withContext(NonCancellable) {
                    context.stopClashService()
                    withTimeout(15000) {
                        while (StatusClient(context).currentProfile() != null) delay(100)
                    }
                    withClash {
                        setLogObserver(null)
                        patchOverride(Clash.OverrideSlot.Persist, persist)
                        patchOverride(Clash.OverrideSlot.Session, session)
                    }
                    repo.ui.proxySort = previousSort
                    repo.ui.proxyExcludeNotSelectable = previousExclude
                    repo.ui.enableVpn = previousVpn
                    repo.select(original)
                    for ((name, node) in selected) runCatching { repo.select(name, node) }
                    if (wasRunning) {
                        context.startClashService()
                        awaitRunningProfile(context)
                    }
                    val report = buildJsonObject {
                        put("checks", JsonArray(results))
                        put(
                            "metrics",
                            buildJsonObject { metrics.forEach { (key, value) -> put(key, value) } },
                        )
                        put("restoredProfile", repo.active()?.uuid == original.uuid)
                        put(
                            "restoredRunningState",
                            (StatusClient(context).currentProfile() != null) == wasRunning,
                        )
                        put("subscriptionSecretsIncluded", false)
                    }
                    File(context.getExternalFilesDir(null), "verification/real-profile.json")
                        .apply {
                            parentFile!!.mkdirs()
                            writeText(report.toString())
                        }
                }
            }
            assertTrue(
                "See sanitized real-profile.json for individual checks",
                results.none { it["status"]?.jsonPrimitive?.content == "failed" },
            )
        }
    }
}
