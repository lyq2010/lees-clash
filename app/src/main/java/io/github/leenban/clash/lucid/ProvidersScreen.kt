package io.github.leenban.clash.lucid

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.leenban.clash.core.model.Provider
import io.github.leenban.clash.design.lucid.*

@Composable
fun ProvidersScreen(state: ClientState, vm: ClientViewModel, back: () -> Unit) {
    val resources = state.providers.filter { it.vehicleType == Provider.VehicleType.HTTP || it.vehicleType == Provider.VehicleType.File }
    fun update(items: List<Provider>) = vm.task(progress = "正在更新外部资源", success = "外部资源已更新") {
        val failures = mutableListOf<String>()
        for (provider in items) {
            try { vm.repository.update(provider) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { failures += "${provider.name}：${e.message ?: "更新失败"}" }
        }
        vm.changed()
        check(failures.isEmpty()) { failures.joinToString("\n") }
    }
    Column(Modifier.fillMaxSize()) {
        LucidHeader("外部资源", back, "全部更新", actionEnabled = !state.busy && resources.isNotEmpty()) { update(resources) }
        LazyColumn(Modifier.weight(1f)) {
            item { LucidSection(footer = if (state.running) "更新按当前路由，间隔跟随配置。" else "未开启代理，更新将直连。") {} }
            items(state.providers, key = { "${it.type}/${it.name}" }) { provider ->
                LucidSection {
                    LucidRow(provider.name,
                        subtitle = "${if (provider.type == Provider.Type.Proxy) "代理集合" else "规则集合"} · ${when (provider.vehicleType) {
                            Provider.VehicleType.HTTP -> "远程"
                            Provider.VehicleType.File -> "本地文件"
                            else -> "内置"
                        }}" + if (provider.updatedAt > 0) "\n${profileDate(provider.updatedAt)}" else "",
                        trailing = {
                            if (provider in resources) CompactIconAction("更新${provider.name}", LucidSymbol.Refresh,
                                enabled = !state.busy) { update(listOf(provider)) }
                        })
                }
            }
            if (state.providers.isEmpty()) item { LucidEmpty("没有外部资源") }
        }
    }
}
