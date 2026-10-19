package app.tatertots.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

enum class TaterMood { AWAKE, TIRED, NAP, NIGHT }

/**
 * Tater, the mascot: an original cartoon potato drawn in code (so it scales and themes cleanly).
 * Awake: blinks. Tired (settling time): droopy lids and a yawn. Asleep: breathes, with floating Zs.
 * Night: adds a nightcap.
 */
@Composable
fun Tater(mood: TaterMood, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "tater")
    val blink by t.animateFloat(
        initialValue = 1f, targetValue = 1f, label = "blink",
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 4200
            1f at 0; 1f at 3900; 0.1f at 4000; 1f at 4150
        }),
    )
    val breathe by t.animateFloat(
        0f, 1f, label = "breathe",
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
    )
    val zDrift by t.animateFloat(
        0f, 1f, label = "z",
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
    )
    val asleep = mood == TaterMood.NAP || mood == TaterMood.NIGHT

    Canvas(modifier) {
        val k = size.minDimension / 100f
        translate((size.width - 100f * k) / 2, (size.height - 100f * k) / 2) {
            scale(k, k, pivot = Offset.Zero) {
                drawTater(mood, blink, if (asleep) breathe else 0f, zDrift)
            }
        }
    }
}

private fun DrawScope.drawTater(mood: TaterMood, blink: Float, breathe: Float, zDrift: Float) {
    val ink = Spud.Outline
    val asleep = mood == TaterMood.NAP || mood == TaterMood.NIGHT

    // shadow
    drawOval(Color.Black.copy(alpha = 0.12f), topLeft = Offset(22f, 86f), size = Size(56f, 8f))

    val s = 1f + breathe * 0.035f
    scale(s, 1f / s * (1f + breathe * 0.01f), pivot = Offset(50f, 88f)) {
        // body: a lumpy potato
        val body = Path().apply {
            moveTo(50f, 22f)
            cubicTo(70f, 18f, 84f, 32f, 82f, 52f)
            cubicTo(80f, 72f, 70f, 88f, 48f, 88f)
            cubicTo(28f, 88f, 18f, 74f, 19f, 56f)
            cubicTo(20f, 38f, 32f, 25f, 50f, 22f)
            close()
        }
        drawPath(body, Spud.Skin)
        // shading on the lower right
        val shade = Path().apply {
            moveTo(80f, 50f)
            cubicTo(80f, 72f, 68f, 86f, 48f, 86f)
            cubicTo(62f, 80f, 74f, 68f, 80f, 50f)
            close()
        }
        drawPath(shade, Spud.SkinShade)
        drawPath(body, ink, style = Stroke(width = 2.6f))

        // potato spots
        listOf(Offset(32f, 40f) to 2.2f, Offset(68f, 36f) to 1.8f, Offset(30f, 72f) to 1.6f, Offset(70f, 70f) to 2f)
            .forEach { (c, r) -> drawCircle(Spud.SkinShade, r, c) }

        // cheeks
        drawCircle(Spud.Blush.copy(alpha = 0.7f), 5f, Offset(33f, 62f))
        drawCircle(Spud.Blush.copy(alpha = 0.7f), 5f, Offset(67f, 62f))

        // eyes
        val le = Offset(40f, 53f)
        val re = Offset(60f, 53f)
        when {
            asleep -> listOf(le, re).forEach { c ->
                val arc = Path().apply { moveTo(c.x - 5f, c.y); quadraticTo(c.x, c.y + 5f, c.x + 5f, c.y) }
                drawPath(arc, ink, style = Stroke(2.4f, cap = StrokeCap.Round))
            }
            mood == TaterMood.TIRED -> listOf(le, re).forEach { c ->
                drawOval(ink, Offset(c.x - 3.6f, c.y - 1f), Size(7.2f, 5f))
                drawLine(ink, Offset(c.x - 4.2f, c.y - 1f), Offset(c.x + 4.2f, c.y - 1f), 2.2f, StrokeCap.Round)
            }
            else -> listOf(le, re).forEach { c ->
                val h = 9f * blink
                drawOval(ink, Offset(c.x - 3.8f, c.y - h / 2), Size(7.6f, h))
                if (blink > 0.5f) drawCircle(Color.White, 1.4f, Offset(c.x + 1.2f, c.y - 1.8f))
            }
        }

        // mouth
        when (mood) {
            TaterMood.AWAKE -> {
                val smile = Path().apply { moveTo(44f, 66f); quadraticTo(50f, 72f, 56f, 66f) }
                drawPath(smile, ink, style = Stroke(2.4f, cap = StrokeCap.Round))
            }
            TaterMood.TIRED -> drawOval(ink, Offset(46.5f, 64f), Size(7f, 8f)) // yawn
            else -> drawOval(ink, Offset(48.2f, 65f), Size(3.6f, 3f))
        }

        // nightcap
        if (mood == TaterMood.NIGHT) {
            val cap = Path().apply {
                moveTo(30f, 32f)
                cubicTo(36f, 16f, 56f, 8f, 78f, 14f)
                cubicTo(70f, 18f, 66f, 26f, 70f, 31f)
                cubicTo(56f, 25f, 42f, 26f, 30f, 32f)
                close()
            }
            drawPath(cap, Spud.Cap)
            drawPath(cap, ink, style = Stroke(2.2f))
            val band = Path().apply {
                moveTo(28f, 33f); cubicTo(42f, 25f, 58f, 24f, 72f, 32f)
            }
            drawPath(band, Spud.CapLight, style = Stroke(5f, cap = StrokeCap.Round))
            drawCircle(Spud.CapLight, 4.5f, Offset(80f, 14f))
            drawCircle(ink, 4.5f, Offset(80f, 14f), style = Stroke(1.6f))
        }
        // little sprout when awake
        if (!asleep) {
            val stem = Path().apply { moveTo(50f, 22f); quadraticTo(49f, 16f, 52f, 12f) }
            drawPath(stem, Spud.Leaf, style = Stroke(2.2f, cap = StrokeCap.Round))
            val leaf = Path().apply {
                moveTo(52f, 12f); cubicTo(56f, 6f, 64f, 8f, 64f, 8f); cubicTo(62f, 14f, 56f, 15f, 52f, 12f); close()
            }
            drawPath(leaf, Spud.Leaf)
        }
    }

    // floating Zs
    if (asleep) {
        for (i in 0..1) {
            val p = (zDrift + i * 0.5f) % 1f
            val x = 78f + p * 10f
            val y = 40f - p * 26f
            val z = 4f + p * 3f
            val a = (1f - p).coerceIn(0f, 1f)
            val zPath = Path().apply {
                moveTo(x, y); lineTo(x + z, y); lineTo(x, y + z); lineTo(x + z, y + z)
            }
            drawPath(zPath, Spud.Cap.copy(alpha = a), style = Stroke(1.8f, cap = StrokeCap.Round))
        }
    }
}

/** Tater's mood mirrors the baby: asleep, napping, getting tired (settling time) or awake. */
fun app.tatertots.data.AppState.mood(now: Long = System.currentTimeMillis()): TaterMood {
    val cur = current
    if (cur != null) return if (cur.kind == app.tatertots.engine.SleepKind.NIGHT) TaterMood.NIGHT else TaterMood.NAP
    val settle = settleAt(prediction(now))
    return if (settle != null && now >= settle) TaterMood.TIRED else TaterMood.AWAKE
}
