package io.github.leenban.clash.service

import android.content.Context
import android.net.Uri
import io.github.leenban.clash.core.bridge.Bridge
import io.github.leenban.clash.core.model.DiagnosticJson
import io.github.leenban.clash.service.store.ServiceStore
import io.github.leenban.clash.service.util.importedDir
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class GeoDataManager(private val context: Context) {
    private val lock = Mutex()

    private fun call(operation: String, id: String = "", path: String = ""): String =
        DiagnosticJson.checked(
            Bridge.nativeDiagnostics(
                operation,
                buildJsonObject {
                        put("id", id)
                        put("path", path)
                        if (operation == "geoUpdate")
                            put(
                                "overrides",
                                JsonArray(
                                    listOf(0, 1).map {
                                        JsonPrimitive(Bridge.nativeReadOverride(it))
                                    }
                                ),
                            )
                    }
                    .toString(),
            )
        )

    fun status(): String = call("geoStatus")

    suspend fun update(id: String) =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val profile = ServiceStore(context).activeProfile
                call(
                    "geoUpdate",
                    id,
                    profile?.let { context.importedDir.resolve(it.toString()).path }.orEmpty(),
                )
                Unit
            }
        }

    suspend fun import(id: String, uri: String) =
        withContext(Dispatchers.IO) {
            lock.withLock {
                require(id in setOf("mmdb", "geoip", "geosite", "asn", "mrs")) { "不支持的 Geo 数据类型" }
                val source = Uri.parse(uri)
                require(source.scheme == "content") { "请选择本地 Geo 文件" }
                val staged =
                    File.createTempFile(".geo-import-", ".tmp", context.filesDir.resolve("clash"))
                try {
                    context.contentResolver.openInputStream(source)!!.use { input ->
                        staged.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var size = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                size += count
                                require(size <= 128L * 1024 * 1024) { "Geo 文件超过 128 MB" }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    call("geoInstall", id, staged.path)
                } finally {
                    staged.delete()
                }
                Unit
            }
        }
}
