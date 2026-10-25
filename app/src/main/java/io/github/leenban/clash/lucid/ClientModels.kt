package io.github.leenban.clash.lucid

import io.github.leenban.clash.core.model.*
import io.github.leenban.clash.service.model.Profile
import io.github.leenban.clash.design.model.File

data class PolicyGroup(val name: String, val group: ProxyGroup)
data class AppChoice(val packageName: String, val label: String, val system: Boolean, val selected: Boolean)
data class ClientState(
    val running: Boolean = false, val starting: Boolean = false, val busy: Boolean = false,
    val active: Profile? = null, val profiles: List<Profile> = emptyList(),
    val mode: TunnelState.Mode = TunnelState.Mode.Rule, val traffic: TrafficStats = TrafficStats(),
    val groups: List<PolicyGroup> = emptyList(), val selectedGroup: ProxyGroup? = null,
    val connections: DiagnosticPage<ConnectionInfo> = DiagnosticPage(),
    val connection: ConnectionInfo? = null, val connectionEnded: Boolean = false,
    val rules: DiagnosticPage<RuleInfo> = DiagnosticPage(), val dns: DnsResult? = null,
    val providers: List<Provider> = emptyList(), val apps: List<AppChoice> = emptyList(),
    val logs: List<LogMessage> = emptyList(), val files: List<File> = emptyList(),
    val overrideJson: String? = null, val editingProfile: Profile? = null,
    val coreVersion: String = "", val error: String? = null,
)

fun formatBytes(value: Long): String {
    if(value<1024)return "$value B"
    val units=listOf("KB","MB","GB","TB")
    var size=value.toDouble()/1024;var index=0
    while(size>=1024&&index<units.lastIndex){size/=1024;index++}
    return String.format(java.util.Locale.ROOT,"%.1f %s",size,units[index])
}

fun modeTitle(mode:TunnelState.Mode)=when(mode){TunnelState.Mode.Direct->"直接连接";TunnelState.Mode.Global->"全局代理";else->"规则模式"}
