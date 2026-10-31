package io.github.leenban.clash.lucid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leenban.clash.core.model.TunnelState
import io.github.leenban.clash.design.lucid.*

@Composable
fun HomeScreen(
    state: ClientState,
    navigate: (String) -> Unit,
    toggle: () -> Unit,
    setMode: (TunnelState.Mode) -> Unit,
    updateProfile: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        HomeHeader(
            profileName = state.active?.name,
            running = state.running,
            starting = state.starting,
            busy = state.busy,
            selectProfile = { navigate("profiles") },
            toggle = toggle,
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(4.dp))
            HomeGeneralCards(
                state = state,
                navigate = navigate,
                setMode = setMode,
                updateProfile = updateProfile,
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun CardButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 48.dp).lucidClickable(enabled = enabled, scale = true, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            Modifier.clip(RoundedCornerShape(9.dp))
                .background(Color.Black.copy(alpha = .15f))
                .padding(horizontal = 15.dp, vertical = 6.dp),
            color = Color.White,
            fontSize = 14.sp,
        )
    }
}

@Composable
fun ToolsScreen(
    state: ClientState,
    vm: ClientViewModel,
    navigate: (String) -> Unit,
    confirm: (String, () -> Unit) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LucidHeader("工具")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SmartToolsSection(
                state,
                vm::setAllSmart,
                clear = {
                    confirm("清除当前配置的 Smart 学习记录？内存记录和本地快照将清除，智能选路开关设置保留。") {
                        vm.clearSmartLearning()
                    }
                },
            )
            LucidSection("网络诊断") {
                LucidRow("IP 与网络检查", symbol = LucidSymbol.Network, onClick = { navigate("network-check") })
                LucidRow(
                    "实时连接",
                    symbol = LucidSymbol.List,
                    tint = Color(0xFFFF3B30),
                    onClick = { navigate("connections") },
                )
                LucidRow("DNS 查询", symbol = LucidSymbol.Dns, onClick = { navigate("dns") })
                LucidRow(
                    "实时统计",
                    symbol = LucidSymbol.Chart,
                    tint = Color(0xFFFF9500),
                    onClick = { navigate("stats") },
                )
                LucidRow("内存详情", symbol = LucidSymbol.Chart, onClick = { navigate("memory") })
                LucidRow(
                    "清理 DNS 缓存",
                    value = if (state.running) null else "需启动服务",
                    symbol = LucidSymbol.Refresh,
                    onClick =
                        if (!state.running || state.busy) null
                        else ({
                            confirm("清理 DNS 缓存？后续请求会重新解析，现有连接和 Fake IP 映射保留。") {
                                vm.task(progress = "正在清理 DNS 缓存", success = "DNS 缓存已清理") {
                                    vm.repository.diagnostic("clearDnsCache")
                                }
                            }
                        }),
                )
                LucidRow(
                    "运行日志",
                    symbol = LucidSymbol.Document,
                    tint = Color(0xFFFF2D55),
                    onClick = { navigate("logs") },
                )
                LucidRow(
                    "规则检索",
                    symbol = LucidSymbol.Search,
                    tint = Color(0xFFAF52DE),
                    onClick = { navigate("rules") },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun MoreScreen(navigate: (String) -> Unit, state: ClientState? = null) {
    val onDemandValue =
        state?.let {
            if (!it.onDemandEnabled) "未启用"
            else if (it.traffic.networkBypassed) "已暂停接管"
            else "已启用"
        }
    Column(Modifier.fillMaxSize()) {
        LucidHeader("更多")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            LucidSection("设置") {
                LucidRow(
                    "应用设置",
                    symbol = LucidSymbol.Settings,
                    tint = Color(0xFF8E8E93),
                    onClick = { navigate("settings") },
                )
                LucidRow("VPN 与网络", symbol = LucidSymbol.Network, onClick = { navigate("network") })
                LucidRow(
                    "分应用代理",
                    symbol = LucidSymbol.Shield,
                    tint = Color(0xFFAF52DE),
                    onClick = { navigate("apps") },
                )
                LucidRow(
                    "按需网络",
                    value = onDemandValue,
                    symbol = LucidSymbol.Shield,
                    onClick = { navigate("on-demand") },
                )
                LucidRow(
                    "局域网信息与共享",
                    symbol = LucidSymbol.Network,
                    tint = Color(0xFFFF9F0A),
                    onClick = { navigate("lan") },
                )
                LucidRow(
                    "通知",
                    symbol = LucidSymbol.Bell,
                    tint = Color(0xFFFF3B30),
                    onClick = { navigate("notifications") },
                )
                LucidRow(
                    "配置覆写",
                    symbol = LucidSymbol.Document,
                    tint = Color(0xFFFF9500),
                    onClick = { navigate("override") },
                )
                LucidRow(
                    "DNS 设置",
                    symbol = LucidSymbol.Dns,
                    tint = Color(0xFF34C759),
                    onClick = { navigate("dns-settings") },
                )
            }
            LucidSection("配置资源") {
                LucidRow("本地备份与恢复", symbol = LucidSymbol.Folder, onClick = { navigate("backup") })
                LucidRow("外部资源", symbol = LucidSymbol.Folder, onClick = { navigate("providers") })
                LucidRow("Geo 数据", symbol = LucidSymbol.Dns, onClick = { navigate("geodata") })
                LucidRow("加密配置密钥", symbol = LucidSymbol.Shield, onClick = { navigate("keys") })
            }
            LucidSection("支持") {
                LucidRow("应用更新", symbol = LucidSymbol.Refresh, onClick = { navigate("update") })
                LucidRow(
                    "使用帮助",
                    symbol = LucidSymbol.Document,
                    tint = Color(0xFF34C759),
                    onClick = { navigate("help") },
                )
                LucidRow("关于 Lee's Clash", symbol = LucidSymbol.Home, onClick = { navigate("about") })
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
