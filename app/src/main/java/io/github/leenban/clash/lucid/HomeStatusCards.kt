package io.github.leenban.clash.lucid

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leenban.clash.design.lucid.*
import io.github.leenban.clash.service.model.Profile
import io.github.leenban.clash.core.model.TrafficStats

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeSubscriptionCard(profile: Profile?, busy: Boolean, updating: Boolean,
    navigate: (String) -> Unit, update: () -> Unit) {
    val subscription = profile?.type == Profile.Type.Url
    LucidCard(if (subscription) "订阅概览" else "当前配置", LucidSymbol.Document,
        listOf(Color(0xFF53B724), Color(0xFF71D02F)), Modifier.testTag("home-subscription-card")) {
        HomeProfileSummary(profile)
        Spacer(Modifier.weight(1f))
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            CardButton(if (profile == null) "导入配置" else "配置详情") {
                navigate(if (profile == null) "import" else "subscription")
            }
            if (subscription) CardButton(if (updating) "更新中" else "更新订阅",
                enabled = !busy && !updating, onClick = update)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeRuntimeCard(running: Boolean, traffic: TrafficStats, navigate: (String) -> Unit) {
    LucidCard("运行状态", LucidSymbol.Chart, listOf(Color(0xFFED7954), Color(0xFFF8A92C)),
        Modifier.testTag("home-runtime-card")) {
        Column(Modifier.padding(horizontal = 15.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(if (traffic.networkBypassed) "按需已暂停 VPN 接管" else if (running) "服务正在运行" else "服务未启动",
                color = Color.White, fontSize = 15.sp)
            Text("↑ ${formatBytes(traffic.uploadRate)}/s    ↓ ${formatBytes(traffic.downloadRate)}/s",
                color = Color.White, fontSize = 15.sp)
            Text("本次累计 ↑ ${formatBytes(traffic.uploadTotal)}  ↓ ${formatBytes(traffic.downloadTotal)}",
                color = Color.White, fontSize = 14.sp)
            Text("服务运行 ${formatRunTime(if (running) traffic.uptimeMs else 0L)}",
                color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            CardButton("查看连接") { navigate("connections") }
        }
    }
}
