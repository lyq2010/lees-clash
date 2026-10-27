package io.github.leenban.clash.lucid

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.leenban.clash.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Requires the local fixture QR poster on the emulator's virtualscene back camera. */
@RunWith(AndroidJUnit4::class)
class CameraImportVerificationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 90000)
    fun cameraScanReturnsUrlAndImportsLocalProfile() {
        val vm = ViewModelProvider(compose.activity)[ClientViewModel::class.java]
        val original = runBlocking { vm.repository.active() }
        try {
            compose.activityRule.scenario.onActivity { vm.openRoute("import") }
            compose.onNodeWithText("扫描二维码").performScrollTo().performClick()
            compose.waitUntil(45000) {
                runCatching {
                    compose.onAllNodes(hasContentDescription("链接") and hasText("http://127.0.0.1:18080/config"))
                        .fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            compose.onNodeWithContentDescription("链接").assertTextContains("http://127.0.0.1:18080/config")
            compose.onNodeWithContentDescription("名称").performTextReplacement("Verification camera QR")
            compose.onNodeWithText("完成").performClick()
            compose.waitUntil(30000) { vm.state.value.profiles.any { it.name == "Verification camera QR" } }
        } finally {
            runBlocking { if (original != null) vm.repository.select(original) }
        }
    }
}
