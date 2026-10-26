package io.github.leenban.clash.lucid

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.leenban.clash.core.model.*
import io.github.leenban.clash.remote.Broadcasts
import io.github.leenban.clash.remote.Remote
import io.github.leenban.clash.service.remote.ILogObserver
import io.github.leenban.clash.util.withClash
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer

class ClientViewModel(application: Application) :
    AndroidViewModel(application), Broadcasts.Observer {
    val repository = ClientRepository(application)
    private val mutable = MutableStateFlow(ClientState())
    val state = mutable.asStateFlow()
    private val refreshMutex = Mutex()
    private var route = "home"
    private var logJob: Job? = null
    private var logClient: io.github.leenban.clash.service.remote.IClashManager? = null
    private var searchJob: Job? = null
    private var taskSequence = 0L
    private var activeTasks = 0
    private val operations = linkedMapOf<Long, String>()
    private var pageVersion = 0
    private var profileInitialized = false
    private val json = DiagnosticJson.format
    private val connections =
        DiagnosticPager<ConnectionInfo>({ it.id }) { query, offset ->
            json.decodeFromString(
                DiagnosticPage.serializer(ConnectionInfo.serializer()),
                repository.diagnostic("connections", query, offset),
            )
        }
    private val rules =
        DiagnosticPager<RuleInfo>({ it.index }) { query, offset ->
            json.decodeFromString(
                DiagnosticPage.serializer(RuleInfo.serializer()),
                repository.diagnostic("rules", query, offset),
            )
        }

    fun clearError() {
        mutable.update { it.copy(error = null) }
    }

    fun backupPreview(uri: String, preview: io.github.leenban.clash.service.backup.BackupPreview) {
        mutable.update { it.copy(backupUri = uri, backupPreview = preview) }
        openRoute("backup-preview")
    }

    fun settingsRestored() {
        mutable.update { it.copy(settingsRevision = it.settingsRevision + 1) }
    }

    fun clearNotice(id: Long) {
        mutable.update { if (it.notice?.id == id) it.copy(notice = null) else it }
    }

    fun notice(message: String) {
        mutable.update { it.copy(notice = ClientNotice(++taskSequence, message)) }
    }

    fun consumeImport(id: UUID) {
        mutable.update { if (it.importedProfile == id) it.copy(importedProfile = null) else it }
    }

    fun openRoute(destination: String) {
        mutable.update { it.copy(navigation = ClientNavigation(++taskSequence, destination)) }
    }

    fun consumeNavigation(id: Long) {
        mutable.update { if (it.navigation?.id == id) it.copy(navigation = null) else it }
    }

    suspend fun imported(id: UUID) {
        val profiles = repository.profiles()
        val active = repository.active()
        mutable.update {
            it.copy(profiles = profiles, active = active, importedProfile = id, recentProfile = id)
        }
    }

    fun importProfile(name: String, url: String, secret: String) {
        if (mutable.value.importing) return
        mutable.update {
            it.copy(importing = true, importStatus = "正在下载配置", importedProfile = null)
        }
        task(progress = "正在导入配置", success = "配置已添加") {
            try {
                val id =
                    repository.importUrl(name.ifBlank { "订阅配置" }, url, secret) { status ->
                        val stage =
                            when (status.action) {
                                FetchStatus.Action.FetchConfiguration -> "正在下载配置"
                                FetchStatus.Action.FetchProviders ->
                                    "正在下载资源（${status.progress + 1}/${status.max}）"
                                FetchStatus.Action.Verifying -> "正在验证配置"
                                else -> "正在保存配置"
                            }
                        mutable.update { it.copy(importStatus = stage) }
                    }
                imported(id)
            } finally {
                mutable.update { it.copy(importing = false) }
            }
        }
    }

    fun updateProfile(profile: io.github.leenban.clash.service.model.Profile) {
        if (!profile.imported) {
            error("该配置尚未完成导入，请先编辑并保存")
            return
        }
        if (profile.uuid in mutable.value.updatingProfiles) return
        mutable.update { it.copy(updatingProfiles = it.updatingProfiles + profile.uuid) }
        task(progress = "正在更新“${profile.name}”", success = "配置更新完成") {
            try {
                repository.reload(profile)
                val profiles = repository.profiles()
                mutable.update { it.copy(profiles = profiles) }
                refreshPage()
            } finally {
                mutable.update { it.copy(updatingProfiles = it.updatingProfiles - profile.uuid) }
            }
        }
    }

    fun error(message: String) {
        mutable.update { it.copy(error = message, starting = false) }
    }

    fun updateGeo(id: String? = null) {
        if (mutable.value.updatingGeo != null) return
        val assets = mutable.value.geoAssets.filter { id == null || it.id == id }
        if (assets.isEmpty()) return
        mutable.update { it.copy(updatingGeo = id ?: "all") }
        task(success = "Geo 数据更新完成，重新启动服务后生效") {
            try {
                for ((index, asset) in assets.withIndex()) {
                    mutable.update {
                        it.copy(operation = "正在下载并校验 ${asset.name}（${index + 1}/${assets.size}）")
                    }
                    repository.updateGeo(asset.id)
                    val current = repository.geoAssets()
                    mutable.update { it.copy(geoAssets = current) }
                }
            } finally {
                mutable.update { it.copy(updatingGeo = null) }
            }
        }
    }

    fun starting() {
        mutable.update { it.copy(starting = true, error = null) }
    }

    suspend fun navigate(destination: String) {
        searchJob?.cancel()
        if (destination.startsWith("extension/")) mutable.update { it.copy(extensionJson = null) }
        if (destination.startsWith("connection/") && destination != route)
            mutable.update { it.copy(connection = null, connectionEnded = false) }
        route = destination
        val version = ++pageVersion
        mutable.update { it.copy(pageLoading = true) }
        try {
            loadPage()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error(e.message ?: "页面加载失败")
        } finally {
            if (version == pageVersion) mutable.update { it.copy(pageLoading = false) }
        }
    }

    fun search(value: String) {
        when (route) {
            "connections" -> {
                connections.reset(value)
                mutable.update { it.copy(connectionQuery = value, connections = connections.page) }
            }
            "rules" -> {
                rules.reset(value)
                mutable.update { it.copy(ruleQuery = value, rules = rules.page) }
            }
            else -> return
        }
        val requestedRoute = route
        searchJob?.cancel()
        searchJob =
            viewModelScope.launch {
                delay(180)
                try {
                    if (route == requestedRoute) refreshPage()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error(e.message ?: "查询失败")
                }
            }
    }

    fun task(
        done: () -> Unit = {},
        progress: String? = null,
        success: String? = null,
        operation: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            val id = ++taskSequence
            activeTasks++
            if (progress != null) operations[id] = progress
            mutable.update {
                it.copy(busy = true, error = null, operation = operations.values.lastOrNull())
            }
            try {
                operation()
                if (success != null) notice(success)
                done()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error(e.message ?: "操作失败，请重试")
            } finally {
                activeTasks--
                operations.remove(id)
                mutable.update {
                    it.copy(busy = activeTasks > 0, operation = operations.values.lastOrNull())
                }
            }
        }
    }

    suspend fun refresh(forceMode: Boolean = false) {
        refreshMutex.withLock {
            try {
                val running = Remote.broadcasts.clashRunning
                val previous = mutable.value
                val active =
                    if (forceMode || !profileInitialized) repository.active() else previous.active
                profileInitialized = true
                val mode =
                    if (
                        (running && route == "home") ||
                            forceMode ||
                            running != previous.running ||
                            active?.uuid != previous.active?.uuid ||
                            active?.updatedAt != previous.active?.updatedAt
                    )
                        repository.mode()
                    else previous.mode
                val traffic =
                    if (
                        running &&
                            (route == "home" || route == "stats" || route.startsWith("connection"))
                    )
                        json.decodeFromString(
                            TrafficStats.serializer(),
                            repository.diagnostic("traffic"),
                        )
                    else if (running) previous.traffic else TrafficStats()
                mutable.update {
                    it.copy(running = running, active = active, mode = mode, traffic = traffic)
                }
                if (
                    route.startsWith("connections") ||
                        route.startsWith("connection/") ||
                        route.startsWith("stats")
                )
                    refreshPage()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error(e.message ?: "无法连接服务")
            }
        }
    }

    private suspend fun loadPage() {
        detach()
        refreshPage()
        if (route == "logs") {
            logJob =
                viewModelScope.launch {
                    withClash {
                        logClient = this
                        setLogObserver(
                            object : ILogObserver {
                                override fun newItem(message: LogMessage) {
                                    mutable.update {
                                        it.copy(logs = (it.logs + message).takeLast(1000))
                                    }
                                }
                            }
                        )
                    }
                }
        }
    }

    private suspend fun refreshPage() {
        val requestedRoute = route
        val screen = requestedRoute.substringBefore('/')
        val argument = android.net.Uri.decode(requestedRoute.substringAfter('/', ""))
        when (screen) {
            "extension" -> {
                val value = repository.extension(argument)
                if (route == requestedRoute) mutable.update { it.copy(extensionJson = value) }
            }
            "profiles" -> {
                val value = repository.profiles()
                if (route == requestedRoute) mutable.update { it.copy(profiles = value) }
            }
            "profile" -> {
                val value = repository.profile(argument)
                if (route == requestedRoute) mutable.update { it.copy(editingProfile = value) }
            }
            "groups" -> {
                val value = repository.groupSummaries()
                if (route == requestedRoute) mutable.update { it.copy(groups = value) }
            }
            "nodes" -> {
                val value = repository.group(argument)
                if (route == requestedRoute) mutable.update { it.copy(selectedGroup = value) }
            }
            "connections" ->
                connections.refresh()?.let { page ->
                    if (route == requestedRoute) mutable.update { it.copy(connections = page) }
                }
            "connection" ->
                try {
                    val connection =
                        json.decodeFromString(
                            ConnectionInfo.serializer(),
                            repository.diagnostic("connection", id = argument),
                        )
                    if (route == requestedRoute)
                        mutable.update { it.copy(connection = connection, connectionEnded = false) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IllegalStateException) {
                    if (route == requestedRoute) mutable.update { it.copy(connectionEnded = true) }
                }
            "rules" ->
                rules.refresh()?.let { page ->
                    if (route == requestedRoute) mutable.update { it.copy(rules = page) }
                }
            "providers" -> {
                val value = repository.providers()
                if (route == requestedRoute) mutable.update { it.copy(providers = value) }
            }
            "geodata" -> {
                val value = repository.geoAssets()
                if (route == requestedRoute) mutable.update { it.copy(geoAssets = value) }
            }
            "apps" -> {
                val value = repository.appChoices()
                if (route == requestedRoute) mutable.update { it.copy(apps = value) }
            }
            "override",
            "dns-settings" -> {
                val value = repository.overrideText()
                if (route == requestedRoute) mutable.update { it.copy(overrideJson = value) }
            }
            "files" -> {
                val value = repository.files.list(argument)
                if (route == requestedRoute) mutable.update { it.copy(files = value) }
            }
            "about" -> {
                val value = repository.coreVersion()
                if (route == requestedRoute) mutable.update { it.copy(coreVersion = value) }
            }
        }
    }

    fun moreConnections() {
        if (route != "connections") return
        task {
            connections.more()?.let { page ->
                if (route == "connections") mutable.update { it.copy(connections = page) }
            }
        }
    }

    fun moreRules() {
        if (route != "rules") return
        task {
            rules.more()?.let { page ->
                if (route == "rules") mutable.update { it.copy(rules = page) }
            }
        }
    }

    fun dns(domain: String, type: String) {
        if (!mutable.value.running) {
            error("启动服务后可使用当前配置的 DNS 查询")
            return
        }
        task(progress = "正在查询 DNS") {
            val result =
                json.decodeFromString(
                    DnsResult.serializer(),
                    repository.diagnostic("dns", domain = domain, recordType = type),
                )
            mutable.update { it.copy(dns = result) }
        }
    }

    fun close(id: String) {
        task(progress = "正在关闭连接", success = "连接已关闭") {
            repository.diagnostic("close", id = id)
            refreshPage()
        }
    }

    fun closeAll() {
        task(progress = "正在关闭连接", success = "全部连接已关闭") {
            repository.diagnostic("closeAll")
            refreshPage()
        }
    }

    fun selectGroup(name: String, proxy: String) {
        task(
            progress = "正在保存策略",
            success = if (mutable.value.running) "代理策略已切换" else "策略已保存，下次启动生效",
        ) {
            repository.select(name, proxy)
            refreshPage()
        }
    }

    fun health(name: String) {
        task(progress = "正在测速", success = "测速完成") {
            repository.health(name)
            refreshPage()
        }
    }

    fun setMode(mode: TunnelState.Mode) {
        task(success = "已切换为${modeTitle(mode)}") {
            repository.setMode(mode)
            mutable.update { it.copy(mode = mode) }
            refresh()
        }
    }

    fun changed() {
        viewModelScope.launch {
            try {
                refresh(forceMode = true)
                refreshPage()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error(e.message ?: "刷新失败")
            }
        }
    }

    fun reloadPage() {
        task(progress = "正在刷新", success = "已刷新") {
            refresh(forceMode = true)
            refreshPage()
        }
    }

    override fun onServiceRecreated() = changed()

    override fun onStarted() {
        mutable.update { it.copy(starting = false) }
        changed()
        notice("服务已启动")
    }

    override fun onStopped(cause: String?) {
        mutable.update { it.copy(starting = false, running = false, error = cause) }
        changed()
        if (cause == null) notice("服务已停止")
    }

    override fun onProfileChanged() = changed()

    override fun onProfileUpdateCompleted(uuid: UUID?) = changed()

    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) = error(reason ?: "配置更新失败")

    override fun onProfileLoaded() = changed()

    suspend fun detach() {
        searchJob?.cancel()
        logJob?.cancel()
        logJob = null
        val client = logClient
        logClient = null
        if (client != null)
            withContext(Dispatchers.IO) {
                try {
                    client.setLogObserver(null)
                } catch (_: android.os.DeadObjectException) {}
            }
    }
}
