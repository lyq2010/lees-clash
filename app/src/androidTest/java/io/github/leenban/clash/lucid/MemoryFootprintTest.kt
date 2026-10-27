package io.github.leenban.clash.lucid

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import io.github.leenban.clash.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MemoryFootprintTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun leavingPoliciesReleasesPayloadAfterTransition() {
        val model = ViewModelProvider(compose.activity)[ClientViewModel::class.java]
        compose.activityRule.scenario.onActivity { model.openRoute("groups") }
        compose.waitUntil(15000) { model.state.value.groups.isNotEmpty() }
        val group = model.state.value.groups.first().name
        compose.activityRule.scenario.onActivity { model.openRoute("nodes/${android.net.Uri.encode(group)}") }
        compose.waitUntil(15000) { model.state.value.selectedGroup?.proxies?.isNotEmpty() == true }
        compose.waitUntil(10000) { model.state.value.groups.isEmpty() }
        assertNotNull("Visible node data must remain loaded", model.state.value.selectedGroup)
        compose.activityRule.scenario.onActivity { model.openRoute("home") }
        compose.waitUntil(10000) { model.state.value.selectedGroup == null }
        assertTrue(model.state.value.groups.isEmpty())
        println("MEMORY_RUNTIME " + runBlocking { model.repository.diagnostic("runtime") })
    }
}
