package org.librehu.launcher.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.librehu.launcher.data.SpectrumPalette
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin

/**
 * Spectrum wallpaper: a band of glowing waves, the "Music visualization" live wallpaper of AOSP
 * (packages/wallpapers/MusicVisualization, Apache License 2.0) redrawn with OpenGL ES 2 for the launcher, as the
 * Neospectro app did. Idle, it shows the original superimposed sine waves; with [audio], the spectrum of the sound
 * being played (mirrored, bass in the middle).
 */
@Composable
fun Spectrum(
    palette: SpectrumPalette,
    audio: Boolean,
) {
    val context = LocalContext.current
    val renderer = remember { SpectrumRenderer(context.resources.displayMetrics.density) }
    val view =
        remember {
            GLSurfaceView(context).apply {
                setEGLContextClientVersion(2)
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        }
    val accent = CarColors.Accent
    val background = CarColors.Background
    SideEffect {
        val (edge, middle, center) =
            if (palette == SpectrumPalette.ACCENT) {
                Triple(lerp(accent, Color.Black, 0.55f), accent, lerp(accent, Color.White, 0.85f))
            } else {
                Triple(Color(palette.edge), Color(palette.middle), Color(palette.center))
            }
        renderer.setColors(edge, middle, center, background)
    }
    val capture = remember { SpectrumAudio(renderer, context) }
    WhileResumed(
        audio,
        onResume = {
            view.onResume()
            when {
                audio && capture.allowed(context) -> capture.start()
                audio -> capture.stop(SpectrumAudio.Kind.NO_PERMISSION)
                else -> capture.stop()
            }
        },
        onPause = {
            capture.stop()
            view.onPause()
        },
    )
    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
}

/** Draws the waves; [setLevels] feeds the spectrum (any thread). */
class SpectrumRenderer(
    private val density: Float,
) : GLSurfaceView.Renderer {
    private val heights = FloatArray(COLUMNS)
    private val offsets = FloatArray(COLUMNS)
    private val levels = FloatArray(COLUMNS)
    private val vertices: FloatBuffer =
        ByteBuffer
            .allocateDirect(COLUMNS * 2 * FLOATS_PER_VERTEX * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    private val data = FloatArray(COLUMNS * 2 * FLOATS_PER_VERTEX)

    @Volatile
    private var colors = floatArrayOf(0.01f, 0.01f, 1f, 0.48f, 0.48f, 1f, 0.95f, 0.95f, 1f, 0f, 0f, 0f)

    private var program = 0
    private var aPos = 0
    private var aT = 0
    private var uEdge = 0
    private var uMiddle = 0
    private var uCenter = 0
    private var width = 1
    private var height = 1

    // Idle animation: phases of the four sine waves of the AOSP wallpaper.
    private var pos1 = 0
    private var amp1 = 0
    private var pos2 = 0
    private var amp2 = 0
    private var pos3 = 0
    private var amp3 = 0
    private var pos4 = 0
    private var amp4 = 0

    /** 0 = idle waves, 1 = spectrum. */
    private var mix = 0f

    @Volatile
    private var lastLevels = 0L

    fun setColors(
        edge: Color,
        middle: Color,
        center: Color,
        background: Color,
    ) {
        colors =
            floatArrayOf(
                edge.red,
                edge.green,
                edge.blue,
                middle.red,
                middle.green,
                middle.blue,
                center.red,
                center.green,
                center.blue,
                background.red,
                background.green,
                background.blue,
            )
    }

    /** Spectrum heights in pixels, one per column. */
    fun setLevels(values: FloatArray) {
        synchronized(levels) { values.copyInto(levels, endIndex = minOf(values.size, COLUMNS)) }
        lastLevels = SystemClock.uptimeMillis()
    }

    override fun onSurfaceCreated(
        gl: GL10?,
        config: EGLConfig?,
    ) {
        program =
            GLES20.glCreateProgram().also {
                GLES20.glAttachShader(it, shader(GLES20.GL_VERTEX_SHADER, VERTEX))
                GLES20.glAttachShader(it, shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT))
                GLES20.glLinkProgram(it)
            }
        aPos = GLES20.glGetAttribLocation(program, "a_pos")
        aT = GLES20.glGetAttribLocation(program, "a_t")
        uEdge = GLES20.glGetUniformLocation(program, "u_edge")
        uMiddle = GLES20.glGetUniformLocation(program, "u_middle")
        uCenter = GLES20.glGetUniformLocation(program, "u_center")
    }

    override fun onSurfaceChanged(
        gl: GL10?,
        w: Int,
        h: Int,
    ) {
        width = max(1, w)
        height = max(1, h)
        GLES20.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        val c = colors
        GLES20.glClearColor(c[9], c[10], c[11], 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        update()

        // Pixels → clip space; the original was drawn on ~480 px tall screens.
        val scale = height / 480f
        val minHalf = 2f * density
        for (i in 0 until COLUMNS) {
            val x = i * 2f / (COLUMNS - 1) - 1f
            val half = max(minHalf, heights[i] * scale)
            val off = offsets[i] * scale
            val top = (off + half) * 2f / height
            val bottom = (off - half) * 2f / height
            val k = i * 2 * FLOATS_PER_VERTEX
            data[k] = x
            data[k + 1] = top
            data[k + 2] = 0f
            data[k + 3] = x
            data[k + 4] = bottom
            data[k + 5] = 1f
        }
        vertices.clear()
        vertices.put(data).position(0)

        GLES20.glUseProgram(program)
        GLES20.glUniform3f(uEdge, c[0], c[1], c[2])
        GLES20.glUniform3f(uMiddle, c[3], c[4], c[5])
        GLES20.glUniform3f(uCenter, c[6], c[7], c[8])
        vertices.position(0)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, FLOATS_PER_VERTEX * 4, vertices)
        GLES20.glEnableVertexAttribArray(aPos)
        vertices.position(2)
        GLES20.glVertexAttribPointer(aT, 1, GLES20.GL_FLOAT, false, FLOATS_PER_VERTEX * 4, vertices)
        GLES20.glEnableVertexAttribArray(aT)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, COLUMNS * 2)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aT)
    }

    /** Next frame of the idle waves and of the spectrum, cross-faded. */
    private fun update() {
        val active = SystemClock.uptimeMillis() - lastLevels < AUDIO_TIMEOUT_MS
        mix = (mix + if (active) FADE_STEP else -FADE_STEP).coerceIn(0f, 1f)
        // Superimposed moving sine waves (AOSP waveform.rs, makeIdleWave), sampled at the original 1024 columns.
        val a1 = sin(0.007f * amp1) * 120
        val a2 = sin(0.023f * amp2) * 80
        val a3 = sin(0.011f * amp3) * 40
        val a4 = sin(0.031f * amp4) * 20
        synchronized(levels) {
            for (i in 0 until COLUMNS) {
                val j = i * 1024 / COLUMNS
                val wave = abs(sin(0.013f * (pos1 + j)) * a1 + sin(0.029f * (pos2 + j)) * a2)
                val off = sin(0.005f * (pos3 + j)) * a3 + sin(0.017f * (pos4 + j)) * a4
                heights[i] = wave * (1 - mix) + levels[i] * mix
                offsets[i] = off * (1 - mix)
            }
        }
        pos1++
        amp1++
        pos2--
        amp2++
        pos3++
        amp3++
        pos4++
        amp4++
    }

    private fun shader(
        type: Int,
        code: String,
    ): Int =
        GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, code)
            GLES20.glCompileShader(it)
        }

    companion object {
        const val COLUMNS = 512
        private const val FLOATS_PER_VERTEX = 3
        private const val AUDIO_TIMEOUT_MS = 1500L
        private const val FADE_STEP = 0.04f

        private const val VERTEX =
            "attribute vec2 a_pos;\n" +
                "attribute float a_t;\n" +
                "varying float v_t;\n" +
                "void main() {\n" +
                "  v_t = a_t;\n" +
                "  gl_Position = vec4(a_pos, 0.0, 1.0);\n" +
                "}\n"

        // Across the band: centre colour in the middle, middle colour, edge colour at both borders.
        private const val FRAGMENT =
            "precision mediump float;\n" +
                "uniform vec3 u_edge;\n" +
                "uniform vec3 u_middle;\n" +
                "uniform vec3 u_center;\n" +
                "varying float v_t;\n" +
                "void main() {\n" +
                "  float d = abs(v_t * 2.0 - 1.0);\n" +
                "  vec3 c = d < 0.5 ? mix(u_center, u_middle, d * 2.0) : mix(u_middle, u_edge, (d - 0.5) * 2.0);\n" +
                "  gl_FragColor = vec4(c, 1.0);\n" +
                "}\n"
    }
}

/**
 * Spectrum of the sound being played, from the global output mix ([Visualizer] on session 0; needs RECORD_AUDIO).
 * Mirrored: bass in the middle of the screen, treble on both sides.
 */
class SpectrumAudio(
    private val renderer: SpectrumRenderer,
    context: Context,
) {
    enum class Kind { OFF, NO_PERMISSION, ERROR, LISTENING, SILENT, SOUND }

    /** What the visualizer gets, for the settings ("follow the music" does nothing otherwise). */
    data class Status(
        val kind: Kind = Kind.OFF,
        val detail: String = "",
    )

    private val audioManager = context.applicationContext.getSystemService(android.media.AudioManager::class.java)
    private var listeningSince = 0L
    private var lastSoundAt = 0L

    private var visualizer: Visualizer? = null
    private val smooth = FloatArray(SpectrumRenderer.COLUMNS / 2)
    private val out = FloatArray(SpectrumRenderer.COLUMNS)

    fun allowed(context: Context) = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (visualizer != null) return
        visualizer =
            try {
                Visualizer(0).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                v: Visualizer?,
                                waveform: ByteArray?,
                                samplingRate: Int,
                            ) = Unit

                            override fun onFftDataCapture(
                                v: Visualizer?,
                                fft: ByteArray?,
                                samplingRate: Int,
                            ) {
                                if (fft != null) onFft(fft)
                            }
                        },
                        Visualizer.getMaxCaptureRate() / 2,
                        false,
                        true,
                    )
                    val code = setEnabled(true)
                    if (code != Visualizer.SUCCESS) throw IllegalStateException("setEnabled: $code")
                }
            } catch (e: Exception) {
                Log.w("LibreHU-Launcher", "Visualizer: ${e.message}")
                _status.value = Status(Kind.ERROR, e.message ?: e.javaClass.simpleName)
                null
            }
        if (visualizer != null) {
            listeningSince = android.os.SystemClock.uptimeMillis()
            lastSoundAt = 0
            _status.value = Status(Kind.LISTENING)
        }
    }

    fun stop(kind: Kind = Kind.OFF) {
        _status.value = Status(kind)
        visualizer?.let {
            runCatching {
                it.enabled = false
                it.release()
            }
        }
        visualizer = null
    }

    private fun onFft(fft: ByteArray) {
        // Bins 1..bins-1 (pairs re/im); the upper half of the spectrum carries little music: keep the lower half.
        val bins = fft.size / 4
        if (bins < 4) return
        val half = smooth.size
        var any = false
        for (j in 0 until half) {
            // Quadratic spread: more columns for the low frequencies.
            val f = j.toFloat() / (half - 1)
            val bin = (1 + f * f * (bins - 2)).toInt().coerceIn(1, bins - 1)
            val mag = hypot(fft[bin * 2].toFloat(), fft[bin * 2 + 1].toFloat())
            val db = if (mag > 0f) 20f * log10(mag) else 0f
            // 0..~42 dB from 8-bit FFT values; a little treble boost.
            val target = (db / 42f).coerceIn(0f, 1f) * MAX_HEIGHT * (0.8f + 0.6f * f)
            val old = smooth[j]
            smooth[j] = if (target > old) old + (target - old) * ATTACK else old + (target - old) * DECAY
            if (mag > 0f) any = true
        }
        val now = android.os.SystemClock.uptimeMillis()
        if (any) {
            lastSoundAt = now
            if (_status.value.kind != Kind.SOUND) _status.value = Status(Kind.SOUND)
        } else if (now - maxOf(lastSoundAt, listeningSince) > SILENT_AFTER_MS && audioManager?.isMusicActive == true) {
            // Music plays but the output mix gives zeros: the visualizer does not see this output.
            if (_status.value.kind != Kind.SILENT) _status.value = Status(Kind.SILENT)
        }
        if (!any) return
        for (j in 0 until half) {
            out[half - 1 - j] = smooth[j]
            out[half + j] = smooth[j]
        }
        renderer.setLevels(out)
    }

    companion object {
        private val _status = kotlinx.coroutines.flow.MutableStateFlow(Status())
        val status: kotlinx.coroutines.flow.StateFlow<Status> = _status
        private const val SILENT_AFTER_MS = 5000L
        private const val MAX_HEIGHT = 170f
        private const val ATTACK = 0.5f
        private const val DECAY = 0.12f
    }
}
