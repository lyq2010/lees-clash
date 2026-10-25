package io.github.leenban.clash.lucid

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leenban.clash.RestartReceiver
import io.github.leenban.clash.design.lucid.*
import io.github.leenban.clash.design.model.DarkMode
import io.github.leenban.clash.design.store.UiStore.Companion.mainActivityAlias
import io.github.leenban.clash.service.model.AccessControlMode

@Composable
fun NetworkScreen(
    state: ClientState,
    vm: ClientViewModel,
    navigate: (String) -> Unit,
    back: () -> Unit,
) {
    val repo = vm.repository
    var revision by remember { mutableIntStateOf(0) }
    fun update(block: () -> Unit) {
        block()
        revision++
    }
    Column(Modifier.fillMaxSize()) {
        LucidHeader("VPN 与网络", back)
        key(revision) {
            LazyColumn {
                item {
                    LucidSection(
                        "流量接管",
                        footer = if (state.running) "停止服务后可更改网络接管选项。" else "关闭 VPN 后仅启动本地代理服务。",
                    ) {
                        LucidToggle("使用 VPN 接管系统流量", repo.ui.enableVpn, !state.running) {
                            update { repo.ui.enableVpn = it }
                        }
                    }
                }
                item {
                    LucidSection("VPN 选项") {
                        LucidToggle("绕过局域网", repo.service.bypassPrivateNetwork, !state.running) {
                            update { repo.service.bypassPrivateNetwork = it }
                        }
                        LucidToggle("DNS 劫持", repo.service.dnsHijacking, !state.running) {
                            update { repo.service.dnsHijacking = it }
                        }
                        LucidToggle("允许应用绕过 VPN", repo.service.allowBypass, !state.running) {
                            update { repo.service.allowBypass = it }
                        }
                        LucidToggle("允许 IPv6", repo.service.allowIpv6, !state.running) {
                            update { repo.service.allowIpv6 = it }
                        }
                        if (Build.VERSION.SDK_INT >= 29)
                            LucidToggle("系统 HTTP 代理", repo.service.systemProxy, !state.running) {
                                update { repo.service.systemProxy = it }
                            }
                        LucidRow(
                            "TUN 协议栈",
                            value = repo.service.tunStackMode,
                            onClick =
                                if (state.running) null
                                else {
                                    {
                                        update {
                                            val values = listOf("system", "gvisor", "mixed", "mips")
                                            repo.service.tunStackMode =
                                                values[
                                                    (values.indexOf(repo.service.tunStackMode) +
                                                        1) % values.size]
                                        }
                                    }
                                },
                        )
                    }
                }
                item {
                    LucidSection("分应用代理") {
                        LucidRow(
                            "代理模式",
                            value = accessModeTitle(repo.service.accessControlMode),
                            onClick =
                                if (state.running) null
                                else {
                                    {
                                        update {
                                            val modes = AccessControlMode.values()
                                            repo.service.accessControlMode =
                                                modes[
                                                    (repo.service.accessControlMode.ordinal + 1) %
                                                        modes.size]
                                        }
                                    }
                                },
                        )
                        LucidRow("选择应用", onClick = { navigate("apps") })
                    }
                }
            }
        }
    }
}

private fun accessModeTitle(mode: AccessControlMode) =
    when (mode) {
        AccessControlMode.AcceptAll -> "全部应用"
        AccessControlMode.AcceptSelected -> "仅选定应用"
        AccessControlMode.DenySelected -> "排除选定应用"
    }

@Composable
fun AppsScreen(state: ClientState, vm: ClientViewModel, back: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var system by rememberSaveable { mutableStateOf(vm.repository.ui.accessControlSystemApp) }
    Column(Modifier.fillMaxSize()) {
        LucidHeader("分应用代理", back)
        LucidSearch(query, { query = it }, "搜索应用名称或包名")
        LucidSection(footer = if (state.running) "当前运行中；停止服务后可修改应用列表。" else "应用选择在下次启动服务时生效。") {
            LucidRow(
                "模式",
                value = accessModeTitle(vm.repository.service.accessControlMode),
                onClick =
                    if (state.running) null
                    else {
                        {
                            val modes = AccessControlMode.values()
                            vm.repository.service.accessControlMode =
                                modes[
                                    (vm.repository.service.accessControlMode.ordinal + 1) %
                                        modes.size]
                            vm.changed()
                        }
                    },
            )
            LucidToggle("显示系统应用", system) {
                system = it
                vm.repository.ui.accessControlSystemApp = it
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(
                state.apps.filter {
                    (system || !it.system) &&
                        (it.label.contains(query, true) || it.packageName.contains(query, true))
                },
                key = { it.packageName },
            ) { app ->
                Box(
                    Modifier.padding(horizontal = 20.dp)
                        .background(LocalLucidColors.current.surface)
                ) {
                    LucidToggle(app.label, app.selected, !state.running, app.packageName) {
                        vm.repository.selectApp(app.packageName, it)
                        vm.changed()
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    vm: ClientViewModel,
    state: ClientState,
    back: () -> Unit,
    themeChanged: () -> Unit,
    confirm: (String, () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val repo = vm.repository
    var revision by remember { mutableIntStateOf(0) }
    val component = ComponentName(context, RestartReceiver::class.java)
    val auto =
        context.packageManager.getComponentEnabledSetting(component) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    fun update(block: () -> Unit) {
        block()
        revision++
    }
    Column(Modifier.fillMaxSize()) {
        LucidHeader("应用设置", back)
        key(revision) {
            LazyColumn {
                item {
                    LucidSection("界面") {
                        LucidRow(
                            "外观",
                            value =
                                when (repo.ui.darkMode) {
                                    DarkMode.Auto -> "跟随系统"
                                    DarkMode.ForceLight -> "浅色"
                                    else -> "深色"
                                },
                            onClick = {
                                update {
                                    val modes = DarkMode.values()
                                    repo.ui.darkMode =
                                        modes[(repo.ui.darkMode.ordinal + 1) % modes.size]
                                }
                                themeChanged()
                            },
                        )
                        LucidToggle("从最近任务中隐藏", repo.ui.hideFromRecents) {
                            update {
                                repo.ui.hideFromRecents = it
                                (context as? Activity)?.setExcludeFromRecents(it)
                            }
                        }
                        LucidToggle("隐藏桌面图标", repo.ui.hideAppIcon) { hide ->
                            val action = {
                                update {
                                    repo.ui.hideAppIcon = hide
                                    context.packageManager.setComponentEnabledSetting(
                                        context.mainActivityAlias,
                                        if (hide) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                                        else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                        PackageManager.DONT_KILL_APP,
                                    )
                                }
                            }
                            if (hide) confirm("隐藏桌面图标？之后可从快捷设置磁贴或 Android 应用设置重新进入。", action)
                            else action()
                        }
                    }
                }
                item {
                    LucidSection("服务") {
                        LucidToggle("开机与更新后自动启动", auto) { value ->
                            update {
                                context.packageManager.setComponentEnabledSetting(
                                    component,
                                    if (value) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                    PackageManager.DONT_KILL_APP,
                                )
                            }
                        }
                        LucidToggle("通知显示实时流量", repo.service.dynamicNotification, !state.running) {
                            update { repo.service.dynamicNotification = it }
                        }
                    }
                }
                item {
                    LucidSection("策略显示") {
                        LucidToggle("仅显示可手动选择的策略组", repo.ui.proxyExcludeNotSelectable) {
                            update { repo.ui.proxyExcludeNotSelectable = it }
                        }
                    }
                }
            }
        }
    }
}

private fun Activity.setExcludeFromRecents(hide: Boolean) {
    getSystemService(android.app.ActivityManager::class.java).appTasks.forEach {
        it.setExcludeFromRecents(hide)
    }
}

@Composable
fun AboutScreen(state: ClientState, back: () -> Unit, openLicense: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LucidHeader("关于", back)
        LazyColumn {
            item {
                LucidSection("Lee’s Clash") {
                    LucidRow("应用版本", value = io.github.leenban.clash.BuildConfig.VERSION_NAME)
                    LucidRow("内核版本", subtitle = state.coreVersion.ifBlank { "读取中" })
                    LucidRow("应用标识", subtitle = io.github.leenban.clash.BuildConfig.APPLICATION_ID)
                }
            }
            item {
                LucidSection("开源鸣谢", footer = "本应用基于 CMFA 修改，使用 mihomo 内核。保留原有版权和许可声明。") {
                    LucidRow("Clash Meta for Android", subtitle = "Android 服务与原生桥接")
                    LucidRow("mihomo", subtitle = "代理、DNS 与规则内核")
                    LucidRow("GPL-3.0 许可证", onClick = { openLicense("LICENSE") })
                    LucidRow("第三方开源声明", onClick = { openLicense("NOTICE") })
                }
            }
        }
    }
}

@Composable
fun HelpScreen(back: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LucidHeader("使用帮助", back)
        LazyColumn {
            item {
                LucidSection("开始使用") {
                    LucidRow("1. 导入配置", subtitle = "点按首页左上角配置名，从 URL、文件或二维码导入 Clash YAML 配置。")
                    LucidRow("2. 启动服务", subtitle = "点按右上角启动，并允许 Android VPN 连接请求。")
                    LucidRow("3. 选择代理策略", subtitle = "从出站模式卡片进入代理策略，选择节点并执行测速。")
                    LucidRow("4. 检查网络", subtitle = "在工具中查看实时连接、匹配规则与 DNS 查询结果。")
                }
            }
            item {
                LucidSection("常见问题") {
                    LucidRow("无法启动", subtitle = "确认已有有效配置并授予 VPN 权限。同一时间只能运行一个 VPN 应用。")
                    LucidRow("没有连接记录", subtitle = "先确认服务运行，再发起网络请求；短暂连接可能不在采样时刻出现。")
                    LucidRow("订阅更新失败", subtitle = "检查链接与网络。失败不会替换最后一次成功导入的配置。")
                    LucidRow("需要修改托管配置", subtitle = "先在配置编辑页克隆为本地副本，再修改 YAML。")
                }
            }
        }
    }
}

@Composable
fun LicenseScreen(name: String, text: String, back: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LucidHeader(name, back)
        LazyColumn(Modifier.padding(horizontal = 20.dp)) {
            item {
                Text(
                    text,
                    color = LocalLucidColors.current.text,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
