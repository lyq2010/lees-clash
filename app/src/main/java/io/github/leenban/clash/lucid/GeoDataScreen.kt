package io.github.leenban.clash.lucid

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.leenban.clash.design.lucid.*
import java.text.DateFormat
import java.util.Date

@Composable
fun GeoDataScreen(
    state: ClientState,
    vm: ClientViewModel,
    back: () -> Unit,
    import: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LucidHeader(
            "Geo 数据",
            back,
            "全部更新",
            actionEnabled = !state.busy && state.geoAssets.isNotEmpty(),
        ) {
            vm.updateGeo()
        }
        LazyColumn {
            item {
                LucidSection(
                    footer =
                        "更新采用当前配置与覆写中的 Geo 来源；MRS 使用内核默认来源。下载后校验格式再替换，失败保留原文件。更新或导入后，重新启动服务使其生效。"
                ) {}
            }
            items(state.geoAssets, key = { it.id }) { asset ->
                val updated =
                    if (asset.updatedAt > 0)
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(asset.updatedAt))
                    else "尚未安装"
                LucidSection(
                    asset.name,
                    footer = "${asset.file} · ${formatBytes(asset.size)} · $updated",
                ) {
                    LucidRow(
                        "在线更新",
                        symbol = LucidSymbol.Refresh,
                        value =
                            if (state.updatingGeo == "all" || state.updatingGeo == asset.id) "更新中"
                            else "",
                        onClick = if (!state.busy) ({ vm.updateGeo(asset.id) }) else null,
                    )
                    LucidRow(
                        "从文件导入",
                        symbol = LucidSymbol.Folder,
                        onClick = if (!state.busy) ({ import(asset.id) }) else null,
                    )
                }
            }
        }
    }
}
