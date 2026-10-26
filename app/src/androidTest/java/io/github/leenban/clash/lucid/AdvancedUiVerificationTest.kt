package io.github.leenban.clash.lucid

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.leenban.clash.MainActivity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdvancedUiVerificationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(350, 5000)
        val image = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "verification/$name.png").apply {
            parentFile!!.mkdirs()
            outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        image.recycle()
    }

    @Test(timeout = 120000)
    fun newPagesAndStructuredRuleFormCompleteWithFeedback() {
        val vm = ViewModelProvider(compose.activity)[ClientViewModel::class.java]
        val repo = vm.repository
        val original = runBlocking { repo.active() }
        val fixture = runBlocking { repo.profiles().first { it.name == "Local integration test" } }
        val old = runBlocking { repo.extension(fixture.uuid.toString()) }
        fun open(route: String, title: String) {
            compose.activityRule.scenario.onActivity { vm.openRoute(route) }
            compose.waitUntil(15000) {
                !vm.state.value.pageLoading && compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
            }
        }
        try {
            for ((route, title) in listOf("backup" to "本地备份与恢复", "on-demand" to "按需网络", "network-check" to "IP 与网络检查", "geodata" to "Geo 数据")) {
                open(route, title)
                screenshot(route)
                compose.onNodeWithContentDescription("返回").performClick()
            }
            runBlocking { repo.select(fixture); repo.saveExtension(fixture.uuid.toString(), "{}") }
            open("extension/${fixture.uuid}", "配置扩展")
            compose.onNodeWithText("添加规则").performClick()
            compose.onNodeWithContentDescription("类型").performTextReplacement("DOMAIN")
            compose.onNodeWithContentDescription("内容").performTextReplacement("ui-extension.test")
            compose.onNodeWithContentDescription("目标策略").performTextReplacement("DIRECT")
            screenshot("extension-rule-form")
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithText("DOMAIN,ui-extension.test,DIRECT").assertIsDisplayed()
            compose.onNodeWithText("保存").performClick()
            compose.waitUntil(15000) { !vm.state.value.busy }
            compose.onNodeWithText("配置扩展已保存").assertIsDisplayed()
            assertTrue(runBlocking { repo.diagnostic("rules", "ui-extension.test") }.contains("ui-extension.test"))
        } finally {
            runBlocking {
                repo.saveExtension(fixture.uuid.toString(), old)
                if (original != null) repo.select(original)
            }
        }
    }
}
