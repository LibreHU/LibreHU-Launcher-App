package org.librehu.launcher.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.librehu.launcher.ui.Accent
import java.io.File

enum class ThemeMode { LIGHT, DARK, AUTO }

/** What is drawn behind the launcher. */
enum class WallpaperKind {
    NONE,

    /** Still picture (JPEG / PNG…), scaled down to the screen. */
    IMAGE,

    /** Animated GIF / WebP / HEIF sequence, played with [android.graphics.drawable.AnimatedImageDrawable]. */
    ANIMATED,

    /** Looping, muted video (MP4 / WebM). */
    VIDEO,

    /** The Android wallpaper, including live wallpapers ("Live Wallpaper" apps). */
    SYSTEM,

    /** Built-in animations, drawn in the accent colour. */
    AURORA,
    STARS,
    WAVES,

    /** Spectrum waves (AOSP "Music visualization" wallpaper), optionally following the music. */
    SPECTRUM,
    ;

    val builtIn get() = this == AURORA || this == STARS || this == WAVES || this == SPECTRUM
}

/** Order of the app drawer. */
enum class DrawerSort { NAME, NAME_DESC, PINNED_FIRST }

/** Where the shortcut rail sits. */
enum class RailPosition { LEFT, BOTTOM }

/** Colours of the spectrum wallpaper: edge, middle and centre of the waves. */
enum class SpectrumPalette(
    val edge: Long,
    val middle: Long,
    val center: Long,
) {
    /** Accent colour of the launcher (colours filled in at runtime). */
    ACCENT(0, 0, 0),
    ICE(0xFF0303FF, 0xFF7B7BFF, 0xFFF1F1FF),
    FIRE(0xFFFF0000, 0xFFFF8000, 0xFFFFFF00),
    LIME(0xFF00C853, 0xFF90EE90, 0xFFFFFFFF),
    MAGENTA(0xFFFF00FF, 0xFFFF70F5, 0xFFFFF0FE),
    CYAN(0xFF00B8D4, 0xFF63FFFF, 0xFFEBFCFC),
    VIOLET(0xFF8000FF, 0xFFB366FF, 0xFFFFFFFF),
}

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.AUTO,
    val accent: Accent = Accent.BLUE,
    /** Also switch the whole system (other apps) to light / dark. Needs MODIFY_DAY_NIGHT_MODE (privileged). */
    val systemWide: Boolean = true,
    val wallpaper: WallpaperKind = WallpaperKind.NONE,
    val rail: RailPosition = RailPosition.LEFT,
    val spectrumPalette: SpectrumPalette = SpectrumPalette.ACCENT,
    /** Spectrum follows the sound being played (Visualizer, needs RECORD_AUDIO). */
    val spectrumAudio: Boolean = false,
    /** Next to the clock: phone of the car (signal, battery, operator), GPS of the head unit, power button. */
    val showPhoneStatus: Boolean = true,
    val showGps: Boolean = true,
    val showPower: Boolean = true,
    /** Volume - / + buttons on the rail (hidden when the car has its own keys). */
    val showVolume: Boolean = true,
    /** App drawer: icon size (dp), labels, order, search field, hidden apps (keys). */
    val drawerIconSize: Int = 72,
    val drawerLabels: Boolean = true,
    val drawerSort: DrawerSort = DrawerSort.NAME,
    val drawerSearch: Boolean = true,
    val hiddenApps: Set<String> = emptySet(),
    /** LibreHU apps' icons drawn in the theme colours (their single-colour icon on the accent colour). */
    val themedIcons: Boolean = true,
) {
    val hasWallpaper get() = wallpaper != WallpaperKind.NONE
}

/** Persistence of the appearance settings and of the wallpaper files. */
class ThemeStore(
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences("theme", Context.MODE_PRIVATE)
    val wallpaperFile = File(context.filesDir, "wallpaper.jpg")
    val animatedFile = File(context.filesDir, "wallpaper_animated")
    val videoFile = File(context.filesDir, "wallpaper_video")

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<ThemeSettings> = _settings.asStateFlow()

    private val _wallpaper = MutableStateFlow(loadWallpaper())
    val wallpaper: StateFlow<Bitmap?> = _wallpaper.asStateFlow()

    /** Bumped each time a wallpaper file is replaced, so that the players reload it. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun update(transform: (ThemeSettings) -> ThemeSettings) {
        val s = transform(_settings.value)
        prefs
            .edit()
            .putString("mode", s.mode.name)
            .putString("accent", s.accent.name)
            .putBoolean("system_wide", s.systemWide)
            .putString("wallpaper", s.wallpaper.name)
            .putString("rail", s.rail.name)
            .putString("spectrum_palette", s.spectrumPalette.name)
            .putBoolean("spectrum_audio", s.spectrumAudio)
            .putBoolean("show_phone", s.showPhoneStatus)
            .putBoolean("show_gps", s.showGps)
            .putBoolean("show_power", s.showPower)
            .putBoolean("show_volume", s.showVolume)
            .putInt("drawer_icon", s.drawerIconSize)
            .putBoolean("drawer_labels", s.drawerLabels)
            .putString("drawer_sort", s.drawerSort.name)
            .putBoolean("drawer_search", s.drawerSearch)
            .putStringSet("hidden_apps", s.hiddenApps)
            .putBoolean("themed_icons", s.themedIcons)
            .apply()
        _settings.value = s
    }

    /** Built-in animation, system wallpaper or none: no file needed. */
    fun setWallpaperKind(kind: WallpaperKind) {
        if (kind == WallpaperKind.NONE) {
            clearWallpaper()
        } else {
            update { it.copy(wallpaper = kind) }
        }
    }

    /**
     * Uses the picked picture: animated GIF / WebP are kept as they are and played, other pictures are scaled
     * down to the screen size. Blocking (decodes / copies), call it off the main thread.
     */
    fun setImage(uri: Uri): Boolean {
        val tmp = File(context.cacheDir, "wallpaper_pick")
        try {
            if (!copy(uri, tmp, MAX_ANIMATED_BYTES)) return false
            if (isAnimated(tmp)) {
                replace(tmp, animatedFile)
                return use(WallpaperKind.ANIMATED)
            }
        } finally {
            tmp.delete()
        }
        val dm = context.resources.displayMetrics
        val target = maxOf(dm.widthPixels, dm.heightPixels)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val input = context.contentResolver.openInputStream(uri) ?: return false
        input.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return false
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        val bmp =
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return false
        val out = File(context.cacheDir, "wallpaper_jpg")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        replace(out, wallpaperFile)
        _wallpaper.value = bmp
        return use(WallpaperKind.IMAGE)
    }

    /** Copies the picked video (played muted and looping). Blocking, call it off the main thread. */
    fun setVideo(uri: Uri): Boolean {
        val tmp = File(context.cacheDir, "wallpaper_pick")
        try {
            if (!copy(uri, tmp, MAX_VIDEO_BYTES) || !isVideo(tmp)) return false
            replace(tmp, videoFile)
        } finally {
            tmp.delete()
        }
        return use(WallpaperKind.VIDEO)
    }

    fun clearWallpaper() {
        deleteFiles()
        update { it.copy(wallpaper = WallpaperKind.NONE) }
    }

    fun reset() {
        prefs.edit().clear().apply()
        deleteFiles()
        _settings.value = ThemeSettings()
    }

    private fun use(kind: WallpaperKind): Boolean {
        // Only keep the file of the wallpaper in use.
        if (kind != WallpaperKind.IMAGE) {
            wallpaperFile.delete()
            _wallpaper.value = null
        }
        if (kind != WallpaperKind.ANIMATED) animatedFile.delete()
        if (kind != WallpaperKind.VIDEO) videoFile.delete()
        _revision.value++
        update { it.copy(wallpaper = kind) }
        return true
    }

    private fun deleteFiles() {
        wallpaperFile.delete()
        animatedFile.delete()
        videoFile.delete()
        _wallpaper.value = null
        _revision.value++
    }

    private fun copy(
        uri: Uri,
        to: File,
        max: Long,
    ): Boolean {
        val input = context.contentResolver.openInputStream(uri) ?: return false
        var total = 0L
        input.use { i ->
            to.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = i.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > max) return false
                    o.write(buf, 0, n)
                }
            }
        }
        return total > 0
    }

    /** The players read the final file: never let them see a half-written one. */
    private fun replace(
        from: File,
        to: File,
    ) {
        to.delete()
        if (!from.renameTo(to)) {
            from.copyTo(to, overwrite = true)
            from.delete()
        }
    }

    private fun isAnimated(f: File): Boolean =
        try {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(f)) { d, info, _ ->
                // Only the type matters here: decode as small as possible.
                d.setTargetSize(maxOf(1, info.size.width / 8), maxOf(1, info.size.height / 8))
            } is AnimatedImageDrawable
        } catch (e: Exception) {
            false
        }

    private fun isVideo(f: File): Boolean {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.path)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
        } catch (e: Exception) {
            false
        } finally {
            r.release()
        }
    }

    private fun load(): ThemeSettings {
        val stored = prefs.getString("wallpaper", null)
        val kind =
            runCatching { WallpaperKind.valueOf(stored!!) }.getOrElse {
                // Settings saved before the animated wallpapers: an image file means IMAGE.
                if (wallpaperFile.exists()) WallpaperKind.IMAGE else WallpaperKind.NONE
            }
        return ThemeSettings(
            mode = runCatching { ThemeMode.valueOf(prefs.getString("mode", null)!!) }.getOrDefault(ThemeMode.AUTO),
            accent = runCatching { Accent.valueOf(prefs.getString("accent", null)!!) }.getOrDefault(Accent.BLUE),
            systemWide = prefs.getBoolean("system_wide", true),
            wallpaper = kind,
            rail = runCatching { RailPosition.valueOf(prefs.getString("rail", null)!!) }.getOrDefault(RailPosition.LEFT),
            spectrumPalette =
                runCatching { SpectrumPalette.valueOf(prefs.getString("spectrum_palette", null)!!) }.getOrDefault(SpectrumPalette.ACCENT),
            spectrumAudio = prefs.getBoolean("spectrum_audio", false),
            showPhoneStatus = prefs.getBoolean("show_phone", true),
            showGps = prefs.getBoolean("show_gps", true),
            showPower = prefs.getBoolean("show_power", true),
            showVolume = prefs.getBoolean("show_volume", true),
            drawerIconSize = prefs.getInt("drawer_icon", 72),
            drawerLabels = prefs.getBoolean("drawer_labels", true),
            drawerSort = runCatching { DrawerSort.valueOf(prefs.getString("drawer_sort", null)!!) }.getOrDefault(DrawerSort.NAME),
            drawerSearch = prefs.getBoolean("drawer_search", true),
            hiddenApps = prefs.getStringSet("hidden_apps", emptySet()).orEmpty().toSet(),
            themedIcons = prefs.getBoolean("themed_icons", true),
        )
    }

    private fun loadWallpaper(): Bitmap? = if (wallpaperFile.exists()) BitmapFactory.decodeFile(wallpaperFile.path) else null

    companion object {
        private const val MAX_ANIMATED_BYTES = 64L * 1024 * 1024
        private const val MAX_VIDEO_BYTES = 512L * 1024 * 1024

        /** Sent to the other LibreHU apps when the effective theme changes (extras: dark, accent). */
        const val ACTION_THEME_CHANGED = "org.librehu.action.THEME_CHANGED"

        fun themeIntent(
            dark: Boolean,
            accent: Int,
        ) = Intent(ACTION_THEME_CHANGED).putExtra("dark", dark).putExtra("accent", accent)
    }
}
