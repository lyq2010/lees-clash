package io.github.leenban.clash.lucid

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.leenban.clash.core.model.ProxySort
import io.github.leenban.clash.design.lucid.*

@Composable
fun GroupsScreen(state: ClientState, navigate: (String) -> Unit, back: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        LucidHeader("代理策略", back)
        if (!state.running) LucidSection(footer = "所选配置的策略组；手动选择会保存，动态策略结果在启动后显示。") {}
        LucidSearch(query, { query = it }, "搜索策略组")
        LazyColumn {
            item {
                LucidSection("策略组") {
                    state.groups
                        .filter { it.name.contains(query, true) }
                        .forEach { entry ->
                            LucidRow(
                                entry.name,
                                subtitle = entry.group.type,
                                value = entry.group.now,
                                symbol = LucidSymbol.Network,
                                tint = Color(0xFF5856D6),
                                onClick = { navigate("nodes/${Uri.encode(entry.name)}") },
                            )
                        }
                }
            }
            if (state.groups.isEmpty()) item { LucidEmpty("没有策略组", "导入包含代理与策略组的配置后，在这里选择线路。") }
        }
    }
}

@Composable
fun NodesScreen(name: String, state: ClientState, vm: ClientViewModel, back: () -> Unit) {
    var query by rememberSaveable(name) { mutableStateOf("") }
    var sort by rememberSaveable(name) { mutableStateOf(vm.repository.ui.proxySort) }
    val group = state.selectedGroup
    val selectable = group?.type?.lowercase() in listOf("selector", "select")
    Column(Modifier.fillMaxSize()) {
        LucidHeader(
            name,
            back,
            if (state.running) "测速" else "启动后测速",
            actionEnabled = state.running && !state.busy,
        ) {
            vm.health(name)
        }
        LucidSearch(query, { query = it }, "搜索节点")
        LucidSection(
            footer =
                if (!state.running) "当前服务已停止。这里显示配置与已缓存节点；可预选手动策略，启动后生效。"
                else if (selectable) "点按节点切换线路，选择会自动保存。" else "该策略组由内核自动选择线路，当前选择会随网络状态更新。"
        ) {
            LucidRow(
                "排序",
                value =
                    when (sort) {
                        ProxySort.Default -> "配置顺序"
                        ProxySort.Title -> "名称"
                        else -> "延迟"
                    },
                onClick = {
                    sort = ProxySort.values()[(sort.ordinal + 1) % ProxySort.values().size]
                    vm.repository.ui.proxySort = sort
                    vm.changed()
                },
            )
        }
        val nodes =
            group?.proxies.orEmpty().filter {
                it.name.contains(query, true) || it.title.contains(query, true)
            }
        LazyColumn(Modifier.weight(1f)) {
            items(nodes, key = { it.name }) { proxy ->
                Box(
                    Modifier.padding(horizontal = 20.dp)
                        .background(LocalLucidColors.current.surface)
                ) {
                    LucidRow(
                        proxy.title.ifBlank { proxy.name },
                        subtitle = proxy.subtitle.ifBlank { proxy.type },
                        value =
                            when {
                                !state.running -> "未运行"
                                proxy.delay <= 0 -> "未测试"
                                proxy.delay >= 65535 -> "超时"
                                else -> "${proxy.delay} ms"
                            },
                        selected = group?.now == proxy.name,
                        onClick =
                            if (selectable) {
                                { vm.selectGroup(name, proxy.name) }
                            } else null,
                    )
                }
            }
            if (nodes.isEmpty()) item { LucidEmpty("没有匹配的节点") }
        }
    }
}

@Composable
fun ProvidersScreen(state: ClientState, vm: ClientViewModel, back: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LucidHeader("资源提供器", back, "刷新") { vm.reloadPage() }
        LazyColumn {
            item {
                LucidSection(
                    "配置中的资源",
                    footer = if (!state.running) "服务已停止，显示所选配置的资源；启动后可以单独更新。" else null,
                ) {
                    state.providers.forEach { provider ->
                        LucidRow(
                            provider.name,
                            subtitle = "${provider.type} · ${provider.vehicleType}",
                            value = if (state.running) "更新" else "启动后更新",
                            onClick = {
                                if (!state.running) vm.error("启动服务后可以单独更新资源；也可从配置列表更新订阅")
                                else
                                    vm.task(progress = "正在更新资源", success = "资源更新完成") {
                                        vm.repository.update(provider)
                                        vm.changed()
                                    }
                            },
                        )
                    }
                }
            }
            if (state.providers.isEmpty()) item { LucidEmpty("没有外部资源", "当前配置没有声明代理或规则提供器。") }
        }
    }
}
