// Protocol and mirror validation adapted from Lee's Music d305079.
package io.github.leenban.clash.update

import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

data class AvailableUpdate(val manifest: UpdateManifest, val mirrors: List<URL>)

internal class UpdateClient(
    private val bases: List<String>,
    private val expectedPackage: String = "io.github.leenban.clash",
    private val open: (URL, Int) -> UpdateResponse = ::openUpdate,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun check(installed: Long, sdk: Int): AvailableUpdate? {
        val found = bases.mapNotNull { base -> runCatching {
            val root = URL(base.trimEnd('/') + "/")
            require(root.protocol == "https" && root.userInfo == null && root.query == null && root.ref == null)
            open(URL(root, "latest-lees-clash.json"), 15000).use { response ->
                check(response.status in 200..299)
                val bytes = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = response.body.read(buffer)
                    if (count < 0) break
                    require(bytes.size() + count <= 65536)
                    bytes.write(buffer, 0, count)
                }
                val manifest = json.decodeFromString(UpdateManifest.serializer(), bytes.toString("UTF-8"))
                manifest.validate(expectedPackage)
                root to manifest
            }
        }.getOrNull() }
        check(found.isNotEmpty()) { "暂时无法检查更新，请稍后重试" }
        val newest = found.filter { it.second.minSdk <= sdk }.maxByOrNull { it.second.versionCode } ?: return null
        if (newest.second.versionCode <= installed) return null
        return AvailableUpdate(newest.second, found.filter { it.second == newest.second }.map { URL(it.first, it.second.apk) })
    }

    fun download(update: AvailableUpdate, destination: File, progress: (Long, Long) -> Unit) {
        val expected = update.manifest
        for (url in update.mirrors) {
            try {
                val started = System.nanoTime()
                open(url, 30000).use { response ->
                    check(response.status in 200..299)
                    val digest = MessageDigest.getInstance("SHA-256")
                    var count = 0L
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            check(System.nanoTime() - started <= TimeUnit.MINUTES.toNanos(5))
                            val read = response.body.read(buffer)
                            if (read < 0) break
                            count += read
                            check(count <= expected.size)
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            progress(count, expected.size)
                        }
                    }
                    check(count == expected.size)
                    check(digest.digest().joinToString("") { "%02x".format(it) }.equals(expected.sha256, true))
                }
                return
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { /* Retry only a mirror with the same validated manifest. */ }
        }
        error("下载失败或安装包校验失败，请重试")
    }
}
