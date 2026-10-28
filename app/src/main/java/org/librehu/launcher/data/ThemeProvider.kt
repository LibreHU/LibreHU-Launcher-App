package org.librehu.launcher.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import org.librehu.launcher.speed.SpeedWidget
import org.librehu.launcher.tpms.TpmsWidget

/**
 * Effective theme for the other LibreHU apps: `content://org.librehu.launcher.theme/theme` returns one row
 * (`dark` 0/1, `accent` ARGB, then `style`: `modern`, `retro…`, `factory` or `ambient`, `font`: `normal` or `segment`,
 * `click`: the key click sound (a name of KeyClick.SOUNDS, or `off`), and the whole
 * palette, ARGB, opaque: `background`, `surface`, `surface_high`, `on_accent`, `text`, `text_dim`). Changes are also
 * broadcast with [ThemeStore.ACTION_THEME_CHANGED], same names as extras. Followed by every LibreHU app: the retro
 * palette, and the segment display font when it is chosen (any style).
 */
class ThemeProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val prefs = context!!.getSharedPreferences(EFFECTIVE, Context.MODE_PRIVATE)
        return MatrixCursor(arrayOf("dark", "accent", "style", "font", "click") + COLORS).apply {
            addRow(
                listOf<Any>(
                    if (prefs.getBoolean("dark", true)) 1 else 0,
                    prefs.getInt("accent", 0xFF8AB4F8.toInt()),
                    prefs.getString("style", "modern")!!,
                    prefs.getString("font", "normal")!!,
                    prefs.getString("click", "soft_modern")!!,
                ).plus(COLORS.map { prefs.getInt(it, 0) }).toTypedArray(),
            )
        }
    }

    override fun getType(uri: Uri) = "vnd.android.cursor.item/vnd.org.librehu.theme"

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    companion object {
        const val EFFECTIVE = "theme_effective"
        val URI: Uri = Uri.parse("content://org.librehu.launcher.theme/theme")

        /** Palette colours shared besides `accent`. */
        private val COLORS = arrayOf("background", "surface", "surface_high", "on_accent", "text", "text_dim")

        private fun argb(c: androidx.compose.ui.graphics.Color): Int =
            android.graphics.Color.argb(255, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

        fun publish(
            context: Context,
            p: org.librehu.launcher.ui.CarPalette,
            click: String,
        ) {
            val dark = p.dark
            val accent = argb(p.accent)
            val style = p.style.name.lowercase()
            val font = if (p.segment) "segment" else "normal"
            val colors = COLORS.zip(listOf(p.background, p.surface, p.surfaceHigh, p.onAccent, p.text, p.textDim).map(::argb))
            context
                .getSharedPreferences(EFFECTIVE, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("dark", dark)
                .putInt("accent", accent)
                .putString("style", style)
                .putString("font", font)
                .putString("click", click)
                .apply { colors.forEach { (k, v) -> putInt(k, v) } }
                .apply()
            context.contentResolver.notifyChange(URI, null)
            fun intent() = ThemeStore.themeIntent(dark, accent).putExtra("style", style).putExtra("font", font).putExtra("click", click).apply { colors.forEach { (k, v) -> putExtra(k, v) } }
            // Running apps listen to the implicit broadcast; manifest receivers (widgets of apps that are not
            // running) only get broadcasts aimed at their package (Android 8+).
            context.sendBroadcast(intent())
            for (pkg in LIBREHU_PACKAGES) context.sendBroadcast(intent().setPackage(pkg))
            TpmsWidget.refresh(context)
            SpeedWidget.refresh(context)
            org.librehu.launcher.weather.WeatherWidget.refresh(context)
            org.librehu.launcher.speed.SpeedLimitWidget.refresh(context)
            org.librehu.launcher.sos.SosWidget.refresh(context)
        }

        /** LibreHU apps with widgets to redraw on theme changes. */
        private val LIBREHU_PACKAGES = listOf("org.librehu.fm", "org.librehu.widgets", "org.librehu.dialer", "org.librehu.btnremap")
    }
}
