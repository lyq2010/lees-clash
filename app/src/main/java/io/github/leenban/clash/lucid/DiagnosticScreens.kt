package io.github.leenban.clash.lucid

import android.net.Uri
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leenban.clash.design.lucid.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ConnectionsScreen(
    state: ClientState,
    vm: ClientViewModel,
    navigate: (String) -> Unit,
    back: () -> Unit,
    confirm: (String, () -> Unit) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LucidHeader("实时连接", back, "全部关闭") { confirm("关闭全部连接？现有传输将会中断。", vm::closeAll) }
        LucidSearch(state.connectionQuery, vm::search, "域名、IP、应用、协议或策略")
        Text(
            "${state.connections.total} 个活动连接",
            Modifier.padding(horizontal = 24.dp, vertical = 7.dp),
            color = LocalLucidColors.current.secondary,
            fontSize = 12.sp,
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(state.connections.items, key = { _, it -> it.id }) { index, c ->
                Box(
                    Modifier.padding(horizontal = 18.dp)
                        .lucidLazyCardItem(index, state.connections.items.size)
                ) {
                    LucidRow(
                        c.host.ifBlank { c.destination },
                        subtitle =
                            "${c.applicationTitle()} · ${c.network.uppercase()} · ${c.chains.firstOrNull()?:"未知策略"}\n↑ ${formatBytes(c.upload)}  ↓ ${formatBytes(c.download)}",
                        onClick = { navigate("connection/${Uri.encode(c.id)}") },
                    )
                }
            }
            if (state.connections.nextOffset >= 0)
                item {
                    Spacer(Modifier.height(10.dp))
                    LucidSection {
                        LucidRow(
                            if (state.busy) "加载中" else "加载更多",
                            onClick = if (state.busy) null else vm::moreConnections,
                        )
                    }
                }
            if (state.connections.items.isEmpty())
                item {
                    LucidEmpty(
                        if (state.running) "暂无活动连接" else "服务未启动",
                    )
                }
        }
    }
}

@Composable
fun ConnectionScreen(
    state: ClientState,
    vm: ClientViewModel,
    back: () -> Unit,
    confirm: (String, () -> Unit) -> Unit,
) {
    val c = state.connection
    Column(Modifier.fillMaxSize()) {
        LucidHeader("连接详情", back)
        LazyColumn {
            if (c != null) {
                item {
                    LucidSection("概览") {
                        LucidRow("状态", value = if (state.connectionEnded) "已结束" else "活动中")
                        LucidRow("目标", subtitle = c.host.ifBlank { c.destination })
                        LucidRow("协议", value = c.network.uppercase())
                        LucidRow("应用", subtitle = c.applicationTitle())
                        if (c.process.isNotBlank()) LucidRow("应用标识", subtitle = c.process)
                        LucidRow("UID", value = if (c.uid < 0L) "未知" else c.uid.toString())
                        LucidRow("开始时间", subtitle = c.start)
                    }
                }
                item {
                    LucidSection("网络地址") {
                        LucidRow("源地址", subtitle = c.source)
                        LucidRow("目标地址", subtitle = c.destination)
                    }
                }
                item {
                    LucidSection("流量") {
                        LucidRow("上传", value = formatBytes(c.upload))
                        LucidRow("下载", value = formatBytes(c.download))
                    }
                }
                item {
                    LucidSection("路由") {
                        LucidRow(
                            "匹配规则",
                            subtitle =
                                listOf(c.rule, c.rulePayload)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")
                                    .ifBlank { "未知" },
                        )
                        LucidRow("代理链", subtitle = c.chains.joinToString(" → ").ifBlank { "未知" })
                    }
                }
                if (!state.connectionEnded)
                    item {
                        LucidSection {
                            LucidRow(
                                "关闭连接",
                                destructive = true,
                                onClick = { confirm("关闭此连接？当前传输将会中断。") { vm.close(c.id) } },
                            )
                        }
                    }
            } else item { LucidEmpty(if (state.connectionEnded) "连接已结束" else "正在读取连接") }
        }
    }
}

@Composable
fun RulesScreen(state: ClientState, vm: ClientViewModel, back: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LucidHeader("代理规则", back, "刷新") { vm.reloadPage() }
        if (!state.running)
            Text(
                "所选配置的规则 · 服务已停止",
                Modifier.padding(horizontal = 24.dp),
                color = LocalLucidColors.current.secondary,
            )
        LucidSearch(state.ruleQuery, vm::search, "规则类型、内容或目标策略")
        Text(
            "${state.rules.total} 条${if (state.running) "生效" else "配置"}规则",
            Modifier.padding(horizontal = 24.dp, vertical = 7.dp),
            color = LocalLucidColors.current.secondary,
            fontSize = 12.sp,
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(state.rules.items, key = { _, it -> it.index }) { index, rule ->
                Box(
                    Modifier.padding(horizontal = 18.dp)
                        .lucidLazyCardItem(index, state.rules.items.size)
                ) {
                    LucidRow(
                        rule.payload.ifBlank { rule.type },
                        subtitle = "#${rule.index+1} · ${rule.type}",
                        value = rule.proxy,
                    )
                }
            }
            if (state.rules.nextOffset >= 0)
                item {
                    Spacer(Modifier.height(10.dp))
                    LucidSection {
                        LucidRow(
                            if (state.busy) "加载中" else "加载更多",
                            onClick = if (state.busy) null else vm::moreRules,
                        )
                    }
                }
            if (state.rules.items.isEmpty()) item { LucidEmpty("没有匹配的规则") }
        }
    }
}

@Composable
fun DnsScreen(state: ClientState, vm: ClientViewModel, back: () -> Unit) {
    var domain by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("A") }
    Column(Modifier.fillMaxSize()) {
        LucidHeader("DNS 查询", back, if (state.busy) "查询中" else "查询") {
            if (!state.busy) vm.dns(domain, type)
        }
        LazyColumn {
            item {
                LucidSection("查询条件") {
                    LucidField("域名", domain, { domain = it }, "example.com")
                    LucidRow(
                        "记录类型",
                        value = type,
                        onClick = {
                            val types = listOf("A", "AAAA", "CNAME", "MX", "TXT", "NS", "HTTPS")
                            type = types[(types.indexOf(type) + 1) % types.size]
                        },
                    )
                }
            }
            state.dns?.let { result ->
                item {
                    LucidSection("查询结果") {
                        LucidRow("状态", value = result.status)
                        LucidRow("耗时", value = "${result.elapsedMs} ms")
                    }
                }
                items(result.answers) { answer ->
                    LucidSection(answer.type) {
                        LucidRow(answer.name, subtitle = answer.data, value = "TTL ${answer.ttl}")
                    }
                }
                if (result.answers.isEmpty()) item { LucidEmpty("没有应答记录", result.status) }
            }
        }
    }
}

@Composable
fun StatsScreen(state: ClientState, back: () -> Unit) {
    val palette = rememberHomeBentoPalette()
    Column(Modifier.fillMaxSize()) {
        LucidHeader("实时统计", back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                HomeBentoCard(
                    palette = palette,
                    cornerRadius = 22.dp,
                    glow = BentoGlow.TopStartBlue,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            if (state.running) "运行中 · ${formatRunTime(state.traffic.uptimeMs)}" else "服务未启动",
                            color = palette.mutedText,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        HomeRateSparkline(
                            history = state.rateHistory,
                            running = state.running,
                            lineColor = palette.primaryText,
                            accentColor = palette.blueAccent,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        )
                    }
                }
            }
            LucidSection("当前速率") {
                LucidRow("上传", value = "${formatBytes(state.traffic.uploadRate)}/s")
                LucidRow("下载", value = "${formatBytes(state.traffic.downloadRate)}/s")
                LucidRow("活动连接", value = "${if (state.running) state.traffic.activeConnections else 0}")
            }
            LucidSection("本次运行累计") {
                LucidRow("上传", value = formatBytes(state.traffic.uploadTotal))
                LucidRow("下载", value = formatBytes(state.traffic.downloadTotal))
            }
        }
    }
}

@Composable
fun LogsScreen(state: ClientState, vm: ClientViewModel, back: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val logs = state.logs.asReversed().filter {
        it.message.contains(query, true) || it.level.name.contains(query, true)
    }
    Column(Modifier.fillMaxSize()) {
        LucidHeader("运行日志", back, "分享", actionEnabled = logs.isNotEmpty() && !state.busy) {
            vm.task(progress = "正在准备日志") {
                val context = vm.repository.context
                val uri = withContext(Dispatchers.IO) {
                    val folder = File(context.cacheDir, "shared-logs").apply { mkdirs() }
                    val file = File.createTempFile("lees-clash-log-", ".txt", folder)
                    val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT)
                    file.bufferedWriter().use { output ->
                        for (log in logs) {
                            output.appendLine("${log.level.name.uppercase()}  ${format.format(log.time)}")
                            output.appendLine(log.message)
                            output.appendLine()
                        }
                    }
                    FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
                }
                val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .apply { clipData = android.content.ClipData.newUri(context.contentResolver, "运行日志", uri) }
                context.startActivity(Intent.createChooser(intent, "分享运行日志").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        LucidSearch(query, { query = it }, "搜索日志内容或级别")
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(18.dp, 6.dp, 18.dp, 16.dp)) {
            itemsIndexed(logs) { index, log ->
                Box(Modifier.fillMaxWidth().lucidLazyCardItem(index, logs.size)) {
                    Text(
                        "${log.level.name.uppercase()}  ${java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.ROOT).format(log.time)}\n${log.message}",
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = LocalLucidColors.current.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
            if (state.logs.isEmpty()) item { LucidEmpty("暂无日志", "打开此页后开始接收日志，最多保留最近 1000 条。") }
        }
    }
}
