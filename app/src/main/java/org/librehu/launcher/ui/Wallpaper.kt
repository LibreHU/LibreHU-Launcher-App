package org.librehu.launcher.ui

import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.librehu.launcher.data.ThemeStore
import org.librehu.launcher.data.WallpaperKind
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Launcher background. Pictures, GIF / WebP and videos get a scrim so that the cards stay readable; the built-in
 * animations are drawn dark enough not to need one. Everything stops while the launcher is in the background
 * (Compose stops its frame clock when the activity is stopped, the GIF and the video are paused explicitly).
 */
@Composable
fun Wallpaper(theme: ThemeStore) {
    val s by theme.settings.collectAsStateWithLifecycle()
    val revision by theme.revision.collectAsStateWithLifecycle()
    when (s.wallpaper) {
        WallpaperKind.NONE -> {
            Box(Modifier.fillMaxSize().background(CarColors.Background))
        }

        // The window shows the system (live) wallpaper behind the launcher: only darken it.
        WallpaperKind.SYSTEM -> {
            Scrim()
        }

        WallpaperKind.IMAGE -> {
            val bmp by theme.wallpaper.collectAsStateWithLifecycle()
            Box(Modifier.fillMaxSize().background(CarColors.Background))
            bmp?.let {
                val image = remember(it) { it.asImageBitmap() }
                Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Scrim()
        }

        WallpaperKind.ONLINE -> {
            val bmp by theme.wallpaper.collectAsStateWithLifecycle()
            val caption by theme.caption.collectAsStateWithLifecycle()
            Box(Modifier.fillMaxSize().background(CarColors.Background))
            bmp?.let {
                val image = remember(it) { it.asImageBitmap() }
                Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Scrim()
            if (s.onlineCaption && caption.isNotEmpty()) {
                Box(Modifier.fillMaxSize()) {
                    androidx.compose.material3.Text(
                        caption,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 12.dp, bottom = 6.dp)
                                .fillMaxWidth(0.6f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }
        }

        WallpaperKind.ANIMATED -> {
            Box(Modifier.fillMaxSize().background(CarColors.Background))
            AnimatedPicture(theme.animatedFile, revision)
            Scrim()
        }

        WallpaperKind.VIDEO -> {
            Box(Modifier.fillMaxSize().background(CarColors.Background))
            VideoLoop(theme.videoFile, revision)
            Scrim()
        }

        WallpaperKind.AURORA -> {
            Aurora()
        }

        WallpaperKind.STARS -> {
            Stars()
        }

        WallpaperKind.WAVES -> {
            Waves()
        }

        WallpaperKind.SPECTRUM -> {
            Spectrum(s.spectrumPalette, s.spectrumAudio)
        }

        WallpaperKind.RETRO_DOLPHIN, WallpaperKind.RETRO_ANALYZER, WallpaperKind.RETRO_CASSETTE, WallpaperKind.RETRO_ROAD, WallpaperKind.RETRO_RALLY, WallpaperKind.RETRO_DEMO -> {
            RetroScene(s.wallpaper)
        }
    }
}

@Composable
private fun Scrim() = Box(Modifier.fillMaxSize().background(CarColors.Background.copy(alpha = 0.35f)))

/** Runs [onResume] / [onPause] with the activity (both also run once on entering / leaving the composition). */
@Composable
internal fun WhileResumed(
    key: Any?,
    onResume: () -> Unit,
    onPause: () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, key) {
        val observer =
            LifecycleEventObserver { _, e ->
                if (e == Lifecycle.Event.ON_RESUME) onResume()
                if (e == Lifecycle.Event.ON_PAUSE) onPause()
            }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            onPause()
        }
    }
}

// --- GIF / animated WebP -----------------------------------------------------------------------------------------

@Composable
private fun AnimatedPicture(
    file: File,
    revision: Int,
) {
    val context = LocalContext.current
    val drawable by produceState<Drawable?>(null, file, revision) {
        value =
            withContext(Dispatchers.IO) {
                val dm = context.resources.displayMetrics
                val screen = maxOf(dm.widthPixels, dm.heightPixels)
                try {
                    ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { d, info, _ ->
                        // Big GIFs: decode at the screen size, not at the file size (memory).
                        val big = maxOf(info.size.width, info.size.height)
                        if (big > screen) {
                            d.setTargetSize(info.size.width * screen / big, info.size.height * screen / big)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("LibreHU-Launcher", "animated wallpaper: ${e.message}")
                    null
                }
            }
    }
    val anim = drawable as? AnimatedImageDrawable
    WhileResumed(anim, onResume = { anim?.start() }, onPause = { anim?.stop() })
    AndroidView(
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
        update = { it.setImageDrawable(drawable) },
        modifier = Modifier.fillMaxSize(),
    )
}

// --- Video -------------------------------------------------------------------------------------------------------

/**
 * Muted looping video on a TextureView, scaled to fill the screen (centre crop). A plain MediaPlayer does not take
 * the audio focus, so the radio / music keeps playing.
 */
@Composable
private fun VideoLoop(
    file: File,
    revision: Int,
) {
    val player = remember(file, revision) { VideoPlayer(file) }
    WhileResumed(player, onResume = player::resume, onPause = player::pause)
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { TextureView(it) },
        update = { player.attach(it) },
        modifier = Modifier.fillMaxSize(),
    )
}

private class VideoPlayer(
    private val file: File,
) : TextureView.SurfaceTextureListener {
    private var mp: MediaPlayer? = null
    private var view: TextureView? = null
    private var surface: Surface? = null
    private var prepared = false
    private var resumed = false

    fun attach(v: TextureView) {
        if (view === v) return
        view = v
        v.surfaceTextureListener = this
        v.surfaceTexture?.let { open(it) }
    }

    fun resume() {
        resumed = true
        if (prepared) mp?.start()
    }

    fun pause() {
        resumed = false
        if (prepared) mp?.pause()
    }

    fun release() {
        prepared = false
        mp?.release()
        mp = null
        surface?.release()
        surface = null
    }

    private fun open(texture: SurfaceTexture) {
        release()
        val s = Surface(texture).also { surface = it }
        mp =
            MediaPlayer().apply {
                try {
                    setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_UNKNOWN)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build(),
                    )
                    setDataSource(file.path)
                    setSurface(s)
                    isLooping = true
                    setVolume(0f, 0f)
                    setOnVideoSizeChangedListener { _, w, h -> crop(w, h) }
                    setOnPreparedListener {
                        prepared = true
                        crop(it.videoWidth, it.videoHeight)
                        if (resumed) it.start()
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.w("LibreHU-Launcher", "video wallpaper error $what/$extra")
                        true
                    }
                    prepareAsync()
                } catch (e: Exception) {
                    Log.w("LibreHU-Launcher", "video wallpaper: ${e.message}")
                }
            }
    }

    /** TextureView stretches the video to its size: scale it back to the video ratio, cropping the overflow. */
    private fun crop(
        w: Int,
        h: Int,
    ) {
        val v = view ?: return
        if (w <= 0 || h <= 0 || v.width <= 0 || v.height <= 0) return
        val vw = v.width.toFloat()
        val vh = v.height.toFloat()
        val scale = maxOf(vw / w, vh / h)
        v.setTransform(Matrix().apply { setScale(w * scale / vw, h * scale / vh, vw / 2, vh / 2) })
    }

    override fun onSurfaceTextureAvailable(
        st: SurfaceTexture,
        width: Int,
        height: Int,
    ) = open(st)

    override fun onSurfaceTextureSizeChanged(
        st: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        mp?.takeIf { prepared }?.let { crop(it.videoWidth, it.videoHeight) }
    }

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        release()
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
}

// --- Built-in animations -----------------------------------------------------------------------------------------

/**
 * 0 → 1 over [ms], forever, at [fps] frames per second: these wallpapers move slowly, and redrawing full-screen
 * gradients at 60 fps kept the head unit's GPU busy behind every screen. Read the value inside the draw lambda, so
 * that only the drawing runs again (no recomposition); it stops with the frames when the launcher is not visible.
 */
@Composable
private fun loop(
    ms: Int,
    fps: Int = WALLPAPER_FPS,
): State<Float> {
    val state = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(ms, fps) {
        val start = SystemClock.uptimeMillis()
        while (true) {
            withFrameMillis { state.floatValue = ((SystemClock.uptimeMillis() - start) % ms) / ms.toFloat() }
            delay(1000L / fps)
        }
    }
    return state
}

private const val WALLPAPER_FPS = 20

/** Slow coloured glows drifting around, like northern lights. */
@Composable
private fun Aurora() {
    val time = loop(40_000)
    val base = CarColors.Background
    val accent = CarColors.Accent
    val second = lerp(accent, Color(0xFF7E57C2), 0.6f)
    val third = lerp(accent, Color(0xFF26A69A), 0.6f)
    val alpha = if (CarColors.palette.dark) 0.55f else 0.40f
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        drawRect(base)
        val a = t * 2 * PI.toFloat()
        val r = maxOf(size.width, size.height) * 0.6f

        fun blob(
            color: Color,
            cx: Float,
            cy: Float,
        ) = drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), Offset(cx, cy), r),
            r,
            Offset(cx, cy),
        )
        blob(accent, size.width * (0.3f + 0.2f * cos(a)), size.height * (0.4f + 0.3f * sin(2 * a)))
        blob(second, size.width * (0.7f + 0.2f * sin(a + 1f)), size.height * (0.6f + 0.3f * cos(a * 2 + 2f)))
        blob(third, size.width * (0.5f + 0.35f * cos(3 * a + 4f)), size.height * (0.3f + 0.2f * sin(a + 3f)))
    }
}

private class Star(
    val x: Float,
    val y: Float,
    val radius: Float,
    val phase: Float,
    val speed: Int,
)

/** Twinkling stars slowly drifting sideways. */
@Composable
private fun Stars() {
    val stars =
        remember {
            val rnd = Random(42)
            List(140) { Star(rnd.nextFloat(), rnd.nextFloat(), 0.8f + rnd.nextFloat() * 2.2f, rnd.nextFloat(), 1 + rnd.nextInt(3)) }
        }
    val time = loop(120_000)
    val dark = CarColors.palette.dark
    val base = CarColors.Background
    val star = if (dark) Color.White else CarColors.Accent
    val glow = CarColors.Accent
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        drawRect(Brush.verticalGradient(listOf(base, lerp(base, glow, if (dark) 0.18f else 0.12f))))
        for (s in stars) {
            val x = ((s.x + t * s.speed) % 1f) * size.width
            val twinkle = 0.5f + 0.5f * sin((t * 60 * s.speed + s.phase) * 2 * PI.toFloat())
            drawCircle(star.copy(alpha = 0.25f + 0.65f * twinkle), s.radius * density / 2, Offset(x, s.y * size.height))
        }
    }
}

/** Layers of soft waves along the bottom of the screen. */
@Composable
private fun Waves() {
    val time = loop(16_000, fps = 30)
    val base = CarColors.Background
    val accent = CarColors.Accent
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        drawRect(base)
        val a = t * 2 * PI.toFloat()
        for (i in 0 until 4) {
            val path = Path()
            val y0 = size.height * (0.55f + i * 0.1f)
            val amp = size.height * (0.05f - i * 0.008f)
            val k = (1.5f + i * 0.4f) * 2 * PI.toFloat() / size.width
            val phase = a * (if (i % 2 == 0) 1 else -1) + i
            path.moveTo(0f, size.height)
            var x = 0f
            while (x <= size.width) {
                path.lineTo(x, y0 + amp * sin(k * x + phase))
                x += 8f
            }
            path.lineTo(size.width, size.height)
            path.close()
            drawPath(path, accent.copy(alpha = 0.12f + i * 0.05f))
        }
    }
}
