package io.github.leenban.clash.service.backup

import android.content.Context
import android.net.Uri
import io.github.leenban.clash.core.Clash
import io.github.leenban.clash.core.bridge.Bridge
import io.github.leenban.clash.core.model.*
import io.github.leenban.clash.service.ProfileProcessor
import io.github.leenban.clash.service.ProfileReceiver
import io.github.leenban.clash.service.StatusProvider
import io.github.leenban.clash.service.data.*
import io.github.leenban.clash.service.model.Profile
import io.github.leenban.clash.service.store.ServiceStore
import io.github.leenban.clash.service.util.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*

internal class LocalBackupManager(private val context: Context) {
    private val json = DiagnosticJson.format
    private val geoIds = setOf("mmdb", "geoip", "geosite", "asn", "mrs")
    private val preferences
        get() = context.getSharedPreferences("service", Context.MODE_PRIVATE)

    private fun parse(text: String): BackupManifest {
        val manifest =
            try {
                json.decodeFromString(BackupManifest.serializer(), text)
            } catch (e: Exception) {
                throw IllegalArgumentException("备份清单格式无效")
            }
        require(manifest.version == 1 && manifest.profiles.size <= 500) { "不支持的备份版本或配置数量" }
        require(manifest.profiles.map { it.uuid }.distinct().size == manifest.profiles.size) {
            "备份包含重复配置"
        }
        require(
            manifest.geo.all { it in geoIds } && manifest.geo.distinct().size == manifest.geo.size
        ) {
            "备份中的 Geo 类型无效"
        }
        PreferenceCodec.validate(manifest.preferences, PreferenceCodec.serviceKeys)
        PreferenceCodec.validate(
            manifest.ui["preferences"]?.jsonObject ?: buildJsonObject {},
            PreferenceCodec.uiKeys,
        )
        manifest.ui["restartReceiver"]?.jsonPrimitive?.int?.let {
            require(it in 0..2) { "备份中的启动设置无效" }
        }
        for (profile in manifest.profiles) {
            require(
                profile.name.isNotBlank() && (profile.interval == 0L || profile.interval >= 900000L)
            ) {
                "备份中的配置属性无效"
            }
            if (profile.type != Profile.Type.File)
                require(Uri.parse(profile.source).scheme in setOf("http", "https", "content")) {
                    "备份中的配置来源无效"
                }
        }
        return manifest
    }

    suspend fun preview(uri: String): String =
        withContext(Dispatchers.IO) {
            val manifest =
                context.contentResolver.openInputStream(Uri.parse(uri))!!.use {
                    parse(BackupArchive.manifest(it))
                }
            json.encodeToString(
                BackupPreview.serializer(),
                BackupPreview(manifest.createdAt, manifest.profiles.size, manifest.geo.size),
            )
        }

    suspend fun export(uri: String, ui: String) =
        withContext(Dispatchers.IO) {
            check(!StatusProvider.serviceRunning) { "请先停止服务再备份" }
            ProfileProcessor.stableSnapshot {
                val active = ServiceStore(context).activeProfile
                val profiles =
                    ImportedDao()
                        .queryAllUUIDs()
                        .mapNotNull { ImportedDao().queryByUUID(it) }
                        .map { item ->
                            Profile(
                                item.uuid,
                                item.name,
                                item.type,
                                item.source,
                                item.uuid == active,
                                item.interval,
                                item.upload,
                                item.download,
                                item.total,
                                item.expire,
                                context.importedDir
                                    .resolve(item.uuid.toString())
                                    .directoryLastModified ?: item.createdAt,
                                true,
                                false,
                                item.ageSecretKey,
                            )
                        }
                val selections =
                    profiles.associate { profile ->
                        profile.uuid.toString() to
                            SelectionDao().querySelections(profile.uuid).associate {
                                it.proxy to it.selected
                            }
                    }
                val geo =
                    json
                        .decodeFromString(
                            ListSerializer(GeoAsset.serializer()),
                            Bridge.nativeDiagnostics("geoStatus", "{}"),
                        )
                        .filter { it.size > 0 }
                val manifest =
                    BackupManifest(
                        createdAt = System.currentTimeMillis(),
                        profiles = profiles,
                        selections = selections,
                        overrides =
                            json.parseToJsonElement(Bridge.nativeReadOverride(0)).jsonObject,
                        preferences =
                            PreferenceCodec.encode(preferences, PreferenceCodec.serviceKeys),
                        ui = json.parseToJsonElement(ui).jsonObject,
                        geo = geo.map { it.id },
                    )
                val files = sequence {
                    for (profile in profiles) {
                        val root = context.importedDir.resolve(profile.uuid.toString())
                        for (file in root.walkTopDown().filter { it.isFile }) {
                            check(
                                file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())
                            ) {
                                "配置资源包含外部路径"
                            }
                            yield(
                                "profiles/${profile.uuid}/${file.relativeTo(root).invariantSeparatorsPath}" to
                                    file
                            )
                        }
                    }
                    geo.forEach {
                        yield("geo/${it.id}" to context.filesDir.resolve("clash/${it.file}"))
                    }
                }
                val archive = File.createTempFile("backup-export-", ".zip", context.cacheDir)
                try {
                    archive.outputStream().use {
                        BackupArchive.write(
                            it,
                            json.encodeToString(BackupManifest.serializer(), manifest),
                            files,
                        )
                    }
                    context.contentResolver.openOutputStream(Uri.parse(uri), "wt")!!.use { output ->
                        archive.inputStream().use { it.copyTo(output, 64 * 1024) }
                    }
                } finally {
                    archive.delete()
                }
            }
        }

    suspend fun restore(uri: String): String =
        withContext(Dispatchers.IO + NonCancellable) {
            check(!StatusProvider.serviceRunning) { "请先停止服务再恢复" }
            ProfileProcessor.stableSnapshot {
                val root = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
                val previousOverride = Bridge.nativeReadOverride(0)
                val previousPreferences =
                    PreferenceCodec.encode(preferences, PreferenceCodec.serviceKeys)
                val created = mutableListOf<UUID>()
                val changedGeo = mutableListOf<Pair<String, File?>>()
                var committed = false
                try {
                    val manifest =
                        context.contentResolver.openInputStream(Uri.parse(uri))!!.use {
                            parse(BackupArchive.extract(it, root))
                        }
                    Bridge.nativeWriteOverride(0, manifest.overrides.toString())
                    for (profile in manifest.profiles) {
                        val folder = root.resolve("profiles/${profile.uuid}")
                        require(folder.resolve("config.yaml").isFile) { "备份缺少配置文件" }
                        Clash.setAgeSecretKey(profile.ageSecretKey?.takeIf { it.isNotBlank() })
                        Clash.fetchAndValid(folder, "", false) {}.await()
                    }
                    for (id in manifest.geo) geoCall("geoValidate", id, root.resolve("geo/$id"))
                    // Existing profiles are kept. All archive validation completes before importing
                    // new records.
                    for (profile in manifest.profiles) {
                        if (ImportedDao().exists(profile.uuid)) continue
                        val target = context.importedDir.resolve(profile.uuid.toString())
                        require(!target.exists()) { "配置目录已存在，未覆盖原文件" }
                        check(root.resolve("profiles/${profile.uuid}").renameTo(target)) {
                            "无法导入配置目录"
                        }
                        created += profile.uuid
                        ImportedDao()
                            .insert(
                                Imported(
                                    profile.uuid,
                                    profile.name,
                                    profile.type,
                                    profile.source,
                                    profile.interval,
                                    profile.upload,
                                    profile.download,
                                    profile.total,
                                    profile.expire,
                                    System.currentTimeMillis(),
                                    profile.ageSecretKey,
                                )
                            )
                        for ((group, node) in
                            manifest.selections[profile.uuid.toString()].orEmpty()) SelectionDao()
                            .setSelected(Selection(profile.uuid, group, node))
                    }
                    val currentGeo =
                        json.decodeFromString(
                            ListSerializer(GeoAsset.serializer()),
                            Bridge.nativeDiagnostics("geoStatus", "{}"),
                        )
                    for (id in manifest.geo) {
                        val old = currentGeo.first { it.id == id }
                        val previous =
                            if (old.size > 0)
                                root.resolve("previous-geo/$id").also { target ->
                                    target.parentFile!!.mkdirs()
                                    context.filesDir.resolve("clash/${old.file}").copyTo(target)
                                }
                            else null
                        geoCall("geoInstall", id, root.resolve("geo/$id"))
                        changedGeo += id to previous
                    }
                    PreferenceCodec.restore(
                        preferences,
                        manifest.preferences,
                        PreferenceCodec.serviceKeys,
                    )
                    val store = ServiceStore(context)
                    if (store.activeProfile == null)
                        manifest.profiles
                            .firstOrNull { it.active }
                            ?.let { store.activeProfile = it.uuid }
                    for (uuid in created) {
                        val imported = ImportedDao().queryByUUID(uuid) ?: continue
                        if (imported.type != Profile.Type.File)
                            ProfileReceiver.scheduleNext(context, imported)
                    }
                    committed = true
                    context.sendOverrideChanged()
                    context.sendProfileChanged(
                        store.activeProfile ?: manifest.profiles.firstOrNull()?.uuid ?: UUID(0, 0)
                    )
                    json.encodeToString(
                        BackupRestoreResult.serializer(),
                        BackupRestoreResult(
                            created.size,
                            manifest.profiles.size - created.size,
                            manifest.ui,
                        ),
                    )
                } finally {
                    if (!committed) {
                        val currentGeo =
                            json.decodeFromString(
                                ListSerializer(GeoAsset.serializer()),
                                Bridge.nativeDiagnostics("geoStatus", "{}"),
                            )
                        for ((id, previous) in changedGeo) {
                            if (previous != null) geoCall("geoInstall", id, previous)
                            else
                                context.filesDir
                                    .resolve("clash/${currentGeo.first { it.id==id }.file}")
                                    .delete()
                        }
                        Bridge.nativeWriteOverride(0, previousOverride)
                        PreferenceCodec.restore(
                            preferences,
                            previousPreferences,
                            PreferenceCodec.serviceKeys,
                        )
                        for (uuid in created) {
                            ImportedDao().remove(uuid)
                            context.importedDir.resolve(uuid.toString()).deleteRecursively()
                        }
                    }
                    val active =
                        ServiceStore(context).activeProfile?.let { ImportedDao().queryByUUID(it) }
                    Clash.setAgeSecretKey(active?.ageSecretKey?.takeIf { it.isNotBlank() })
                    root.deleteRecursively()
                    Clash.forceGc()
                }
            }
        }

    private fun geoCall(operation: String, id: String, file: File) {
        DiagnosticJson.checked(
            Bridge.nativeDiagnostics(
                operation,
                buildJsonObject {
                        put("id", id)
                        put("path", file.path)
                    }
                    .toString(),
            )
        )
    }
}
