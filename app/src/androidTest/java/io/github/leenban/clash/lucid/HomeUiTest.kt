package io.github.leenban.clash.lucid

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.leenban.clash.design.lucid.LocalLucidColors
import io.github.leenban.clash.design.lucid.LucidPageStack
import io.github.leenban.clash.design.lucid.LucidTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun shortProfileTitleDoesNotStretchBeforeDisclosure() {
        compose.setContent {
            LucidTheme(false) {
                Box(Modifier.width(360.dp)) { HomeHeader("Weget", false, false, false, {}, {}) }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("home-profile-name", useUnmergedTree = true).performSemanticsAction(
            SemanticsActions.GetTextLayoutResult
        ) {
            it(layouts)
        }
        val text = layouts.single()
        assertTrue(
            "Short title must use its text width",
            text.size.width - text.getLineRight(0) < 4f,
        )
        val arrow =
            compose
                .onNodeWithTag("profile-disclosure", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val title =
            compose
                .onNodeWithTag("home-profile-name", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue("Disclosure must follow the title", arrow.left - title.right < 24f)
    }

    private fun save(name: String, bitmap: Bitmap) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "verification").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun profileHeaderKeepsControlsSeparateAtNarrowWidthsAndLargeFonts() {
        var width by mutableIntStateOf(360)
        var fontScale by mutableFloatStateOf(1f)
        var name by mutableStateOf("Local integration test")
        var selected = false
        var toggled = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LucidTheme(false) {
                    Box(Modifier.width(width.dp).background(LocalLucidColors.current.background)) {
                        HomeHeader(
                            name,
                            false,
                            false,
                            false,
                            { selected = true },
                            { toggled = true },
                        )
                    }
                }
            }
        }
        for ((screenWidth, scale) in listOf(360 to 1f, 320 to 1f, 320 to 1.5f)) {
            compose.runOnIdle {
                width = screenWidth
                fontScale = scale
            }
            val selector =
                compose.onNodeWithTag("home-profile-selector").fetchSemanticsNode().boundsInRoot
            val service =
                compose.onNodeWithTag("home-service-control").fetchSemanticsNode().boundsInRoot
            val disclosure =
                compose
                    .onNodeWithTag("profile-disclosure", useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
            assertTrue(
                "Profile selector must not overlap the service button",
                selector.right < service.left,
            )
            assertTrue(disclosure.left >= selector.left && disclosure.right <= selector.right)
            if (scale == 1f) {
                val layouts = mutableListOf<TextLayoutResult>()
                compose
                    .onNodeWithTag("home-profile-name", useUnmergedTree = true)
                    .performSemanticsAction(
                        androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult
                    ) {
                        it(layouts)
                    }
                save(
                    "header-${screenWidth}-font-$scale",
                    compose.onRoot().captureToImage().asAndroidBitmap(),
                )
                val layout = layouts.single()
                assertEquals("The profile name must stay on one line", 1, layout.lineCount)
                if (screenWidth == 360) {
                    assertFalse("Reference name at normal width", layout.isLineEllipsized(0))
                    assertEquals(name.length, layout.getLineEnd(0))
                }
            }
        }
        compose.onNodeWithText("当前配置").assertDoesNotExist()
        compose.runOnIdle { name = "李的长名称配置－家庭与移动网络兼容性验证" }
        compose.onNodeWithContentDescription("选择配置，当前：$name").performClick()
        compose.onNodeWithText("启动").performClick()
        compose.runOnIdle {
            assertTrue(selected)
            assertTrue(toggled)
        }
        save("header-narrow-large-font", compose.onRoot().captureToImage().asAndroidBitmap())
    }

    @Test
    fun navigationHasIntermediateFramesAndCanReverseBeforeCompletion() {
        var stack by mutableStateOf(listOf("home"))
        compose.setContent {
            LucidTheme(false) {
                LucidPageStack(stack, Modifier.size(240.dp)) { route ->
                    Box(
                        Modifier.fillMaxSize()
                            .background(if (route == "home") Color.Blue else Color.Green)
                    ) {
                        Text(route)
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeByFrame()
        val before = compose.onRoot().captureToImage().asAndroidBitmap()
        compose.runOnIdle { stack = listOf("home", "details") }
        compose.mainClock.advanceTimeBy(80)
        val middle = compose.onRoot().captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(400)
        val after = compose.onRoot().captureToImage().asAndroidBitmap()
        assertFalse(before.sameAs(middle))
        assertFalse(middle.sameAs(after))
        save("motion-forward-middle", middle)
        save("motion-forward-end", after)
        compose.runOnIdle { stack = listOf("home") }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle { stack = listOf("home", "details") }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { stack = listOf("home") }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("home").assertIsDisplayed()
        compose.onNodeWithText("details").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }
}
