package io.github.leenban.clash.design.lucid

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Modifier.lucidGlassSurface(cornerRadius: Dp = 20.dp): Modifier {
    val c = LocalLucidColors.current
    val shape = RoundedCornerShape(cornerRadius)
    val borderBrush = remember(c) { Brush.verticalGradient(listOf(c.borderTop, c.borderBottom)) }
    return this.clip(shape).drawWithContent {
        if (size.height > 2.dp.toPx() && size.width > 2.dp.toPx()) {
            val cr = CornerRadius(cornerRadius.toPx())
            val sw = 1.dp.toPx()
            drawRoundRect(c.surface, cornerRadius = cr)
            drawContent()
            drawRoundRect(
                brush = borderBrush,
                topLeft = Offset(sw / 2f, sw / 2f),
                size = Size(size.width - sw, size.height - sw),
                cornerRadius = cr,
                style = Stroke(sw),
            )
        } else {
            drawContent()
        }
    }
}

@Composable
fun Modifier.lucidLazyCardItem(index: Int, count: Int, cornerRadius: Dp = 20.dp): Modifier {
    val c = LocalLucidColors.current
    val first = index == 0
    val last = index == count - 1
    val shape =
        when {
            first && last -> RoundedCornerShape(cornerRadius)
            first -> RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
            last -> RoundedCornerShape(bottomStart = cornerRadius, bottomEnd = cornerRadius)
            else -> RectangleShape
        }
    val sideColor = remember(c) { c.borderTop.copy(alpha = (c.borderTop.alpha + c.borderBottom.alpha) * 0.5f) }
    val borderBrush = remember(c) { Brush.verticalGradient(listOf(c.borderTop, c.borderBottom)) }
    return this.clip(shape).background(c.surface).drawWithContent {
        drawContent()
        val sw = 1.dp.toPx()
        val half = sw / 2f
        val r = cornerRadius.toPx()
        clipRect {
            when {
                first && last ->
                    drawRoundRect(
                        brush = borderBrush,
                        topLeft = Offset(half, half),
                        size = Size(size.width - sw, size.height - sw),
                        cornerRadius = CornerRadius(r),
                        style = Stroke(sw),
                    )
                first ->
                    drawRoundRect(
                        color = c.borderTop,
                        topLeft = Offset(half, half),
                        size = Size(size.width - sw, size.height + r),
                        cornerRadius = CornerRadius(r),
                        style = Stroke(sw),
                    )
                last ->
                    drawRoundRect(
                        color = sideColor,
                        topLeft = Offset(half, -r),
                        size = Size(size.width - sw, size.height + r - half),
                        cornerRadius = CornerRadius(r),
                        style = Stroke(sw),
                    )
                else -> {
                    drawLine(sideColor, Offset(half, 0f), Offset(half, size.height), sw)
                    drawLine(sideColor, Offset(size.width - half, 0f), Offset(size.width - half, size.height), sw)
                }
            }
        }
    }
}

@Composable
fun LucidPill(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalLucidColors.current
    val color by
        animateColorAsState(
            c.blue.copy(alpha = if (enabled) 1f else .4f),
            tween(160),
            label = "service-button",
        )
    Box(
        Modifier.heightIn(min = 48.dp)
            .lucidClickable(enabled = enabled, scale = true, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            text,
            Modifier.clip(RoundedCornerShape(24.dp))
                .background(color)
                .animateContentSize(tween(160))
                .padding(horizontal = 20.dp, vertical = 8.dp),
            tween(120),
            label = "service-label",
        ) { label ->
            Text(label, Modifier, Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun LucidHeader(
    title: String,
    back: (() -> Unit)? = null,
    action: String? = null,
    actionEnabled: Boolean = true,
    onAction: () -> Unit = {},
) {
    val c = LocalLucidColors.current
    val borderBrush = remember(c) { Brush.verticalGradient(listOf(c.borderTop, c.borderBottom)) }
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = if (back == null) 18.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (back != null)
            Box(
                Modifier.size(48.dp)
                    .semantics { contentDescription = "返回" }
                    .lucidClickable(scale = true, onClick = back),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(34.dp)
                        .clip(CircleShape)
                        .background(c.surface)
                        .border(1.dp, borderBrush, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    LucidIcon(LucidSymbol.Back, c.text, Modifier.size(18.dp))
                }
            }
        Text(
            title,
            Modifier.weight(1f).padding(start = if (back == null) 2.dp else 4.dp),
            color = c.text,
            fontSize = if (back == null) 24.sp else 19.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (action != null) {
            val pillShape = RoundedCornerShape(18.dp)
            Box(
                Modifier.heightIn(min = 48.dp)
                    .lucidClickable(enabled = actionEnabled, scale = true, onClick = onAction)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    action,
                    Modifier.clip(pillShape)
                        .background(c.surface)
                        .border(1.dp, borderBrush, pillShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    color = c.blue.copy(alpha = if (actionEnabled) 1f else .4f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
fun LucidSection(
    title: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalLucidColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 12.dp)) {
        if (title != null)
            Text(
                title,
                Modifier.padding(start = 14.dp, bottom = 7.dp, top = 8.dp),
                color = c.secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        Column(
            Modifier.fillMaxWidth().lucidGlassSurface(20.dp),
            content = content,
        )
        if (footer != null)
            Text(
                footer,
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                color = c.secondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
    }
}

@Composable
private fun LucidIconBadge(symbol: LucidSymbol, tint: Color) {
    Box(
        Modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(tint),
        contentAlignment = Alignment.Center,
    ) {
        LucidIcon(symbol, Color.White, Modifier.size(22.dp))
    }
}

@Composable
fun LucidRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    symbol: LucidSymbol? = null,
    tint: Color = Color(0xFF007AFF),
    selected: Boolean? = null,
    destructive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onDetail: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = LocalLucidColors.current
    Column {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 52.dp)
                .then(
                    if (selected != null) Modifier.semantics { this.selected = selected }
                    else Modifier
                )
                .then(
                    if (onClick != null)
                        Modifier.lucidClickable(
                            role = if (selected != null) Role.RadioButton else Role.Button,
                            onClick = onClick,
                        )
                    else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(14.dp))
            } else if (symbol != null) {
                LucidIconBadge(symbol, tint)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = if (destructive) c.red else c.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp,
                )
                if (!subtitle.isNullOrEmpty())
                    Text(subtitle, color = c.secondary, fontSize = 12.sp, lineHeight = 17.sp)
            }
            if (value != null)
                Text(
                    value,
                    Modifier.widthIn(max = 160.dp).padding(start = 10.dp),
                    c.secondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            AnimatedVisibility(
                selected == true,
                enter = fadeIn(tween(140)) + expandHorizontally(tween(160)),
                exit = fadeOut(tween(100)) + shrinkHorizontally(tween(160)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(10.dp))
                    LucidIcon(LucidSymbol.Check, c.blue, Modifier.size(20.dp))
                }
            }
            if (onClick != null && selected == null) {
                Spacer(Modifier.width(10.dp))
                LucidIcon(LucidSymbol.Chevron, c.secondary.copy(alpha = .55f), Modifier.size(14.dp))
            }
            trailing?.invoke()
            if (onDetail != null)
                Box(
                    Modifier.size(48.dp)
                        .semantics { contentDescription = "编辑$title" }
                        .lucidClickable(onClick = onDetail),
                    contentAlignment = Alignment.Center,
                ) {
                    LucidIcon(LucidSymbol.Edit, c.blue, Modifier.size(20.dp))
                }
        }
        Box(
            Modifier.padding(start = if (symbol == null && leading == null) 16.dp else 59.dp)
                .fillMaxWidth()
                .height(.5.dp)
                .background(c.separator)
        )
    }
}

@Composable
fun LucidField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    multiline: Boolean = false,
    secret: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
) {
    val c = LocalLucidColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (label.isNotBlank())
            Text(label, Modifier.padding(bottom = 7.dp), color = c.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        BasicTextField(
            value,
            onValueChange,
            Modifier.fillMaxWidth()
                .heightIn(min = if (multiline) 130.dp else 28.dp)
                .semantics { if (label.isNotBlank()) contentDescription = label },
            enabled = enabled,
            textStyle =
                TextStyle(
                    color = c.text,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontFamily = if (multiline) FontFamily.Monospace else FontFamily.Default,
                ),
            singleLine = !multiline,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            visualTransformation =
                if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation()
                else VisualTransformation.None,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, color = c.secondary.copy(alpha = 0.6f), fontSize = 15.sp)
                    inner()
                }
            },
        )
    }
}

@Composable
fun LucidSearch(value: String, onValueChange: (String) -> Unit, placeholder: String = "搜索") {
    val c = LocalLucidColors.current
    Row(
        Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
            .fillMaxWidth()
            .lucidGlassSurface(18.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucidIcon(LucidSymbol.Search, c.secondary, Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        BasicTextField(
            value,
            onValueChange,
            Modifier.weight(1f),
            textStyle = TextStyle(color = c.text, fontSize = 15.sp),
            singleLine = true,
            decorationBox = { inner ->
                Box {
                    if (value.isBlank()) Text(placeholder, color = c.secondary.copy(alpha = 0.7f), fontSize = 15.sp)
                    inner()
                }
            },
        )
    }
}

@Composable
fun LucidToggle(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    subtitle: String? = null,
    symbol: LucidSymbol? = null,
    onToggle: (Boolean) -> Unit,
) {
    val c = LocalLucidColors.current
    val thumb by
        animateDpAsState(
            if (checked) 19.dp else 0.dp,
            tween(180, easing = FastOutSlowInEasing),
            label = "switch-thumb",
        )
    val track by
        animateColorAsState(if (checked) c.green else c.field, tween(180), label = "switch-track")
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 54.dp)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (symbol != null) {
            LucidIconBadge(symbol, Color(0xFF007AFF))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = c.text.copy(alpha = if (enabled) 1f else .4f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp,
            )
            if (subtitle != null) Text(subtitle, color = c.secondary, fontSize = 12.sp, lineHeight = 17.sp)
        }
        val trackShape = RoundedCornerShape(20.dp)
        Box(
            Modifier.width(48.dp)
                .height(28.dp)
                .clip(trackShape)
                .background(track)
                .border(0.8.dp, if (checked) Color.Transparent else c.separator, trackShape)
                .padding(2.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier.offset(x = thumb)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Composable
fun LucidCard(
    title: String,
    symbol: LucidSymbol,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.padding(horizontal = 18.dp)
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .heightIn(min = (204 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(colors))
            .drawBehind {
                val p = Path()
                p.moveTo(size.width * .65f, 0f)
                p.lineTo(size.width, 0f)
                p.lineTo(size.width, size.height * .55f)
                p.lineTo(0f, size.height)
                p.close()
                drawPath(p, Color.White.copy(alpha = .05f))
            }
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 38.dp).padding(horizontal = 15.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LucidIcon(symbol, Color.White.copy(alpha = .92f), Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                title,
                Modifier.weight(1f),
                color = Color.White,
                fontSize = 18.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        content()
    }
}

@Composable
fun LucidEmpty(title: String, detail: String = "") {
    val c = LocalLucidColors.current
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        if (detail.isNotBlank())
            Text(
                detail,
                Modifier.padding(top = 8.dp),
                color = c.secondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
    }
}
