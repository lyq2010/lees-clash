package io.github.leenban.clash.lucid

import androidx.compose.runtime.*
import io.github.leenban.clash.design.lucid.*
import kotlinx.serialization.json.*

@Composable
internal fun ExtensionEntryEditor(
    section: String,
    advanced: Boolean,
    text: String,
    change: (String) -> Unit,
    error: String?,
) {
    if (advanced) {
        LucidSection("JSON", footer = error ?: "可填写 NTP、嗅探、DNS、全局 User-Agent 等原生字段。") {
            LucidField("高级字段 JSON", text, change, multiline = true)
        }
        return
    }
    if (section == "rules") {
        val parts = text.split(',')
        fun update(index: Int, value: String) {
            val values = parts.toMutableList()
            while (values.size <= index) values.add("")
            values[index] = value
            change(values.joinToString(","))
        }
        LucidSection("规则", footer = error ?: "规则按列表顺序优先匹配。复杂逻辑规则可在完整表达式中编辑。") {
            LucidField("类型", parts.firstOrNull().orEmpty(), { update(0, it) })
            LucidField("内容", parts.getOrNull(1).orEmpty(), { update(1, it) })
            if (parts.firstOrNull() != "MATCH")
                LucidField("目标策略", parts.getOrNull(2).orEmpty(), { update(2, it) })
            LucidField("完整表达式", text, change, multiline = true)
        }
        return
    }
    val obj = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
    fun value(key: String) = (obj?.get(key) as? JsonPrimitive)?.content.orEmpty()
    fun field(key: String, value: JsonElement) {
        change(
            JsonObject((obj ?: JsonObject(emptyMap())).toMutableMap().apply { put(key, value) })
                .toString()
        )
    }
    LucidSection("参数", footer = error ?: "协议的其他参数可在完整 JSON 中填写，保存时由内核校验。") {
        LucidField("名称", value("name"), { field("name", JsonPrimitive(it)) })
        LucidField("类型", value("type"), { field("type", JsonPrimitive(it)) })
        if (section == "proxies") {
            LucidField("服务器", value("server"), { field("server", JsonPrimitive(it)) })
            LucidField(
                "端口",
                value("port"),
                { value ->
                    field("port", value.toIntOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(value))
                },
            )
        } else {
            LucidField(
                "成员（每行一个）",
                (obj?.get("proxies") as? JsonArray)
                    ?.joinToString("\n") { (it as? JsonPrimitive)?.content.orEmpty() }
                    .orEmpty(),
                { value ->
                    field(
                        "proxies",
                        JsonArray(
                            value
                                .lines()
                                .filter { it.isNotBlank() }
                                .map { JsonPrimitive(it.trim()) }
                        ),
                    )
                },
                multiline = true,
            )
        }
        LucidField("完整 JSON", text, change, multiline = true)
    }
}
