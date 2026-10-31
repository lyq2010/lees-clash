package io.github.leenban.clash.lucid

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.leenban.clash.core.model.ProxySort
import io.github.leenban.clash.design.lucid.*
import io.github.leenban.clash.design.store.UiStore

@Composable
fun GroupsScreen(state: ClientState, navigate: (String) -> Unit, back: (() -> Unit)? = null, ui: UiStore? = null) {
    val context = LocalContext.current
    val preferences = ui ?: remember(context) { UiStore(context.applicationContext) }
    var query by rememberSaveable { mutableStateOf("") }
    var layout by remember(state.settingsRevision) { mutableStateOf(preferences.groupLayout) }
    val groups = remember(state.groups, query) { state.groups.filter { it.name.contains(query, true) } }
    Column(Modifier.fillMaxSize()) {
        LucidHeader("代理策略", back)
        LucidSearch(query, { query = it }, "搜索策略组")
        PolicyLayoutPicker(layout) { layout = it; preferences.groupLayout = it }
        if (groups.isEmpty()) LucidEmpty("没有匹配的策略组")
        else PolicyCollection(groups, layout, { it.name }, Modifier.weight(1f)) { entry, list ->
            val open = { navigate("nodes/${Uri.encode(entry.name)}") }
            val delay = if (state.running) entry.group.delay else 0
            if (list) PolicyGroupRow(entry.name, entry.group.type, entry.group.now, entry.group.icon, delay, open)
            else PolicyTile(entry.name, entry.group.type, entry.group.now, icon = entry.group.icon, delay = delay, onClick = open)
        }
    }
}

@Composable
fun NodesScreen(name: String, state: ClientState, vm: ClientViewModel, back: () -> Unit) {
    var query by rememberSaveable(name) { mutableStateOf("") }
    var sort by rememberSaveable(name) { mutableStateOf(vm.repository.ui.proxySort) }
    var layout by remember(state.settingsRevision) { mutableStateOf(vm.repository.ui.nodeLayout) }
    val group = state.selectedGroup
    val exits = rememberNodeExitLookup(name, state, vm.repository)
    val selectable = group?.type?.lowercase() in listOf("selector", "select")
    val nodes = remember(group?.proxies, query) {
        group?.proxies.orEmpty().filter { it.name.contains(query, true) || it.title.contains(query, true) }
    }
    Column(Modifier.fillMaxSize()) {
        LucidHeader(name, back, if (state.running) "测速" else "启动后测速",
            actionEnabled = state.running && !state.busy) { vm.health(name) }
        LucidSearch(query, { query = it }, "搜索节点")
        PolicyLayoutPicker(layout) { layout = it; vm.repository.ui.nodeLayout = it }
        PolicyActionBar(state.smartMode, sort, showMode = state.smartSupported,
            modeEnabled = !state.busy && group != null,
            exitEnabled = exits.running || (state.running && !state.busy),
            progress = if (exits.running) "${exits.completed}/${exits.total}" else null,
            onMode = { vm.setSmartMode(name, if (state.smartMode == "smart") "config" else "smart") }, onSort = {
                sort = ProxySort.values()[(sort.ordinal + 1) % ProxySort.values().size]
                vm.repository.ui.proxySort = sort
                vm.changed()
            }, onExit = {
                if (exits.running) exits.cancel() else exits.start(group?.proxies.orEmpty())
            })
        if (nodes.isEmpty()) LucidEmpty("没有匹配的节点")
        else PolicyCollection(nodes, layout, { it.name }, Modifier.weight(1f)) { proxy, list ->
            val delay = if (state.running) proxy.delay else 0
            val subtitle = listOfNotNull(proxy.subtitle.ifBlank { proxy.type },
                if (proxy.isGroup) null else exits.subtitles[proxy.name]).joinToString("\n")
            val select = if (selectable) ({ vm.selectGroup(name, proxy.name) }) else null
            if (list) LucidRow(proxy.title.ifBlank { proxy.name }, subtitle = subtitle,
                trailing = { PolicyLatency(delay, Modifier.padding(start = 10.dp)) },
                selected = group?.now == proxy.name, onClick = select)
            else PolicyTile(proxy.title.ifBlank { proxy.name }, subtitle, "",
                selected = group?.now == proxy.name, delay = delay, onClick = select)
        }
    }
}
