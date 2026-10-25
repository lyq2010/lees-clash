package io.github.leenban.clash.lucid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
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
) {
    val c = LocalLucidColors.current
    var section by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).heightIn(min = 48.dp).clickable { navigate("profiles") },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(23.dp).clip(RoundedCornerShape(5.dp)).background(c.blue),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("L", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    state.active?.name ?: "选择配置",
                    Modifier.padding(start = 9.dp).weight(1f),
                    color = c.text,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "⌄",
                    Modifier.padding(start = 6.dp, end = 14.dp),
                    color = c.text,
                    fontSize = 23.sp,
                )
            }
            LucidPill(
                if (state.starting) "连接中" else if (state.running) "停止" else "启动",
                enabled = !state.busy && !state.starting,
                onClick = toggle,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            listOf("通用", "网络", "覆写").forEachIndexed { i, title ->
                Column(
                    Modifier.width(44.dp).clickable { section = i },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        title,
                        Modifier.heightIn(min = 48.dp).wrapContentHeight(),
                        color = if (section == i) c.blue else c.secondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Box(
                        Modifier.height(2.dp)
                            .fillMaxWidth()
                            .background(if (section == i) c.blue else Color.Transparent)
                    )
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (section) {
                0 -> {
                    LucidCard(
                        "出站模式",
                        LucidSymbol.Mode,
                        listOf(Color(0xFF5450D3), Color(0xFF4298EB)),
                    ) {
                        listOf(
                                TunnelState.Mode.Direct,
                                TunnelState.Mode.Global,
                                TunnelState.Mode.Rule,
                            )
                            .forEach { mode ->
                                val selected = state.mode == mode
                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(
                                            if (selected) Color.Black.copy(alpha = .19f)
                                            else Color.Transparent
                                        )
                                        .clickable(enabled = !state.busy) { setMode(mode) }
                                        .padding(
                                            horizontal = 15.dp,
                                            vertical = if (selected) 10.dp else 8.dp,
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.width(34.dp)) {
                                        if (selected)
                                            LucidIcon(
                                                LucidSymbol.Check,
                                                Color.White,
                                                Modifier.size(23.dp),
                                            )
                                    }
                                    Column {
                                        Text(modeTitle(mode), color = Color.White, fontSize = 16.sp)
                                        if (selected)
                                            Text(
                                                when (mode) {
                                                    TunnelState.Mode.Direct -> "所有连接直接发送至目标服务器"
                                                    TunnelState.Mode.Global -> "所有连接使用选定的代理策略"
                                                    else -> "根据配置中的规则决定连接策略"
                                                },
                                                Modifier.padding(top = 9.dp),
                                                color = Color.White.copy(alpha = .6f),
                                                fontSize = 12.sp,
                                                lineHeight = 17.sp,
                                            )
                                    }
                                }
                            }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            CardButton("代理策略") { navigate("groups") }
                            Spacer(Modifier.width(8.dp))
                            CardButton("代理规则") { navigate("rules") }
                        }
                    }
                    LucidCard(
                        "DNS",
                        LucidSymbol.Dns,
                        listOf(Color(0xFF53B724), Color(0xFF71D02F)),
                    ) {
                        Text(
                            "使用当前配置解析域名，并管理 DNS 覆写设置。",
                            Modifier.padding(horizontal = 15.dp),
                            color = Color.White,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(15.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            CardButton("DNS 设置") { navigate("dns-settings") }
                            Spacer(Modifier.width(8.dp))
                            CardButton("域名查询") { navigate("dns") }
                        }
                    }
                    LucidCard(
                        "配置与资源",
                        LucidSymbol.Document,
                        listOf(Color(0xFFED7954), Color(0xFFF8A92C)),
                    ) {
                        Text(
                            state.active?.name ?: "导入配置后即可使用代理与规则",
                            Modifier.padding(horizontal = 15.dp),
                            color = Color.White,
                            fontSize = 15.sp,
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(15.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            CardButton("资源管理") { navigate("providers") }
                            Spacer(Modifier.width(8.dp))
                            CardButton("配置文件") { navigate("profiles") }
                        }
                    }
                    LucidCard(
                        "运行状态",
                        LucidSymbol.Chart,
                        listOf(Color(0xFF73747A), Color(0xFF8C8D94)),
                    ) {
                        Text(
                            if (state.running) "服务正在运行" else "服务未启动",
                            Modifier.padding(horizontal = 15.dp),
                            color = Color.White,
                            fontSize = 15.sp,
                        )
                        Text(
                            "↑ ${formatBytes(state.traffic.uploadRate)}/s    ↓ ${formatBytes(state.traffic.downloadRate)}/s",
                            Modifier.padding(15.dp),
                            color = Color.White,
                            fontSize = 15.sp,
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(end = 15.dp, bottom = 15.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            CardButton("查看连接") { navigate("connections") }
                        }
                    }
                }
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
                    LucidSection("诊断") {
                        LucidRow("DNS 查询", symbol = LucidSymbol.Dns, onClick = { navigate("dns") })
                        LucidRow(
                            "实时连接",
                            symbol = LucidSymbol.List,
                            tint = Color(0xFFFF453A),
                            onClick = { navigate("connections") },
                        )
                        LucidRow(
                            "实时流量",
                            symbol = LucidSymbol.Chart,
                            tint = Color(0xFFFF9F0A),
                            onClick = { navigate("stats") },
                        )
                    }
                }
                2 -> {
                    LucidSection("配置覆写", footer = "仅设置明确需要更改的选项，其余选项继续使用配置文件中的值。") {
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

@Composable
private fun CardButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 48.dp).clickable(onClick = onClick),
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
fun ToolsScreen(navigate: (String) -> Unit) {
    val c = LocalLucidColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "工具",
            Modifier.padding(start = 20.dp, top = 34.dp, bottom = 17.dp),
            color = c.text,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
        )
        LucidSection("当前会话") {
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
            LucidRow(
                "代理策略",
                symbol = LucidSymbol.Network,
                tint = Color(0xFF34C759),
                onClick = { navigate("groups") },
            )
            LucidRow(
                "运行日志",
                symbol = LucidSymbol.Document,
                tint = Color(0xFFFF2D55),
                onClick = { navigate("logs") },
            )
        }
        LucidSection("配置与资源") {
            LucidRow(
                "规则检索",
                symbol = LucidSymbol.Search,
                tint = Color(0xFFAF52DE),
                onClick = { navigate("rules") },
            )
            LucidRow(
                "资源提供器",
                symbol = LucidSymbol.Folder,
                tint = Color(0xFF32ADE6),
                onClick = { navigate("providers") },
            )
            LucidRow(
                "配置管理",
                symbol = LucidSymbol.Document,
                tint = Color(0xFFFFCC00),
                onClick = { navigate("profiles") },
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
            LucidRow(
                "分应用代理",
                symbol = LucidSymbol.Shield,
                tint = Color(0xFFAF52DE),
                onClick = { navigate("apps") },
            )
            LucidRow(
                "配置覆写",
                symbol = LucidSymbol.Document,
                tint = Color(0xFFFF9500),
                onClick = { navigate("override") },
            )
        }
        LucidSection("支持") {
            LucidRow(
                "使用帮助",
                symbol = LucidSymbol.Document,
                tint = Color(0xFF34C759),
                onClick = { navigate("help") },
            )
            LucidRow("关于 Lee’s Clash", symbol = LucidSymbol.Home, onClick = { navigate("about") })
        }
    }
}
