package io.github.leenban.clash.lucid

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.leenban.clash.MainActivity
import io.github.leenban.clash.remote.StatusClient
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileWorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private lateinit var vm: ClientViewModel

    private suspend fun await(label: String, check: () -> Boolean) {
        try {
            withTimeout(20000) {
                while (true) {
                    compose.waitForIdle()
                    if (check()) break
                    delay(100)
                }
            }
        } catch (e: TimeoutCancellationException) {
            val state = vm.state.value
            throw AssertionError(
                "Waiting for $label: busy=${state.busy}, importing=${state.importing}, groups=${state.groups.size}, nodes=${state.selectedGroup?.proxies?.size}, rules=${state.rules.total}, error=${state.error?.replace(Regex("https?://[^ ]+"), "[link]")}",
                e,
            )
        }
    }

    private fun enterImport(name: String, url: String) {
        compose.onNodeWithTag("home-profile-selector").performClick()
        compose.onNode(hasText("导入") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("名称").performTextInput(name)
        compose.onNodeWithContentDescription("链接").performTextInput(url)
    }

    @Test(timeout = 90000)
    fun successfulImportReturnsToListUpdateIsInlineAndStoppedConfigurationRemainsBrowsable() =
        runBlocking {
            val repository = ClientRepository(compose.activity.applicationContext)
            val original = repository.active()
            assertNull(
                "Run this regression with the proxy service stopped",
                StatusClient(compose.activity).currentProfile(),
            )
            compose.activityRule.scenario.onActivity {
                vm = ViewModelProvider(it)[ClientViewModel::class.java]
            }
            val name = "UI workflow ${System.currentTimeMillis()}"
            val before = repository.profiles().size
            try {
                enterImport(name, "http://127.0.0.1:18080/config-slow")
                compose.onNodeWithText("完成").performClick()
                compose.onNodeWithText("导入中").assertIsNotEnabled()
                // A repeated call must not create a second profile while the form is busy.
                compose.activityRule.scenario.onActivity {
                    vm.importProfile(name, "http://127.0.0.1:18080/config-slow", "")
                }
                await("successful import") {
                    !vm.state.value.importing && vm.state.value.profiles.any { it.name == name }
                }
                compose.onNodeWithText("导入配置").assertDoesNotExist()
                compose.onNodeWithText("配置已添加").assertIsDisplayed()
                assertEquals(before + 1, repository.profiles().size)
                val imported = repository.profiles().single { it.name == name }

                compose.onNodeWithContentDescription("更新$name").performScrollTo().performClick()
                await("update started") { imported.uuid in vm.state.value.updatingProfiles }
                compose.onNodeWithText("编辑配置").assertDoesNotExist()
                compose.onNodeWithContentDescription("更新$name").assertIsNotEnabled()
                await("update completed") {
                    imported.uuid !in vm.state.value.updatingProfiles && !vm.state.value.busy
                }
                compose.onNodeWithText("配置更新完成").assertIsDisplayed()
                assertTrue(
                    repository.profile(imported.uuid.toString())!!.updatedAt >= imported.updatedAt
                )

                compose.onNodeWithContentDescription("编辑$name").performScrollTo().performClick()
                await("editor ready") {
                    vm.state.value.editingProfile?.uuid == imported.uuid && !vm.state.value.busy
                }
                val editedName = "$name edited"
                compose.onNodeWithContentDescription("名称").performTextReplacement(editedName)
                compose.onNodeWithText("完成").performClick()
                await("edit saved") {
                    !vm.state.value.busy &&
                        vm.state.value.profiles.any {
                            it.uuid == imported.uuid && it.name == editedName
                        }
                }
                assertEquals(editedName, repository.profile(imported.uuid.toString())?.name)
                compose.onNodeWithText("编辑配置").assertDoesNotExist()
                compose.onNodeWithText("配置已保存").assertIsDisplayed()
                compose.onNodeWithText(editedName).performClick()
                await("profile selected") { vm.state.value.active?.uuid == imported.uuid }
                compose.onNodeWithContentDescription("返回").performClick()
                compose.onNodeWithText("代理策略").performClick()
                await("stopped groups") { vm.state.value.groups.any { it.name == "Local-Test" } }
                compose.onNodeWithText("Local-Test").performClick()
                await("stopped nodes") { vm.state.value.selectedGroup?.proxies?.size == 2 }
                compose.onNodeWithText("启动后测速").assertIsNotEnabled()
                compose.onNodeWithText("Local-B").performClick()
                await("saved selection") { vm.state.value.selectedGroup?.now == "Local-B" }
                compose.onNodeWithText("策略已保存，下次启动生效").assertIsDisplayed()
                compose.onNodeWithContentDescription("返回").performClick()
                compose.onNodeWithContentDescription("返回").performClick()
                compose.onNodeWithText("工具").performClick()
                compose.onNodeWithText("代理策略").assertDoesNotExist()
                compose.onNodeWithText("配置管理").assertDoesNotExist()
                compose.onNodeWithText("规则检索").performScrollTo().performClick()
                await("stopped rules") { vm.state.value.rules.total == 2 }
                assertTrue(vm.state.value.rules.items.any { it.payload == "lees-clash.test" })
                assertNull(StatusClient(compose.activity).currentProfile())
            } finally {
                if (original != null) repository.select(original)
            }
        }

    @Test(timeout = 60000)
    fun failedImportStaysOnFormAndCanBeRetried() = runBlocking {
        val repository = ClientRepository(compose.activity.applicationContext)
        val before = repository.profiles().size
        compose.activityRule.scenario.onActivity {
            vm = ViewModelProvider(it)[ClientViewModel::class.java]
        }
        val name = "UI retry ${System.currentTimeMillis()}"
        enterImport(name, "http://127.0.0.1:18080/config-invalid")
        compose.onNodeWithText("完成").performClick()
        await("failed import reported") {
            !vm.state.value.importing && vm.state.value.error != null
        }
        compose.onNodeWithText("导入配置").assertIsDisplayed()
        compose.onNodeWithText("完成").assertIsEnabled()
        assertEquals(before, repository.profiles().size)
        compose
            .onNodeWithContentDescription("链接")
            .performTextReplacement("http://127.0.0.1:18080/config")
        compose.onNodeWithText("完成").performClick()
        await("retry completed") {
            !vm.state.value.importing && vm.state.value.profiles.any { it.name == name }
        }
        compose.onNodeWithText("导入配置").assertDoesNotExist()
        compose.onNodeWithText("配置已添加").assertIsDisplayed()
        assertEquals(before + 1, repository.profiles().size)
    }
}
