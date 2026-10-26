package io.github.leenban.clash.lucid

import android.content.Context
import io.github.leenban.clash.core.model.DiagnosticJson
import io.github.leenban.clash.util.withClash
import kotlinx.serialization.json.*

suspend fun ClientRepository.networkProbe(url: String, direct: Boolean, ip: Boolean): JsonObject =
    withClash {
        DiagnosticJson.format
            .parseToJsonElement(
                queryDiagnostics(
                    "networkCheck",
                    buildJsonObject {
                            put("url", url)
                            put("direct", direct)
                            put("recordType", if (ip) "ip" else "http")
                        }
                        .toString(),
                )
            )
            .jsonObject
    }

fun ClientRepository.probeHistory(): JsonArray =
    runCatching {
            Json.parseToJsonElement(
                    context
                        .getSharedPreferences("network-probes", Context.MODE_PRIVATE)
                        .getString("history", "[]")!!
                )
                .jsonArray
        }
        .getOrDefault(JsonArray(emptyList()))

fun ClientRepository.saveProbe(result: JsonObject): JsonArray {
    val values = JsonArray((listOf(result) + probeHistory()).take(20))
    context
        .getSharedPreferences("network-probes", Context.MODE_PRIVATE)
        .edit()
        .putString("history", values.toString())
        .apply()
    return values
}
