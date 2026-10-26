package io.github.leenban.clash.lucid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.github.leenban.clash.design.lucid.*

@Composable
fun OnDemandScreen(state: ClientState, vm: ClientViewModel, back: () -> Unit) {
    val context = LocalContext.current
    val store = vm.repository.service
    var enabled by rememberSaveable { mutableStateOf(store.onDemandEnabled) }
    var cellular by rememberSaveable { mutableStateOf(store.onDemandCellular) }
    var ssids by rememberSaveable {
        mutableStateOf(store.onDemandSsids.sorted().joinToString("\n"))
    }
    var permissionRevision by remember { mutableIntStateOf(0) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionRevision++
            if (it[Manifest.permission.ACCESS_FINE_LOCATION] != true)
                vm.notice("SSID 未获得精确定位权限，将继续正常接管")
        }
    val granted =
        remember(permissionRevision) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    Column(Modifier.fillMaxSize()) {
        LucidHeader("按需网络", back, "保存", actionEnabled = !state.running && !state.busy) {
            vm.task(done = back, success = "按需网络已保存") {
                val values = ssids.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                require(values.size <= 100 && values.all { it.toByteArray().size <= 32 }) {
                    "SSID 不得超过 32 字节，最多 100 个"
                }
                store.onDemandSsids = values
                store.onDemandCellular = cellular
                store.onDemandEnabled = enabled
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            LucidSection(
                "VPN 接管",
                footer = "服务运行期间，命中条件时暂停 VPN 接管，离开后恢复。显式本地代理端口仍可用。仅在 VPN 模式生效；停止服务后可修改。",
            ) {
                LucidToggle("启用按需网络", enabled, !state.running) { enabled = it }
                LucidToggle("蜂窝网络暂停 VPN", cellular, !state.running) { cellular = it }
                LucidField(
                    "暂停 VPN 的 Wi-Fi（每行一个 SSID）",
                    ssids,
                    { ssids = it },
                    multiline = true,
                    enabled = !state.running,
                )
            }
            LucidSection(
                "Wi-Fi 识别",
                footer =
                    "Android 要求定位权限及系统定位开关才能读取 SSID；后台切换可能需要“始终允许”。无法识别时保持正常 VPN 接管。SSID 仅保存在本机，不写日志。",
            ) {
                LucidRow(
                    "精确定位权限",
                    value = if (granted) "已允许" else "未允许",
                    onClick = {
                        permission.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    },
                )
                LucidRow(
                    "后台定位与权限设置",
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    },
                )
            }
        }
    }
}
