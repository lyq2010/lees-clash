package io.github.leenban.clash.lucid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import io.github.leenban.clash.common.constants.Intents
import io.github.leenban.clash.core.model.*
import io.github.leenban.clash.remote.Remote
import io.github.leenban.clash.util.startClashService
import io.github.leenban.clash.util.stopClashService

open class LucidActivity : ComponentActivity() {
    private val model: ClientViewModel by viewModels()
    private var importTarget: String? = null
    private var geoTarget: String? = null
    private var exportTarget: String? = null
    private var externalEditTarget: String? = null

    private val externalEditor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        externalEditTarget?.let { id ->
            model.task(progress = "正在验证外部编辑结果", success = "编辑结果已验证") {
                val uuid = java.util.UUID.fromString(id.substringBefore('/'))
                io.github.leenban.clash.util.withProfile {
                    try { commit(uuid) } finally { release(uuid) }
                }
                externalEditTarget = null
                model.changed()
            }
        }
    }

    private val backupExporter =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri
            ->
            if (uri != null)
                model.task(progress = "正在创建备份", success = "备份已保存") {
                    model.repository.exportBackup(uri)
                }
        }
    private val backupImporter =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                model.task(progress = "正在读取备份") {
                    model.backupPreview(uri.toString(), model.repository.previewBackup(uri))
                }
        }

    protected open fun initialRoute(): String = "home"

    private val vpn =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) beginStart() else model.error("未允许 VPN 连接，服务尚未启动")
        }
    private val notifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { beginStart() }
    private val filePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importDocument(uri)
        }
    private val exporter =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/yaml")) { uri
            ->
            val id = exportTarget
            if (uri != null && id != null)
                model.task(progress = "正在导出文件", success = "文件已导出") {
                    model.repository.files.copyDocument(uri, id)
                }
        }
    private val qr =
        registerForActivityResult(ScanQRCode()) { result ->
            when (result) {
                is QRResult.QRSuccess -> {
                    val value = result.content.rawValue
                    if (value != null) showRoute("import/${Uri.encode(importUrl(value))}")
                }
                is QRResult.QRError -> model.error("二维码识别失败")
                else -> Unit
            }
        }
    private val provider =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    if (result.data!!.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0)
                        runCatching {
                            contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        }
                    model.task(progress = "正在导入配置", success = "配置已添加") {
                        val id =
                            model.repository.importUrl(
                                result.data?.getStringExtra(Intents.EXTRA_NAME) ?: "外部配置",
                                uri.toString(),
                                null,
                            )
                        model.imported(id)
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        importTarget = savedInstanceState?.getString("document.importTarget")
        geoTarget = savedInstanceState?.getString("document.geoTarget")
        exportTarget = savedInstanceState?.getString("document.exportTarget")
        externalEditTarget = savedInstanceState?.getString("document.externalEditTarget")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK !=
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        render(initialRoute())
        setupShortcuts()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("document.importTarget", importTarget)
        outState.putString("document.geoTarget", geoTarget)
        outState.putString("document.exportTarget", exportTarget)
        outState.putString("document.externalEditTarget", externalEditTarget)
        super.onSaveInstanceState(outState)
    }

    private fun render(route: String) {
        setContent {
            ClientApp(
                model,
                route,
                ::toggle,
                { folder ->
                    importTarget = folder
                    geoTarget = null
                    filePicker.launch(arrayOf("*/*"))
                },
                { qr.launch(null) },
                ::externalProvider,
                { id, name ->
                    exportTarget = id
                    exporter.launch(name)
                },
                { name ->
                    geoTarget = name
                    filePicker.launch(arrayOf("*/*"))
                },
                { backupExporter.launch("lees-clash-backup-${java.time.LocalDate.now()}.zip") },
                { backupImporter.launch(arrayOf("application/zip", "application/octet-stream")) },
                { id ->
                    val intent = Intent(Intent.ACTION_EDIT).setDataAndType(model.repository.files.buildDocumentUri(id), "text/plain")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    if (intent.resolveActivity(packageManager) == null) model.error("没有可用的外部编辑器，请先安装支持文本编辑的应用")
                    else {
                        externalEditTarget = id
                        externalEditor.launch(Intent.createChooser(intent, "选择编辑器"))
                    }
                },
                ::finishWithFeedback,
            )
        }
    }

    private fun showRoute(route: String) {
        model.openRoute(route)
    }

    private fun finishWithFeedback() {
        model.state.value.notice?.let {
            android.widget.Toast.makeText(this, it.text, android.widget.Toast.LENGTH_SHORT).show()
            model.clearNotice(it.id)
        }
        finish()
    }

    override fun onStart() {
        super.onStart()
        Remote.broadcasts.addObserver(model)
    }

    override fun onStop() {
        Remote.broadcasts.removeObserver(model)
        super.onStop()
    }

    private fun toggle() {
        if (model.state.value.running) {
            stopClashService()
            return
        }
        if (model.state.value.active?.imported != true) {
            showRoute("profiles")
            model.error("请先导入并选择一份有效配置")
            return
        }
        model.starting()
        if (
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        )
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else beginStart()
    }

    private fun beginStart() {
        try {
            val request = startClashService()
            if (request != null) vpn.launch(request)
        } catch (e: Exception) {
            model.error(e.message ?: "无法启动 VPN")
        }
    }

    private fun importUrl(value: String): String =
        if (value.startsWith("clash://") || value.startsWith("clashmeta://"))
            Uri.parse(value).getQueryParameter("url") ?: value
        else value

    private fun importDocument(uri: Uri) {
        model.task(progress = "正在导入文件", success = "文件已导入") {
            val geo = geoTarget
            val folder = importTarget
            when {
                geo != null -> model.repository.importGeo(geo, uri)
                folder != null -> {
                    val name = displayName(uri)
                    require(name.matches(Regex("[^/\\\\]+"))) { "文件名无效" }
                    model.repository.files.importDocument(folder, uri, name)
                    val uuid = java.util.UUID.fromString(folder.substringBefore('/'))
                    io.github.leenban.clash.util.withProfile {
                        try {
                            commit(uuid)
                        } finally {
                            release(uuid)
                        }
                    }
                }
                else ->
                    model.imported(
                        model.repository.importFile(displayName(uri).substringBeforeLast('.'), uri)
                    )
            }
            geoTarget = null
            importTarget = null
            model.changed()
        }
    }

    private fun displayName(uri: Uri): String =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "config.yaml"

    private fun externalProvider() {
        val query = Intent(Intents.ACTION_PROVIDE_URL)
        val providers =
            packageManager.queryIntentActivities(query, 0).map {
                query
                    .cloneFilter()
                    .setComponent(
                        android.content.ComponentName(
                            it.activityInfo.packageName,
                            it.activityInfo.name,
                        )
                    )
            }
        if (providers.isEmpty()) {
            model.error("未发现提供配置的应用，可从文件或链接导入")
            return
        }
        val chooser =
            Intent.createChooser(providers.first(), "选择配置提供器")
                .putExtra(Intent.EXTRA_INITIAL_INTENTS, providers.drop(1).toTypedArray())
        provider.launch(chooser)
    }

    private fun setupShortcuts() {
        if (model.repository.ui.hideAppIcon) return
        val shortcuts =
            listOf(
                    Intents.ACTION_TOGGLE_CLASH to "切换服务",
                    Intents.ACTION_START_CLASH to "启动服务",
                    Intents.ACTION_STOP_CLASH to "停止服务",
                )
                .mapIndexed { index, (action, label) ->
                    androidx.core.content.pm.ShortcutInfoCompat.Builder(this, "service_$index")
                        .setShortLabel(label)
                        .setRank(index)
                        .setIcon(
                            androidx.core.graphics.drawable.IconCompat.createWithResource(
                                this,
                                io.github.leenban.clash.R.drawable.ic_toggle_all,
                            )
                        )
                        .setIntent(
                            Intent(action)
                                .setClass(
                                    this,
                                    io.github.leenban.clash.ExternalControlActivity::class.java,
                                )
                        )
                        .build()
                }
        androidx.core.content.pm.ShortcutManagerCompat.setDynamicShortcuts(this, shortcuts)
    }
}
