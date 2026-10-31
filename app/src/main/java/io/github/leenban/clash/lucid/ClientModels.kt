package io.github.leenban.clash.lucid

import io.github.leenban.clash.core.model.*
import io.github.leenban.clash.design.model.File
import io.github.leenban.clash.service.model.Profile

data class PolicyGroup(val name: String, val group: ProxyGroup)
data class SmartGroupState(val mode: String = "config", val supported: Boolean = false)
data class SmartSummary(val total: Int = 0, val enabled: Int = 0) {
    val allEnabled get() = total > 0 && enabled == total
}

data class AppChoice(
    val packageName: String,
    val label: String,
    val system: Boolean,
    val selected: Boolean,
)

data class ClientNotice(val id: Long, val text: String)

data class ClientNavigation(val id: Long, val route: String)

data class ClientState(
    val running: Boolean = false,
    val starting: Boolean = false,
    val busy: Boolean = false,
    val pageLoading: Boolean = false,
    val operation: String? = null,
    val notice: ClientNotice? = null,
    val navigation: ClientNavigation? = null,
    val importing: Boolean = false,
    val importStatus: String = "",
    val importedProfile: java.util.UUID? = null,
    val recentProfile: java.util.UUID? = null,
    val updatingProfiles: Set<java.util.UUID> = emptySet(),
    val backupUri: String? = null,
    val backupPreview: io.github.leenban.clash.service.backup.BackupPreview? = null,
    val settingsRevision: Int = 0,
    val onDemandEnabled: Boolean = false,
    val active: Profile? = null,
    val profiles: List<Profile> = emptyList(),
    val mode: TunnelState.Mode = TunnelState.Mode.Rule,
    val traffic: TrafficStats = TrafficStats(),
    val rateHistory: List<Long> = emptyList(),
    val primaryPolicy: PolicyGroup? = null,
    val groups: List<PolicyGroup> = emptyList(),
    val selectedGroup: ProxyGroup? = null,
    val smartMode: String = "config",
    val smartSupported: Boolean = false,
    val smartSummary: SmartSummary? = null,
    val connections: DiagnosticPage<ConnectionInfo> = DiagnosticPage(),
    val connectionQuery: String = "",
    val connection: ConnectionInfo? = null,
    val connectionEnded: Boolean = false,
    val rules: DiagnosticPage<RuleInfo> = DiagnosticPage(),
    val ruleQuery: String = "",
    val dns: DnsResult? = null,
    val providers: List<Provider> = emptyList(),
    val geoAssets: List<GeoAsset> = emptyList(),
    val updatingGeo: String? = null,
    val apps: List<AppChoice> = emptyList(),
    val logs: List<LogMessage> = emptyList(),
    val files: List<File> = emptyList(),
    val overrideJson: String? = null,
    val editingProfile: Profile? = null,
    val extensionJson: String? = null,
    val coreVersion: String = "",
    val error: String? = null,
)

fun formatBytes(value: Long): String {
    if (value < 1024) return "$value B"
    val units = listOf("KB", "MB", "GB", "TB")
    var size = value.toDouble() / 1024
    var index = 0
    while (size >= 1024 && index < units.lastIndex) {
        size /= 1024
        index++
    }
    return String.format(java.util.Locale.ROOT, "%.1f %s", size, units[index])
}

fun modeTitle(mode: TunnelState.Mode) =
    when (mode) {
        TunnelState.Mode.Direct -> "直接连接"
        TunnelState.Mode.Global -> "全局代理"
        else -> "规则模式"
    }

fun modeShortTitle(mode: TunnelState.Mode) =
    when (mode) {
        TunnelState.Mode.Direct -> "直连"
        TunnelState.Mode.Global -> "全局"
        else -> "规则"
    }

fun nextMode(mode: TunnelState.Mode) =
    when (mode) {
        TunnelState.Mode.Global -> TunnelState.Mode.Direct
        TunnelState.Mode.Direct -> TunnelState.Mode.Rule
        else -> TunnelState.Mode.Global
    }

fun formatRateDisplay(bytesPerSecond: Long): Pair<String, String> {
    val safe = bytesPerSecond.coerceAtLeast(0L)
    if (safe == 0L) return "0.00" to "KB/s"
    if (safe < 1024L) {
        val kb = (safe.toDouble() / 1024.0).coerceAtLeast(0.01)
        return String.format(java.util.Locale.ROOT, "%.2f", kb) to "KB/s"
    }
    val units = listOf("KB/s", "MB/s", "GB/s", "TB/s")
    var value = safe.toDouble() / 1024.0
    var index = 0
    while (value >= 1000.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    val digits =
        when {
            value < 10.0 -> String.format(java.util.Locale.ROOT, "%.2f", value)
            value < 100.0 -> String.format(java.util.Locale.ROOT, "%.1f", value)
            else -> String.format(java.util.Locale.ROOT, "%.0f", value)
        }
    return digits to units[index]
}
