package org.librehu.launcher.ui

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.librehu.launcher.data.WallpaperKind
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.random.Random

/** Built-in animations the GPU draws (Settings → wallpaper → GPU acceleration). */
internal fun gpuDrawn(kind: WallpaperKind): Boolean =
    kind == WallpaperKind.AURORA || kind == WallpaperKind.STARS || kind == WallpaperKind.WAVES || kind == WallpaperKind.VAPORWAVE || kind.retroScene

/**
 * The built-in animated wallpapers drawn by the GPU (OpenGL ES 2 fragment shaders), when GPU acceleration is on in the
 * settings: the same pictures as the usual drawing (Wallpaper.kt, RetroScenes.kt, Vaporwave.kt), computed for each
 * pixel by the graphics chip instead of being drawn shape by shape. The dot matrix scenes still compute their dots
 * (256 across) on the processor, on the GL thread, and hand them to the GPU as a small texture. Frames are asked at
 * [GlSceneRenderer.fps] (not at the screen's 60 Hz) and stop with the launcher.
 */
@Composable
internal fun GlWallpaper(kind: WallpaperKind) {
    val context = LocalContext.current
    key(kind) {
        val renderer = remember { GlSceneRenderer(kind, context.resources.displayMetrics.density) }
        val view =
            remember {
                GLSurfaceView(context).apply {
                    setEGLContextClientVersion(2)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                }
            }
        val accent = CarColors.Accent
        val background = CarColors.Background
        val dark = CarColors.palette.dark
        SideEffect { renderer.setColors(accent, background, dark) }
        val ticker = remember { FrameTicker(view, renderer.fps) }
        WhileResumed(
            kind,
            onResume = {
                view.onResume()
                ticker.start()
            },
            onPause = {
                ticker.stop()
                view.onPause()
            },
        )
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
    }
}

/** Asks [view] for a frame [fps] times a second while started. */
private class FrameTicker(
    private val view: GLSurfaceView,
    private val fps: Int,
) {
    private val main = Handler(Looper.getMainLooper())
    private var running = false
    private val tick =
        object : Runnable {
            override fun run() {
                if (!running) return
                view.requestRender()
                main.postDelayed(this, 1000L / fps)
            }
        }

    fun start() {
        if (running) return
        running = true
        main.post(tick)
    }

    fun stop() {
        running = false
        main.removeCallbacks(tick)
    }
}

/** One full-screen rectangle, one fragment shader per wallpaper. */
internal class GlSceneRenderer(
    private val kind: WallpaperKind,
    private val density: Float,
) : GLSurfaceView.Renderer {
    /** The dot matrix moves at the pace of a head unit's display; the others more smoothly. */
    val fps = if (kind.retroScene) 15 else 30

    @Volatile
    private var colors = floatArrayOf(0.54f, 0.71f, 0.97f, 0.07f, 0.07f, 0.08f, 1f)

    private var program = 0
    private var width = 1
    private var height = 1
    private val start = SystemClock.uptimeMillis()
    private val quad: FloatBuffer =
        ByteBuffer
            .allocateDirect(8 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
                position(0)
            }

    // Dot matrix: the scene's dots, as a texture of one byte per dot.
    private var texture = 0
    private var matrix: Matrix? = null
    private var dots: ByteBuffer? = null
    private val state = SceneState()

    // Vaporwave: the two mountain ridges (same as the usual drawing), and the palm trees' triangles of each frame.
    private val far = ridge(Random(7), 15, 0.10f, 0.24f)
    private val near = ridge(Random(11), 9, 0.05f, 0.15f)
    private var palmProgram = 0
    private val palms = PalmMesh()

    fun setColors(
        accent: Color,
        background: Color,
        dark: Boolean,
    ) {
        colors = floatArrayOf(accent.red, accent.green, accent.blue, background.red, background.green, background.blue, if (dark) 1f else 0f)
    }

    override fun onSurfaceCreated(
        gl: GL10?,
        config: EGLConfig?,
    ) {
        program = link(VERTEX, header() + fragment())
        if (kind == WallpaperKind.VAPORWAVE) palmProgram = link(PALM_VERTEX, PALM_FRAGMENT)
        if (kind.retroScene) {
            val t = IntArray(1)
            GLES20.glGenTextures(1, t, 0)
            texture = t[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }
    }

    override fun onSurfaceChanged(
        gl: GL10?,
        w: Int,
        h: Int,
    ) {
        width = w.coerceAtLeast(1)
        height = h.coerceAtLeast(1)
        GLES20.glViewport(0, 0, width, height)
        if (kind.retroScene) {
            val rows = (height / (width.toFloat() / COLS)).toInt().coerceAtLeast(32)
            matrix = Matrix(COLS, rows)
            dots = ByteBuffer.allocateDirect(COLS * rows)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        if (program == 0) return
        val s = (SystemClock.uptimeMillis() - start) / 1000f
        val c = colors
        GLES20.glUseProgram(program)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uRes"), width.toFloat(), height.toFloat())
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uTime"), s)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uDensity"), density)
        GLES20.glUniform3f(GLES20.glGetUniformLocation(program, "uAccent"), c[0], c[1], c[2])
        GLES20.glUniform3f(GLES20.glGetUniformLocation(program, "uBack"), c[3], c[4], c[5])
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uDark"), c[6])
        when {
            kind.retroScene -> uploadDots(s)
            kind == WallpaperKind.VAPORWAVE -> {
                GLES20.glUniform1fv(GLES20.glGetUniformLocation(program, "uFar"), far.size, far.map { it.second }.toFloatArray(), 0)
                GLES20.glUniform1fv(GLES20.glGetUniformLocation(program, "uNear"), near.size, near.map { it.second }.toFloatArray(), 0)
            }
        }
        val aPos = GLES20.glGetAttribLocation(program, "aPos")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        if (kind == WallpaperKind.VAPORWAVE && palmProgram != 0) drawPalms(s)
    }

    /** The two palm trees swaying, as in Vaporwave.kt: their shapes in triangles, one flat colour. */
    private fun drawPalms(s: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        palms.clear()
        palms.palm(w * 0.08f, h * 0.98f, h * 0.62f, -1f, s, density)
        palms.palm(w * 0.93f, h * 1.02f, h * 0.55f, 1f, s + 1.3f, density)
        GLES20.glUseProgram(palmProgram)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(palmProgram, "uRes"), w, h)
        val aPos = GLES20.glGetAttribLocation(palmProgram, "aPos")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, palms.buffer())
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, palms.vertices)
        GLES20.glDisableVertexAttribArray(aPos)
    }

    /** The scene's dots this frame, one byte each (0 unlit, else its brightness step), into the texture. */
    private fun uploadDots(s: Float) {
        val m = matrix ?: return
        val b = dots ?: return
        m.v.fill(0f)
        retroFrame(kind, m, s, state)
        b.position(0)
        for (a in m.v) b.put(if (a < 0.04f) 0 else (((a * LEVELS).toInt().coerceIn(0, LEVELS - 1) + 1) * 255 / LEVELS).toByte())
        b.position(0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, m.w, m.h, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, b)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uDots"), 0)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uGrid"), m.w.toFloat(), m.h.toFloat())
    }

    private fun header() =
        """
        precision mediump float;
        uniform vec2 uRes;
        uniform float uTime;
        uniform float uDensity;
        uniform vec3 uAccent;
        uniform vec3 uBack;
        uniform float uDark;
        // Pixel position, origin at the top left as on the screen.
        vec2 px() { return vec2(gl_FragCoord.x, uRes.y - gl_FragCoord.y); }
        float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
        """.trimIndent() + "\n"

    private fun fragment(): String =
        when {
            kind.retroScene -> RETRO
            kind == WallpaperKind.AURORA -> AURORA
            kind == WallpaperKind.STARS -> STARS
            kind == WallpaperKind.WAVES -> WAVES
            else -> VAPORWAVE.replace("FAR_N", far.size.toString()).replace("NEAR_N", near.size.toString())
        }

    private fun link(
        vs: String,
        fs: String,
    ): Int {
        fun shader(
            type: Int,
            src: String,
        ): Int {
            val sh = GLES20.glCreateShader(type)
            GLES20.glShaderSource(sh, src)
            GLES20.glCompileShader(sh)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(sh, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) Log.w(TAG, "shader: " + GLES20.glGetShaderInfoLog(sh))
            return sh
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            Log.w(TAG, "program: " + GLES20.glGetProgramInfoLog(p))
            return 0
        }
        return p
    }

    private companion object {
        const val TAG = "LibreHU-GlScenes"

        /** Palm trees: positions in pixels from the top left. */
        const val PALM_VERTEX = """
            attribute vec2 aPos;
            uniform vec2 uRes;
            void main() { gl_Position = vec4(aPos.x / uRes.x * 2.0 - 1.0, 1.0 - aPos.y / uRes.y * 2.0, 0.0, 1.0); }
        """

        const val PALM_FRAGMENT = """
            precision mediump float;
            void main() { gl_FragColor = vec4(0.027, 0.004, 0.059, 1.0); }
        """

        const val VERTEX = """
            attribute vec2 aPos;
            void main() { gl_Position = vec4(aPos, 0.0, 1.0); }
        """

        /** Three coloured glows drifting (Wallpaper.kt's Aurora): 40 s for a full turn. */
        const val AURORA = """
            void main() {
                vec2 p = px();
                float a = fract(uTime / 40.0) * 6.2831853;
                float r = max(uRes.x, uRes.y) * 0.6;
                float alpha = uDark > 0.5 ? 0.55 : 0.40;
                vec3 second = mix(uAccent, vec3(0.494, 0.341, 0.761), 0.6);
                vec3 third = mix(uAccent, vec3(0.149, 0.651, 0.604), 0.6);
                vec3 col = uBack;
                vec2 c1 = uRes * vec2(0.3 + 0.2 * cos(a), 0.4 + 0.3 * sin(2.0 * a));
                vec2 c2 = uRes * vec2(0.7 + 0.2 * sin(a + 1.0), 0.6 + 0.3 * cos(a * 2.0 + 2.0));
                vec2 c3 = uRes * vec2(0.5 + 0.35 * cos(3.0 * a + 4.0), 0.3 + 0.2 * sin(a + 3.0));
                col = mix(col, uAccent, alpha * clamp(1.0 - distance(p, c1) / r, 0.0, 1.0));
                col = mix(col, second, alpha * clamp(1.0 - distance(p, c2) / r, 0.0, 1.0));
                col = mix(col, third, alpha * clamp(1.0 - distance(p, c3) / r, 0.0, 1.0));
                gl_FragColor = vec4(col, 1.0);
            }
        """

        /** Twinkling stars drifting sideways in three layers (Wallpaper.kt's Stars), over a soft gradient. */
        const val STARS = """
            void main() {
                vec2 p = px();
                float g = p.y / uRes.y;
                vec3 col = mix(uBack, mix(uBack, uAccent, uDark > 0.5 ? 0.18 : 0.12), g);
                vec3 star = uDark > 0.5 ? vec3(1.0) : uAccent;
                float t = fract(uTime / 120.0);
                for (int layer = 1; layer <= 3; layer++) {
                    float cell = 68.0 * uDensity;
                    vec2 q = vec2(p.x - t * float(layer) * uRes.x, p.y) + float(layer) * 37.0;
                    vec2 id = floor(q / cell);
                    vec2 f = fract(q / cell);
                    float h = hash(id + float(layer) * 13.0);
                    if (h < 0.55) continue;
                    vec2 pos = vec2(hash(id * 1.7 + 3.1), hash(id * 2.3 + 7.9));
                    float radius = (0.8 + hash(id + 5.0) * 2.2) * uDensity / 2.0;
                    float d = length((f - pos) * cell);
                    float tw = 0.5 + 0.5 * sin((t * 60.0 * float(layer) + hash(id + 9.0)) * 6.2831853);
                    col = mix(col, star, (0.25 + 0.65 * tw) * clamp(radius + 0.5 - d, 0.0, 1.0));
                }
                gl_FragColor = vec4(col, 1.0);
            }
        """

        /** Four soft waves along the bottom (Wallpaper.kt's Waves), 16 s a cycle. */
        const val WAVES = """
            void main() {
                vec2 p = px();
                float a = fract(uTime / 16.0) * 6.2831853;
                vec3 col = uBack;
                for (int i = 0; i < 4; i++) {
                    float fi = float(i);
                    float y0 = uRes.y * (0.55 + fi * 0.1);
                    float amp = uRes.y * (0.05 - fi * 0.008);
                    float k = (1.5 + fi * 0.4) * 6.2831853 / uRes.x;
                    float ph = a * (mod(fi, 2.0) < 0.5 ? 1.0 : -1.0) + fi;
                    float edge = y0 + amp * sin(k * p.x + ph);
                    col = mix(col, uAccent, (0.12 + fi * 0.05) * clamp(p.y - edge + 0.5, 0.0, 1.0));
                }
                gl_FragColor = vec4(col, 1.0);
            }
        """

        /** The dot matrix (RetroScenes.kt): every dot faintly lit, the scene's dots in their brightness step. */
        const val RETRO = """
            uniform sampler2D uDots;
            uniform vec2 uGrid;
            void main() {
                vec2 p = px();
                float cell = uRes.x / uGrid.x;
                vec2 id = floor(p / cell);
                vec2 f = fract(p / cell);
                vec3 col = uBack;
                if (id.y < uGrid.y && f.x < 0.82 && f.y < 0.82) {
                    float v = texture2D(uDots, (id + 0.5) / uGrid).r;
                    float a = v > 0.0 ? 0.22 + 0.78 * v : 0.06;
                    col = mix(uBack, uAccent, a);
                }
                gl_FragColor = vec4(col, 1.0);
            }
        """

        /** The vaporwave sunset (Vaporwave.kt) without its palm trees (drawn over it, still). */
        const val VAPORWAVE = """
            uniform float uFar[FAR_N];
            uniform float uNear[NEAR_N];
            const vec3 SKY_TOP = vec3(0.043, 0.008, 0.129);
            const vec3 SKY_MID = vec3(0.227, 0.043, 0.420);
            const vec3 SKY_LOW = vec3(0.878, 0.212, 0.478);
            const vec3 SUN_TOP = vec3(1.0, 0.894, 0.369);
            const vec3 SUN_MID = vec3(1.0, 0.541, 0.239);
            const vec3 SUN_LOW = vec3(1.0, 0.180, 0.533);
            const vec3 GLOW = vec3(1.0, 0.310, 0.639);
            const vec3 M_FAR = vec3(0.165, 0.039, 0.310);
            const vec3 M_NEAR = vec3(0.102, 0.024, 0.212);
            const vec3 CYAN = vec3(0.176, 0.886, 0.902);
            const vec3 FLOOR_TOP = vec3(0.106, 0.016, 0.220);
            const vec3 FLOOR_LOW = vec3(0.035, 0.0, 0.078);
            const vec3 GRID = vec3(1.0, 0.220, 0.831);
            vec3 sky(float y, float horizon) {
                float g = clamp(y / horizon, 0.0, 1.0);
                return g < 0.45 ? mix(SKY_TOP, SKY_MID, g / 0.45) : mix(SKY_MID, SKY_LOW, (g - 0.45) / 0.55);
            }
            float ridgeFar(float x) {
                float fx = clamp(x, 0.0, 1.0) * float(FAR_N - 1);
                float r = 0.0;
                for (int i = 0; i < FAR_N - 1; i++) { if (fx >= float(i) && fx <= float(i + 1)) r = mix(uFar[i], uFar[i + 1], fx - float(i)); }
                return r;
            }
            float ridgeNear(float x) {
                float fx = clamp(x, 0.0, 1.0) * float(NEAR_N - 1);
                float r = 0.0;
                for (int i = 0; i < NEAR_N - 1; i++) { if (fx >= float(i) && fx <= float(i + 1)) r = mix(uNear[i], uNear[i + 1], fx - float(i)); }
                return r;
            }
            float seg(vec2 p, vec2 a, vec2 b) {
                vec2 pa = p - a;
                vec2 ba = b - a;
                return length(pa - ba * clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0));
            }
            void main() {
                vec2 p = px();
                float w = uRes.x;
                float h = uRes.y;
                float horizon = h * 0.62;
                float line = 1.6 * uDensity;
                vec3 col;
                if (p.y < horizon) {
                    col = sky(p.y, horizon);
                    // Stars in the upper sky.
                    vec2 cell = vec2(w / 18.0, horizon * 0.55 / 5.0);
                    vec2 id = floor(p / cell);
                    if (p.y < horizon * 0.55 && hash(id) > 0.0) {
                        vec2 pos = vec2(hash(id + 1.3), hash(id + 7.1)) * cell;
                        float rad = (0.6 + hash(id + 3.0) * 1.6) * uDensity / 2.0;
                        float tw = 0.45 + 0.55 * sin(uTime * 2.2 + hash(id + 5.0) * 6.28);
                        col = mix(col, vec3(1.0), (0.25 + 0.6 * tw) * clamp(rad + 0.5 - length(p - id * cell - pos), 0.0, 1.0));
                    }
                    // Sun: glow, disc in three colours, bands sliding down over its lower half.
                    float r = h * 0.24;
                    vec2 sun = vec2(w / 2.0, horizon - r * 0.78);
                    float d = distance(p, sun);
                    col = mix(col, GLOW, 0.55 * clamp(1.0 - d / (r * 2.1), 0.0, 1.0) * clamp(1.0 - d / (r * 2.1), 0.0, 1.0));
                    if (d < r) {
                        float g = (p.y - (sun.y - r)) / (2.0 * r);
                        vec3 disc = g < 0.5 ? mix(SUN_TOP, SUN_MID, g * 2.0) : mix(SUN_MID, SUN_LOW, (g - 0.5) * 2.0);
                        bool band = false;
                        float slide = fract(uTime * 0.25);
                        for (int i = 0; i < 8; i++) {
                            float k = (float(i) + slide) / 8.0;
                            float y = sun.y - r * 0.1 + r * 1.1 * k;
                            float gap = r * (0.02 + 0.11 * k);
                            if (p.y >= y && p.y < y + gap) band = true;
                        }
                        if (!band) col = mix(col, disc, clamp(r - d, 0.0, 1.0));
                    }
                    // Mountains: far ridge then near ridge, filled dark, outlined in cyan.
                    float x = p.x / w;
                    float yf = horizon - ridgeFar(x) * h;
                    if (p.y > yf) {
                        col = M_FAR;
                        // Lines down the faces (wireframe), from each peak towards the middle.
                        for (int i = 0; i < FAR_N; i++) {
                            float px0 = float(i) / float(FAR_N - 1) * w;
                            float hy = uFar[i] * h;
                            vec2 top = vec2(px0, horizon - hy);
                            vec2 foot = vec2(px0 + (px0 < w / 2.0 ? 1.0 : -1.0) * hy * 0.5, horizon);
                            col = mix(col, CYAN, 0.55 * 0.35 * clamp(uDensity - seg(p, top, foot), 0.0, 1.0));
                        }
                    }
                    col = mix(col, CYAN, 0.55 * clamp(line - abs(p.y - yf), 0.0, 1.0));
                    float yn = horizon - ridgeNear(x) * h;
                    if (p.y > yn) {
                        col = M_NEAR;
                        for (int i = 0; i < NEAR_N; i++) {
                            float px0 = float(i) / float(NEAR_N - 1) * w;
                            float hy = uNear[i] * h;
                            vec2 top = vec2(px0, horizon - hy);
                            vec2 foot = vec2(px0 + (px0 < w / 2.0 ? 1.0 : -1.0) * hy * 0.5, horizon);
                            col = mix(col, CYAN, 0.35 * clamp(uDensity - seg(p, top, foot), 0.0, 1.0));
                        }
                    }
                    col = mix(col, CYAN, clamp(line - abs(p.y - yn), 0.0, 1.0));
                } else {
                    // Floor: dark gradient, the sun's light, neon lines running towards the viewer.
                    float depth = h - horizon;
                    float t = (p.y - horizon) / depth;
                    col = mix(FLOOR_TOP, FLOOR_LOW, t);
                    col = mix(col, GLOW, 0.28 * clamp(1.0 - distance(p, vec2(w / 2.0, horizon)) / (w * 0.35), 0.0, 1.0));
                    float dy = max(p.y - horizon, 0.5);
                    float z0 = (depth * 0.45 / dy - 0.45) / 0.55;
                    float z = z0 + fract(uTime * 0.6);
                    float dzdy = depth * 0.45 / (0.55 * dy * dy);
                    float fade = clamp(t, 0.0, 1.0);
                    float distLine = min(fract(z), 1.0 - fract(z)) / dzdy;
                    // 18 lines at most, as drawn by the usual way, faded where they come closer than a few pixels.
                    float tight = clamp((1.0 / dzdy - 2.0) / 6.0, 0.0, 1.0);
                    if (z0 < 18.0) col = mix(col, GRID, tight * (0.1 + 0.9 * fade) * clamp(line * (0.6 + fade) / 2.0 + 0.5 - distLine, 0.0, 1.0));
                    float spread = (w / 14.0) * (0.08 + 1.52 * t);
                    float lane = (p.x - w / 2.0) / spread;
                    float distLane = abs(lane - floor(lane + 0.5)) * spread;
                    col = mix(col, GRID, 0.85 * clamp(line / 2.0 + 0.5 - distLane, 0.0, 1.0));
                    col = mix(col, GRID, clamp(line * 0.75 + 0.5 - (p.y - horizon), 0.0, 1.0));
                }
                gl_FragColor = vec4(col, 1.0);
            }
        """
    }
}

/** Triangles of the vaporwave palm trees (the same shapes as [palm] in Vaporwave.kt), rebuilt each frame. */
private class PalmMesh {
    private var data = FloatArray(4096)
    private var n = 0
    private var direct: FloatBuffer = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    val vertices get() = n / 2

    fun clear() {
        n = 0
    }

    fun buffer(): FloatBuffer {
        if (direct.capacity() < n) direct = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        direct.position(0)
        direct.put(data, 0, n)
        direct.position(0)
        return direct
    }

    private fun v(
        x: Float,
        y: Float,
    ) {
        if (n + 2 > data.size) data = data.copyOf(data.size * 2)
        data[n++] = x
        data[n++] = y
    }

    private fun tri(
        a: FloatArray,
        b: FloatArray,
        c: FloatArray,
    ) {
        v(a[0], a[1])
        v(b[0], b[1])
        v(c[0], c[1])
    }

    /** Quadratic curve from [p0] to [p2] through the pull of [c], [steps] + 1 points. */
    private fun curve(
        p0: FloatArray,
        c: FloatArray,
        p2: FloatArray,
        steps: Int,
    ): List<FloatArray> =
        (0..steps).map { i ->
            val t = i / steps.toFloat()
            val u = 1 - t
            floatArrayOf(u * u * p0[0] + 2 * u * t * c[0] + t * t * p2[0], u * u * p0[1] + 2 * u * t * c[1] + t * t * p2[1])
        }

    /** The band between two curves of as many points, point i against point i. */
    private fun band(
        a: List<FloatArray>,
        b: List<FloatArray>,
    ) {
        for (i in 0 until a.size - 1) {
            tri(a[i], a[i + 1], b[i])
            tri(b[i], a[i + 1], b[i + 1])
        }
    }

    private fun line(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        width: Float,
    ) {
        val dx = x1 - x0
        val dy = y1 - y0
        val l = kotlin.math.hypot(dx, dy).coerceAtLeast(0.001f)
        val nx = -dy / l * width / 2
        val ny = dx / l * width / 2
        val a = floatArrayOf(x0 + nx, y0 + ny)
        val b = floatArrayOf(x0 - nx, y0 - ny)
        val c = floatArrayOf(x1 + nx, y1 + ny)
        val d = floatArrayOf(x1 - nx, y1 - ny)
        tri(a, b, c)
        tri(c, b, d)
    }

    fun palm(
        footX: Float,
        footY: Float,
        height: Float,
        lean: Float,
        s: Float,
        density: Float,
    ) {
        val topX = footX + lean * height * 0.18f
        val topY = footY - height
        val left = curve(floatArrayOf(footX - height * 0.035f, footY), floatArrayOf(footX + lean * height * 0.02f, footY - height * 0.55f), floatArrayOf(topX - height * 0.012f, topY), 12)
        val right = curve(floatArrayOf(footX + height * 0.035f, footY), floatArrayOf(footX + lean * height * 0.06f, footY - height * 0.55f), floatArrayOf(topX + height * 0.012f, topY), 12)
        band(left, right)
        val sway = kotlin.math.sin(s * 0.9f) * 0.05f
        val leaflet = kotlin.math.max(1f, 2.2f * density)
        for (i in 0 until 7) {
            val a = (-Math.PI / 2 + (i - 3) * 0.48 + sway).toFloat()
            val len = height * (0.46f - kotlin.math.abs(i - 3) * 0.03f)
            val endX = topX + kotlin.math.cos(a) * len
            val endY = topY + kotlin.math.sin(a) * len * 0.45f + len * 0.42f
            val midX = topX + kotlin.math.cos(a) * len * 0.55f
            val midY = topY + kotlin.math.sin(a) * len * 0.55f - len * 0.08f
            val upper = curve(floatArrayOf(topX, topY), floatArrayOf(midX, midY - len * 0.06f), floatArrayOf(endX, endY), 10)
            val lower = curve(floatArrayOf(topX, topY + len * 0.03f), floatArrayOf(midX, midY + len * 0.06f), floatArrayOf(endX, endY), 10)
            band(upper, lower)
            for (j in 1..6) {
                val t = j / 7f
                val px = topX + (endX - topX) * t
                val py = topY + (endY - topY) * t - kotlin.math.sin(t * Math.PI.toFloat()) * len * 0.12f
                line(px, py, px + kotlin.math.cos(a + 1.2f) * len * 0.1f, py + len * 0.1f, leaflet)
                line(px, py, px + kotlin.math.cos(a - 1.2f) * len * 0.1f, py + len * 0.1f, leaflet)
            }
        }
    }
}
