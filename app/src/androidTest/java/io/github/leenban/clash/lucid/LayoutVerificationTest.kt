package io.github.leenban.clash.lucid

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.asAndroidBitmap
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.design.model.DarkMode
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run on phone/tablet, both orientations and font_scale=2; see docs/RELEASE.md. */
@RunWith(AndroidJUnit4::class)
class LayoutVerificationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 300000)
    fun pagesRemainReachableInCurrentDeviceConfiguration() {
        val vm = ViewModelProvider(compose.activity)[ClientViewModel::class.java]
        val repository = vm.repository
        val original = runBlocking { repository.active() }
        val theme = repository.ui.darkMode
        val fixture = runBlocking { repository.profiles().first { it.name == "Local integration test" } }
        val label = InstrumentationRegistry.getArguments().getString("layoutLabel", "layout")
        val pages = listOf(
            "profiles" to "配置", "import" to "导入配置",
            "profile/${fixture.uuid}" to "编辑配置", "files/${fixture.uuid}" to "配置与资源文件",
            "groups" to "代理策略", "nodes/Local-Test" to "Local-Test",
            "providers" to "外部资源", "connections" to "实时连接",
            "connection/missing-layout-fixture" to "连接详情", "rules" to "代理规则",
            "dns" to "DNS 查询", "stats" to "实时统计", "logs" to "运行日志",
            "network" to "VPN 与网络", "apps" to "分应用代理", "settings" to "应用设置",
            "override" to "配置覆写", "dns-settings" to "DNS 设置",
            "about" to "关于", "help" to "使用帮助", "license/LICENSE" to "LICENSE",
            "geodata" to "Geo 数据", "extension/${fixture.uuid}" to "配置扩展",
            "on-demand" to "按需网络", "network-check" to "IP 与网络检查",
            "update" to "应用更新", "backup" to "本地备份与恢复", "keys" to "加密配置密钥",
            "backup-preview" to "恢复备份",
            "subscription" to "当前配置详情", "lan" to "局域网信息与共享", "memory" to "内存详情",
        )
        try {
            runBlocking { repository.select(fixture) }
            for (mode in listOf(DarkMode.ForceLight, DarkMode.ForceDark)) {
                repository.ui.darkMode = mode
                compose.activityRule.scenario.recreate()
                val model = ViewModelProvider(compose.activity)[ClientViewModel::class.java]
                for ((route, title) in pages) {
                    compose.activityRule.scenario.onActivity {
                        if (route == "backup-preview") model.backupPreview("file:///layout-fixture.zip",
                            io.github.leenban.clash.service.backup.BackupPreview(0, 500, 5))
                        else model.openRoute(route)
                    }
                    compose.waitUntil(20000) {
                        !model.state.value.pageLoading &&
                            compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
                    }
                    compose.onAllNodesWithText(title).onFirst().assertIsDisplayed()
                    assertNull("$label/$route", model.state.value.error)
                    // These short pages used to have no scroll container at all. Check the
                    // final action/row, not just the header that can survive clipped content.
                    when (route) {
                        "stats" -> compose.onAllNodesWithText("下载").onLast().performScrollTo().assertIsDisplayed()
                        "backup" -> compose.onNodeWithText("选择备份文件").performScrollTo().assertIsDisplayed()
                        "backup-preview" -> compose.onNodeWithText("Geo 文件").performScrollTo().assertIsDisplayed()
                        "lan" -> compose.onNodeWithContentDescription("混合代理端口").performScrollTo().assertIsDisplayed()
                        "memory" -> compose.onNodeWithText("Go 协程数").performScrollTo().assertIsDisplayed()
                    }
                    compose.mainClock.advanceTimeBy(500)
                    compose.waitForIdle()
                    val instrumentation = InstrumentationRegistry.getInstrumentation()
                    val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
                    File(instrumentation.targetContext.getExternalFilesDir(null),
                        "verification/$label-${mode.name}-${route.substringBefore('/')}.png").apply {
                        parentFile!!.mkdirs()
                        outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                    bitmap.recycle()
                    compose.onNodeWithContentDescription("返回").performClick()
                }
            }
        } finally {
            repository.ui.darkMode = theme
            runBlocking { if (original != null) repository.select(original) }
        }
    }
}
