package com.v2ray.ang.sovra

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal val SovraBg = Color(0xFF070B16)
internal val SovraSurface = Color(0xFF0E1627)
internal val SovraCyan = Color(0xFF22D3EE)
internal val SovraGreen = Color(0xFF34D399)
internal val SovraMuted = Color(0xFF94A3B8)
internal val SovraDanger = Color(0xFFFB7185)

/**
 * Neon shield. In 3D mode it sways by itself and can be turned with a finger;
 * in 2D mode it is a still picture with a soft glow (no animation, lowest battery use).
 */
@Composable
internal fun SovraShield(connected: Boolean, fx3d: Boolean, modifier: Modifier = Modifier) {
    if (fx3d) {
        // Real WebGL shield (three.js, offline assets). See SovraShieldWeb.kt / assets/shield.html
        SovraShieldWeb(connected = connected, modifier = modifier)
    } else {
        // Low-power mode: still picture with a soft glow, no animation.
        Shield2D(connected, modifier)
    }
}

@Composable
private fun Shield3D(connected: Boolean, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "shield")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val sway by transition.animateFloat(
        initialValue = -24f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(tween(4600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway",
    )
    var drag by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch { animate(drag, 0f) { v, _ -> drag = v } }
                    },
                    onDragCancel = {
                        scope.launch { animate(drag, 0f) { v, _ -> drag = v } }
                    },
                    onHorizontalDrag = { _, dx -> drag = (drag + dx * 0.35f).coerceIn(-80f, 80f) },
                )
            },
    ) {
        drawShield(angleDeg = sway + drag, glow = pulse, connected = connected, depthLayers = 18)
    }
}

@Composable
private fun Shield2D(connected: Boolean, modifier: Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawShield(angleDeg = 0f, glow = 0.8f, connected = connected, depthLayers = 0)
    }
}

private fun shieldPath(cx: Float, cy: Float, w: Float, h: Float): Path {
    val l = cx - w / 2
    val r = cx + w / 2
    val t = cy - h / 2
    val b = cy + h / 2
    return Path().apply {
        moveTo(cx, t)
        cubicTo(cx + w * 0.18f, t + h * 0.10f, r - w * 0.05f, t + h * 0.12f, r, t + h * 0.18f)
        lineTo(r, t + h * 0.50f)
        cubicTo(r, t + h * 0.78f, cx + w * 0.25f, b - h * 0.10f, cx, b)
        cubicTo(cx - w * 0.25f, b - h * 0.10f, l, t + h * 0.78f, l, t + h * 0.50f)
        lineTo(l, t + h * 0.18f)
        cubicTo(l + w * 0.05f, t + h * 0.12f, cx - w * 0.18f, t + h * 0.10f, cx, t)
        close()
    }
}

private fun DrawScope.drawShield(angleDeg: Float, glow: Float, connected: Boolean, depthLayers: Int) {
    val accent = if (connected) SovraGreen else SovraCyan
    val cx = size.width / 2
    val cy = size.height / 2
    val h = min(size.height * 0.80f, size.width * 1.05f)
    val w = h * 0.82f
    val center = Offset(cx, cy)

    // soft glow behind the shield
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.40f * glow), Color.Transparent),
            center = center,
            radius = max(size.width, size.height) * 0.55f,
        ),
        radius = max(size.width, size.height) * 0.55f,
        center = center,
    )

    val theta = Math.toRadians(angleDeg.toDouble())
    val sinT = sin(theta).toFloat()
    val scaleX = max(0.22f, abs(cos(theta).toFloat()))
    val step = w * 0.012f
    val path = shieldPath(cx, cy, w, h)

    // "thickness": stacked copies shifted sideways by the rotation angle
    for (i in depthLayers downTo 1) {
        val shade = 0.10f + 0.30f * (1f - i.toFloat() / depthLayers)
        withTransform({
            translate(left = -sinT * i * step * 3.2f, top = 0f)
            scale(scaleX, 1f, pivot = center)
        }) {
            drawPath(path, color = accent.copy(alpha = shade))
        }
    }

    // front face
    withTransform({
        scale(scaleX, 1f, pivot = center)
    }) {
        drawPath(
            path,
            brush = Brush.verticalGradient(
                colors = listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.55f), Color(0xFF0B1B33)),
                startY = cy - h / 2,
                endY = cy + h / 2,
            ),
        )
        drawPath(path, color = Color.White.copy(alpha = 0.55f), style = Stroke(width = w * 0.018f))

        // glossy highlight on the upper left
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                center = Offset(cx - w * 0.18f, cy - h * 0.22f),
                radius = w * 0.45f,
            ),
            radius = w * 0.45f,
            center = Offset(cx - w * 0.18f, cy - h * 0.22f),
        )

        // power mark when disconnected, check mark when connected
        val markStroke = Stroke(width = w * 0.065f, cap = StrokeCap.Round)
        val markColor = Color(0xFF04202A)
        if (connected) {
            val check = Path().apply {
                moveTo(cx - w * 0.20f, cy + h * 0.02f)
                lineTo(cx - w * 0.05f, cy + h * 0.17f)
                lineTo(cx + w * 0.22f, cy - h * 0.12f)
            }
            drawPath(check, color = markColor, style = markStroke)
        } else {
            val r = w * 0.20f
            drawArc(
                color = markColor,
                startAngle = -55f,
                sweepAngle = 290f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r + h * 0.03f),
                size = Size(r * 2, r * 2),
                style = markStroke,
            )
            drawLine(
                color = markColor,
                start = Offset(cx, cy - h * 0.20f),
                end = Offset(cx, cy - h * 0.02f),
                strokeWidth = w * 0.065f,
                cap = StrokeCap.Round,
            )
        }
    }
}
