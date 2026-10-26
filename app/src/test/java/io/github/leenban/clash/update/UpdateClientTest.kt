package io.github.leenban.clash.update

import java.io.ByteArrayInputStream
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class UpdateClientTest {
    private val bytes = "transport fixture; actual APK signatures are tested on Android".toByteArray()
    private val manifest = UpdateManifest("0.1.1", 2, "io.github.leenban.clash", "lees-clash-0.1.1.apk",
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, bytes.size.toLong(), 26)
    private fun client(response: (URL) -> Pair<Int, ByteArray>) = UpdateClient(listOf("https://primary.test", "https://backup.test"), open = { url, _ ->
        val (code, body) = response(url); UpdateResponse(code, ByteArrayInputStream(body))
    })
    private fun metadata(value: UpdateManifest = manifest) = 200 to Json.encodeToString(value).toByteArray()

    @Test fun unavailableCosFallsBackToR2() {
        val update = client { if (it.host == "primary.test") 503 to byteArrayOf() else metadata() }.check(1, 26)!!
        assertEquals("backup.test", update.mirrors.single().host)
    }
    @Test fun staleCosDoesNotHideNewR2Release() {
        val update = client { metadata(if (it.host == "primary.test") manifest.copy(versionCode = 1) else manifest) }.check(1, 26)!!
        assertEquals(2L, update.manifest.versionCode)
        assertEquals("backup.test", update.mirrors.single().host)
    }
    @Test fun installedAndUnsupportedVersionsAreNotOffered() {
        assertNull(client { metadata() }.check(2, 26))
        assertNull(client { metadata(manifest.copy(minSdk = 28)) }.check(1, 26))
    }
    @Test fun invalidIdentityFilenameHashAndSizeFailClosed() {
        listOf(manifest.copy(apk = "../payload.apk"), manifest.copy(packageName = "another.app"),
            manifest.copy(sha256 = "bad"), manifest.copy(size = Long.MAX_VALUE), manifest.copy(version = "0.1.50")).forEach { invalid ->
            assertTrue(runCatching { client { metadata(invalid) }.check(1, 26) }.isFailure)
        }
    }
    @Test fun corruptCosDownloadFallsBackToExactR2Bytes() {
        val transport = client { if (it.path.endsWith("json")) metadata() else 200 to if (it.host == "primary.test") ByteArray(bytes.size) else bytes }
        val update = transport.check(1, 26)!!
        val file = Files.createTempFile("clash-update-test", ".apk").toFile()
        try { transport.download(update, file) { _, _ -> }; assertArrayEquals(bytes, file.readBytes()) }
        finally { file.delete() }
    }
    @Test fun oversizedTruncatedAndOversizedFeedFailClosed() {
        for (body in listOf(bytes + byteArrayOf(1), bytes.copyOf(2))) {
            val transport = client { if (it.path.endsWith("json")) metadata() else 200 to body }
            val file = Files.createTempFile("clash-update-test", ".apk").toFile()
            try { assertTrue(runCatching { transport.download(transport.check(1, 26)!!, file) { _, _ -> } }.isFailure) }
            finally { file.delete() }
        }
        assertTrue(runCatching { client { 200 to ByteArray(65537) }.check(1, 26) }.isFailure)
    }
}
