@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.github.leenban.clash.lucid

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import io.github.leenban.clash.core.model.ConfigurationOverride
import io.github.leenban.clash.design.lucid.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.*

// One schema drives the editor so new or less frequently used fields are not lost.
private val overrideTitles =
    mapOf(
        "port" to "HTTP 端口",
        "socks-port" to "SOCKS 端口",
        "mixed-port" to "混合端口",
        "redir-port" to "重定向端口",
        "tproxy-port" to "透明代理端口",
        "allow-lan" to "允许局域网访问",
        "bind-address" to "监听地址",
        "mode" to "出站模式",
        "log-level" to "日志级别",
        "ipv6" to "IPv6",
        "external-controller" to "外部控制器",
        "external-controller-tls" to "TLS 控制器",
        "external-controller-cors" to "跨域访问",
        "secret" to "控制器密钥",
        "hosts" to "本地映射",
        "authentication" to "代理认证",
        "unified-delay" to "统一延迟测试",
        "geodata-mode" to "Geo 数据模式",
        "tcp-concurrent" to "TCP 并发",
        "find-process-mode" to "应用识别模式",
        "dns" to "DNS 设置",
        "sniffer" to "协议嗅探",
        "geox-url" to "Geo 数据更新地址",
        "clash-for-android" to "系统 DNS",
        "enable" to "启用",
        "prefer-h3" to "优先 HTTP/3",
        "listen" to "监听地址",
        "use-hosts" to "使用本地映射",
        "enhanced-mode" to "增强模式",
        "nameserver" to "域名服务器",
        "fallback" to "备用服务器",
        "default-nameserver" to "默认服务器",
        "fake-ip-filter" to "Fake IP 过滤",
        "fake-ip-filter-mode" to "Fake IP 过滤模式",
        "fallback-filter" to "备用解析过滤",
        "nameserver-policy" to "域名解析策略",
        "append-system-dns" to "附加系统 DNS",
        "sniff" to "嗅探协议",
        "override-destination" to "覆盖目标地址",
        "force-dns-mapping" to "强制 DNS 映射",
        "parse-pure-ip" to "解析纯 IP 连接",
        "skip-domain" to "跳过域名",
        "force-domain" to "强制嗅探域名",
        "ports" to "端口范围",
        "geoip" to "GeoIP",
        "geoip-code" to "GeoIP 地区",
        "ipcidr" to "IP 网段",
        "domain" to "域名",
        "geoip-url" to "GeoIP 地址",
        "geosite-url" to "GeoSite 地址",
        "mmdb-url" to "MMDB 地址",
    )

private fun elementAt(root: JsonElement, path: List<String>): JsonElement =
    path.fold(root) { node, key -> node.jsonObject[key] ?: JsonNull }

private fun replaceAt(root: JsonElement, path: List<String>, value: JsonElement): JsonElement {
    if (path.isEmpty()) return value
    val obj = root.jsonObject.toMutableMap()
    val key = path.first()
    obj[key] = replaceAt(obj[key] ?: JsonNull, path.drop(1), value)
    return JsonObject(obj)
}

private fun schemaAt(path: List<String>): SerialDescriptor =
    path.fold(ConfigurationOverride.serializer().descriptor) { descriptor, key ->
        descriptor.getElementDescriptor(descriptor.getElementIndex(key))
    }

@Composable
fun OverrideScreen(
    state: ClientState,
    vm: ClientViewModel,
    dnsOnly: Boolean,
    back: () -> Unit,
    confirm: (String, () -> Unit) -> Unit,
) {
    val original = state.overrideJson
    if (original == null) {
        Column {
            LucidHeader("配置覆写", back)
            LucidEmpty("正在读取配置")
        }
        return
    }
    var draft by rememberSaveable(original) { mutableStateOf(original) }
    var path by rememberSaveable(dnsOnly) { mutableStateOf(if (dnsOnly) "dns" else "") }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var fieldError by rememberSaveable { mutableStateOf<String?>(null) }
    val keys = path.split('/').filter { it.isNotBlank() }
    val root = Json.parseToJsonElement(draft)
    val node = elementAt(root, keys)
    val schema = schemaAt(keys)
    fun exit() {
        if (draft != original) confirm("放弃尚未保存的覆写修改？", back) else back()
    }
    val goBack: () -> Unit = {
        when {
            editing != null -> {
                editing = null
                fieldError = null
            }
            keys.isNotEmpty() && !(dnsOnly && keys == listOf("dns")) ->
                path = keys.dropLast(1).joinToString("/")
            else -> exit()
        }
    }
    BackHandler(onBack = goBack)
    Column(Modifier.fillMaxSize()) {
        LucidHeader(
            editing?.let { overrideTitles[it] ?: it }
                ?: if (keys.isEmpty()) "配置覆写" else overrideTitles[keys.last()] ?: keys.last(),
            goBack,
            if (editing == null) "完成" else "确定",
        ) {
            if (editing == null) vm.task(done = back) { vm.repository.saveOverride(draft) }
            else
                try {
                    val key = editing!!
                    val descriptor = schema.getElementDescriptor(schema.getElementIndex(key))
                    val value =
                        if (text.isBlank() && descriptor.isNullable) JsonNull
                        else
                            when (descriptor.kind) {
                                PrimitiveKind.STRING -> JsonPrimitive(text)
                                PrimitiveKind.INT,
                                PrimitiveKind.LONG -> JsonPrimitive(text.toLong())
                                PrimitiveKind.FLOAT,
                                PrimitiveKind.DOUBLE -> JsonPrimitive(text.toDouble())
                                else -> Json.parseToJsonElement(text)
                            }
                    if (key.endsWith("port") && value != JsonNull)
                        require(value.jsonPrimitive.int in 0..65535) { "端口应在 0 至 65535 之间" }
                    val updated = replaceAt(root, keys + key, value)
                    vm.repository.configurationJson.decodeFromJsonElement(
                        ConfigurationOverride.serializer(),
                        updated,
                    )
                    draft = updated.toString()
                    editing = null
                    fieldError = null
                } catch (e: Exception) {
                    fieldError = e.message ?: "请输入有效值"
                }
        }
        LazyColumn(Modifier.weight(1f)) {
            if (editing != null) {
                val key = editing!!
                val desc = schema.getElementDescriptor(schema.getElementIndex(key))
                item {
                    LucidSection(
                        key,
                        footer = fieldError ?: if (desc.isNullable) "留空表示不覆写，继续使用配置文件的值。" else null,
                    ) {
                        LucidField(
                            "",
                            text,
                            { text = it },
                            multiline =
                                desc.kind == StructureKind.LIST || desc.kind == StructureKind.MAP,
                            secret = key == "secret",
                        )
                    }
                }
            } else {
                item {
                    LucidSection(footer = "修改在点按“完成”后生效。未设置的项目保持配置文件原值。") {
                        for (i in 0 until schema.elementsCount) {
                            val key = schema.getElementName(i)
                            val descriptor = schema.getElementDescriptor(i)
                            val value = node.jsonObject[key] ?: JsonNull
                            val composite =
                                descriptor.kind == StructureKind.CLASS ||
                                    descriptor.kind == StructureKind.OBJECT
                            val label = overrideTitles[key] ?: key
                            LucidRow(
                                label,
                                subtitle = if (label != key) key else null,
                                value =
                                    when {
                                        composite -> null
                                        value == JsonNull -> "不覆写"
                                        descriptor.kind == PrimitiveKind.BOOLEAN ->
                                            if (value.jsonPrimitive.boolean) "启用" else "停用"
                                        value is JsonPrimitive -> value.content
                                        else ->
                                            "${if(value is JsonArray)value.size else value.jsonObject.size} 项"
                                    },
                                onClick = {
                                    when {
                                        composite -> path = (keys + key).joinToString("/")
                                        descriptor.kind == PrimitiveKind.BOOLEAN -> {
                                            val next =
                                                when (value) {
                                                    JsonNull -> JsonPrimitive(true)
                                                    JsonPrimitive(true) -> JsonPrimitive(false)
                                                    else ->
                                                        if (descriptor.isNullable) JsonNull
                                                        else JsonPrimitive(true)
                                                }
                                            draft = replaceAt(root, keys + key, next).toString()
                                        }
                                        descriptor.kind == SerialKind.ENUM -> {
                                            val values =
                                                (if (descriptor.isNullable)
                                                    listOf<JsonElement>(JsonNull)
                                                else emptyList()) +
                                                    (0 until descriptor.elementsCount).map {
                                                        JsonPrimitive(descriptor.getElementName(it))
                                                    }
                                            val next =
                                                values[(values.indexOf(value) + 1) % values.size]
                                            draft = replaceAt(root, keys + key, next).toString()
                                        }
                                        else -> {
                                            editing = key
                                            text =
                                                if (value == JsonNull) ""
                                                else if (value is JsonPrimitive) value.content
                                                else value.toString()
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
                if (keys.isEmpty())
                    item {
                        LucidSection {
                            LucidRow(
                                "清除持久覆写",
                                destructive = true,
                                onClick = {
                                    confirm("清除全部持久覆写？原配置文件不会改变。") {
                                        vm.task(done = back) { vm.repository.resetOverride() }
                                    }
                                },
                            )
                        }
                    }
            }
        }
    }
}
