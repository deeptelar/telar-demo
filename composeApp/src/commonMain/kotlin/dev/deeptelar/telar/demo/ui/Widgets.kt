package dev.deeptelar.telar.demo.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** How much a button stands out. */
enum class Emphasis {
    /** The one action the screen is about: filled red. */
    Primary,

    /** An action next to the primary one: an outline that fills on hover. */
    Secondary,

    /** A way out or a link: text only. */
    Quiet,
}

private val Pill = RoundedCornerShape(percent = 50)
val PanelShape = RoundedCornerShape(6.dp)

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: Emphasis = Emphasis.Secondary,
    enabled: Boolean = true,
) {
    val colors = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val lit = enabled && (hovered || focused)

    val fill = when (emphasis) {
        Emphasis.Primary -> if (lit) colors.ink else colors.red
        Emphasis.Secondary -> if (lit) colors.ink else Color.Transparent
        Emphasis.Quiet -> Color.Transparent
    }
    val content = when (emphasis) {
        Emphasis.Primary -> if (lit) colors.ground else colors.onRed
        Emphasis.Secondary -> if (lit) colors.ground else colors.ink
        Emphasis.Quiet -> if (lit) colors.ink else colors.dim
    }
    val outline = when (emphasis) {
        Emphasis.Primary -> fill
        Emphasis.Secondary -> colors.ink
        Emphasis.Quiet -> if (focused) colors.ink else Color.Transparent
    }
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.35f)
            .height(36.dp)
            .defaultMinSize(minWidth = 64.dp)
            .background(fill, Pill)
            .border(1.dp, outline, Pill)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (emphasis == Emphasis.Quiet) 10.dp else 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Label(text, color = content)
    }
}

/** A bordered box with a labelled header, the building block of every screen. */
@Composable
fun Panel(
    title: String,
    modifier: Modifier = Modifier,
    edge: Color = Theme.colors.line,
    trailing: @Composable () -> Unit = {},
    padding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.border(1.dp, edge, PanelShape)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(title, color = if (edge == Theme.colors.line) Theme.colors.dim else edge)
            trailing()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(edge))
        Column(Modifier.fillMaxWidth().padding(padding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    secret: Boolean = false,
    enabled: Boolean = true,
    onEnter: (() -> Unit)? = null,
) {
    val colors = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(label)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panel, RoundedCornerShape(4.dp))
                .border(1.dp, if (focused) colors.ink else colors.line, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            enabled = enabled,
            textStyle = Theme.body.copy(color = colors.ink),
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(imeAction = if (onEnter != null) ImeAction.Go else ImeAction.Default),
            keyboardActions = KeyboardActions(onGo = { onEnter?.invoke() }),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            interactionSource = interaction,
            cursorBrush = SolidColor(colors.red),
        )
    }
}

/**
 * One of several choices, or a tick box when it stands alone: a square that fills when it is on.
 * A choice that is a thing with a picture gives the picture as [mark], which takes the place of the square.
 */
@Composable
fun Choice(
    selected: Boolean,
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.RadioButton,
    mark: (@Composable () -> Unit)? = null,
) {
    val colors = Theme.colors
    Row(
        modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(role = role, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (mark != null) {
            mark()
        } else {
            Box(
                Modifier.padding(top = 3.dp).size(14.dp).border(1.dp, if (selected) colors.red else colors.dim).padding(3.dp)
                    .background(if (selected) colors.red else Color.Transparent),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label(title, color = if (selected) colors.ink else colors.dim)
            if (detail.isNotEmpty()) Body(detail, color = colors.dim)
        }
    }
}

/** A field name with its value, as on a spec sheet. */
@Composable
fun Spec(name: String, value: String, valueColor: Color = Theme.colors.ink) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Label(name, Modifier.padding(top = 4.dp).width(76.dp))
        BasicText(value, Modifier.weight(1f), Theme.body.copy(color = valueColor))
    }
}

/** A faint grid of dots behind the content, like an unlit display. */
fun Modifier.dotGrid(color: Color, spacing: Dp = 14.dp): Modifier = drawBehind {
    val step = spacing.toPx()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            drawCircle(color, radius = 1.dp.toPx() * 0.6f, center = Offset(x, y))
            x += step
        }
        y += step
    }
}

/** Fades between 1 and [low] and back, once per [periodMillis]. For things that wait for the player. */
@Composable
fun pulse(low: Float = 0.25f, periodMillis: Int = 700): State<Float> =
    rememberInfiniteTransition().animateFloat(1f, low, infiniteRepeatable(tween(periodMillis, easing = LinearEasing), RepeatMode.Reverse))

/**
 * A yellow line around what the player is asked to press next, which fades in and out. It is drawn
 * outside the thing it is around, so it takes no room. [corner] is the roundness of that line, and
 * without one it is as round as a button.
 */
@Composable
fun Modifier.beckon(on: Boolean, corner: Dp? = null): Modifier {
    if (!on) return this
    val color = Theme.colors.yellow
    val glow by pulse(low = 0.35f)
    return drawBehind {
        val out = 4.dp.toPx()
        val around = Size(size.width + out * 2, size.height + out * 2)
        val radius = corner?.toPx() ?: (around.height / 2)
        drawRoundRect(color.copy(alpha = glow), Offset(-out, -out), around, CornerRadius(radius), style = Stroke(1.5.dp.toPx()))
    }
}

/** Returns [text] one more character at a time, the way a game prints dialogue. */
@Composable
fun typed(text: String, millisPerChar: Long = 14): String {
    var shown by remember(text) { mutableStateOf(0) }
    LaunchedEffect(text) {
        while (shown < text.length) {
            delay(millisPerChar)
            shown++
        }
    }
    return text.take(shown)
}
