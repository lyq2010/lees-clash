package io.github.leenban.clash.design.lucid

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
                .padding(horizontal = 21.dp, vertical = 7.dp),
            tween(120),
            label = "service-label",
        ) { label ->
            Text(label, Modifier, Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun LucidHeader(
    title: String,
    back: (() -> Unit)? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val c = LocalLucidColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (back != null)
            Box(
                Modifier.size(48.dp)
                    .semantics { contentDescription = "返回" }
                    .lucidClickable(onClick = back),
                contentAlignment = Alignment.Center,
            ) {
                LucidIcon(LucidSymbol.Back, c.blue, Modifier.size(25.dp))
            }
        Text(
            title,
            Modifier.weight(1f).padding(start = if (back == null) 8.dp else 0.dp),
            color = c.text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (action != null)
            Text(
                action,
                Modifier.heightIn(min = 48.dp)
                    .wrapContentHeight()
                    .lucidClickable(onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                color = c.blue,
                fontSize = 17.sp,
            )
    }
}

@Composable
fun LucidSection(
    title: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalLucidColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 22.dp)) {
        if (title != null)
            Text(
                title,
                Modifier.padding(start = 18.dp, bottom = 7.dp, top = 9.dp),
                color = c.secondary,
                fontSize = 12.sp,
            )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface),
            content = content,
        )
        if (footer != null)
            Text(
                footer,
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = c.secondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
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
    onDetail: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = LocalLucidColors.current
    Column {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 50.dp)
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
                .padding(horizontal = 17.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (symbol != null) {
                Box(
                    Modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(tint),
                    contentAlignment = Alignment.Center,
                ) {
                    LucidIcon(symbol, Color.White, Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = if (destructive) c.red else c.text,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                )
                if (!subtitle.isNullOrEmpty())
                    Text(subtitle, color = c.secondary, fontSize = 12.sp, lineHeight = 17.sp)
            }
            if (value != null)
                Text(
                    value,
                    Modifier.widthIn(max = 160.dp).padding(start = 10.dp),
                    c.secondary,
                    fontSize = 14.sp,
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
                    LucidIcon(LucidSymbol.Check, c.blue, Modifier.size(21.dp))
                }
            }
            if (onClick != null && selected == null) {
                Spacer(Modifier.width(10.dp))
                LucidIcon(LucidSymbol.Chevron, c.secondary.copy(alpha = .6f), Modifier.size(15.dp))
            }
            if (onDetail != null)
                Box(
                    Modifier.size(48.dp)
                        .semantics { contentDescription = "编辑$title" }
                        .lucidClickable(onClick = onDetail),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("ⓘ", color = c.blue, fontSize = 22.sp)
                }
        }
        Box(
            Modifier.padding(start = if (symbol == null) 17.dp else 60.dp)
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
) {
    val c = LocalLucidColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 12.dp)) {
        if (label.isNotBlank())
            Text(label, Modifier.padding(bottom = 8.dp), color = c.text, fontSize = 15.sp)
        BasicTextField(
            value,
            onValueChange,
            Modifier.fillMaxWidth().heightIn(min = if (multiline) 130.dp else 28.dp),
            textStyle =
                TextStyle(
                    color = c.text,
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    fontFamily = if (multiline) FontFamily.Monospace else FontFamily.Default,
                ),
            singleLine = !multiline,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            visualTransformation =
                if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation()
                else VisualTransformation.None,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, color = c.secondary, fontSize = 16.sp)
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
        Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.field)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucidIcon(LucidSymbol.Search, c.secondary, Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value,
            onValueChange,
            Modifier.weight(1f),
            textStyle = TextStyle(color = c.text, fontSize = 15.sp),
            singleLine = true,
            decorationBox = { inner ->
                Box {
                    if (value.isBlank()) Text(placeholder, color = c.secondary, fontSize = 15.sp)
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
            .padding(horizontal = 17.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text.copy(alpha = if (enabled) 1f else .4f), fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = c.secondary, fontSize = 12.sp)
        }
        Box(
            Modifier.width(49.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(track)
                .padding(2.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier.offset(x = thumb)
                    .size(26.dp)
                    .clip(RoundedCornerShape(20.dp))
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
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.padding(horizontal = 24.dp)
            .padding(bottom = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
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
            Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LucidIcon(symbol, Color.White, Modifier.size(23.dp))
            Spacer(Modifier.width(9.dp))
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
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
        Text(title, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        if (detail.isNotBlank())
            Text(
                detail,
                Modifier.padding(top = 10.dp),
                color = c.secondary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
    }
}
