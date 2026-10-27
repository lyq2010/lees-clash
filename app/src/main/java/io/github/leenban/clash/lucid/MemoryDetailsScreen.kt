package io.github.leenban.clash.lucid

import android.os.Debug
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.leenban.clash.core.model.DiagnosticJson
import io.github.leenban.clash.design.lucid.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

@Composable
fun MemoryDetailsScreen(state: ClientState, vm: ClientViewModel, back: () -> Unit) {
    var snapshot by remember { mutableStateOf<JsonObject?>(null) }
    var uiPss by remember { mutableLongStateOf(0L) }
    var sampledAt by remember { mutableLongStateOf(0L) }
    suspend fun sample() {
        val core = DiagnosticJson.format.parseToJsonElement(vm.repository.diagnostic("runtime")).jsonObject
        val ui = withContext(Dispatchers.IO) { Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss.toLong() * 1024 }
        snapshot = core
        uiPss = ui
        sampledAt = System.currentTimeMillis()
    }
    LaunchedEffect(Unit) {
        try { sample() } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { vm.error(e.message ?: "无法读取内存") }
    }
    Column(Modifier.fillMaxSize()) {
        LucidHeader("内存详情", back, "刷新", actionEnabled = !state.busy) {
            vm.task(progress = "正在读取内存") { sample() }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            LucidSection("进程内存", footer = "PSS 为当前进程按比例分摊的物理内存；手动采样，不代表峰值。后台包含 Go 内核，下面的 Go 数据不能重复相加。") {
                LucidRow("界面进程 PSS", value = if (snapshot == null) "正在读取" else formatBytes(uiPss))
                LucidRow("后台进程 PSS", value = snapshot?.get("servicePssKiB")?.jsonPrimitive?.longOrNull?.let { formatBytes(it * 1024) } ?: "正在读取")
                LucidRow("采样时间", value = profileDate(sampledAt))
            }
            LucidSection("Go 内核", footer = "此页通过后台服务读取内核统计，不在界面进程加载另一份内核，也不自动触发垃圾回收。") {
                for ((key, title) in listOf("heapAlloc" to "堆中已分配", "heapInuse" to "堆使用中", "heapReleased" to "已归还系统", "sys" to "运行时向系统申请"))
                    LucidRow(title, value = snapshot?.get(key)?.jsonPrimitive?.longOrNull?.let(::formatBytes) ?: "正在读取")
                LucidRow("Go 协程数", value = snapshot?.get("goroutines")?.jsonPrimitive?.content ?: "正在读取")
            }
        }
    }
}
