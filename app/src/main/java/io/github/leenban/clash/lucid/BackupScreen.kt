package io.github.leenban.clash.lucid

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.leenban.clash.design.lucid.*
import java.text.DateFormat
import java.util.Date

@Composable
fun BackupScreen(state: ClientState, back: () -> Unit, export: () -> Unit, restore: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LucidHeader("本地备份与恢复", back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            LucidSection("备份", footer = "包含配置、资源缓存、Geo 数据、手动选择和设置。备份含订阅链接与私钥，请妥善保存。") {
                LucidRow(
                    "保存备份",
                    symbol = LucidSymbol.Document,
                    onClick = if (!state.running && !state.busy) export else null,
                )
                LucidRow(
                    "选择备份文件",
                    symbol = LucidSymbol.Folder,
                    onClick = if (!state.running && !state.busy) restore else null,
                )
            }
            if (state.running) LucidSection(footer = "请先停止服务，再备份或恢复。") {}
        }
    }
}

@Composable
fun BackupPreviewScreen(state: ClientState, vm: ClientViewModel, back: () -> Unit) {
    val preview = state.backupPreview
    val uri = state.backupUri
    Column(Modifier.fillMaxSize()) {
        LucidHeader(
            "恢复备份",
            back,
            if (preview != null) "恢复" else null,
            actionEnabled = !state.busy,
        ) {
            if (uri != null)
                vm.task(done = back, progress = "正在校验并恢复备份") {
                    val result = vm.repository.restoreBackup(Uri.parse(uri))
                    vm.settingsRestored()
                    vm.notice("恢复完成：新增 ${result.restored} 个配置，保留 ${result.skipped} 个已有配置")
                    vm.changed()
                }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (preview == null) LucidEmpty("请重新选择备份文件")
            else
                LucidSection("备份内容", footer = "合并缺少的配置，恢复 Geo 数据与设置。已有配置不会被覆盖。") {
                    LucidRow(
                        "创建时间",
                        value =
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(preview.createdAt)),
                    )
                    LucidRow("配置", value = preview.profiles.toString())
                    LucidRow("Geo 文件", value = preview.geoFiles.toString())
                }
        }
    }
}
