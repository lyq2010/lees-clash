package io.github.leenban.clash.lucid

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.leenban.clash.BuildConfig
import io.github.leenban.clash.design.lucid.*
import io.github.leenban.clash.update.*
import java.io.File
import kotlinx.coroutines.*

@Composable
fun UpdateScreen(back: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { UpdateClient(listOf("https://releases.angelolee.cn", "https://plt-releases.leenbsl.workers.dev"), context.packageName) }
    var available by remember { mutableStateOf<AvailableUpdate?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("点按检查更新") }
    var percent by remember { mutableIntStateOf(0) }
    var downloaded by remember { mutableStateOf(false) }
    val file = remember { File(context.cacheDir, "updates/update.apk") }
    Column(Modifier.fillMaxSize()) {
        LucidHeader("应用更新", back, "检查", actionEnabled = !busy) {
            scope.launch {
                busy = true; message = "正在检查更新"; downloaded = false; available = null
                try {
                    available = withContext(Dispatchers.IO) { client.check(BuildConfig.VERSION_CODE.toLong(), android.os.Build.VERSION.SDK_INT) }
                    message = if (available == null) "已是最新版本" else "发现新版本 ${available!!.manifest.version}"
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: Exception) { message = error.message ?: "检查更新失败"
                } finally { busy = false }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            LucidSection("Lee's Clash", footer = message) {
                LucidRow("当前版本", value = BuildConfig.VERSION_NAME)
                LucidRow("主通道", value = "腾讯 COS")
                LucidRow("备用通道", value = "Cloudflare R2")
                if (busy) LucidRow("进度", value = if (percent > 0) "$percent%" else "进行中")
            }
            if (BuildConfig.DEBUG) LucidSection(footer = "开发版签名与正式版不同，不能覆盖安装正式更新。") {}
            available?.let { update ->
                LucidSection("可用更新", footer = update.manifest.notes) {
                    LucidRow("版本", value = update.manifest.version)
                    LucidRow("安装包", value = formatBytes(update.manifest.size))
                    LucidRow(if (downloaded) "安装更新" else "下载更新", onClick = if (busy || BuildConfig.DEBUG) null else {
                        {
                            if (downloaded) {
                                runCatching { UpdateInstaller.install(context, file, update.manifest) }
                                    .onSuccess { if (!it) message = "允许安装此来源的应用后，返回并再次点按安装" }
                                    .onFailure { message = it.message ?: "无法打开安装程序" }
                            } else scope.launch {
                                busy = true; percent = 0; message = "正在下载并验证安装包"
                                try {
                                    val job = currentCoroutineContext()
                                    withContext(Dispatchers.IO) {
                                        var previous = 0L
                                        UpdateCache.download(file) {
                                            client.download(update, file) { count, total ->
                                                job.ensureActive()
                                                val now = System.nanoTime()
                                                if (count == total || now - previous >= 100000000L) {
                                                    percent = (count * 100 / total).toInt(); previous = now
                                                }
                                            }
                                            UpdateInstaller.verify(context, file, update.manifest)
                                            job.ensureActive()
                                        }
                                    }
                                    downloaded = true; message = "下载和校验完成，请点按安装"
                                } catch (cancelled: CancellationException) { throw cancelled
                                } catch (error: Exception) { message = error.message ?: "下载失败，请重试"
                                } finally { busy = false }
                            }
                        }
                    })
                }
            }
        }
    }
}
