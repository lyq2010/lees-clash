package io.github.leenban.clash.lucid

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leenban.clash.core.model.TunnelState
import io.github.leenban.clash.design.lucid.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: ClientState,
    navigate: (String) -> Unit,
    toggle: () -> Unit,
    setMode: (TunnelState.Mode) -> Unit,
    updateProfile: () -> Unit,
) {
    val c = LocalLucidColors.current
    var section by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        HomeHeader(
            state.active?.name,
            state.running,
            state.starting,
            state.busy,
            { navigate("profiles") },
            toggle,
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            listOf("通用", "网络", "覆写").forEachIndexed { i, title ->
                val tint by
                    animateColorAsState(
                        if (section == i) c.blue else c.secondary,
                        tween(160),
                        label = "section-label",
                    )
                val underline by
                    animateColorAsState(
                        if (section == i) c.blue else Color.Transparent,
                        tween(160),
                        label = "section-indicator",
                    )
                Column(
                    Modifier.width(44.dp).height(48.dp).lucidClickable { section = i },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        title,
                        Modifier.weight(1f).wrapContentHeight(),
                        color = tint,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Box(Modifier.height(2.dp).fillMaxWidth().background(underline))
                }
            }
        }
        Crossfade(section, Modifier.weight(1f), tween(160), label = "home-section") { visibleSection
            ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (visibleSection) {
                    0 -> HomeGeneralCards(state, navigate, setMode, updateProfile)
                    1 -> {
                        LucidSection("网络接管") {
                            LucidRow(
                                "VPN 与网络",
                                symbol = LucidSymbol.Network,
                                onClick = { navigate("network") },
                            )
                            LucidRow(
                                "分应用代理",
                                symbol = LucidSymbol.Shield,
                                tint = Color(0xFFAF52DE),
                                onClick = { navigate("apps") },
                            )
                        }
                        LucidSection("网络检查与共享") {
                            LucidRow("IP 与网络检查", symbol = LucidSymbol.Network,
                                onClick = { navigate("network-check") })
                            LucidRow("按需网络", symbol = LucidSymbol.Shield,
                                value = if (!state.onDemandEnabled) "未启用" else if (state.traffic.networkBypassed) "已暂停接管" else "已启用",
                                onClick = { navigate("on-demand") })
                            LucidRow("局域网信息与共享", symbol = LucidSymbol.Network,
                                tint = Color(0xFFFF9F0A), onClick = { navigate("lan") })
                        }
                    }
                    2 -> {
                        LucidSection("配置覆写") {
                            LucidRow(
                                "通用与高级设置",
                                symbol = LucidSymbol.Settings,
                                tint = Color(0xFFAF52DE),
                                onClick = { navigate("override") },
                            )
                            LucidRow(
                                "DNS 设置",
                                symbol = LucidSymbol.Dns,
                                tint = Color(0xFF34C759),
                                onClick = { navigate("dns-settings") },
                            )
                            LucidRow(
                                "Geo 数据与资源",
                                symbol = LucidSymbol.Folder,
                                tint = Color(0xFFFF9F0A),
                                onClick = { navigate("geodata") },
                            )
                            LucidRow(
                                "加密配置密钥",
                                symbol = LucidSymbol.Shield,
                                onClick = { navigate("keys") },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
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
fun ToolsScreen(state: ClientState, vm: ClientViewModel, navigate: (String) -> Unit, confirm: (String, () -> Unit) -> Unit) {
    val c = LocalLucidColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "工具",
            Modifier.padding(start = 20.dp, top = 34.dp, bottom = 17.dp),
            color = c.text,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
        )
        SmartToolsSection(state, vm::setAllSmart, clear = {
            confirm("清除当前配置的 Smart 学习记录？内存记录和本地快照将清除，智能选路开关设置保留。") {
                vm.clearSmartLearning()
            }
        })
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
            LucidRow("清理 DNS 缓存", value = if (state.running) null else "需启动服务",
                symbol = LucidSymbol.Refresh, onClick = if (!state.running || state.busy) null else ({
                    confirm("清理 DNS 缓存？后续请求会重新解析，现有连接和 Fake IP 映射保留。") {
                        vm.task(progress = "正在清理 DNS 缓存", success = "DNS 缓存已清理") {
                            vm.repository.diagnostic("clearDnsCache")
                        }
                    }
                }))
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
    }
}

@Composable
fun MoreScreen(navigate: (String) -> Unit) {
    val c = LocalLucidColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "更多",
            Modifier.padding(start = 20.dp, top = 34.dp, bottom = 17.dp),
            color = c.text,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
        )
        LucidSection("设置") {
            LucidRow(
                "应用设置",
                symbol = LucidSymbol.Settings,
                tint = Color(0xFF8E8E93),
                onClick = { navigate("settings") },
            )
            LucidRow("VPN 与网络", symbol = LucidSymbol.Network, onClick = { navigate("network") })
            LucidRow("通知", symbol = LucidSymbol.Bell, tint = Color(0xFFFF3B30), onClick = { navigate("notifications") })
            LucidRow(
                "配置覆写",
                symbol = LucidSymbol.Document,
                tint = Color(0xFFFF9500),
                onClick = { navigate("override") },
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
    }
}
