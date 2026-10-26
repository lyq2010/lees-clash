package io.github.leenban.clash.lucid

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import io.github.leenban.clash.core.Clash
import io.github.leenban.clash.core.bridge.Bridge
import io.github.leenban.clash.core.model.*
import io.github.leenban.clash.design.store.UiStore
import io.github.leenban.clash.remote.FilesClient
import io.github.leenban.clash.service.model.Profile
import io.github.leenban.clash.service.remote.IFetchObserver
import io.github.leenban.clash.service.store.ServiceStore
import io.github.leenban.clash.util.withClash
import io.github.leenban.clash.util.withProfile
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class ClientRepository(val context: Context) {
    val ui = UiStore(context)
    val service = ServiceStore(context)
    val files = FilesClient(context)
    val configurationJson = Json {
        encodeDefaults = true
        prettyPrint = true
        ignoreUnknownKeys = false
    }

    suspend fun profiles() = withProfile { queryAll() }

    suspend fun active() = withProfile { queryActive() }

    suspend fun mode() = withClash { queryTunnelState().mode }

    suspend fun select(profile: Profile) = withProfile { setActive(profile) }

    suspend fun group(name: String) = withClash { queryProxyGroup(name, ui.proxySort) }

    suspend fun groups() = withClash {
        queryProxyGroupNames(ui.proxyExcludeNotSelectable).map {
            PolicyGroup(it, queryProxyGroup(it, ui.proxySort))
        }
    }

    suspend fun select(group: String, proxy: String) {
        check(withClash { patchSelector(group, proxy) }) { "该策略组不支持手动选择" }
    }

    suspend fun health(group: String) = withClash { healthCheck(group) }

    suspend fun providers() = withClash { queryProviders().toList() }

    suspend fun update(provider: Provider) = withClash {
        updateProvider(provider.type, provider.name)
    }

    suspend fun reload(profile: Profile) = withProfile { updateNow(profile.uuid) }

    suspend fun delete(profile: Profile) = withProfile { delete(profile.uuid) }

    suspend fun profile(uuid: String) = withProfile { queryByUUID(UUID.fromString(uuid)) }

    suspend fun overrideText(): String = withClash {
        configurationJson.encodeToString(
            ConfigurationOverride.serializer(),
            queryOverride(Clash.OverrideSlot.Persist),
        )
    }

    suspend fun saveOverride(text: String) {
        val config = configurationJson.decodeFromString(ConfigurationOverride.serializer(), text)
        withClash { patchOverride(Clash.OverrideSlot.Persist, config) }
    }

    suspend fun resetOverride() = withClash { clearOverride(Clash.OverrideSlot.Persist) }

    suspend fun setMode(mode: TunnelState.Mode) = withClash {
        val override = queryOverride(Clash.OverrideSlot.Session)
        override.mode = mode
        patchOverride(Clash.OverrideSlot.Session, override)
    }

    suspend fun diagnostic(
        operation: String,
        query: String = "",
        offset: Int = 0,
        id: String = "",
        domain: String = "",
        recordType: String = "A",
    ): String {
        val payload =
            buildJsonObject {
                    put("query", query)
                    put("offset", offset)
                    put("limit", 50)
                    put("id", id)
                    put("domain", domain)
                    put("recordType", recordType)
                }
                .toString()
        return withClash { queryDiagnostics(operation, payload) }
    }

    suspend fun importUrl(
        name: String,
        url: String,
        secret: String?,
        progress: (FetchStatus) -> Unit = {},
    ): UUID = withProfile {
        val source = url.trim()
        require(name.isNotBlank()) { "请输入配置名称" }
        require(Uri.parse(source).scheme in listOf("https", "http", "content")) {
            "请输入 HTTP、HTTPS 或内容提供器链接"
        }
        val type =
            if (Uri.parse(source).scheme == "content") Profile.Type.External else Profile.Type.Url
        val uuid = create(type, name.trim(), source, secret?.takeIf { it.isNotBlank() })
        try {
            commit(uuid, IFetchObserver { progress(it) })
            queryByUUID(uuid)?.let { if (queryActive() == null) setActive(it) }
        } finally {
            release(uuid)
        }
        uuid
    }

    suspend fun importFile(name: String, source: Uri): UUID = withProfile {
        val uuid = create(Profile.Type.File, name.ifBlank { "本地配置" })
        try {
            files.copyDocument("$uuid/config.yaml", source)
            commit(uuid)
            queryByUUID(uuid)?.let { if (queryActive() == null) setActive(it) }
        } finally {
            release(uuid)
        }
        uuid
    }

    suspend fun saveProfile(
        profile: Profile,
        name: String,
        source: String,
        interval: Long,
        secret: String?,
        contents: String?,
    ) = withProfile {
        require(name.isNotBlank()) { "请输入配置名称" }
        require(interval >= 0) { "更新间隔不能小于零" }
        try {
            patch(
                profile.uuid,
                name.trim(),
                source.trim(),
                interval,
                secret?.takeIf { it.isNotBlank() },
            )
            if (contents != null) {
                require(profile.type == Profile.Type.File) { "托管配置需要先克隆为本地副本" }
                writeDocument("${profile.uuid}/config.yaml", contents)
            }
            commit(profile.uuid)
        } finally {
            release(profile.uuid)
        }
    }

    suspend fun cloneProfile(profile: Profile): Profile = withProfile {
        val uuid = clone(profile.uuid)
        try {
            commit(uuid)
            checkNotNull(queryByUUID(uuid))
        } finally {
            release(uuid)
        }
    }

    suspend fun readDocument(id: String): String =
        withContext(Dispatchers.IO) {
            context.contentResolver
                .openInputStream(files.buildDocumentUri(id))!!
                .bufferedReader()
                .use { reader ->
                    val text = reader.readText()
                    require(text.length <= 2_000_000) { "文件过大，请使用外部编辑器" }
                    text
                }
        }

    suspend fun writeDocument(id: String, text: String) =
        withContext(Dispatchers.IO) {
            context.contentResolver
                .openOutputStream(files.buildDocumentUri(id), "wt")!!
                .bufferedWriter()
                .use { it.write(text) }
        }

    suspend fun coreVersion() =
        withContext(Dispatchers.IO) { Bridge.nativeCoreVersion().replace('_', ' ') }

    suspend fun appChoices(): List<AppChoice> =
        withContext(Dispatchers.IO) {
            context.packageManager
                .getInstalledApplications(0)
                .map {
                    AppChoice(
                        it.packageName,
                        it.loadLabel(context.packageManager).toString(),
                        it.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                        it.packageName in service.accessControlPackages,
                    )
                }
                .sortedBy { it.label.lowercase() }
        }

    fun selectApp(name: String, selected: Boolean) {
        val items = service.accessControlPackages.toMutableSet()
        if (selected) items.add(name) else items.remove(name)
        service.accessControlPackages = items
    }
}
