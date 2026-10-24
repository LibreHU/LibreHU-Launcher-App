package org.librehu.launcher.data

import android.content.Context
import android.widget.RemoteViews
import org.librehu.launcher.R

/**
 * Colours of the launcher's own widgets, from the theme it publishes ([ThemeProvider.EFFECTIVE]): the usual ones, or
 * the retro palette when the style is retro (plain colours: widget backgrounds cannot be tinted on Android 9, the
 * dashboard's rounded slot clips them).
 */
class WidgetLook(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(ThemeProvider.EFFECTIVE, Context.MODE_PRIVATE)
    val dark = prefs.getBoolean("dark", true)
    val retro = prefs.getString("style", "modern")!!.startsWith("retro") && prefs.getInt("surface", 0) != 0
    val text = if (retro) prefs.getInt("text", 0) else if (dark) 0xFFE8EAED.toInt() else 0xFF202124.toInt()
    val dim = if (retro) prefs.getInt("text_dim", 0) else if (dark) 0xFF9AA0A6.toInt() else 0xFF5F6368.toInt()
    /** Texts in the segment display font ([SegmentViews]). */
    val segment = prefs.getString("font", "normal") == "segment"
    val accent = prefs.getInt("accent", 0).takeIf { it != 0 } ?: if (dark) 0xFF8AB4F8.toInt() else 0xFF1A73E8.toInt()
    private val surface = prefs.getInt("surface", 0)
    private val surfaceHigh = prefs.getInt("surface_high", 0)

    /** The widget's card. */
    fun background(
        views: RemoteViews,
        id: Int,
    ) = if (retro) {
        views.setInt(id, "setBackgroundColor", surface)
    } else {
        views.setInt(id, "setBackgroundResource", if (dark) R.drawable.widget_background else R.drawable.widget_background_light)
    }

    /** A cell inside it ([lightRes] / [darkRes] in the usual look). */
    fun cell(
        views: RemoteViews,
        id: Int,
        darkRes: Int,
        lightRes: Int,
    ) = if (retro) views.setInt(id, "setBackgroundColor", surfaceHigh) else views.setInt(id, "setBackgroundResource", if (dark) darkRes else lightRes)
}
