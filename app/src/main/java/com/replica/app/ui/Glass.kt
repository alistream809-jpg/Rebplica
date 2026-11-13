package com.replica.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

val Ink = Color(0xFF05060B)
val Indigo = Color(0xFF4C5DFF)
val Sky = Color(0xFF28B7FF)
val Violet = Color(0xFF9B5CFF)

fun label(size: Int, weight: FontWeight = FontWeight.Medium, color: Color = Color.White, spacing: Float = 0f) =
    TextStyle(color = color, fontSize = size.sp, fontWeight = weight, letterSpacing = spacing.sp)

// ---------------------------------------------------------------- background

private fun DrawScope.blob(color: Color, center: Offset, radius: Float, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            listOf(color.copy(alpha = alpha), Color.Transparent), center = center, radius = radius
        ),
        radius = radius, center = center
    )
}

@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "aurora")
    val a by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(21000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val c by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(27000, easing = LinearEasing), RepeatMode.Reverse), label = "c")
    Canvas(modifier.fillMaxSize()) {
        drawRect(Ink)
        val w = size.width
        val h = size.height
        blob(Indigo, Offset(w * (0.10f + 0.65f * a), h * (0.10f + 0.20f * b)), w * 1.05f, 0.62f)
        blob(Violet, Offset(w * (0.95f - 0.60f * b), h * (0.45f + 0.15f * c)), w * 0.95f, 0.42f)
        blob(Sky, Offset(w * (0.20f + 0.50f * c), h * (0.88f - 0.22f * a)), w * 0.90f, 0.38f)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = 0.55f))))
    }
}

// ---------------------------------------------------------------- glass surface

private val DefaultFill = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.07f))
)
private val RimBrush = Brush.linearGradient(
    listOf(Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.30f))
)

fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    glow: Float = 0.22f,
    fill: Brush = DefaultFill
): Modifier = this
    .shadow(
        elevation = 16.dp, shape = shape,
        ambientColor = Color.White.copy(alpha = glow), spotColor = Color.White.copy(alpha = glow)
    )
    .clip(shape)
    .background(fill)
    .border(1.dp, RimBrush, shape)

@Composable
fun Modifier.appear(delayMs: Int = 0): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        shown = true
    }
    val a by animateFloatAsState(
        if (shown) 1f else 0f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow), label = "appear"
    )
    return this.graphicsLayer {
        alpha = a.coerceIn(0f, 1f)
        translationY = (1f - a) * 56f
        scaleX = 0.93f + 0.07f * a
        scaleY = 0.93f + 0.07f * a
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, delayMs: Int = 0, content: @Composable () -> Unit) {
    Box(modifier.appear(delayMs).glass()) { content() }
}

// ---------------------------------------------------------------- button

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    delayMs: Int = 0,
    height: Dp = 58.dp,
    icon: Glyph? = null
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "press")
    val inf = rememberInfiniteTransition(label = "btn")
    val pulse by inf.animateFloat(0.16f, 0.46f, infiniteRepeatable(tween(2300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val shim by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "shim")
    val shape = RoundedCornerShape(height / 2.7f)
    val fill = if (primary) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.96f), Color(0xFFD9DEFF).copy(alpha = 0.84f)))
    } else DefaultFill
    val fg = if (primary) Color(0xFF0A0B14) else Color.White
    val sweep = if (primary) Color(0xFF7C8CFF).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.30f)

    Row(
        modifier
            .appear(delayMs)
            .graphicsLayer {
                scaleX = press; scaleY = press
                alpha = if (enabled) 1f else 0.45f
            }
            .fillMaxWidth()
            .height(height)
            .glass(shape, glow = if (primary) pulse + 0.1f else pulse * 0.8f, fill = fill)
            .drawWithContent {
                drawContent()
                val x = size.width * (-0.45f + 1.9f * shim)
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, sweep, Color.Transparent),
                        start = Offset(x, 0f), end = Offset(x + size.width * 0.38f, size.height * 0.7f)
                    )
                )
            }
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Glyphs(icon, fg, 22.dp)
            Box(Modifier.width(10.dp))
        }
        BasicText(text, style = label(17, FontWeight.SemiBold, fg, -0.2f))
    }
}

@Composable
fun IconPill(icon: Glyph, onClick: () -> Unit, modifier: Modifier = Modifier, delayMs: Int = 0) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.9f else 1f, spring(0.55f, 600f), label = "pill")
    Box(
        modifier
            .appear(delayMs)
            .graphicsLayer { scaleX = s; scaleY = s }
            .size(46.dp)
            .glass(CircleShape, glow = 0.3f)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Glyphs(icon, Color.White, 22.dp) }
}

// ---------------------------------------------------------------- switch + segmented

@Composable
fun GlassSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, spring(0.7f, 500f), label = "knob")
    val track by animateColorAsState(
        if (checked) Color(0xFF34C759) else Color.White.copy(alpha = 0.18f), tween(220), label = "track"
    )
    Box(
        Modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(track)
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!checked) }
    ) {
        Box(
            Modifier
                .offset(x = x, y = 2.dp)
                .size(28.dp)
                .shadow(4.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .padding(3.dp)
    ) {
        options.forEachIndexed { i, text ->
            val on = i == selected
            val bg by animateColorAsState(if (on) Color.White.copy(alpha = 0.92f) else Color.Transparent, tween(220), label = "seg")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(11.dp))
                    .background(bg)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text,
                    style = label(15, FontWeight.SemiBold, if (on) Color(0xFF0A0B14) else Color.White.copy(alpha = 0.85f))
                )
            }
        }
    }
}

@Composable
fun GlassToast(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp), glow = 0.3f, fill = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.16f))
            ))
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        BasicText(message, style = label(15, FontWeight.Medium, Color.White))
    }
}

// ---------------------------------------------------------------- icons (drawn, no emoji)

enum class Glyph { Projects, Converter, Settings, Back, Video, Check, Trash, Share, Download, Open }

@Composable
fun Glyphs(kind: Glyph, tint: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val sw = s * 0.085f
        val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, p(x1, y1), p(x2, y2), strokeWidth = sw, cap = StrokeCap.Round)
        fun poly(vararg pts: Float) {
            val path = Path()
            path.moveTo(pts[0] * s, pts[1] * s)
            var i = 2
            while (i < pts.size) { path.lineTo(pts[i] * s, pts[i + 1] * s); i += 2 }
            drawPath(path, tint, style = st)
        }
        when (kind) {
            Glyph.Projects -> {
                val r = CornerRadius(s * 0.1f)
                val q = s * 0.34f
                for (gx in 0..1) for (gy in 0..1) {
                    drawRoundRect(
                        tint, Offset(s * (0.12f + gx * 0.44f), s * (0.12f + gy * 0.44f)),
                        Size(q, q), r, style = st
                    )
                }
            }
            Glyph.Converter -> {
                line(0.14f, 0.34f, 0.84f, 0.34f); poly(0.66f, 0.18f, 0.84f, 0.34f, 0.66f, 0.50f)
                line(0.86f, 0.68f, 0.16f, 0.68f); poly(0.34f, 0.52f, 0.16f, 0.68f, 0.34f, 0.84f)
            }
            Glyph.Settings -> {
                line(0.12f, 0.26f, 0.88f, 0.26f); line(0.12f, 0.50f, 0.88f, 0.50f); line(0.12f, 0.74f, 0.88f, 0.74f)
                drawCircle(Ink, s * 0.1f, p(0.66f, 0.26f)); drawCircle(tint, s * 0.1f, p(0.66f, 0.26f), style = st)
                drawCircle(Ink, s * 0.1f, p(0.34f, 0.50f)); drawCircle(tint, s * 0.1f, p(0.34f, 0.50f), style = st)
                drawCircle(Ink, s * 0.1f, p(0.72f, 0.74f)); drawCircle(tint, s * 0.1f, p(0.72f, 0.74f), style = st)
            }
            Glyph.Back -> poly(0.62f, 0.18f, 0.30f, 0.50f, 0.62f, 0.82f)
            Glyph.Video -> {
                drawRoundRect(tint, p(0.10f, 0.20f), Size(s * 0.80f, s * 0.60f), CornerRadius(s * 0.14f), style = st)
                val path = Path()
                path.moveTo(s * 0.43f, s * 0.36f); path.lineTo(s * 0.43f, s * 0.64f); path.lineTo(s * 0.66f, s * 0.50f); path.close()
                drawPath(path, tint)
            }
            Glyph.Check -> poly(0.20f, 0.54f, 0.42f, 0.76f, 0.82f, 0.28f)
            Glyph.Trash -> {
                line(0.18f, 0.28f, 0.82f, 0.28f)
                poly(0.28f, 0.28f, 0.32f, 0.84f, 0.68f, 0.84f, 0.72f, 0.28f)
                poly(0.40f, 0.28f, 0.40f, 0.16f, 0.60f, 0.16f, 0.60f, 0.28f)
            }
            Glyph.Share -> {
                line(0.50f, 0.60f, 0.50f, 0.12f); poly(0.32f, 0.30f, 0.50f, 0.12f, 0.68f, 0.30f)
                poly(0.30f, 0.42f, 0.20f, 0.42f, 0.20f, 0.88f, 0.80f, 0.88f, 0.80f, 0.42f, 0.70f, 0.42f)
            }
            Glyph.Download -> {
                line(0.50f, 0.12f, 0.50f, 0.62f); poly(0.30f, 0.44f, 0.50f, 0.64f, 0.70f, 0.44f)
                poly(0.18f, 0.64f, 0.18f, 0.88f, 0.82f, 0.88f, 0.82f, 0.64f)
            }
            Glyph.Open -> {
                poly(0.44f, 0.20f, 0.20f, 0.20f, 0.20f, 0.82f, 0.80f, 0.82f, 0.80f, 0.56f)
                line(0.46f, 0.54f, 0.84f, 0.16f); poly(0.60f, 0.16f, 0.84f, 0.16f, 0.84f, 0.40f)
            }
        }
    }
}
