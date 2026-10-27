package io.github.leenban.clash

import android.app.Application
import android.content.Context
import io.github.leenban.clash.common.Global
import io.github.leenban.clash.common.compat.currentProcessName
import io.github.leenban.clash.common.log.Log
import io.github.leenban.clash.remote.Remote
import io.github.leenban.clash.service.util.sendServiceRecreated
import io.github.leenban.clash.util.clashDir
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("unused")
class MainApplication : Application() {

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
            io.github.leenban.clash.lucid.PolicyIconLoader.clearMemory()
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        Global.init(this)
    }

    override fun onCreate() {
        super.onCreate()

        val processName = currentProcessName
        extractGeoFiles()

        Log.d("Process $processName started")

        if (processName == packageName) {
            Remote.launch()
            Global.launch(Dispatchers.IO) {
                runCatching { io.github.leenban.clash.update.UpdateInstaller.cleanupCache(this@MainApplication) }
            }
        } else {
            sendServiceRecreated()
        }
    }

    private fun extractGeoFiles() {
        clashDir.mkdirs()
        val preferences = getSharedPreferences("bundled_assets", MODE_PRIVATE)
        val manifest = org.json.JSONObject(assets.open("manifest.json").bufferedReader().use { it.readText() }).getJSONArray("assets")
        for (index in 0 until manifest.length()) {
            val entry = manifest.getJSONObject(index)
            val name = entry.getString("name")
            val bundledHash = entry.getString("sha256")
            val previousHash = preferences.getString(name, null)
            val target = File(clashDir, name)
            if (target.exists() && previousHash == bundledHash) continue
            val unchanged = target.exists() && previousHash != null && target.inputStream().use { input ->
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(32768)
                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
                digest.digest().joinToString("") { "%02x".format(it) } == previousHash
            }
            if (!target.exists() || unchanged) assets.open(name).use { input -> target.outputStream().use { input.copyTo(it) } }
            preferences.edit().putString(name, bundledHash).apply()
        }
    }

    fun finalize() {
        Global.destroy()
    }
}
