package io.github.leenban.clash.service.backup

import android.content.SharedPreferences
import kotlinx.serialization.json.*

object PreferenceCodec {
    val serviceKeys =
        setOf(
            "bypass_private_network",
            "access_control_mode",
            "access_control_packages",
            "dns_hijacking",
            "system_proxy",
            "allow_bypass",
            "allow_ipv6",
            "tun_stack_mode",
            "dynamic_notification",
            "on_demand_enabled", "on_demand_ssids", "on_demand_cellular",
        )
    val uiKeys =
        setOf(
            "enable_vpn",
            "dark_mode",
            "hide_app_icon",
            "hide_from_recents",
            "proxy_exclude_not_selectable",
            "proxy_line",
            "proxy_sort",
            "proxy_last_group",
            "group_layout", "node_layout",
            "access_control_sort",
            "access_control_reverse",
            "access_control_system_app",
        )

    fun validate(data: JsonObject, keys: Set<String>) {
        data
            .filterKeys { it in keys }
            .forEach { (key, item) ->
                val value = item.jsonObject.getValue("value")
                val expected =
                    when (key) {
                    "access_control_packages", "on_demand_ssids" -> "set"
                        "proxy_line" -> "int"
                        "access_control_mode",
                        "tun_stack_mode",
                        "dark_mode",
                        "proxy_sort",
                        "proxy_last_group",
                        "group_layout", "node_layout",
                        "access_control_sort" -> "string"
                        else -> "boolean"
                    }
                val type = item.jsonObject.getValue("type").jsonPrimitive.content
                require(type == expected) { "备份中的设置类型不匹配" }
                when (type) {
                    "string" -> require(value.jsonPrimitive.isString)
                    "boolean" -> value.jsonPrimitive.boolean
                    "int" -> value.jsonPrimitive.int
                    "long" -> value.jsonPrimitive.long
                    "float" -> value.jsonPrimitive.float
                    "set" -> value.jsonArray.forEach { require(it.jsonPrimitive.isString) }
                    else -> error("备份中的设置类型无效")
                }
            }
    }

    fun encode(preferences: SharedPreferences, keys: Set<String>): JsonObject = buildJsonObject {
        preferences.all
            .filterKeys { it in keys }
            .forEach { (key, value) ->
                val type =
                    when (value) {
                        is String -> "string"
                        is Boolean -> "boolean"
                        is Int -> "int"
                        is Long -> "long"
                        is Float -> "float"
                        is Set<*> -> "set"
                        else -> return@forEach
                    }
                put(
                    key,
                    buildJsonObject {
                        put("type", type)
                        put(
                            "value",
                            if (value is Set<*>)
                                JsonArray(value.map { JsonPrimitive(it.toString()) })
                            else
                                when (value) {
                                    is Boolean -> JsonPrimitive(value)
                                    is Number -> JsonPrimitive(value)
                                    else -> JsonPrimitive(value.toString())
                                },
                        )
                    },
                )
            }
    }

    fun restore(preferences: SharedPreferences, data: JsonObject, keys: Set<String>) {
        validate(data, keys)
        val editor = preferences.edit()
        keys.forEach(editor::remove)
        data
            .filterKeys { it in keys }
            .forEach { (key, item) ->
                val value = item.jsonObject.getValue("value")
                when (item.jsonObject.getValue("type").jsonPrimitive.content) {
                    "string" -> editor.putString(key, value.jsonPrimitive.content)
                    "boolean" -> editor.putBoolean(key, value.jsonPrimitive.boolean)
                    "int" -> editor.putInt(key, value.jsonPrimitive.int)
                    "long" -> editor.putLong(key, value.jsonPrimitive.long)
                    "float" -> editor.putFloat(key, value.jsonPrimitive.float)
                    "set" ->
                        editor.putStringSet(
                            key,
                            value.jsonArray.map { it.jsonPrimitive.content }.toSet(),
                        )
                    else -> error("备份中的设置类型无效")
                }
            }
        check(editor.commit()) { "无法恢复设置" }
    }
}
