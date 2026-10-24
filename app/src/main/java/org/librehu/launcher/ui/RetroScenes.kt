package org.librehu.launcher.ui

import android.graphics.Paint
import android.os.SystemClock
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.delay
import org.librehu.launcher.data.WallpaperKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Car stereo display animations of the 1990s-2000s, drawn on a fine dot matrix in the accent colour (the colour of
 * the retro display, any colour with the colour wheel): dolphins jumping in a moonlit sea (in the spirit of Pioneer's
 * organic EL head units of the early 2000s), a spectrum analyser with its peaks and level meters, a cassette
 * playing, a night road, an old-school rally stage, and the demo mode going through them all. Every picture is drawn
 * here (no picture of any brand): 256 dots across, 8 brightness steps per dot as on an OEL display, shapes smoothed,
 * unlit dots faintly visible. 15 frames a second; the unlit grid is drawn once, the lit dots in one call per step.
 */
@Composable
internal fun RetroScene(kind: WallpaperKind) {
    val time = seconds()
    val base = CarColors.Background
    val lit = CarColors.Accent
    val state = remember { SceneState() }
    Spacer(
        Modifier.fillMaxSize().drawWithCache {
            val cell = size.width / COLS
            val rows = (size.height / cell).toInt().coerceAtLeast(32)
            val m = Matrix(COLS, rows)
            val dot = cell * DOT
            // Every dot, unlit: one bitmap per size.
            val grid = android.graphics.Bitmap.createBitmap(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(grid).apply {
                val p = Paint().apply { color = lit.copy(alpha = 0.06f).toArgb() }
                for (y in 0 until rows) for (x in 0 until COLS) drawRect(x * cell, y * cell, x * cell + dot, y * cell + dot, p)
            }
            val paints =
                Array(LEVELS) { l ->
                    Paint().apply {
                        color = lit.copy(alpha = 0.22f + 0.78f * (l + 1) / LEVELS).toArgb()
                        strokeWidth = dot
                        strokeCap = Paint.Cap.SQUARE
                    }
                }
            val points = Array(LEVELS) { FloatArray(COLS * rows * 2) }
            val counts = IntArray(LEVELS)
            onDrawBehind {
                drawRect(base)
                drawIntoCanvas { canvas ->
                    val c = canvas.nativeCanvas
                    c.drawBitmap(grid, 0f, 0f, null)
                    m.v.fill(0f)
                    val s = time.value
                    when (if (kind == WallpaperKind.RETRO_DEMO) DEMO[(s / DEMO_S).toInt() % DEMO.size] else kind) {
                        WallpaperKind.RETRO_ANALYZER -> analyzer(m, s, state)
                        WallpaperKind.RETRO_CASSETTE -> cassette(m, s)
                        WallpaperKind.RETRO_ROAD -> road(m, s)
                        WallpaperKind.RETRO_RALLY -> rally(m, s, state)
                        else -> dolphins(m, s)
                    }
                    counts.fill(0)
                    val half = dot / 2
                    for (y in 0 until m.h) {
                        for (x in 0 until m.w) {
                            val a = m.v[y * m.w + x]
                            if (a < 0.04f) continue
                            val l = (a * LEVELS).toInt().coerceIn(0, LEVELS - 1)
                            val i = counts[l]
                            points[l][i] = x * cell + half
                            points[l][i + 1] = y * cell + half
                            counts[l] = i + 2
                        }
                    }
                    for (l in 0 until LEVELS) if (counts[l] > 0) c.drawPoints(points[l], 0, counts[l], paints[l])
                }
            }
        },
    )
}

private const val COLS = 256
private const val DOT = 0.82f
private const val LEVELS = 8
private const val FPS = 15
private const val DEMO_S = 20f
private val DEMO =
    listOf(WallpaperKind.RETRO_DOLPHIN, WallpaperKind.RETRO_RALLY, WallpaperKind.RETRO_ANALYZER, WallpaperKind.RETRO_CASSETTE, WallpaperKind.RETRO_ROAD)

/** Seconds since the scene started, refreshed [FPS] times a second. */
@Composable
private fun seconds(): State<Float> {
    val state = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = SystemClock.uptimeMillis()
        while (true) {
            withFrameMillis { state.floatValue = (SystemClock.uptimeMillis() - start) / 1000f }
            delay(1000L / FPS)
        }
    }
    return state
}

/** What a scene keeps from one frame to the next (peaks of the analyser, the rally's dust). */
private class SceneState {
    val peaks = FloatArray(64)
    val dust = ArrayList<FloatArray>()
    var lastS = 0f
}

/** Brightness of each dot, 0..1, with smoothed (anti-aliased) drawing. */
private class Matrix(
    val w: Int,
    val h: Int,
) {
    val v = FloatArray(w * h)

    /** Drawing limited to this rectangle when set (a window the picture is seen through). */
    var clip: IntArray? = null

    /** Drawing clears dots instead of lighting them (a dark outline around a shape). */
    var erase = false

    fun set(
        x: Int,
        y: Int,
        a: Float = 1f,
    ) {
        val c = clip
        if (c != null && (x < c[0] || y < c[1] || x > c[2] || y > c[3])) return
        if (erase) {
            if (x in 0 until w && y in 0 until h && a > 0.3f) v[y * w + x] = 0f
            return
        }
        if (x in 0 until w && y in 0 until h && a > 0f) {
            val i = y * w + x
            if (a > v[i]) v[i] = min(a, 1f)
        }
    }

    /** A point between dots, spread on the four around it. */
    fun plot(
        x: Float,
        y: Float,
        a: Float = 1f,
    ) {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        set(x0, y0, a * (1 - fx) * (1 - fy))
        set(x0 + 1, y0, a * fx * (1 - fy))
        set(x0, y0 + 1, a * (1 - fx) * fy)
        set(x0 + 1, y0 + 1, a * fx * fy)
    }

    fun line(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        a: Float = 1f,
    ) {
        val n = max(abs(x1 - x0), abs(y1 - y0)).toInt().coerceAtLeast(1) * 2
        for (i in 0..n) plot(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n, a)
    }

    /** Filled ellipse with a soft edge (one dot wide). */
    fun ellipse(
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        a: Float = 1f,
    ) {
        if (rx <= 0 || ry <= 0) return
        for (y in (cy - ry - 1).toInt()..(cy + ry + 1).toInt()) {
            for (x in (cx - rx - 1).toInt()..(cx + rx + 1).toInt()) {
                val dx = (x - cx) / rx
                val dy = (y - cy) / ry
                val d = sqrt(dx * dx + dy * dy)
                val edge = (1 - d) * min(rx, ry)
                if (edge > -1f) set(x, y, a * (edge + 1f).coerceIn(0f, 1f))
            }
        }
    }

    /** Ring of radius [r], [width] dots thick, soft edges. */
    fun ring(
        cx: Float,
        cy: Float,
        r: Float,
        width: Float,
        a: Float = 1f,
    ) {
        for (y in (cy - r - width).toInt()..(cy + r + width).toInt()) {
            for (x in (cx - r - width).toInt()..(cx + r + width).toInt()) {
                val d = abs(sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) - r)
                set(x, y, a * (width / 2 + 0.5f - d).coerceIn(0f, 1f))
            }
        }
    }

    fun rect(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        a: Float = 1f,
    ) {
        for (y in y0.roundToInt()..y1.roundToInt()) for (x in x0.roundToInt()..x1.roundToInt()) set(x, y, a)
    }

    /** Filled triangle (scanline, no smoothing: crisp like a sprite). */
    fun triangle(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        cx: Float,
        cy: Float,
        a: Float = 1f,
    ) {
        val minY = min(ay, min(by, cy)).toInt()
        val maxY = max(ay, max(by, cy)).toInt()
        for (y in minY..maxY) {
            val yc = y + 0.5f
            var lo = Float.MAX_VALUE
            var hi = -Float.MAX_VALUE
            for ((p, q) in listOf((ax to ay) to (bx to by), (bx to by) to (cx to cy), (cx to cy) to (ax to ay))) {
                val (x1, y1) = p
                val (x2, y2) = q
                if ((yc - y1) * (yc - y2) <= 0 && y1 != y2) {
                    val x = x1 + (yc - y1) * (x2 - x1) / (y2 - y1)
                    lo = min(lo, x)
                    hi = max(hi, x)
                }
            }
            if (lo <= hi) for (x in lo.roundToInt()..hi.roundToInt()) set(x, y, a)
        }
    }

    /** Text in the 5 × 7 font, [scale] dots per font dot. */
    fun text(
        s: String,
        x: Int,
        y: Int,
        scale: Int = 1,
        a: Float = 1f,
    ) {
        var cx = x
        for (ch in s) {
            val g = FONT[ch] ?: FONT[' ']!!
            for (j in 0 until 7) {
                for (i in 0 until 5) {
                    if (g[j * 5 + i] != '#') continue
                    for (u in 0 until scale) for (v2 in 0 until scale) set(cx + i * scale + u, y + j * scale + v2, a)
                }
            }
            cx += 6 * scale
        }
    }
}

/** 5 × 7 dot font of the displays: the characters the scenes write. */
private val FONT: Map<Char, String> =
    mapOf(
        ' ' to "...................................",
        '0' to ".###.#...##..###.#.###..##...#.###.",
        '1' to "..#...##....#....#....#....#...###.",
        '2' to ".###.#...#....#...#...#...#...#####",
        '3' to "#####...#...#.....#.....##...#.###.",
        '4' to "...#...##..#.#.#..#.#####...#....#.",
        '5' to "######....####.....#....##...#.###.",
        '6' to "..##..#...#....####.#...##...#.###.",
        '7' to "#####....#...#...#...#....#....#...",
        '8' to ".###.#...##...#.###.#...##...#.###.",
        '9' to ".###.#...##...#.####....#...#..##..",
        ':' to "......##...##........##...##.......",
        '.' to "..........................##...##..",
        '-' to "...............#####...............",
        'A' to ".###.#...##...#######...##...##...#",
        'C' to ".###.#...##....#....#....#...#.###.",
        'E' to "######....#....####.#....#....#####",
        'G' to ".###.#...##....#.####...##...#.####",
        'I' to ".###...#....#....#....#....#...###.",
        'L' to "#....#....#....#....#....#....#####",
        'P' to "####.#...##...#####.#....#....#....",
        'R' to "####.#...##...#####.#.#..#..#.#...#",
        'S' to ".#####....#.....###.....#....#####.",
        'T' to "#####..#....#....#....#....#....#..",
        'Y' to "#...##...#.#.#...#....#....#....#..",
    )

// --- Dolphins ------------------------------------------------------------------------------------------------------

/**
 * A dolphin seen from the side, drawn along a spine that undulates as it swims: [x], [y] its centre, [len] its length,
 * [dir] 1 to the right, -1 to the left, [phase] of the tail beat, [pitch] the nose up (+) or down (-).
 */
private fun dolphin(
    m: Matrix,
    x: Float,
    y: Float,
    len: Float,
    dir: Float,
    phase: Float,
    pitch: Float,
    a: Float = 1f,
) {
    val steps = (len * 1.6f).toInt()
    var lastX = 0f
    var lastY = 0f
    for (i in 0..steps) {
        val u = i / steps.toFloat() // 0 = nose, 1 = tail
        // Body thickness: thin beak, round melon, thick chest, tapering tail stock.
        val thick =
            when {
                u < 0.08f -> 0.035f + u * 0.6f
                u < 0.22f -> 0.08f + (u - 0.08f) * 0.55f
                u < 0.5f -> 0.157f - (u - 0.22f) * 0.05f
                else -> 0.143f - (u - 0.5f) * 0.25f
            } * len
        val wave = sin(phase - u * 4f) * u * u * len * 0.12f
        val px = x + dir * (len * 0.5f - u * len) * cos(pitch)
        val py = y - (len * 0.5f - u * len) * sin(pitch) + wave + (u - 0.3f) * (u - 0.3f) * len * 0.06f
        m.ellipse(px, py, thick * 0.7f, thick * 0.55f, a)
        if (i == (steps * 0.28f).toInt()) {
            // Eye: an unlit dot.
            val ex = (px + dir * thick * 0.2f).roundToInt()
            val ey = (py - thick * 0.15f).roundToInt()
            if (ex in 0 until m.w && ey in 0 until m.h) m.v[ey * m.w + ex] = 0f
        }
        if (i == (steps * 0.42f).toInt()) {
            // Dorsal fin, swept back.
            m.triangle(px - dir * len * 0.04f, py - thick * 0.45f, px - dir * len * 0.14f, py - thick * 0.45f, px - dir * len * 0.17f, py - thick * 1.35f, a)
            // Pectoral fin, below.
            m.triangle(px + dir * len * 0.02f, py + thick * 0.3f, px - dir * len * 0.08f, py + thick * 0.4f, px - dir * len * 0.1f, py + thick * 1.1f, a * 0.85f)
        }
        lastX = px
        lastY = py
    }
    // Tail fluke: two lobes, flapping with the beat.
    val flap = sin(phase - 4f) * len * 0.045f
    m.triangle(lastX, lastY, lastX - dir * len * 0.1f, lastY - len * 0.09f + flap, lastX - dir * len * 0.05f, lastY + flap * 0.5f, a)
    m.triangle(lastX, lastY, lastX - dir * len * 0.1f, lastY + len * 0.09f + flap, lastX - dir * len * 0.05f, lastY + flap * 0.5f, a)
}

private fun dolphins(
    m: Matrix,
    s: Float,
) {
    val horizon = m.h * 0.52f
    // Stars, a few twinkling.
    for (i in 0 until 70) {
        val x = (i * 67 + 13) % m.w
        val y = (i * 31 + 5) % (horizon * 0.9f).toInt().coerceAtLeast(1)
        val tw = if ((i + (s * 3).toInt()) % 9 == 0) 0.9f else 0.25f + 0.15f * (i % 3)
        m.set(x, y, tw)
    }
    // Full moon: halo, disc, darker seas on it.
    val mx = m.w * 0.74f
    val my = horizon * 0.42f
    val mr = m.h * 0.12f
    m.ring(mx, my, mr * 1.3f, 1.2f, 0.16f)
    m.ring(mx, my, mr * 1.12f, 1.2f, 0.28f)
    m.ellipse(mx, my, mr, mr, 1f)
    for ((dx, dy, r) in listOf(Triple(-0.3f, -0.25f, 0.28f), Triple(0.25f, -0.05f, 0.22f), Triple(-0.1f, 0.3f, 0.2f), Triple(0.35f, 0.38f, 0.12f))) {
        for (y in (my + (dy - r) * mr).toInt()..(my + (dy + r) * mr).toInt()) {
            for (x in (mx + (dx - r) * mr).toInt()..(mx + (dx + r) * mr).toInt()) {
                val ux = (x - mx) / mr - dx
                val uy = (y - my) / mr - dy
                if (ux * ux + uy * uy < r * r && x in 0 until m.w && y in 0 until m.h) m.v[y * m.w + x] = 0.55f
            }
        }
    }
    // Horizon, then the sea in perspective: wave lines closer together far away, the moon's glitter path on it.
    for (x in 0 until m.w) m.set(x, horizon.toInt(), 0.55f)
    var row = 0
    var y = horizon + 2f
    while (y < m.h) {
        val d = (y - horizon) / (m.h - horizon)
        val amp = 0.4f + d * 2.2f
        val k = 0.05f + (1 - d) * 0.12f
        val dir = if (row % 2 == 0) 1 else -1
        for (x in 0 until m.w) {
            val wy = y + sin(x * k + s * (1.1f + d) * dir + row) * amp
            // Wave crests catch the moonlight only here and there: short dashes, dark water between.
            val crest = 0.5f + 0.5f * sin(x * 0.19f * (1.2f - d) + row * 1.7f + s * 0.8f * dir) * sin(x * 0.043f + row * 0.6f - s * 0.3f)
            if (crest > 0.62f) m.plot(x.toFloat(), wy, (crest - 0.5f) * (0.5f + 0.6f * d))
        }
        // Glitter under the moon, wider and brighter as it comes closer.
        val half = mr * (0.5f + d * 2.6f)
        for (gx in (mx - half).toInt()..(mx + half).toInt()) {
            val flick = sin(gx * 1.7f + row * 3.1f + s * 6f) + sin(gx * 0.37f - s * 2.3f + row)
            if (flick > 1.1f - d * 0.4f) m.plot(gx.toFloat(), y + sin(gx * k + s) * amp, 0.75f + 0.25f * d)
        }
        y += 2.5f + d * d * 12f
        row++
    }
    // Dolphins jumping at three distances, each on its own period; splash and rings where they leave and re-enter.
    for ((i, spec) in listOf(Triple(0.92f, 5.5f, 0f), Triple(0.62f, 6.5f, 2.2f), Triple(0.38f, 7.5f, 4.4f)).withIndex()) {
        val (scale, period, offset) = spec
        val t = s + offset
        val n = (t / period).toInt()
        val p = (t % period) / period
        val waterY = horizon + (m.h - horizon) * (0.18f + 0.62f * scale * scale)
        val len = m.h * 0.36f * scale
        val span = m.w * 0.42f * scale
        val dir = if ((n + i) % 2 == 0) 1f else -1f
        val startX = m.w * (0.12f + ((n * 37 + i * 23) % 50) / 100f)
        val x0 = if (dir > 0) startX else m.w - startX
        val jump = 0.55f
        if (p < jump) {
            val q = p / jump
            val x = x0 + dir * span * q
            val yy = waterY - sin(PI.toFloat() * q) * m.h * 0.52f * scale
            // A dark outline first, so that the dolphin stands out against the glitter.
            m.erase = true
            dolphin(m, x, yy, len * 1.12f, dir, t * 9f, cos(PI.toFloat() * q) * 0.7f)
            m.erase = false
            dolphin(m, x, yy, len, dir, t * 9f, cos(PI.toFloat() * q) * 0.7f, 0.65f + 0.35f * scale)
            for ((edge, ex) in listOf(0f to x0, 1f to x0 + dir * span)) {
                val dq = abs(q - edge)
                if (dq < 0.12f) {
                    val f = dq / 0.12f
                    for (j in 0 until 16) {
                        val ang = PI.toFloat() * (0.12f + 0.76f * j / 15f)
                        val r = (1 - f) * len * 0.35f
                        m.plot(ex - cos(ang) * r, waterY - sin(ang) * r * 1.1f, 0.85f * (1 - f * 0.5f))
                    }
                }
            }
        } else if (p < jump + 0.25f) {
            // Rings spreading on the water after the dive.
            val f = (p - jump) / 0.25f
            val ex = x0 + dir * span
            for (ring in 0..1) {
                val rr = len * (0.2f + f * 0.6f + ring * 0.15f)
                for (j in 0 until 40) {
                    val ang = j / 40f * 2 * PI.toFloat()
                    m.plot(ex + cos(ang) * rr, waterY + sin(ang) * rr * 0.18f, 0.5f * (1 - f))
                }
            }
        }
    }
}

// --- Spectrum analyser ---------------------------------------------------------------------------------------------

private fun analyzer(
    m: Matrix,
    s: Float,
    st: SceneState,
) {
    val bands = 32
    val bw = (m.w - 8) / bands
    val top = (m.h * 0.22f).toInt()
    val bottom = m.h - 10
    val height = bottom - top
    val dt = (s - st.lastS).coerceIn(0f, 0.2f)
    st.lastS = s
    val beat = (1 - ((s % 0.48f) / 0.48f)).let { it * it * it }
    var left = 0f
    var right = 0f
    for (b in 0 until bands) {
        val f = b / bands.toFloat()
        val level =
            (
                0.18f + 0.55f * (1 - f) * beat +
                    0.2f * (0.5f + 0.5f * sin(s * (1.9f + b * 0.17f) + b * 0.9f)) +
                    0.16f * (0.5f + 0.5f * sin(s * (4.3f + b * 0.11f) + b * 2.3f)) * (0.4f + f)
            ).coerceIn(0.03f, 1f)
        if (b < bands / 2) left += level else right += level
        st.peaks[b] = if (level >= st.peaks[b]) level else (st.peaks[b] - 0.45f * dt).coerceAtLeast(level)
        val x = (m.w - bands * bw) / 2 + b * bw
        val barTop = bottom - (height * level).toInt()
        for (y in barTop..bottom) {
            if ((bottom - y) % 3 == 2) continue
            val a = 0.5f + 0.5f * (bottom - y) / height.toFloat()
            for (dx in 0 until bw - 2) m.set(x + dx, y, a)
        }
        val py = bottom - (height * st.peaks[b]).toInt() - 3
        for (dx in 0 until bw - 2) {
            m.set(x + dx, py, 1f)
            m.set(x + dx, py - 1, 1f)
        }
    }
    // Level meters (left, right) along the top, segmented.
    for ((row, lv) in listOf(4 to left / (bands / 2), 10 to right / (bands / 2))) {
        m.text(if (row == 4) "L" else "R", 4, row - 1)
        val n = ((m.w - 20) * lv).toInt()
        for (x in 0 until n) if (x % 4 != 3) for (dy in 0..3) m.set(14 + x, row + dy, if (x > (m.w - 20) * 0.8f) 1f else 0.7f)
    }
    for (x in 4 until m.w - 4) m.set(x, bottom + 3, 0.35f)
}

// --- Cassette ------------------------------------------------------------------------------------------------------

private fun cassette(
    m: Matrix,
    s: Float,
) {
    val l = m.w * 0.12f
    val r = m.w * 0.88f
    val t = m.h * 0.16f
    val b = m.h * 0.9f
    // Shell, its rounded corners and the five screws.
    m.line(l + 4, t, r - 4, t, 0.7f)
    m.line(l + 4, b, r - 4, b, 0.7f)
    m.line(l, t + 4, l, b - 4, 0.7f)
    m.line(r, t + 4, r, b - 4, 0.7f)
    for ((cx, cy) in listOf(l to t, r to t, l to b, r to b)) m.ring(cx + if (cx < m.w / 2) 4 else -4, cy + if (cy < m.h / 2) 4 else -4, 4f, 1f, 0.7f)
    for ((sx, sy) in listOf(l + 8 to t + 8, r - 8 to t + 8, l + 8 to b - 8, r - 8 to b - 8, m.w / 2f to b - 6)) {
        m.ring(sx, sy, 2.2f, 1f, 0.6f)
        m.line(sx - 1.5f, sy, sx + 1.5f, sy, 0.6f)
    }
    // Label with its lines and the text.
    m.text("C-90", (l + 14).toInt(), (t + 6).toInt(), 1, 0.8f)
    for (k in 0..1) m.line(l + 46, t + 10 + k * 6, r - 16, t + 10 + k * 6, 0.25f)
    // Window and the reels inside: tape from the right one to the left one over a 90 s side, hubs turning.
    val wl = m.w * 0.25f
    val wr = m.w * 0.75f
    val wt = m.h * 0.34f
    val wb = m.h * 0.72f
    m.line(wl, wt, wr, wt, 0.45f)
    m.line(wl, wb, wr, wb, 0.45f)
    m.line(wl, wt, wl, wb, 0.45f)
    m.line(wr, wt, wr, wb, 0.45f)
    val p = (s % 90f) / 90f
    val cy = (wt + wb) / 2
    val big = m.w * 0.135f
    val hub = (wb - wt) * 0.2f
    for ((cx, fill) in listOf(m.w * 0.36f to p, m.w * 0.64f to 1 - p)) {
        val pack = hub * 1.3f + (big - hub * 1.3f) * (0.12f + 0.88f * fill)
        // Tape pack: shaded disc with fine rings, seen through the window only.
        m.clip = intArrayOf(wl.toInt() + 1, wt.toInt() + 1, wr.toInt() - 1, wb.toInt() - 1)
        for (k in 0..((pack - hub * 1.3f) / 2).toInt()) m.ring(cx, cy, hub * 1.3f + k * 2f, 1f, 0.22f + 0.04f * (k % 2))
        m.clip = null
        m.ring(cx, cy, hub, 1.4f, 1f)
        val turn = s * 3.2f * (big / pack)
        // Six teeth of the hub.
        for (k in 0 until 6) {
            val a = turn + k * PI.toFloat() / 3
            m.line(cx + cos(a) * hub * 0.35f, cy + sin(a) * hub * 0.35f, cx + cos(a) * hub * 0.95f, cy + sin(a) * hub * 0.95f, 0.95f)
        }
    }
    // Tape path to the heads at the bottom, and the guides.
    m.line(m.w * 0.36f - big * 0.3f, wb, m.w * 0.3f, b - 10, 0.6f)
    m.line(m.w * 0.64f + big * 0.3f, wb, m.w * 0.7f, b - 10, 0.6f)
    m.line(m.w * 0.3f, b - 10, m.w * 0.7f, b - 10, 0.9f)
    for (gx in listOf(m.w * 0.3f, m.w * 0.7f)) m.ellipse(gx, b - 10, 2f, 2f, 0.8f)
    // Mode and counter, as on the deck's display above.
    val blink = (s * 2).toInt() % 2 == 0
    m.text("PLAY", 6, 3, 1, if (blink) 1f else 0.5f)
    m.triangle(34f, 3f, 34f, 9f, 39f, 6f, 1f)
    m.text(((s * 2).toInt() % 1000).toString().padStart(3, '0'), m.w - 24, 3)
}

// --- Night road ----------------------------------------------------------------------------------------------------

private fun road(
    m: Matrix,
    s: Float,
) {
    val horizon = m.h * 0.42f
    for (i in 0 until 60) m.set((i * 53 + 7) % m.w, ((i * 29 + 3) % horizon.toInt().coerceAtLeast(1)), if ((i + (s * 2).toInt()) % 7 == 0) 0.9f else 0.3f)
    // Moon and the hills' outline.
    m.ellipse(m.w * 0.8f, horizon * 0.35f, 7f, 7f, 0.5f)
    m.ellipse(m.w * 0.8f + 3, horizon * 0.35f - 2, 6f, 6f, 0f)
    for (x in 0 until m.w) {
        val hill = m.h * 0.07f * abs(sin(x * 0.03f + s * 0.04f)) + m.h * 0.04f * abs(sin(x * 0.083f + 1.3f))
        m.plot(x.toFloat(), horizon - hill, 0.55f)
    }
    val curve = sin(s * 0.22f) * m.w * 0.3f
    val depth = m.h - horizon
    for (y in horizon.toInt() + 1 until m.h) {
        val d = (y - horizon) / depth
        val centre = m.w / 2f + curve * (1 - d) * (1 - d)
        val half = m.w * 0.42f * d + 1
        m.plot(centre - half, y.toFloat(), 1f)
        m.plot(centre + half, y.toFloat(), 1f)
        val z = 1f / (d + 0.02f)
        if (((z * 0.6f + s * 2.4f) % 1f) < 0.45f) for (dx in 0..(d * 3).toInt()) m.set((centre - d * 1.5f + dx).roundToInt(), y, 0.9f)
        if (((z * 0.3f + s * 1.2f) % 1f) < 0.07f) {
            for (side in listOf(-1, 1)) for (k in 0..(d * 6).toInt()) m.set((centre + side * half * 1.12f).roundToInt(), y - k, 0.6f)
        }
    }
    // Tail lights of a car ahead, following the bends.
    val ahead = 0.25f + 0.05f * sin(s * 0.5f)
    val ay = horizon + depth * ahead
    val ax = m.w / 2f + curve * (1 - ahead) * (1 - ahead)
    m.ellipse(ax - 4, ay, 1.4f, 1f, 1f)
    m.ellipse(ax + 4, ay, 1.4f, 1f, 1f)
}

// --- Rally ---------------------------------------------------------------------------------------------------------

/**
 * An old-school rally stage seen from behind the car, as in the 90s arcade rally games: a gravel road winding
 * through a pine forest, the car sliding through the bends with counter-steer, dust thrown up behind the wheels, the
 * stage's split time.
 */
private fun rally(
    m: Matrix,
    s: Float,
    st: SceneState,
) {
    val horizon = m.h * 0.36f
    // Mountains and the sky's horizon line.
    for (x in 0 until m.w) {
        val peak = m.h * 0.13f * abs(sin(x * 0.021f + 0.6f)) + m.h * 0.05f * abs(sin(x * 0.067f + 2f))
        for (y in (horizon - peak).toInt() until horizon.toInt()) if ((x + y) % 2 == 0) m.set(x, y, 0.2f)
        m.plot(x.toFloat(), horizon - peak, 0.6f)
    }
    val depth = m.h - horizon
    // The road bends left and right; the car slides the other way.
    val bend = sin(s * 0.45f) * 0.9f + sin(s * 0.17f) * 0.4f
    fun centre(d: Float) = m.w / 2f + bend * m.w * 0.45f * (1 - d) * (1 - d)

    fun half(d: Float) = m.w * 0.36f * d + 1.5f
    for (y in horizon.toInt() + 1 until m.h) {
        val d = (y - horizon) / depth
        val c = centre(d)
        val h = half(d)
        m.plot(c - h, y.toFloat(), 0.9f)
        m.plot(c + h, y.toFloat(), 0.9f)
        // Gravel: speckles running towards the viewer, wheel ruts.
        val z = 1f / (d + 0.03f)
        val run = z * 0.8f + s * 3.2f
        for (k in 0 until (h * 0.15f).toInt()) {
            val gx = c - h + ((k * 37 + (run * 7).toInt() * 13) % (2 * h).toInt().coerceAtLeast(1))
            m.set(gx.toInt(), y, 0.3f)
        }
        if ((run % 1f) < 0.5f) {
            m.set((c - h * 0.35f).roundToInt(), y, 0.35f)
            m.set((c + h * 0.35f).roundToInt(), y, 0.35f)
        }
    }
    // Pines on both sides, coming closer.
    for (k in 0 until 14) {
        val side = if (k % 2 == 0) -1 else 1
        val d = ((k * 0.071f + s * 0.32f) % 1f).let { it * it }
        if (d < 0.02f) continue
        val y = horizon + depth * d
        val x = centre(d) + side * (half(d) + 6 + d * 40 + (k % 3) * d * 12)
        val size = 4 + d * m.h * 0.55f
        for (tier in 0 until 3) {
            val ty = y - size * (0.25f + tier * 0.28f)
            val w = size * (0.32f - tier * 0.08f)
            m.triangle(x - w, ty, x + w, ty, x, ty - size * 0.42f, 0.55f + 0.3f * d)
        }
        m.rect(x - size * 0.03f, y - size * 0.25f, x + size * 0.03f, y, 0.5f)
    }
    // The car: slide angle against the bend, a bounce on the gravel.
    val slide = -bend * 0.5f
    val carW = m.w * 0.17f
    val carH = carW * 0.62f
    val cx = m.w / 2f - bend * m.w * 0.06f
    val cy = m.h - carH * 0.68f + sin(s * 21f) * 0.6f
    val shift = slide * carW * 0.18f
    // Dust from the rear wheels, kept between frames: born at the wheels, growing, drifting, fading.
    val dt = (s - st.lastS).coerceIn(0f, 0.2f)
    st.lastS = s
    if (dt > 0) {
        for (side in listOf(-1, 1)) st.dust += floatArrayOf(cx + side * carW * 0.42f, cy + carH * 0.3f, -bend * 18f + side * 6f, -6f - (s * 13 % 5), 0f)
    }
    val it = st.dust.iterator()
    while (it.hasNext()) {
        val p = it.next()
        p[4] += dt
        p[0] += p[2] * dt
        p[1] += p[3] * dt
        if (p[4] > 1.6f || st.dust.size > 120) it.remove()
    }
    for (p in st.dust) {
        val age = p[4]
        val r = 2f + age * 9f
        for (k in 0 until 10) {
            val ang = k * 0.63f + age * 3
            m.plot(p[0] + cos(ang) * r * ((k * 7 % 5) / 5f), p[1] + sin(ang) * r * 0.5f * ((k * 3 % 5) / 5f), 0.45f * (1 - age / 1.6f))
        }
    }
    // The car from behind: wheels, bumper and plate, rear panel with its lights, cabin with the rear window, roof
    // spoiler; the cabin leans with the slide.
    fun trap(
        topL: Float,
        topR: Float,
        top: Float,
        botL: Float,
        botR: Float,
        bot: Float,
        a: Float,
    ) {
        m.triangle(topL, top, topR, top, botR, bot, a)
        m.triangle(topL, top, botR, bot, botL, bot, a)
    }
    val lean = shift * 1.4f
    val bottom = cy + carH * 0.55f
    for (side in listOf(-1, 1)) {
        // Tyres with their tread, and the mud flaps behind.
        val wx = cx + side * carW * 0.4f
        m.rect(wx - carW * 0.075f, bottom - carH * 0.3f, wx + carW * 0.075f, bottom, 0.95f)
        for (k in 0 until 5) m.line(wx - carW * 0.075f, bottom - carH * (0.05f + k * 0.055f), wx + carW * 0.075f, bottom - carH * (0.05f + k * 0.055f), 0.35f)
        m.rect(wx - carW * 0.05f, bottom - carH * 0.12f, wx + carW * 0.05f, bottom + 1, 0.55f)
    }
    // Rear panel, a trapeze wider at the bottom.
    trap(cx - carW * 0.44f + shift, cx + carW * 0.44f + shift, cy - carH * 0.02f, cx - carW * 0.5f, cx + carW * 0.5f, cy + carH * 0.34f, 0.7f)
    // Bumper, darker, and the number plate.
    m.rect(cx - carW * 0.5f, cy + carH * 0.34f, cx + carW * 0.5f, cy + carH * 0.42f, 0.45f)
    m.rect(cx - carW * 0.11f + shift * 0.5f, cy + carH * 0.18f, cx + carW * 0.11f + shift * 0.5f, cy + carH * 0.29f, 0.15f)
    m.rect(cx - carW * 0.09f + shift * 0.5f, cy + carH * 0.2f, cx + carW * 0.09f + shift * 0.5f, cy + carH * 0.27f, 0.9f)
    // Tail lights, the brightest, and the hatch's shut line.
    for (side in listOf(-1, 1)) {
        m.rect(cx + side * carW * 0.37f + shift - carW * 0.06f, cy + carH * 0.04f, cx + side * carW * 0.37f + shift + carW * 0.06f, cy + carH * 0.14f, 1f)
    }
    m.line(cx - carW * 0.3f + shift, cy + carH * 0.16f, cx + carW * 0.3f + shift, cy + carH * 0.16f, 0.25f)
    // Cabin and its rear window (dark, with a reflection), then the roof spoiler on two stands.
    trap(cx - carW * 0.3f + lean, cx + carW * 0.3f + lean, cy - carH * 0.42f, cx - carW * 0.42f + shift, cx + carW * 0.42f + shift, cy - carH * 0.02f, 0.75f)
    trapDark(m, cx - carW * 0.24f + lean, cx + carW * 0.24f + lean, cy - carH * 0.36f, cx - carW * 0.34f + shift, cx + carW * 0.34f + shift, cy - carH * 0.07f)
    m.line(cx - carW * 0.12f + lean, cy - carH * 0.32f, cx - carW * 0.2f + shift, cy - carH * 0.1f, 0.35f)
    m.rect(cx - carW * 0.36f + lean, cy - carH * 0.52f, cx + carW * 0.36f + lean, cy - carH * 0.47f, 0.95f)
    for (side in listOf(-1, 1)) m.line(cx + side * carW * 0.18f + lean, cy - carH * 0.47f, cx + side * carW * 0.18f + lean, cy - carH * 0.42f, 0.8f)
    // Stage and split time, top left.
    val t = s % 300f
    m.text("SS3", 4, 3)
    m.text(String.format(java.util.Locale.ROOT, "%d:%02d.%d", (t / 60).toInt(), (t % 60).toInt(), ((t * 10) % 10).toInt()), 4, 12)
    m.text("RALLY", m.w - 34, 3, 1, 0.7f)
    // Speed bar, right.
    val speed = (0.6f + 0.35f * cos(s * 0.45f * 2)).coerceIn(0f, 1f)
    for (k in 0 until (speed * 20).toInt()) for (dx in 0..4) m.set(m.w - 8 + dx, 40 - k * 2, if (k > 15) 1f else 0.6f)
}

/** A trapeze cleared to unlit (a window seen dark through the lit body). */
private fun trapDark(
    m: Matrix,
    topL: Float,
    topR: Float,
    top: Float,
    botL: Float,
    botR: Float,
    bot: Float,
) {
    for (y in top.roundToInt()..bot.roundToInt()) {
        val f = (y - top) / (bot - top)
        val l = topL + (botL - topL) * f
        val r = topR + (botR - topR) * f
        for (x in l.roundToInt()..r.roundToInt()) if (x in 0 until m.w && y in 0 until m.h) m.v[y * m.w + x] = 0.06f
    }
}
