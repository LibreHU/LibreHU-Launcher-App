package org.librehu.launcher.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.librehu.launcher.ui.Accent
import java.io.File

enum class ThemeMode { LIGHT, DARK, AUTO }

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.AUTO,
    val accent: Accent = Accent.BLUE,
    /** Also switch the whole system (other apps) to light / dark. Needs MODIFY_DAY_NIGHT_MODE (privileged). */
    val systemWide: Boolean = true,
    val hasWallpaper: Boolean = false,
)

/** Persistence of the appearance settings and of the wallpaper image. */
class ThemeStore(
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences("theme", Context.MODE_PRIVATE)
    val wallpaperFile = File(context.filesDir, "wallpaper.jpg")

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<ThemeSettings> = _settings.asStateFlow()

    private val _wallpaper = MutableStateFlow(loadWallpaper())
    val wallpaper: StateFlow<Bitmap?> = _wallpaper.asStateFlow()

    fun update(transform: (ThemeSettings) -> ThemeSettings) {
        val s = transform(_settings.value)
        prefs
            .edit()
            .putString("mode", s.mode.name)
            .putString("accent", s.accent.name)
            .putBoolean("system_wide", s.systemWide)
            .apply()
        _settings.value = s
    }

    /** Copies the picked image, scaled down to the screen size. */
    fun setWallpaper(uri: Uri): Boolean {
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
        wallpaperFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        _wallpaper.value = bmp
        _settings.value = _settings.value.copy(hasWallpaper = true)
        return true
    }

    fun clearWallpaper() {
        wallpaperFile.delete()
        _wallpaper.value = null
        _settings.value = _settings.value.copy(hasWallpaper = false)
    }

    fun reset() {
        prefs.edit().clear().apply()
        clearWallpaper()
        _settings.value = ThemeSettings()
    }

    private fun load() =
        ThemeSettings(
            mode = runCatching { ThemeMode.valueOf(prefs.getString("mode", null)!!) }.getOrDefault(ThemeMode.AUTO),
            accent = runCatching { Accent.valueOf(prefs.getString("accent", null)!!) }.getOrDefault(Accent.BLUE),
            systemWide = prefs.getBoolean("system_wide", true),
            hasWallpaper = wallpaperFile.exists(),
        )

    private fun loadWallpaper(): Bitmap? = if (wallpaperFile.exists()) BitmapFactory.decodeFile(wallpaperFile.path) else null

    companion object {
        /** Sent to the other LibreHU apps when the effective theme changes (extras: dark, accent). */
        const val ACTION_THEME_CHANGED = "org.librehu.action.THEME_CHANGED"

        fun themeIntent(
            dark: Boolean,
            accent: Int,
        ) = Intent(ACTION_THEME_CHANGED).putExtra("dark", dark).putExtra("accent", accent)
    }
}
