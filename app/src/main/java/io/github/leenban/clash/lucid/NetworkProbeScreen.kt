package io.github.leenban.clash.lucid

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import io.github.leenban.clash.design.lucid.*
import java.text.DateFormat
import java.util.Date
import kotlinx.serialization.json.*

@Composable
fun NetworkProbeScreen(state: ClientState, vm: ClientViewModel, back: () -> Unit) {
    val repo = vm.repository
    val preferences = remember {
        repo.context.getSharedPreferences("network-probes", Context.MODE_PRIVATE)
    }
    var ip by rememberSaveable { mutableStateOf(true) }
    var url by rememberSaveable { mutableStateOf("https://api.ipquery.io/?format=json") }
    var direct by rememberSaveable { mutableStateOf(false) }
    var save by remember { mutableStateOf(preferences.getBoolean("save", false)) }
    var result by remember { mutableStateOf<JsonObject?>(null) }
    var history by remember { mutableStateOf(repo.probeHistory()) }
    Column(Modifier.fillMaxSize()) {
        LucidHeader("IP 与网络检查", back, "检查", actionEnabled = !state.busy) {
            vm.task(progress = "正在检查网络") {
                val value = repo.networkProbe(url.trim(), direct, ip)
                result = value
                if (save) history = repo.saveProbe(value)
                vm.notice("网络检查完成")
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            item {
                LucidSection(
                    "检查选项",
                    footer =
                        if (ip) "手动请求 IPQuery 或兼容 JSON 服务，服务方可见出口 IP。归属及风险标记由第三方返回，不代表安全保证。"
                        else "读取最多 64KB 响应用于连通性检查，不保存正文。",
                ) {
                    LucidRow(
                        "类型",
                        value = if (ip) "出口 IP 与风险信息" else "HTTP 连通性",
                        onClick = {
                            ip = !ip
                            url =
                                if (ip) "https://api.ipquery.io/?format=json"
                                else "https://www.gstatic.com/generate_204"
                        },
                    )
                    LucidField("服务地址", url, { url = it })
                    LucidToggle("强制直连", direct) { direct = it }
                    LucidToggle("保存最近 20 次结果（仅本机）", save) {
                        save = it
                        preferences.edit().putBoolean("save", it).apply()
                    }
                }
            }
            result?.let { value -> item { NetworkProbeDetails(value) } }
            if (history.isNotEmpty())
                item {
                    LucidSection("本地历史", footer = "仅保存域名和检查结果，不保存请求路径、查询参数或响应正文。") {
                        history.forEach { value ->
                            val item = value.jsonObject
                            LucidRow(
                                item["ip"]?.jsonPrimitive?.content
                                    ?: item.getValue("host").jsonPrimitive.content,
                                subtitle =
                                    DateFormat.getDateTimeInstance()
                                        .format(Date(item.getValue("at").jsonPrimitive.long)),
                                value = "${item.getValue("elapsedMs").jsonPrimitive.content} ms",
                                onClick = { result = item },
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun NetworkProbeDetails(value: JsonObject) {
    LucidSection("检查结果") {
        for ((key, label) in
            listOf(
                "host" to "服务",
                "ip" to "出口 IP",
                "country" to "国家 / 地区",
                "city" to "城市",
                "isp" to "运营商",
                "asn" to "ASN",
                "status" to "HTTP 状态",
                "elapsedMs" to "耗时（ms）",
                "sampleBytes" to "采样响应（字节）",
            )) {
            value[key]
                ?.jsonPrimitive
                ?.content
                ?.takeIf { it.isNotBlank() }
                ?.let { LucidRow(label, value = it) }
        }
        LucidRow(
            "路径",
            value = if (value.getValue("direct").jsonPrimitive.boolean) "强制直连" else "当前内核路由",
        )
        val risk = value["risk"]?.jsonObject
        if (risk != null) {
            for ((key, label) in
                listOf(
                    "is_mobile" to "移动网络",
                    "is_vpn" to "VPN",
                    "is_proxy" to "公开代理",
                    "is_tor" to "Tor",
                    "is_datacenter" to "数据中心",
                )) {
                LucidRow(
                    label,
                    value =
                        when (risk[key]?.jsonPrimitive?.booleanOrNull) {
                            true -> "是"
                            false -> "否"
                            null -> "未提供"
                        },
                )
            }
            LucidRow("风险分数", value = risk["risk_score"]?.jsonPrimitive?.content ?: "未提供")
        }
    }
}
