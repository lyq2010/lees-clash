package io.github.leenban.clash.service.backup

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Streaming archive IO. No archive body is retained in memory. */
internal object BackupArchive {
    private const val maxTotal = 512L * 1024 * 1024
    private const val maxFile = 128L * 1024 * 1024
    private const val maxEntries = 20000

    fun manifest(input: InputStream): String {
        ZipInputStream(input).use { zip ->
            val entry = zip.nextEntry ?: error("备份为空")
            require(entry.name == "manifest.json") { "不是 Lee’s Clash 备份文件" }
            val data = zip.readBytesLimited(2 * 1024 * 1024)
            return data.toString(Charsets.UTF_8)
        }
    }

    fun extract(input: InputStream, root: File): String {
        var total = 0L
        var count = 0
        val names = hashSetOf<String>()
        var manifest: String? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++count <= maxEntries) { "备份文件数量过多" }
                val name = entry.name
                require(
                    name.isNotBlank() &&
                        !name.contains('\\') &&
                        !name.startsWith('/') &&
                        !name.split('/').any { it == ".." || it == "." } &&
                        names.add(name)
                ) {
                    "备份路径无效或重复"
                }
                val file = root.resolve(name).canonicalFile
                require(file.toPath().startsWith(root.canonicalFile.toPath())) { "备份路径越界" }
                if (entry.isDirectory) {
                    file.mkdirs()
                    continue
                }
                file.parentFile!!.mkdirs()
                var size = 0L
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        size += read
                        total += read
                        require(size <= maxFile && total <= maxTotal) { "备份解压大小超过限制" }
                        output.write(buffer, 0, read)
                    }
                }
                if (entry.time > 0) file.setLastModified(entry.time)
                if (name == "manifest.json") {
                    require(size <= 2 * 1024 * 1024) { "备份清单过大" }
                    manifest = file.readText()
                }
            }
        }
        return manifest ?: error("备份缺少清单")
    }

    fun write(output: OutputStream, manifest: String, files: Sequence<Pair<String, File>>) {
        require(manifest.toByteArray().size <= 2 * 1024 * 1024) { "备份清单过大" }
        var total = 0L
        var count = 1
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
            files.forEach { (name, file) ->
                require(file.isFile && file.length() <= maxFile) { "备份文件过大" }
                total += file.length()
                require(total <= maxTotal && ++count <= maxEntries) { "备份大小或文件数量超过限制" }
                zip.putNextEntry(ZipEntry(name).apply { time = file.lastModified() })
                file.inputStream().use { it.copyTo(zip, 64 * 1024) }
                zip.closeEntry()
            }
        }
    }

    private fun InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            require(output.size() + read <= limit) { "备份清单过大" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
