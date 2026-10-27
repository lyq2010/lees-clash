package io.github.leenban.clash.lucid

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import io.github.leenban.clash.core.Clash
import io.github.leenban.clash.core.model.*
import io.github.leenban.clash.design.lucid.*
import io.github.leenban.clash.util.withClash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun localLanAddresses(context: Context): List<String> {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    return manager.allNetworks.flatMap { network ->
        val caps = manager.getNetworkCapabilities(network)
        if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
            !(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) emptyList()
        else manager.getLinkProperties(network)?.linkAddresses.orEmpty().map { it.address }
            .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress || it.isAnyLocalAddress }
            .mapNotNull { it.hostAddress?.substringBefore('%') }
    }.distinct().sorted()
}

internal fun lanProxyAddress(address: String, port: Int): String =
    "${if (':' in address) "[$address]" else address}:$port"

suspend fun ClientRepository.saveLanSharing(allow: Boolean?, mixedPort: Int?) = withClash {
    require(mixedPort == null || mixedPort in 0..65535) { "端口须在 0～65535 之间，留空表示跟随配置" }
    val current = queryOverride(Clash.OverrideSlot.Persist)
    current.allowLan = allow
    if (allow == true) current.bindAddress = "*"
    else if (allow == null) current.bindAddress = null
    current.mixedPort = mixedPort
    patchOverride(Clash.OverrideSlot.Persist, current)
}

@Composable
fun LanSharingScreen(state: ClientState, vm: ClientViewModel, back: () -> Unit) {
    val repo = vm.repository
    var loaded by rememberSaveable { mutableStateOf(false) }
    var allow by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var port by rememberSaveable { mutableStateOf("") }
    var addresses by remember { mutableStateOf(emptyList<String>()) }
    var live by remember { mutableStateOf<LanStatus?>(null) }
    suspend fun refresh() {
        addresses = withContext(Dispatchers.IO) { localLanAddresses(repo.context) }
        if (!loaded) {
            val saved = withClash { queryOverride(Clash.OverrideSlot.Persist) }
            allow = saved.allowLan
            port = saved.mixedPort?.toString().orEmpty()
            loaded = true
        }
        live = if (state.running) DiagnosticJson.format.decodeFromString(LanStatus.serializer(), repo.diagnostic("lan")) else null
    }
    LaunchedEffect(state.running) {
        try { refresh() } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { vm.error(e.message ?: "无法读取局域网信息") }
    }
    Column(Modifier.fillMaxSize()) {
        LucidHeader("局域网信息与共享", back, "保存", actionEnabled = loaded && !state.running && !state.busy) {
            vm.task(done = back, progress = "正在保存共享设置", success = "共享设置已保存，下次启动生效") {
                val value = if (port.isBlank()) null else port.toIntOrNull()
                    ?: error("端口须为整数")
                repo.saveLanSharing(allow, value)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            LucidSection("当前网络", footer = "仅显示 Wi-Fi / 以太网地址，不包含 VPN 虚拟地址；网络变化后点按刷新。") {
                if (!loaded) LucidRow("局域网地址", value = "正在读取")
                else if (addresses.isEmpty()) LucidRow("局域网地址", value = "未连接 Wi-Fi 或以太网")
                addresses.forEach { address -> LucidRow(if (':' in address) "IPv6" else "IPv4", value = address) }
                LucidRow("刷新网络信息", onClick = if (state.busy) null else ({ vm.task { refresh() } }))
            }
            LucidSection("当前生效状态", footer = "代理服务运行且允许局域网访问时才可共享；地址可达性仍受路由、防火墙及同网设备隔离影响。") {
                val status = live.takeIf { state.running }
                LucidRow("共享", value = when { !state.running -> "服务未启动"; status == null -> "正在读取"; status.allowLan -> "允许局域网访问"; else -> "仅本机" })
                if (status != null) {
                    LucidRow("监听地址", value = if (status.allowLan) status.bindAddress else "127.0.0.1")
                    val http = status.mixedPort.takeIf { it > 0 } ?: status.httpPort
                    val socks = status.mixedPort.takeIf { it > 0 } ?: status.socksPort
                    LucidRow("HTTP 代理端口", value = if (http > 0) "$http" else "未监听")
                    LucidRow("SOCKS5 代理端口", value = if (socks > 0) "$socks" else "未监听")
                    if (status.allowLan) for (address in addresses) {
                        val bind = status.bindAddress.removeSurrounding("[", "]")
                        val listens = bind == "*" || bind.isEmpty() || bind == "::" || bind == address || (bind == "0.0.0.0" && ':' !in address)
                        if (listens) for ((label, value) in listOf("HTTP" to http, "SOCKS5" to socks).filter { it.second > 0 }) {
                            val endpoint = lanProxyAddress(address, value)
                            LucidRow("复制 $label 地址", subtitle = endpoint, onClick = {
                                repo.context.getSystemService(ClipboardManager::class.java)
                                    .setPrimaryClip(ClipData.newPlainText("$label 代理", endpoint))
                                vm.notice("代理地址已复制")
                            })
                        }
                    }
                }
            }
            LucidSection("共享设置", footer = "停止服务后可修改。允许共享将绑定所有接口（*），请用于可信局域网；认证可在通用与高级设置中配置。跟随配置会清除局域网访问和绑定地址覆写，其余设置保留。") {
                LucidRow("局域网访问", value = when (allow) { null -> "跟随配置"; true -> "允许共享"; false -> "仅本机" },
                    onClick = if (!loaded || state.running || state.busy) null else ({ allow = when (allow) { null -> true; true -> false; false -> null } }))
                LucidField("混合代理端口", port, { port = it }, "留空跟随配置；0 关闭混合端口", keyboard = KeyboardType.Number,
                    enabled = loaded && !state.running && !state.busy)
            }
        }
    }
}
