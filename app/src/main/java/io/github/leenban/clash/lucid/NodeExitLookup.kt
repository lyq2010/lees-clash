package io.github.leenban.clash.lucid

import androidx.compose.runtime.*
import io.github.leenban.clash.core.model.Proxy
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import io.github.leenban.clash.core.model.DiagnosticJson
import io.github.leenban.clash.util.withClash

@Serializable
internal data class NodeExitInfo(
    val ip: String,
    val isp: String = "",
    val asn: String = "",
    val country: String = "",
    val city: String = "",
) {
    fun subtitle(): String = listOf(
        ip,
        listOf(isp.ifBlank { "运营商未知" }, asn).filter { it.isNotBlank() }.joinToString(" · "),
        listOf(country, city).filter { it.isNotBlank() }.joinToString(" · "),
    ).filter { it.isNotBlank() }.joinToString("\n")
}

internal suspend fun ClientRepository.nodeExit(group: String, node: String): NodeExitInfo =
    withClash {
        DiagnosticJson.format.decodeFromString(
            NodeExitInfo.serializer(),
            queryDiagnostics("nodeExit", buildJsonObject {
                put("query", group)
                put("id", node)
            }.toString()),
        )
    }

internal class NodeExitLookup(
    private val scope: CoroutineScope,
    private val probe: suspend (String) -> NodeExitInfo,
) {
    val subtitles = mutableStateMapOf<String, String>()
    var running by mutableStateOf(false)
        private set
    var completed by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    private var job: Job? = null

    fun start(nodes: List<Proxy>) {
        if (job?.isActive == true) return
        val names = nodes.filter { !it.isGroup }.map { it.name }.distinct()
        if (names.isEmpty()) return
        completed = 0
        total = names.size
        names.forEach { subtitles[it] = "等待检测落地信息" }
        running = true
        job = scope.launch {
            try {
                // At most three requests and three worker coroutines at once.
                names.chunked(3).forEach { batch ->
                    coroutineScope {
                        batch.forEach { name -> launch {
                            subtitles[name] = "正在检测落地信息…"
                            subtitles[name] = try {
                                withContext(Dispatchers.IO) { probe(name) }.subtitle()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                "落地检测失败：${e.message ?: "请重试"}"
                            }
                            completed++
                        } }
                    }
                }
            } finally {
                names.filter { subtitles[it] in listOf("等待检测落地信息", "正在检测落地信息…") }
                    .forEach { subtitles[it] = "落地检测已取消" }
                running = false
            }
        }
    }

    fun cancel() { job?.cancel() }
}

@Composable
internal fun rememberNodeExitLookup(name: String, state: ClientState, repository: ClientRepository): NodeExitLookup {
    val scope = rememberCoroutineScope()
    val lookup = remember(name, state.active?.uuid, state.active?.updatedAt, state.running) {
        NodeExitLookup(scope) { repository.nodeExit(name, it) }
    }
    DisposableEffect(lookup) { onDispose { lookup.cancel() } }
    return lookup
}
