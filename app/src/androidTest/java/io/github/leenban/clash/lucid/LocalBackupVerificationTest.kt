package io.github.leenban.clash.lucid

import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.core.model.DiagnosticJson
import io.github.leenban.clash.remote.StatusClient
import io.github.leenban.clash.service.backup.BackupManifest
import io.github.leenban.clash.util.*
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalBackupVerificationTest {
    @Test(timeout = 240000)
    fun streamingExportMergeRestoreAndRejectedArchivesPreserveData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use {
            val repo = ClientRepository(context)
            val original = repo.active()
            val running = StatusClient(context).currentProfile() != null
            val vpn = repo.ui.enableVpn
            val root = File(context.cacheDir, "backup-test-${UUID.randomUUID()}").apply { mkdirs() }
            val format = DiagnosticJson.format
            try {
                repo.restoreSelection(context, original)
                val before = repo.profiles().map { p -> p.uuid }.toSet()
                val archive = root.resolve("backup.zip")
                repo.exportBackup(Uri.fromFile(archive))
                val preview = repo.previewBackup(Uri.fromFile(archive))
                assertEquals(before.size, preview.profiles)
                assertTrue(preview.geoFiles > 0)

                // Rewrite only the fixture entry to a new UUID to test a real import while
                // leaving every existing profile untouched. Retain the imported fixture.
                val source = repo.profiles().first { p -> p.name == "Local integration test" }
                val previousImport =
                    repo.profiles().firstOrNull { p -> p.name == "Verification restored backup" }
                val targetId = previousImport?.uuid ?: UUID.randomUUID()
                val reduced = root.resolve("merge.zip")
                ZipOutputStream(reduced.outputStream()).use { output ->
                    ZipInputStream(archive.inputStream()).use { input ->
                        while (true) {
                            val entry = input.nextEntry ?: break
                            if (entry.name == "manifest.json") {
                                val manifest =
                                    format.decodeFromString(
                                        BackupManifest.serializer(),
                                        input.readBytes().toString(Charsets.UTF_8),
                                    )
                                val ui = manifest.ui.toMutableMap()
                                val prefs = ui.getValue("preferences").jsonObject.toMutableMap()
                                prefs["enable_vpn"] = buildJsonObject {
                                    put("type", "boolean")
                                    put("value", !vpn)
                                }
                                ui["preferences"] = JsonObject(prefs)
                                val changed =
                                    manifest.copy(
                                        profiles =
                                            listOf(
                                                source.copy(
                                                    uuid = targetId,
                                                    name = "Verification restored backup",
                                                    active = false,
                                                )
                                            ),
                                        selections =
                                            mapOf(
                                                targetId.toString() to
                                                    manifest.selections[source.uuid.toString()]
                                                        .orEmpty()
                                            ),
                                        geo = emptyList(),
                                        ui = JsonObject(ui),
                                    )
                                output.putNextEntry(ZipEntry("manifest.json"))
                                output.write(
                                    format
                                        .encodeToString(BackupManifest.serializer(), changed)
                                        .toByteArray()
                                )
                                output.closeEntry()
                            } else if (entry.name.startsWith("profiles/${source.uuid}/")) {
                                output.putNextEntry(
                                    ZipEntry(
                                        entry.name.replace(
                                            source.uuid.toString(),
                                            targetId.toString(),
                                        )
                                    )
                                )
                                input.copyTo(output, 64 * 1024)
                                output.closeEntry()
                            }
                        }
                    }
                }
                val result = repo.restoreBackup(Uri.fromFile(reduced))
                assertEquals(if (previousImport == null) 1 else 0, result.restored)
                assertEquals(!vpn, repo.ui.enableVpn)
                assertEquals(original?.uuid, repo.active()?.uuid)
                assertTrue(repo.profiles().map { p -> p.uuid }.containsAll(before))
                assertEquals(
                    repo.readDocument("${source.uuid}/config.yaml"),
                    repo.readDocument("$targetId/config.yaml"),
                )
                val second = repo.restoreBackup(Uri.fromFile(reduced))
                assertEquals(0, second.restored)
                assertEquals(1, second.skipped)

                val ids = repo.profiles().map { p -> p.uuid }.toSet()
                val config = repo.readDocument("${source.uuid}/config.yaml")
                val geo = repo.geoAssets()
                for ((name, path) in
                    listOf("zip-slip" to "../outside", "invalid" to "manifest.json")) {
                    val invalid = root.resolve("$name.zip")
                    ZipOutputStream(invalid.outputStream()).use { zip ->
                        zip.putNextEntry(ZipEntry(path))
                        zip.write("invalid".toByteArray())
                        zip.closeEntry()
                    }
                    assertTrue(runCatching { repo.restoreBackup(Uri.fromFile(invalid)) }.isFailure)
                }
                assertEquals(ids, repo.profiles().map { p -> p.uuid }.toSet())
                assertEquals(config, repo.readDocument("${source.uuid}/config.yaml"))
                assertEquals(geo, repo.geoAssets())
                assertFalse(root.parentFile!!.resolve("outside").exists())
                assertEquals(!vpn, repo.ui.enableVpn)
            } finally {
                withContext(NonCancellable) {
                    repo.ui.enableVpn = vpn
                    repo.restoreSelection(context, original)
                    if (running) {
                        context.startClashService()
                        awaitRunningProfile(context)
                    }
                    root.deleteRecursively()
                }
            }
        }
    }
}
