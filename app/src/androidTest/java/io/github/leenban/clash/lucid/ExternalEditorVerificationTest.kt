package io.github.leenban.clash.lucid

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import io.github.leenban.clash.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Requires Markor with its first-run setup completed on the dedicated emulator. */
@RunWith(AndroidJUnit4::class)
class ExternalEditorVerificationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val model get() = ViewModelProvider(compose.activity)[ClientViewModel::class.java]

    private fun edit(text: String, rotate: Boolean) {
        compose.onAllNodesWithText("使用外部编辑器").onFirst().performScrollTo().performClick()
        if (!device.wait(Until.hasObject(By.pkg("net.gsantner.markor")), 2000))
            device.wait(Until.findObject(By.text("Markor")), 15000).click()
        if (rotate) { device.setOrientationLeft(); device.waitForIdle() }
        val field = device.findObject(UiSelector().packageName("net.gsantner.markor").classNameMatches(".*EditText"))
        assertTrue("Markor editor must open the granted document URI", field.waitForExists(15000))
        assertTrue(field.setText(text))
        repeat(3) {
            if (device.currentPackageName == "net.gsantner.markor") { device.pressBack(); device.waitForIdle() }
        }
        assertTrue(device.wait(Until.hasObject(By.pkg("io.github.leenban.clash")), 15000))
    }

    @Test(timeout = 180000)
    fun externalYamlEditingAfterRotationValidatesAndRollsBackInvalidChanges() {
        val repo = model.repository
        val original = runBlocking { repo.active() }
        val fixture = runBlocking { repo.profiles().first { it.name == "Local integration test" } }
        val workspace = runBlocking {
            repo.profiles().firstOrNull { it.name == "Verification external editor" }
                ?: repo.cloneProfile(fixture).also { repo.saveProfile(it, "Verification external editor", it.source, 0, null, null) }
        }
        val text = runBlocking { repo.readDocument("${workspace.uuid}/config.yaml") }
        try {
            compose.activityRule.scenario.onActivity { model.openRoute("files/${workspace.uuid}") }
            compose.waitUntil(15000) { model.state.value.files.any { it.id.endsWith("/config.yaml") } }
            val valid = text + "\n# verification-external-editor\n"
            edit(valid, true)
            compose.waitUntil(15000) { !model.state.value.busy && model.state.value.notice?.text == "编辑结果已验证" }
            assertEquals(valid, runBlocking { repo.readDocument("${workspace.uuid}/config.yaml") })
            edit("proxies: [invalid: yaml", false)
            compose.waitUntil(15000) { !model.state.value.busy && model.state.value.error != null }
            assertEquals(valid, runBlocking { repo.readDocument("${workspace.uuid}/config.yaml") })
        } finally {
            repeat(3) { if (device.currentPackageName != "io.github.leenban.clash") device.pressBack() }
            device.setOrientationNatural()
            device.unfreezeRotation()
            runBlocking {
                repo.saveProfile(workspace, workspace.name, workspace.source, 0, null, text)
                if (original != null) repo.select(original)
            }
        }
    }
}
