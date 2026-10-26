package io.github.leenban.clash.service

import android.content.Context
import io.github.leenban.clash.core.Clash
import io.github.leenban.clash.service.data.ImportedDao
import io.github.leenban.clash.service.store.ServiceStore
import io.github.leenban.clash.service.util.importedDir
import io.github.leenban.clash.service.util.sendProfileChanged
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

internal class ProfileExtensionManager(private val context: Context) {
    suspend fun read(uuid: UUID): String =
        withContext(Dispatchers.IO) {
            require(ImportedDao().exists(uuid)) { "配置不存在" }
            val file = context.importedDir.resolve("$uuid/extension.json")
            require(!file.exists() || file.length() <= 192 * 1024) { "配置扩展超过 192 KB" }
            if (file.exists()) file.readText() else "{}"
        }

    suspend fun write(uuid: UUID, text: String) =
        withContext(Dispatchers.IO + NonCancellable) {
            require(text.toByteArray().size <= 192 * 1024) { "配置扩展超过 192 KB" }
            Json.parseToJsonElement(text).jsonObject
            ProfileProcessor.stableSnapshot {
                val profile = ImportedDao().queryByUUID(uuid) ?: error("配置不存在")
                val source = context.importedDir.resolve(uuid.toString())
                val temporary = File(context.cacheDir, "extension-${UUID.randomUUID()}")
                try {
                    source.copyRecursively(temporary)
                    temporary.resolve("extension.json").writeText(text)
                    Clash.setAgeSecretKey(profile.ageSecretKey?.takeIf { it.isNotBlank() })
                    Clash.fetchAndValid(temporary, "", false) {}.await()
                    val pending = source.resolve("extension.json.tmp")
                    pending.writeText(text)
                    check(pending.renameTo(source.resolve("extension.json"))) { "无法保存配置扩展" }
                    context.sendProfileChanged(uuid)
                } finally {
                    temporary.deleteRecursively()
                    val active =
                        ServiceStore(context).activeProfile?.let { ImportedDao().queryByUUID(it) }
                    Clash.setAgeSecretKey(active?.ageSecretKey?.takeIf { it.isNotBlank() })
                    Clash.forceGc()
                }
            }
        }
}
