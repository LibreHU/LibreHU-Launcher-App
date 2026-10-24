package org.librehu.launcher.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Vaporwave / synthwave sunset, as on the record covers and music videos of the 2010s looking back at the 1980s: a
 * striped sun setting behind wireframe mountains, a neon grid floor running towards the viewer, palm trees, stars.
 * Its own colours (purple, magenta, cyan, sun yellow), drawn here (no picture), 20 frames a second.
 */
@Composable
internal fun Vaporwave() {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = SystemClock.uptimeMillis()
        while (true) {
            withFrameMillis { time.floatValue = (SystemClock.uptimeMillis() - start) / 1000f }
            delay(1000L / FPS)
        }
    }
    val stars =
        remember {
            val rnd = Random(1984)
            List(90) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 0.55f, 0.6f + rnd.nextFloat() * 1.6f, rnd.nextFloat() * 6.28f) }
        }
    val far = remember { ridge(Random(7), 15, 0.10f, 0.24f) }
    val near = remember { ridge(Random(11), 9, 0.05f, 0.15f) }
    Canvas(Modifier.fillMaxSize()) {
        val s = time.floatValue
        val w = size.width
        val h = size.height
        val horizon = h * 0.62f
        val sky = Brush.verticalGradient(0f to SKY_TOP, 0.45f to SKY_MID, 1f to SKY_LOW, startY = 0f, endY = horizon)
        drawRect(sky, size = Size(w, horizon))

        for (st in stars) {
            val twinkle = 0.45f + 0.55f * sin(s * 2.2f + st[3])
            drawCircle(Color.White.copy(alpha = 0.25f + 0.6f * twinkle), st[2] * density / 2, Offset(st[0] * w, st[1] * horizon))
        }

        // Sun with its glow, its lower half cut by bands that slide down and widen.
        val r = h * 0.24f
        val sun = Offset(w / 2, horizon - r * 0.78f)
        drawCircle(Brush.radialGradient(listOf(GLOW.copy(alpha = 0.55f), Color.Transparent), sun, r * 2.1f), r * 2.1f, sun)
        drawCircle(Brush.verticalGradient(listOf(SUN_TOP, SUN_MID, SUN_LOW), startY = sun.y - r, endY = sun.y + r), r, sun)
        val slide = (s * 0.25f) % 1f
        for (i in 0 until 8) {
            val k = (i + slide) / 8f
            val y = sun.y - r * 0.1f + r * 1.1f * k
            val gap = r * (0.02f + 0.11f * k)
            drawRect(sky, Offset(sun.x - r, y), Size(2 * r, gap))
        }

        mountains(far, horizon, w, h, MOUNTAIN_FAR, CYAN.copy(alpha = 0.55f))
        mountains(near, horizon, w, h, MOUNTAIN_NEAR, CYAN)

        // Floor: dark, the sun's light on it, neon lines in perspective (horizontal ones running to the viewer).
        drawRect(Brush.verticalGradient(listOf(FLOOR_TOP, FLOOR_LOW), startY = horizon, endY = h), Offset(0f, horizon), Size(w, h - horizon))
        drawRect(
            Brush.radialGradient(listOf(GLOW.copy(alpha = 0.28f), Color.Transparent), Offset(w / 2, horizon), w * 0.35f),
            Offset(0f, horizon),
            Size(w, h - horizon),
        )
        val line = 1.6f * density
        val depth = h - horizon
        val run = (s * 0.6f) % 1f
        for (i in 0 until 18) {
            val z = 18f - i - run
            val y = horizon + depth / (z * 0.55f + 0.45f) * 0.45f
            if (y > h || y < horizon) continue
            val fade = ((y - horizon) / depth).coerceIn(0f, 1f)
            drawLine(GRID.copy(alpha = 0.1f + 0.9f * fade), Offset(0f, y), Offset(w, y), line * (0.6f + fade))
        }
        val lanes = 14
        for (i in -lanes..lanes) {
            val xb = w / 2 + i * w / lanes * 1.6f
            drawLine(GRID.copy(alpha = 0.85f), Offset(w / 2 + i * w / lanes * 0.08f, horizon), Offset(xb, h), line)
        }
        drawLine(GRID, Offset(0f, horizon), Offset(w, horizon), line * 1.5f)

        palm(Offset(w * 0.08f, h * 0.98f), h * 0.62f, -1f, s)
        palm(Offset(w * 0.93f, h * 1.02f), h * 0.55f, 1f, s + 1.3f)
    }
}

/** A mountain ridge: (x, height) pairs, 0 to 1 across the screen, heights in screen heights. */
internal fun ridge(
    rnd: Random,
    peaks: Int,
    low: Float,
    high: Float,
): List<Pair<Float, Float>> =
    (0..peaks).map { i ->
        val x = i / peaks.toFloat()
        // Higher towards the sides: the sun stays clear in the middle.
        val side = (kotlin.math.abs(x - 0.5f) * 2).coerceIn(0f, 1f)
        x to (low + (high - low) * rnd.nextFloat()) * (0.2f + 0.8f * side) * if (i % 2 == 0) 0.55f else 1f
    }

/** A ridge filled dark and outlined in neon, with lines down its faces (wireframe). */
private fun DrawScope.mountains(
    ridge: List<Pair<Float, Float>>,
    horizon: Float,
    w: Float,
    h: Float,
    fill: Color,
    edge: Color,
) {
    val path = Path().apply { moveTo(0f, horizon) }
    for ((x, y) in ridge) path.lineTo(x * w, horizon - y * h)
    path.lineTo(w, horizon)
    path.close()
    drawPath(path, fill)
    val outline = Path().apply { ridge.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * w, horizon - y * h) else lineTo(x * w, horizon - y * h) } }
    drawPath(outline, edge, style = Stroke(1.6f * density))
    for ((x, y) in ridge) {
        if (y < 0.02f) continue
        drawLine(edge.copy(alpha = edge.alpha * 0.35f), Offset(x * w, horizon - y * h), Offset(x * w + (if (x < 0.5f) 1 else -1) * y * h * 0.5f, horizon), 1f * density)
    }
}

/** A palm tree silhouette leaning towards [lean] (-1 left, 1 right), its fronds swaying a little. */
private fun DrawScope.palm(
    foot: Offset,
    height: Float,
    lean: Float,
    s: Float,
) {
    val top = Offset(foot.x + lean * height * 0.18f, foot.y - height)
    val trunk =
        Path().apply {
            moveTo(foot.x - height * 0.035f, foot.y)
            quadraticTo(foot.x + lean * height * 0.02f, foot.y - height * 0.55f, top.x - height * 0.012f, top.y)
            lineTo(top.x + height * 0.012f, top.y)
            quadraticTo(foot.x + lean * height * 0.06f, foot.y - height * 0.55f, foot.x + height * 0.035f, foot.y)
            close()
        }
    drawPath(trunk, PALM)
    val sway = sin(s * 0.9f) * 0.05f
    for (i in 0 until 7) {
        val a = (-PI / 2 + (i - 3) * 0.48 + sway).toFloat()
        val len = height * (0.46f - kotlin.math.abs(i - 3) * 0.03f)
        val end = Offset(top.x + cos(a) * len, top.y + sin(a) * len * 0.45f + len * 0.42f)
        val mid = Offset(top.x + cos(a) * len * 0.55f, top.y + sin(a) * len * 0.55f - len * 0.08f)
        val frond =
            Path().apply {
                moveTo(top.x, top.y)
                quadraticTo(mid.x, mid.y - len * 0.06f, end.x, end.y)
                quadraticTo(mid.x, mid.y + len * 0.06f, top.x, top.y + len * 0.03f)
                close()
            }
        drawPath(frond, PALM)
        // Leaflets along the frond.
        for (j in 1..6) {
            val t = j / 7f
            val p = Offset(top.x + (end.x - top.x) * t, top.y + (end.y - top.y) * t - sin(t * PI.toFloat()) * len * 0.12f)
            drawLine(PALM, p, Offset(p.x + cos(a + 1.2f) * len * 0.1f, p.y + len * 0.1f), max(1f, 2.2f * density), StrokeCap.Round)
            drawLine(PALM, p, Offset(p.x + cos(a - 1.2f) * len * 0.1f, p.y + len * 0.1f), max(1f, 2.2f * density), StrokeCap.Round)
        }
    }
}

private const val FPS = 20
private val SKY_TOP = Color(0xFF0B0221)
private val SKY_MID = Color(0xFF3A0B6B)
private val SKY_LOW = Color(0xFFE0367A)
private val SUN_TOP = Color(0xFFFFE45E)
private val SUN_MID = Color(0xFFFF8A3D)
private val SUN_LOW = Color(0xFFFF2E88)
private val GLOW = Color(0xFFFF4FA3)
private val MOUNTAIN_FAR = Color(0xFF2A0A4F)
private val MOUNTAIN_NEAR = Color(0xFF1A0636)
private val CYAN = Color(0xFF2DE2E6)
private val FLOOR_TOP = Color(0xFF1B0438)
private val FLOOR_LOW = Color(0xFF090014)
private val GRID = Color(0xFFFF38D4)
private val PALM = Color(0xFF07010F)
