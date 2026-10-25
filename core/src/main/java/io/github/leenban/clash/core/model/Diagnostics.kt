package io.github.leenban.clash.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class DiagnosticPage<T>(val items: List<T> = emptyList(), val total: Int = 0, val nextOffset: Int = -1)

@Serializable
data class ConnectionInfo(
    val id: String, val host: String = "", val source: String = "", val destination: String = "",
    val network: String = "", val process: String = "", val uid: Long = 0,
    val upload: Long = 0, val download: Long = 0, val start: String = "",
    val chains: List<String> = emptyList(), val rule: String = "", val rulePayload: String = "",
)

@Serializable
data class RuleInfo(val index: Int, val type: String, val payload: String, val proxy: String)

@Serializable
data class TrafficStats(val uploadRate: Long = 0, val downloadRate: Long = 0, val uploadTotal: Long = 0, val downloadTotal: Long = 0)

@Serializable
data class DnsAnswer(val name: String, val type: String, val ttl: Long, val data: String)

@Serializable
data class DnsResult(val status: String, val elapsedMs: Long, val answers: List<DnsAnswer>)

object DiagnosticJson {
    val format = Json { ignoreUnknownKeys = true }
    fun checked(value: String): String {
        val error = format.parseToJsonElement(value).jsonObject["error"]?.jsonPrimitive?.content
        check(error == null) { error ?: "诊断失败" }
        return value
    }
}
