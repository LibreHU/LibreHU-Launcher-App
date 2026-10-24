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

    /** A picture from the internet, renewed by itself ([OnlineSource], [OnlineRefresh]). */
    ONLINE,

    /** Car stereo display animations of the 1990s-2000s on a dot matrix ([org.librehu.launcher.ui.RetroScene]). */
    RETRO_DOLPHIN,
    RETRO_ANALYZER,
    RETRO_CASSETTE,
    RETRO_ROAD,
    RETRO_RALLY,

    /** All of them, one after the other (a head unit's demo mode). */
    RETRO_DEMO,
    ;

    val retroScene get() = name.startsWith("RETRO_")

    val builtIn get() = this == AURORA || this == STARS || this == WAVES || this == SPECTRUM || retroScene
}

/** Where the internet wallpaper comes from. */
enum class OnlineSource {
    /** Bing's picture of the day, for a market (country and language). */
    BING,

    /** Wikimedia Commons' picture of the day. */
    WIKIMEDIA,

    /** A random photo (Lorem Picsum, free photos from Unsplash). */
    PICSUM,
}

/** When the internet wallpaper is renewed (when there is network). */
enum class OnlineRefresh { DAILY, SIX_HOURS, EACH_START }

/** Order of the app drawer. */
enum class DrawerSort { NAME, NAME_DESC, PINNED_FIRST }

/** Where the shortcut rail sits. */
enum class RailPosition { LEFT, BOTTOM }

/** Text under the phone status: operator, phone name, both (two centred lines) or nothing. */
enum class PhoneLabel { OPERATOR, NAME, BOTH, NONE }

/** Phone status options, also read by the standby clock (outside the launcher's ThemeStore). */
fun phoneDisplay(context: Context): Pair<PhoneLabel, Boolean> {
    val prefs = context.getSharedPreferences("theme", Context.MODE_PRIVATE)
    val label = runCatching { PhoneLabel.valueOf(prefs.getString("phone_label", null)!!) }.getOrDefault(PhoneLabel.OPERATOR)
    return label to prefs.getBoolean("phone_percent", false)
}

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

/** The accent colour itself: the chosen one, light or dark variant, or the custom colour. */
fun ThemeSettings.accentColor(dark: Boolean): androidx.compose.ui.graphics.Color =
    if (accent == Accent.CUSTOM) androidx.compose.ui.graphics.Color(customAccent) else accent.color(dark)

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.AUTO,
    val accent: Accent = Accent.BLUE,
    /** [Accent.CUSTOM]'s colour (ARGB), set with its red, green and blue. */
    val customAccent: Int = 0xFFFF9800.toInt(),
    /** Modern or retro look (retro: a display's colours from the accent, scan lines at night). */
    val style: org.librehu.launcher.ui.ThemeStyle = org.librehu.launcher.ui.ThemeStyle.MODERN,
    /** Segment display font everywhere (LibreHU Seg14) instead of the usual one, in any style. */
    val segmentFont: Boolean = false,
    /** Also switch the whole system (other apps) to light / dark. Needs MODIFY_DAY_NIGHT_MODE (privileged). */
    val systemWide: Boolean = true,
    val wallpaper: WallpaperKind = WallpaperKind.NONE,
    val rail: RailPosition = RailPosition.LEFT,
    /** Background of the shortcut bar, in % (0 = none, the wallpaper shows through). */
    val railOpacity: Int = ThemeStore.DEFAULT_RAIL_OPACITY,
    val spectrumPalette: SpectrumPalette = SpectrumPalette.ACCENT,
    /** Spectrum follows the sound being played (Visualizer, needs RECORD_AUDIO). */
    val spectrumAudio: Boolean = false,
    /** Next to the clock: phone of the car (signal, battery, operator), GPS of the head unit, power button. */
    val showPhoneStatus: Boolean = true,
    val phoneLabel: PhoneLabel = PhoneLabel.OPERATOR,
    /** Phone battery as a percentage next to the gauge (HFP gives 6 levels: steps of 20 %). */
    val phoneBatteryPercent: Boolean = false,
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
    /** Android Auto's picture in the dashboard card while its screen is not shown (Open Headunit LibreHU). */
    val aaLiveVideo: Boolean = false,
    val onlineSource: OnlineSource = OnlineSource.BING,
    /** Bing market, e.g. fr-FR; "auto" = the language of the device. */
    val bingMarket: String = "auto",
    val onlineRefresh: OnlineRefresh = OnlineRefresh.DAILY,
    /** Title and credit of the internet picture, small, in a corner. */
    val onlineCaption: Boolean = true,
    /** Home pages swiped sideways: the dashboard, then pages of four widgets (1 to 4). */
    val homePages: Int = 1,
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
            .putInt("custom_accent", s.customAccent)
            .putString("style", s.style.name)
            .putBoolean("segment_font", s.segmentFont)
            .putBoolean("system_wide", s.systemWide)
            .putString("wallpaper", s.wallpaper.name)
            .putString("rail", s.rail.name)
            .putInt("rail_opacity", s.railOpacity)
            .putString("spectrum_palette", s.spectrumPalette.name)
            .putBoolean("spectrum_audio", s.spectrumAudio)
            .putBoolean("show_phone", s.showPhoneStatus)
            .putString("phone_label", s.phoneLabel.name)
            .putBoolean("phone_percent", s.phoneBatteryPercent)
            .putBoolean("show_gps", s.showGps)
            .putBoolean("show_power", s.showPower)
            .putBoolean("show_volume", s.showVolume)
            .putInt("drawer_icon", s.drawerIconSize)
            .putBoolean("drawer_labels", s.drawerLabels)
            .putString("drawer_sort", s.drawerSort.name)
            .putBoolean("drawer_search", s.drawerSearch)
            .putStringSet("hidden_apps", s.hiddenApps)
            .putBoolean("themed_icons", s.themedIcons)
            .putBoolean("aa_live_video", s.aaLiveVideo)
            .putString("online_source", s.onlineSource.name)
            .putString("bing_market", s.bingMarket)
            .putString("online_refresh", s.onlineRefresh.name)
            .putBoolean("online_caption", s.onlineCaption)
            .putInt("home_pages", s.homePages)
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

    private val _caption = MutableStateFlow(prefs.getString("online_caption_text", "").orEmpty())

    /** Title and credit of the internet picture shown now. */
    val caption: StateFlow<String> = _caption.asStateFlow()

    /** When the internet picture was last renewed (epoch ms), 0 = never. */
    val onlineFetchedAt: Long get() = prefs.getLong("online_fetched_at", 0)

    /**
     * The downloaded internet picture [file] becomes the wallpaper (scaled down to the screen like a picked one), with
     * its [caption]. Blocking, call it off the main thread.
     */
    fun setOnline(
        file: File,
        caption: String,
    ): Boolean {
        val dm = context.resources.displayMetrics
        val target = maxOf(dm.widthPixels, dm.heightPixels)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return false
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
        val out = File(context.cacheDir, "wallpaper_jpg")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        replace(out, wallpaperFile)
        _wallpaper.value = bmp
        _caption.value = caption
        prefs
            .edit()
            .putString("online_caption_text", caption)
            .putLong("online_fetched_at", System.currentTimeMillis())
            .apply()
        return use(WallpaperKind.ONLINE)
    }

    private fun use(kind: WallpaperKind): Boolean {
        // Only keep the file of the wallpaper in use.
        if (kind != WallpaperKind.IMAGE && kind != WallpaperKind.ONLINE) {
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
            customAccent = prefs.getInt("custom_accent", 0xFFFF9800.toInt()),
            style = org.librehu.launcher.ui.ThemeStyle.parse(prefs.getString("style", null)),
            // The retro styles of before always had the segment font.
            segmentFont = prefs.getBoolean("segment_font", prefs.getString("style", null)?.startsWith("RETRO_") == true),
            systemWide = prefs.getBoolean("system_wide", true),
            wallpaper = kind,
            rail = runCatching { RailPosition.valueOf(prefs.getString("rail", null)!!) }.getOrDefault(RailPosition.LEFT),
            railOpacity = prefs.getInt("rail_opacity", DEFAULT_RAIL_OPACITY).coerceIn(0, 100),
            spectrumPalette =
                runCatching { SpectrumPalette.valueOf(prefs.getString("spectrum_palette", null)!!) }.getOrDefault(SpectrumPalette.ACCENT),
            spectrumAudio = prefs.getBoolean("spectrum_audio", false),
            showPhoneStatus = prefs.getBoolean("show_phone", true),
            phoneLabel = runCatching { PhoneLabel.valueOf(prefs.getString("phone_label", null)!!) }.getOrDefault(PhoneLabel.OPERATOR),
            phoneBatteryPercent = prefs.getBoolean("phone_percent", false),
            showGps = prefs.getBoolean("show_gps", true),
            showPower = prefs.getBoolean("show_power", true),
            showVolume = prefs.getBoolean("show_volume", true),
            drawerIconSize = prefs.getInt("drawer_icon", 72),
            drawerLabels = prefs.getBoolean("drawer_labels", true),
            drawerSort = runCatching { DrawerSort.valueOf(prefs.getString("drawer_sort", null)!!) }.getOrDefault(DrawerSort.NAME),
            drawerSearch = prefs.getBoolean("drawer_search", true),
            hiddenApps = prefs.getStringSet("hidden_apps", emptySet()).orEmpty().toSet(),
            themedIcons = prefs.getBoolean("themed_icons", true),
            aaLiveVideo = prefs.getBoolean("aa_live_video", false),
            onlineSource = runCatching { OnlineSource.valueOf(prefs.getString("online_source", null)!!) }.getOrDefault(OnlineSource.BING),
            bingMarket = prefs.getString("bing_market", "auto") ?: "auto",
            onlineRefresh = runCatching { OnlineRefresh.valueOf(prefs.getString("online_refresh", null)!!) }.getOrDefault(OnlineRefresh.DAILY),
            onlineCaption = prefs.getBoolean("online_caption", true),
            homePages = prefs.getInt("home_pages", 1).coerceIn(1, MAX_HOME_PAGES),
        )
    }

    private fun loadWallpaper(): Bitmap? = if (wallpaperFile.exists()) BitmapFactory.decodeFile(wallpaperFile.path) else null

    companion object {
        private const val MAX_ANIMATED_BYTES = 64L * 1024 * 1024
        private const val MAX_VIDEO_BYTES = 512L * 1024 * 1024
        const val MAX_HOME_PAGES = 4

        /** Shortcut bar background by default: readable over a wallpaper, still showing it. */
        const val DEFAULT_RAIL_OPACITY = 70
        val RAIL_OPACITIES = listOf(0, 25, 50, 70, 85, 100)

        /** Sent to the other LibreHU apps when the effective theme changes (extras: dark, accent, style and palette, see [ThemeProvider]). */
        const val ACTION_THEME_CHANGED = "org.librehu.action.THEME_CHANGED"

        fun themeIntent(
            dark: Boolean,
            accent: Int,
        ) = Intent(ACTION_THEME_CHANGED).putExtra("dark", dark).putExtra("accent", accent)
    }
}
