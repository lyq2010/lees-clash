package io.github.leenban.clash.lucid

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leenban.clash.design.lucid.*

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
        LazyColumn(Modifier.weight(1f)) {
            items(state.connections.items, key = { it.id }) { c ->
                Box(Modifier.background(LocalLucidColors.current.surface)) {
                    LucidRow(
                        c.host.ifBlank { c.destination },
                        subtitle =
                            "${c.network.uppercase()} · ${c.chains.firstOrNull()?:"未知策略"}\n↑ ${formatBytes(c.upload)}  ↓ ${formatBytes(c.download)}",
                        onClick = { navigate("connection/${Uri.encode(c.id)}") },
                    )
                }
            }
            if (state.connections.nextOffset >= 0)
                item {
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
                        "这里展示内核当前可观测的连接，不包含 HTTP 请求内容。",
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
                        LucidRow("应用", subtitle = c.process.ifBlank { "未知" })
                        LucidRow("UID", value = if (c.uid == 0L) "未知" else c.uid.toString())
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
        LucidHeader("代理规则", back, "刷新") { vm.changed() }
        LucidSearch(state.ruleQuery, vm::search, "规则类型、内容或目标策略")
        Text(
            "${state.rules.total} 条生效规则",
            Modifier.padding(horizontal = 24.dp, vertical = 7.dp),
            color = LocalLucidColors.current.secondary,
            fontSize = 12.sp,
        )
        LazyColumn(Modifier.weight(1f)) {
            items(state.rules.items, key = { it.index }) { rule ->
                Box(Modifier.background(LocalLucidColors.current.surface)) {
                    LucidRow(
                        rule.payload.ifBlank { rule.type },
                        subtitle = "#${rule.index+1} · ${rule.type}",
                        value = rule.proxy,
                    )
                }
            }
            if (state.rules.nextOffset >= 0)
                item {
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
                LucidSection("查询条件", footer = "使用当前内核配置的 DNS 解析器；查询超时为 8 秒。") {
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
    Column {
        LucidHeader("实时统计", back)
        LucidSection("当前速率") {
            LucidRow("上传", value = "${formatBytes(state.traffic.uploadRate)}/s")
            LucidRow("下载", value = "${formatBytes(state.traffic.downloadRate)}/s")
        }
        LucidSection("本次运行累计") {
            LucidRow("上传", value = formatBytes(state.traffic.uploadTotal))
            LucidRow("下载", value = formatBytes(state.traffic.downloadTotal))
        }
    }
}

@Composable
fun LogsScreen(state: ClientState, back: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        LucidHeader("运行日志", back)
        LucidSearch(query, { query = it }, "搜索日志内容或级别")
        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp)) {
            items(
                state.logs.asReversed().filter {
                    it.message.contains(query, true) || it.level.name.contains(query, true)
                }
            ) { log ->
                Text(
                    "${log.level.name.uppercase()}  ${java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.ROOT).format(log.time)}\n${log.message}",
                    Modifier.padding(vertical = 9.dp),
                    color = LocalLucidColors.current.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
            if (state.logs.isEmpty()) item { LucidEmpty("暂无日志", "打开此页后开始接收日志，最多保留最近 1000 条。") }
        }
    }
}
