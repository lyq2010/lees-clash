package io.github.leenban.clash.design.lucid

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

/** Short, reversible feedback. Compose uses the system's animation duration scale. */
@Composable
fun Modifier.lucidClickable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    scale: Boolean = false,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val opacity by
        animateFloatAsState(
            if (pressed && enabled) .65f else 1f,
            tween(90),
            label = "press-opacity",
        )
    val size by
        animateFloatAsState(
            if (pressed && enabled && scale) .97f else 1f,
            spring(dampingRatio = 1f, stiffness = 700f),
            label = "press-scale",
        )
    return graphicsLayer {
            alpha = opacity
            scaleX = size
            scaleY = size
        }
        .clickable(source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

@Composable
fun LucidPageStack(
    stack: List<String>,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit,
) {
    AnimatedContent(
        targetState = stack,
        modifier = modifier,
        contentKey = { it.last() },
        transitionSpec = {
            if (initialState.size == targetState.size) {
                    fadeIn(tween(160)) togetherWith fadeOut(tween(110))
                } else {
                    val direction = if (targetState.size > initialState.size) 1 else -1
                    (fadeIn(tween(210)) +
                        slideInHorizontally(tween(230, easing = FastOutSlowInEasing)) {
                            direction * it / 12
                        }) togetherWith
                        (fadeOut(tween(150)) +
                            slideOutHorizontally(tween(230, easing = FastOutSlowInEasing)) {
                                -direction * it / 16
                            })
                }
                .using(SizeTransform(clip = true))
        },
        label = "page-navigation",
    ) {
        content(it.last())
    }
}
