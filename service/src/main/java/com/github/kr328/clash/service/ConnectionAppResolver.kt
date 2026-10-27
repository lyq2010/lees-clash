package io.github.leenban.clash.service

import android.content.Context
import android.util.LruCache
import io.github.leenban.clash.core.model.*

class ConnectionAppResolver(context: Context) {
    private val packages = context.packageManager
    private val labels = LruCache<String, String>(128)

    private fun label(name: String): String? = labels.get(name) ?: runCatching {
        packages.getApplicationInfo(name, 0).loadLabel(packages).toString()
    }.getOrNull()?.also { labels.put(name, it) }

    private fun resolve(info: ConnectionInfo): ConnectionInfo {
        if (info.process.isNotBlank()) label(info.process)?.let { return info.copy(appName = it) }
        val names = if (info.uid >= 0) packages.getPackagesForUid(info.uid.toInt()).orEmpty()
            .mapNotNull(::label).distinct() else emptyList()
        return info.copy(appName = names.joinToString(" / "))
    }

    fun decorate(operation: String, value: String): String {
        val json = DiagnosticJson.format
        return if (operation == "connection") {
            json.encodeToString(ConnectionInfo.serializer(), resolve(json.decodeFromString(ConnectionInfo.serializer(), value)))
        } else {
            val serializer = DiagnosticPage.serializer(ConnectionInfo.serializer())
            val page = json.decodeFromString(serializer, value)
            json.encodeToString(serializer, page.copy(items = page.items.map(::resolve)))
        }
    }
}
