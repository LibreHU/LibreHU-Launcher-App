package org.librehu.launcher.ui

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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.delay
import org.librehu.launcher.data.WallpaperKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Car stereo display animations of the 1990s-2000s, drawn on a dot matrix in the theme's colours (the retro styles'
 * VFD amber, green LCD or cassette tones; the accent in the modern style): dolphins jumping over the sea (as on
 * Pioneer's organic EL head units of the early 2000s), a spectrum analyser with falling peaks, a cassette with its
 * reels and tape counter, a night road, and the demo mode that goes through them all. Every picture is drawn here (no
 * picture of any brand), unlit dots stay faintly visible as on a real display, 15 frames a second.
 */
@Composable
internal fun RetroScene(kind: WallpaperKind) {
    val time = seconds()
    val base = CarColors.Background
    val lit = CarColors.Accent
    val peaks = remember { FloatArray(64) }
    Spacer(
        Modifier.fillMaxSize().drawWithCache {
            val cell = size.width / COLS
            val rows = (size.height / cell).toInt().coerceAtLeast(16)
            // Every dot, unlit: drawn once per size, the lit ones go over it.
            val grid = Path()
            for (y in 0 until rows) for (x in 0 until COLS) grid.addRect(Rect(x * cell, y * cell, x * cell + cell * DOT, y * cell + cell * DOT))
            val m = Matrix(COLS, rows)
            onDrawBehind {
                drawRect(base)
                drawPath(grid, lit.copy(alpha = 0.07f))
                m.v.fill(0f)
                val s = time.value
                when (if (kind == WallpaperKind.RETRO_DEMO) DEMO[(s / DEMO_S).toInt() % DEMO.size] else kind) {
                    WallpaperKind.RETRO_ANALYZER -> analyzer(m, s, peaks)
                    WallpaperKind.RETRO_CASSETTE -> cassette(m, s)
                    WallpaperKind.RETRO_ROAD -> road(m, s)
                    else -> dolphins(m, s)
                }
                draw(m, cell, lit)
            }
        },
    )
}

private const val COLS = 128
private const val DOT = 0.78f
private const val FPS = 15
private const val DEMO_S = 20f
private val DEMO = listOf(WallpaperKind.RETRO_DOLPHIN, WallpaperKind.RETRO_ANALYZER, WallpaperKind.RETRO_CASSETTE, WallpaperKind.RETRO_ROAD)

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

/** Brightness of each dot, 0..1. */
private class Matrix(
    val w: Int,
    val h: Int,
) {
    val v = FloatArray(w * h)

    fun set(
        x: Int,
        y: Int,
        a: Float = 1f,
    ) {
        if (x in 0 until w && y in 0 until h) v[y * w + x] = maxOf(v[y * w + x], a)
    }

    fun line(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        a: Float = 1f,
    ) {
        val n = maxOf(abs(x1 - x0), abs(y1 - y0), 1)
        for (i in 0..n) set(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n, a)
    }

    fun circle(
        cx: Float,
        cy: Float,
        r: Float,
        a: Float = 1f,
        fill: Boolean = false,
    ) {
        val steps = (r * 8).toInt().coerceAtLeast(12)
        for (i in 0 until steps) {
            val t = i * 2 * PI.toFloat() / steps
            set((cx + r * cos(t)).roundToInt(), (cy + r * sin(t)).roundToInt(), a)
        }
        if (fill) {
            for (y in (cy - r).toInt()..(cy + r).toInt()) {
                for (x in (cx - r).toInt()..(cx + r).toInt()) {
                    if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) set(x, y, a)
                }
            }
        }
    }
}

/** Lit dots in four brightness steps: four paths, four draw calls a frame. */
private fun DrawScope.draw(
    m: Matrix,
    cell: Float,
    lit: androidx.compose.ui.graphics.Color,
) {
    val dot = cell * DOT
    val paths = Array(4) { Path() }
    for (y in 0 until m.h) {
        for (x in 0 until m.w) {
            val a = m.v[y * m.w + x]
            if (a <= 0.02f) continue
            paths[(a * 3.99f).toInt().coerceIn(0, 3)].addRect(Rect(x * cell, y * cell, x * cell + dot, y * cell + dot))
        }
    }
    for (l in 0..3) drawPath(paths[l], lit.copy(alpha = 0.4f + 0.2f * l))
}

// --- Dolphins ------------------------------------------------------------------------------------------------------

/** A dolphin, drawn for this launcher (20 × 8 dots), nose to the right. */
private val DOLPHIN =
    listOf(
        "...........##.......",
        "..........###.......",
        ".....###########....",
        "...###############..",
        ".#################.#",
        "##...############.##",
        "........######......",
        ".........##.##......",
    )

private fun dolphins(
    m: Matrix,
    s: Float,
) {
    val sea = (m.h * 0.66f).toInt()
    // Moon.
    m.circle(m.w * 0.84f, m.h * 0.2f, m.h * 0.09f, 0.35f, fill = true)
    m.circle(m.w * 0.84f, m.h * 0.2f, m.h * 0.09f, 0.8f)
    // Waves: a moving crest line and glints below it.
    for (x in 0 until m.w) {
        val crest = sea + (1.3f * sin(x * 0.22f + s * 2.2f) + 0.6f * sin(x * 0.07f - s * 1.1f)).roundToInt()
        m.set(x, crest, 1f)
        for (y in crest + 2 until m.h) {
            if ((x * 7 + y * 13 + (s * 5).toInt()) % 29 == 0) m.set(x, y, 0.45f)
        }
    }
    // Two dolphins, one jump every 7 s each, half a period apart.
    for (k in 0..1) {
        val period = 7f
        val t = s + k * period / 2
        val n = (t / period).toInt()
        val p = (t % period) / period
        val span = m.w * 0.42f
        val x0 = ((n * 37 + k * 50) % (m.w - span.toInt() - 24)) + 4f
        if (p < 0.5f) {
            val q = p / 0.5f
            val x = x0 + span * q
            val y = sea - m.h * 0.42f * sin(PI.toFloat() * q)
            sprite(m, DOLPHIN, x, y, slope = cos(PI.toFloat() * q) * 0.55f)
            // Splash on leaving and on entering the water.
            for (edge in listOf(0f, 1f)) {
                val d = abs(q - edge)
                if (d < 0.12f) splash(m, (x0 + span * edge).toInt() + 10, sea, d / 0.12f, n * 7 + k)
            }
        } else {
            // Swimming: the dorsal fin cutting the water.
            val q = (p - 0.5f) / 0.5f
            val x = (x0 + span + 10 + (m.w * 0.3f) * q).toInt() % m.w
            m.set(x, sea - 2)
            m.set(x - 1, sea - 1)
            m.set(x, sea - 1)
            m.set(x + 1, sea - 1)
        }
    }
    // Bubbles rising.
    for (i in 0 until 6) {
        val bx = (i * 23 + 9) % m.w
        val by = m.h - 1 - ((s * 3 + i * 5) % (m.h - sea - 3)).toInt()
        m.set(bx, by, 0.6f)
    }
}

/** [rows] drawn centred on ([x], [y]), each column shifted by [slope] (nose up when it is positive). */
private fun sprite(
    m: Matrix,
    rows: List<String>,
    x: Float,
    y: Float,
    slope: Float,
) {
    val w = rows[0].length
    val h = rows.size
    for (j in 0 until h) {
        for (i in 0 until w) {
            if (rows[j][i] != '#') continue
            val dy = (-slope * (i - w / 2f)).roundToInt()
            m.set((x - w / 2f + i).roundToInt(), (y - h / 2f + j).roundToInt() + dy)
        }
    }
}

private fun splash(
    m: Matrix,
    x: Int,
    sea: Int,
    f: Float,
    seed: Int,
) {
    for (i in 0 until 7) {
        val dx = ((seed * 31 + i * 17) % 9) - 4
        val up = (4 * (1 - f) * (1 + (i % 3))).roundToInt()
        m.set(x + dx * (1 + (1 - f).roundToInt()), sea - up, 0.9f - f * 0.5f)
    }
}

// --- Spectrum analyser ---------------------------------------------------------------------------------------------

private fun analyzer(
    m: Matrix,
    s: Float,
    peaks: FloatArray,
) {
    val bands = minOf(m.w / 4, peaks.size)
    val top = (m.h * 0.12f).toInt()
    val bottom = m.h - 3
    val height = bottom - top
    for (b in 0 until bands) {
        // A beat every 0.48 s (125 bpm), stronger in the bass, plus slow movements of the other bands.
        val beat = (1 - ((s % 0.48f) / 0.48f)).let { it * it }
        val bass = (1 - b / bands.toFloat())
        val level =
            (
                0.25f + 0.35f * bass * beat +
                    0.22f * (0.5f + 0.5f * sin(s * (1.7f + b * 0.13f) + b * 0.9f)) +
                    0.18f * (0.5f + 0.5f * sin(s * (3.1f + b * 0.07f) + b * 2.3f))
            ).coerceIn(0.05f, 1f)
        peaks[b] = if (level >= peaks[b]) level else (peaks[b] - 0.6f / FPS).coerceAtLeast(level)
        val x = b * 4 + (m.w - bands * 4) / 2
        val barTop = bottom - (height * level).toInt()
        for (y in barTop..bottom) {
            // LED segments: every other row, brighter at the top.
            if ((bottom - y) % 2 != 0) continue
            val a = 0.55f + 0.45f * (bottom - y) / height.toFloat()
            for (dx in 0..2) m.set(x + dx, y, a)
        }
        val py = bottom - (height * peaks[b]).toInt() - 2
        for (dx in 0..2) m.set(x + dx, py, 1f)
    }
    // Baseline.
    for (x in 0 until m.w) m.set(x, bottom + 2, 0.4f)
}

// --- Cassette ------------------------------------------------------------------------------------------------------

/** Digits 0-9 of a 3 × 5 dot font. */
private val DIGITS =
    listOf(
        "111101101101111",
        "010110010010111",
        "111001111100111",
        "111001111001111",
        "101101111001001",
        "111100111001111",
        "111100111101111",
        "111001001001001",
        "111101111101111",
        "111101111001111",
    )

private fun digits(
    m: Matrix,
    text: String,
    x: Int,
    y: Int,
    scale: Int,
) {
    text.forEachIndexed { k, c ->
        val g = DIGITS[c - '0']
        for (j in 0 until 5) {
            for (i in 0 until 3) {
                if (g[j * 3 + i] != '1') continue
                for (a in 0 until scale) for (b in 0 until scale) m.set(x + k * 4 * scale + i * scale + a, y + j * scale + b)
            }
        }
    }
}

private fun cassette(
    m: Matrix,
    s: Float,
) {
    val l = (m.w * 0.1f).toInt()
    val r = (m.w * 0.9f).toInt()
    val t = (m.h * 0.18f).toInt()
    val b = (m.h * 0.88f).toInt()
    // Shell and its window.
    m.line(l, t, r, t, 0.6f)
    m.line(l, b, r, b, 0.6f)
    m.line(l, t, l, b, 0.6f)
    m.line(r, t, r, b, 0.6f)
    val wl = (m.w * 0.24f).toInt()
    val wr = (m.w * 0.76f).toInt()
    val wt = (m.h * 0.34f).toInt()
    val wb = (m.h * 0.66f).toInt()
    m.line(wl, wt, wr, wt, 0.35f)
    m.line(wl, wb, wr, wb, 0.35f)
    // Reels: tape from the right one to the left one over a 90 s side, spokes turning.
    val side = 90f
    val p = (s % side) / side
    val cy = (wt + wb) / 2f
    val big = (wb - wt) * 0.62f
    val hub = big * 0.3f
    for ((cx, fill) in listOf(m.w * 0.33f to p, m.w * 0.67f to 1 - p)) {
        val tape = hub + (big - hub) * (0.15f + 0.85f * fill)
        m.circle(cx, cy, tape, 0.3f, fill = true)
        m.circle(cx, cy, hub, 1f)
        val turn = s * 3f * (big / tape)
        for (k in 0 until 6) {
            val a = turn + k * PI.toFloat() / 3
            m.line(cx.roundToInt(), cy.roundToInt(), (cx + hub * cos(a)).roundToInt(), (cy + hub * sin(a)).roundToInt(), 0.9f)
        }
    }
    // Tape across the bottom, and the counter.
    m.line((m.w * 0.3f).toInt(), b - 3, (m.w * 0.7f).toInt(), b - 3, 0.8f)
    val counter = ((s * 2).toInt() % 1000).toString().padStart(3, '0')
    digits(m, counter, m.w / 2 - 6 * 2, t - 13, 2)
}

// --- Night road ----------------------------------------------------------------------------------------------------

private fun road(
    m: Matrix,
    s: Float,
) {
    val horizon = (m.h * 0.42f).toInt()
    // Stars and hills.
    for (i in 0 until 24) m.set((i * 53 + 7) % m.w, (i * 29 + 3) % (horizon - 4), if ((i + (s * 2).toInt()) % 5 == 0) 0.9f else 0.35f)
    for (x in 0 until m.w) {
        val hill = (m.h * 0.08f * abs(sin(x * 0.045f + s * 0.05f)) + m.h * 0.04f * abs(sin(x * 0.11f + 1.3f))).roundToInt()
        m.set(x, horizon - hill, 0.55f)
    }
    for (x in 0 until m.w) m.set(x, horizon, 0.7f)
    // Road towards the horizon, bending slowly, centre dashes running.
    val curve = sin(s * 0.25f) * m.w * 0.25f
    val depth = (m.h - horizon).toFloat()
    for (y in horizon + 1 until m.h) {
        val d = (y - horizon) / depth
        val centre = m.w / 2f + curve * (1 - d) * (1 - d)
        val half = m.w * 0.45f * d + 1
        m.set((centre - half).roundToInt(), y, 1f)
        m.set((centre + half).roundToInt(), y, 1f)
        val z = 1f / (d + 0.02f)
        if (((z * 0.5f + s * 2.2f) % 1f) < 0.45f) {
            m.set(centre.roundToInt(), y, 0.9f)
            if (d > 0.5f) m.set(centre.roundToInt() + 1, y, 0.9f)
        }
        // Posts on the side.
        if (((z * 0.25f + s * 1.1f) % 1f) < 0.08f) {
            m.set((centre - half * 1.15f).roundToInt(), y, 0.6f)
            m.set((centre + half * 1.15f).roundToInt(), y, 0.6f)
        }
    }
}
