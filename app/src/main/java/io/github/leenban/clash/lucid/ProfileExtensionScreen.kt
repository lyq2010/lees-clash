package io.github.leenban.clash.lucid

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import io.github.leenban.clash.design.lucid.*
import kotlinx.serialization.json.*

private val extensionSections =
    linkedMapOf("rules" to "规则", "proxies" to "代理", "proxy-groups" to "策略组")

@Composable
fun ProfileExtensionScreen(
    uuid: String,
    state: ClientState,
    vm: ClientViewModel,
    back: () -> Unit,
    confirm: (String, () -> Unit) -> Unit,
) {
    if (state.extensionJson == null) {
        Column { LucidHeader("配置扩展", back); LucidEmpty(if (state.pageLoading) "正在读取配置扩展" else "无法读取配置扩展") }
        return
    }
    val original = state.extensionJson ?: "{}"
    var draft by rememberSaveable(uuid, original) { mutableStateOf(original) }
    var section by rememberSaveable(uuid) { mutableStateOf("rules") }
    var editing by rememberSaveable(uuid) { mutableStateOf<Int?>(null) }
    var entry by rememberSaveable(uuid) { mutableStateOf("") }
    var fieldError by remember { mutableStateOf<String?>(null) }
    val root = Json.parseToJsonElement(draft).jsonObject
    val items = root[section] as? JsonArray ?: JsonArray(emptyList())
    fun replaceItems(values: List<JsonElement>) {
        draft = JsonObject(root.toMutableMap().apply { put(section, JsonArray(values)) }).toString()
    }
    val leave: () -> Unit = {
        if (editing != null) {
            editing = null
            fieldError = null
        } else if (draft != original) confirm("放弃尚未保存的配置扩展？", back) else back()
    }
    BackHandler(onBack = leave)
    Column(Modifier.fillMaxSize()) {
        LucidHeader(
            if (editing == null) "配置扩展"
            else if (editing == -3) "配置脚本"
            else if (editing == -2) "高级扩展" else "编辑${extensionSections[section]}",
            leave,
            if (editing == null) "保存" else "完成",
            actionEnabled = !state.busy,
        ) {
            if (editing == null)
                vm.task(done = back, progress = "正在验证配置扩展", success = "配置扩展已保存") {
                    vm.repository.saveExtension(uuid, draft)
                    vm.changed()
                }
            else
                try {
                    val index = editing!!
                    if (index == -3) {
                        draft =
                            JsonObject(
                                    root.toMutableMap().apply {
                                        put("script", JsonPrimitive(entry))
                                    }
                                )
                                .toString()
                    } else if (index == -2) {
                        val value = Json.parseToJsonElement(entry).jsonObject
                        draft =
                            JsonObject(root.toMutableMap().apply { put("override", value) })
                                .toString()
                    } else {
                        val value =
                            if (section == "rules") {
                                require(entry.isNotBlank()) { "规则不能为空" }
                                JsonPrimitive(entry.trim())
                            } else {
                                val obj = Json.parseToJsonElement(entry).jsonObject
                                require(!obj["name"]?.jsonPrimitive?.content.isNullOrBlank()) {
                                    "名称不能为空"
                                }
                                obj
                            }
                        replaceItems(
                            items.toMutableList().apply {
                                if (index < 0) add(value) else set(index, value)
                            }
                        )
                    }
                    editing = null
                    fieldError = null
                } catch (e: Exception) {
                    fieldError = e.message ?: "输入无效"
                }
        }
        LazyColumn(Modifier.weight(1f)) {
            if (editing != null) {
                item {
                    if (editing == -3)
                        LucidSection(
                            "JavaScript",
                            footer =
                                fieldError
                                    ?: "定义 main(config) 并同步返回配置对象。仅处理配置；不提供网络、文件或 HTTP 抓包接口。运行时限 500ms、堆上限 32MB；留空停用。",
                        ) {
                            LucidField("脚本内容", entry, { entry = it }, multiline = true)
                        }
                    else
                        ExtensionEntryEditor(
                            section,
                            editing == -2,
                            entry,
                            { entry = it },
                            fieldError,
                        )
                }
                if (editing!! >= 0)
                    item {
                        LucidSection("排序与移除") {
                            if (editing!! > 0)
                                LucidRow(
                                    "上移一项",
                                    onClick = {
                                        val index = editing!!
                                        replaceItems(
                                            items.toMutableList().apply {
                                                add(index - 1, removeAt(index))
                                            }
                                        )
                                        editing = index - 1
                                    },
                                )
                            LucidRow(
                                "移除该项",
                                destructive = true,
                                onClick = {
                                    confirm("从扩展中移除此项？保存后生效。") {
                                        replaceItems(
                                            items.toMutableList().apply { removeAt(editing!!) }
                                        )
                                        editing = null
                                    }
                                },
                            )
                        }
                    }
            } else {
                item {
                    LucidSection(
                        "扩展范围",
                        footer = "扩展独立保存，更新订阅后仍保留。自定义规则优先匹配；同名代理与策略组替换原项。保存前由内核验证。",
                    ) {
                        extensionSections.forEach { (key, title) ->
                            LucidRow(title, selected = section == key, onClick = { section = key })
                        }
                    }
                }
                item {
                    LucidSection(extensionSections.getValue(section)) {
                        LucidRow(
                            "添加${extensionSections[section]}",
                            symbol = LucidSymbol.Add,
                            onClick = {
                                entry =
                                    when (section) {
                                        "rules" -> "DOMAIN-SUFFIX,example.com,DIRECT"
                                        "proxies" ->
                                            "{\"name\":\"自定义代理\",\"type\":\"socks5\",\"server\":\"127.0.0.1\",\"port\":1080}"
                                        else ->
                                            "{\"name\":\"自定义策略组\",\"type\":\"select\",\"proxies\":[\"DIRECT\"]}"
                                    }
                                editing = -1
                            },
                        )
                    }
                }
                items(items.size) { index ->
                    val value = items[index]
                    val label =
                        if (section == "rules") value.jsonPrimitive.content
                        else value.jsonObject["name"]?.jsonPrimitive?.content.orEmpty()
                    LucidSection {
                        LucidRow(
                            label,
                            onClick = {
                                entry =
                                    if (section == "rules") value.jsonPrimitive.content
                                    else value.toString()
                                editing = index
                            },
                        )
                    }
                }
                item {
                    LucidSection("高级", footer = "JSON 对象按 mihomo 字段覆盖原配置；全局覆写优先于此处。") {
                        LucidRow(
                            "高级字段",
                            onClick = {
                                entry = (root["override"] ?: JsonObject(emptyMap())).toString()
                                editing = -2
                            },
                        )
                        LucidRow(
                            "配置生成脚本",
                            onClick = {
                                entry =
                                    root["script"]?.jsonPrimitive?.content
                                        ?: "function main(config) {\n  return config;\n}"
                                editing = -3
                            },
                        )
                    }
                }
            }
        }
    }
}
