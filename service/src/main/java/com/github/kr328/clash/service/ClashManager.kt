package io.github.leenban.clash.service

import android.content.Context
import io.github.leenban.clash.common.log.Log
import io.github.leenban.clash.core.Clash
import io.github.leenban.clash.core.bridge.Bridge
import io.github.leenban.clash.core.model.*
import io.github.leenban.clash.service.data.Selection
import io.github.leenban.clash.service.data.ImportedDao
import io.github.leenban.clash.service.data.SelectionDao
import io.github.leenban.clash.service.remote.IClashManager
import io.github.leenban.clash.service.remote.ILogObserver
import io.github.leenban.clash.service.store.ServiceStore
import io.github.leenban.clash.service.util.importedDir
import io.github.leenban.clash.service.util.sendOverrideChanged
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*

class ClashManager(private val context: Context) :
    IClashManager, CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private val store = ServiceStore(context)
    private val geo = GeoDataManager(context)
    private var logReceiver: ReceiveChannel<LogMessage>? = null

    override fun queryGeoData(): String = geo.status()

    override fun queryCoreVersion(): String = Bridge.nativeCoreVersion()

    override fun generateAgeKeyPair(hybrid: Boolean): String =
        DiagnosticJson.format.encodeToString(
            AgeKeyPair.serializer(),
            if (hybrid) Clash.genHybridKeyPair() else Clash.genX25519KeyPair(),
        )

    override suspend fun updateGeoData(id: String) = geo.update(id)

    override suspend fun importGeoData(id: String, uri: String) = geo.import(id, uri)

    override fun queryDiagnostics(operation: String, payload: String): String {
        require(
            operation in
                setOf(
                    "connections",
                    "connection",
                    "close",
                    "closeAll",
                    "rules",
                    "traffic",
                    "dns",
                    "runtime",
                    "networkCheck",
                    "lan",
                    "clearDnsCache",
                )
        )
        require(payload.length <= 8192)
        if (operation == "clearDnsCache") check(StatusProvider.serviceRunning) { "启动服务后可以清理 DNS 缓存" }
        if (operation == "runtime" && payload.contains("javaStacks")) {
            check(io.github.leenban.clash.service.BuildConfig.DEBUG)
            return buildJsonObject {
                    put(
                        "main",
                        android.os.Looper.getMainLooper().thread.stackTrace.joinToString("\n"),
                    )
                }
                .toString()
        }
        if (operation == "runtime" && payload.contains("stacks"))
            check(io.github.leenban.clash.service.BuildConfig.DEBUG)
        if (operation == "rules" && !StatusProvider.serviceRunning)
            return profileDiagnostic(
                "profileRules",
                DiagnosticJson.format.parseToJsonElement(payload).jsonObject,
            )
        val result = DiagnosticJson.checked(
            io.github.leenban.clash.core.bridge.Bridge.nativeDiagnostics(operation, payload)
        )
        if (operation == "traffic") return JsonObject(
            DiagnosticJson.format.parseToJsonElement(result).jsonObject +
                mapOf(
                    "networkBypassed" to JsonPrimitive(StatusProvider.networkBypassed),
                    "uptimeMs" to JsonPrimitive(if (StatusProvider.serviceRunning)
                        (android.os.SystemClock.elapsedRealtime() - StatusProvider.startedAtElapsed).coerceAtLeast(0L) else 0L),
                )
        ).toString()
        if (operation == "runtime") {
            val memory = android.os.Debug.MemoryInfo()
            android.os.Debug.getMemoryInfo(memory)
            return JsonObject(DiagnosticJson.format.parseToJsonElement(result).jsonObject +
                ("servicePssKiB" to JsonPrimitive(memory.totalPss))).toString()
        }
        return result
    }

    private fun profileDiagnostic(
        operation: String,
        request: JsonObject = buildJsonObject {},
    ): String {
        val current = checkNotNull(store.activeProfile) { "请先选择配置" }
        val secret = runBlocking { ImportedDao().queryByUUID(current)?.ageSecretKey }.orEmpty()
        val payload =
            JsonObject(
                request +
                    ("secretKey" to JsonPrimitive(secret)) +
                    ("path" to JsonPrimitive(context.importedDir.resolve(current.toString()).path))
            )
        return DiagnosticJson.checked(Bridge.nativeDiagnostics(operation, payload.toString()))
    }

    override fun queryTunnelState(): TunnelState {
        if (!StatusProvider.serviceRunning && store.activeProfile != null) {
            val configured =
                DiagnosticJson.format.decodeFromString(
                    TunnelState.serializer(),
                    profileDiagnostic("profileMode"),
                )
            return configured.copy(
                mode =
                    Clash.queryOverride(Clash.OverrideSlot.Session).mode
                        ?: Clash.queryOverride(Clash.OverrideSlot.Persist).mode
                        ?: configured.mode
            )
        }
        return Clash.queryTunnelState()
    }

    override fun queryTrafficTotal(): Long {
        return Clash.queryTrafficTotal()
    }

    override fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String> {
        if (!StatusProvider.serviceRunning)
            return DiagnosticJson.format.decodeFromString(
                ListSerializer(String.serializer()),
                profileDiagnostic(
                    "profileGroupNames",
                    buildJsonObject { put("query", if (excludeNotSelectable) "select" else "") },
                ),
            )
        return Clash.queryGroupNames(excludeNotSelectable)
    }

    override fun queryProxyGroup(name: String, proxySort: ProxySort): ProxyGroup {
        if (!StatusProvider.serviceRunning) {
            val group =
                DiagnosticJson.format.decodeFromString(
                    ProxyGroup.serializer(),
                    profileDiagnostic("profileGroup", buildJsonObject { put("id", name) }),
                )
            val selected = runBlocking {
                SelectionDao()
                    .querySelections(checkNotNull(store.activeProfile))
                    .find { it.proxy == name }
                    ?.selected
            }
            val nodes =
                if (proxySort == ProxySort.Title) group.proxies.sortedBy { it.title.lowercase() }
                else group.proxies
            return group.copy(
                proxies = nodes,
                now =
                    selected?.takeIf { choice ->
                        group.type == "select" && nodes.any { it.name == choice }
                    } ?: group.now,
            )
        }
        return Clash.queryGroup(name, proxySort)
    }

    override fun queryProxyGroupSummary(name: String): ProxyGroup {
        val payload = buildJsonObject {
            put("id", name)
            if (!StatusProvider.serviceRunning) put("query", runBlocking {
                SelectionDao().querySelections(checkNotNull(store.activeProfile)).find { it.proxy == name }?.selected.orEmpty()
            })
        }
        val text = if (StatusProvider.serviceRunning) DiagnosticJson.checked(Bridge.nativeDiagnostics("groupSummary", payload.toString()))
            else profileDiagnostic("profileGroupSummary", payload)
        return DiagnosticJson.format.decodeFromString(ProxyGroup.serializer(), text)
    }

    override fun queryConfiguration(): UiConfiguration {
        return Clash.queryConfiguration()
    }

    override fun queryProviders(): ProviderList {
        if (!StatusProvider.serviceRunning)
            return ProviderList(
                DiagnosticJson.format.decodeFromString(
                    ListSerializer(Provider.serializer()),
                    profileDiagnostic("profileProviders"),
                )
            )
        return ProviderList(Clash.queryProviders())
    }

    override fun queryOverride(slot: Clash.OverrideSlot): ConfigurationOverride {
        return Clash.queryOverride(slot)
    }

    override fun patchSelector(group: String, name: String): Boolean {
        if (!StatusProvider.serviceRunning) {
            val preview = queryProxyGroup(group, ProxySort.Default)
            if (preview.type != "select" || preview.proxies.none { it.name == name }) return false
            SelectionDao().setSelected(Selection(checkNotNull(store.activeProfile), group, name))
            return true
        }
        return Clash.patchSelector(group, name).also {
            val current = store.activeProfile ?: return@also

            if (it) {
                SelectionDao().setSelected(Selection(current, group, name))
            }
        }
    }

    override fun patchOverride(slot: Clash.OverrideSlot, configuration: ConfigurationOverride) {
        Clash.patchOverride(slot, configuration)

        context.sendOverrideChanged()
    }

    override fun clearOverride(slot: Clash.OverrideSlot) {
        Clash.clearOverride(slot)
    }

    override suspend fun healthCheck(group: String) {
        check(StatusProvider.serviceRunning) { "启动服务后可以测速" }
        return Clash.healthCheck(group).await()
    }

    override suspend fun updateProvider(type: Provider.Type, name: String) {
        return Clash.updateProvider(type, name).await()
    }

    override fun setLogObserver(observer: ILogObserver?) {
        synchronized(this) {
            logReceiver?.apply {
                cancel()

                Clash.forceGc()
            }

            if (observer != null) {
                logReceiver =
                    Clash.subscribeLogcat().also { c ->
                        launch {
                            try {
                                while (isActive) {
                                    observer.newItem(c.receive())
                                }
                            } catch (e: CancellationException) {
                                // intended behavior
                                // ignore
                            } catch (e: Exception) {
                                Log.w("UI crashed", e)
                            } finally {
                                withContext(NonCancellable) {
                                    c.cancel()

                                    Clash.forceGc()
                                }
                            }
                        }
                    }
            }
        }
    }
}
