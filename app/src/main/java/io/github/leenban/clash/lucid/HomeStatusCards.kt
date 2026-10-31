package io.github.leenban.clash.lucid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leenban.clash.core.model.TrafficStats
import io.github.leenban.clash.design.lucid.LucidIcon
import io.github.leenban.clash.design.lucid.LucidSymbol
import io.github.leenban.clash.design.lucid.lucidClickable
import io.github.leenban.clash.service.model.Profile

private fun saturatedSum(a: Long, b: Long): Long {
    val first = a.coerceAtLeast(0L)
    val second = b.coerceAtLeast(0L)
    return if (Long.MAX_VALUE - first < second) Long.MAX_VALUE else first + second
}

@Composable
internal fun HomeRuntimeCard(
    running: Boolean,
    traffic: TrafficStats,
    navigate: (String) -> Unit,
    rateHistory: List<Long> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val palette = rememberHomeBentoPalette()
    val (digits, unit) = formatRateDisplay(if (running) traffic.downloadRate else 0L)
    val uploadBytes = if (running) traffic.uploadRate else 0L
    val totalBytes = if (running) saturatedSum(traffic.uploadTotal, traffic.downloadTotal) else 0L

    HomeBentoCard(
        palette = palette,
        cornerRadius = 24.dp,
        glow = BentoGlow.TopStartBlue,
        onClick = { navigate("stats") },
        modifier = modifier.testTag("home-runtime-card"),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeBentoIcon(HomeBentoSymbol.Throughput, palette.primaryText, Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "实时速率",
                    Modifier.weight(1f),
                    color = palette.primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LucidIcon(LucidSymbol.Chevron, palette.mutedText.copy(alpha = 0.65f), Modifier.size(12.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "↓",
                    Modifier.padding(end = 3.dp, bottom = 3.dp),
                    color = palette.blueAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    digits,
                    color = palette.primaryText,
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    unit,
                    Modifier.padding(start = 4.dp, bottom = 3.dp),
                    color = palette.mutedText,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "↑ ${formatBytes(uploadBytes)}/s · 累计 ${formatBytes(totalBytes)}",
                color = palette.mutedText,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(8.dp))
            HomeRateSparkline(
                history = rateHistory,
                running = running,
                lineColor = palette.primaryText,
                accentColor = palette.blueAccent,
                modifier = Modifier.fillMaxWidth().height(34.dp),
            )
        }
    }
}

@Composable
internal fun HomeConnectionsCard(
    running: Boolean,
    traffic: TrafficStats,
    navigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberHomeBentoPalette()
    val activeCount = if (running) traffic.activeConnections.coerceAtLeast(0) else 0
    val statusLine =
        when {
            traffic.networkBypassed -> "按需已暂停"
            running -> "运行 ${formatRunTime(traffic.uptimeMs)}"
            else -> "服务未启动"
        }

    HomeBentoCard(
        palette = palette,
        cornerRadius = 24.dp,
        glow = BentoGlow.BottomEndAmber,
        onClick = { navigate("connections") },
        modifier =
            modifier
                .semantics { contentDescription = "查看连接" }
                .testTag("home-connections-card"),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeBentoIcon(HomeBentoSymbol.Connections, palette.primaryText, Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "实时连接",
                    Modifier.weight(1f),
                    color = palette.primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LucidIcon(LucidSymbol.Chevron, palette.mutedText.copy(alpha = 0.65f), Modifier.size(12.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    activeCount.toString(),
                    color = palette.primaryText,
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    "活动连接",
                    Modifier.padding(start = 5.dp, bottom = 3.dp),
                    color = palette.mutedText,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(6.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                traffic.networkBypassed -> palette.amberAccent
                                running -> palette.readyDot
                                else -> palette.mutedText.copy(alpha = 0.45f)
                            }
                        )
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    statusLine,
                    color = palette.mutedText,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(8.dp))
            HomeConnectionLoadBar(
                activeConnections = activeCount,
                running = running,
                activeColor = palette.primaryText,
                accentColor = palette.amberAccent,
                dimColor = palette.trackFill,
                modifier = Modifier.fillMaxWidth().height(28.dp),
            )
        }
    }
}

@Composable
internal fun HomeSubscriptionCard(
    profile: Profile?,
    busy: Boolean,
    updating: Boolean,
    navigate: (String) -> Unit,
    update: () -> Unit,
) {
    val palette = rememberHomeBentoPalette()
    val subscription = profile?.type == Profile.Type.Url
    val title = if (subscription) "订阅概览" else "当前配置"
    val remaining =
        if (profile != null && profile.type == Profile.Type.Url)
            remainingQuota(profile.total, profile.upload, profile.download)
        else null
    val expired =
        profile != null &&
            profile.type == Profile.Type.Url &&
            profile.expire > 0 &&
            profile.expire <= System.currentTimeMillis()

    val quotaLabel =
        when {
            profile == null -> "尚未选择配置"
            subscription -> {
                val base =
                    remaining?.let { "剩余 ${formatBytes(it)} / ${formatBytes(profile.total)}" }
                        ?: "订阅未提供总额度"
                if (expired) "$base · 已到期" else base
            }
            profile.type == Profile.Type.File -> "${profile.name} · 本地配置"
            else -> "${profile.name} · 外部配置"
        }

    val progress =
        when {
            profile == null -> 0f
            subscription && remaining != null && profile.total > 0L ->
                (remaining.toDouble() / profile.total.toDouble()).toFloat().coerceIn(0f, 1f)
            subscription -> 0f
            else -> 1f
        }

    val metaLine =
        when {
            profile == null -> "点按导入 Clash 订阅链接、本地文件或二维码"
            subscription -> {
                val exp =
                    if (profile.expire > 0)
                        "${if (expired) "已到期" else "到期"} ${profileDate(profile.expire)}"
                    else "未提供到期日"
                "$exp · 更新于 ${profileDate(profile.updatedAt)}"
            }
            else -> "更新于 ${profileDate(profile.updatedAt)}"
        }

    val progressBrush =
        remember(palette, expired, progress) {
            when {
                expired || (subscription && remaining != null && progress < 0.12f) ->
                    Brush.horizontalGradient(listOf(Color(0xFFFF6B5E), palette.amberAccent))
                else ->
                    Brush.horizontalGradient(listOf(palette.blueAccent, palette.emeraldAccent))
            }
        }

    HomeBentoCard(
        palette = palette,
        cornerRadius = 24.dp,
        glow = BentoGlow.EndEmerald,
        onClick = { navigate(if (profile == null) "import" else "subscription") },
        modifier =
            Modifier.fillMaxWidth()
                .semantics { contentDescription = if (profile == null) "导入配置" else "配置详情" }
                .testTag("home-subscription-card"),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeBentoIcon(HomeBentoSymbol.Subscription, palette.primaryText, Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    title,
                    color = palette.primaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    quotaLabel,
                    Modifier.weight(1f),
                    color = if (expired) Color(0xFFFF6B5E) else palette.primaryText.copy(alpha = 0.88f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subscription) {
                    Spacer(Modifier.width(7.dp))
                    Box(
                        Modifier.size(32.dp)
                            .semantics { contentDescription = if (updating) "更新中" else "更新订阅" }
                            .lucidClickable(enabled = !busy && !updating, scale = true, onClick = update),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(26.dp)
                                .clip(CircleShape)
                                .background(palette.pillBg)
                                .border(1.dp, palette.subBorder, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            HomeBentoIcon(
                                HomeBentoSymbol.Refresh,
                                if (updating) palette.mutedText.copy(alpha = 0.45f) else palette.primaryText,
                                Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }
            HomeQuotaProgressBar(
                progress = progress,
                activeBrush = progressBrush,
                trackColor = palette.trackFill,
                modifier = Modifier.fillMaxWidth().height(7.dp),
            )
            Text(
                metaLine,
                color = palette.mutedText,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
