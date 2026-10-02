package org.librehu.launcher.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Effective theme for the other LibreHU apps: `content://org.librehu.launcher.theme/theme` returns one row
 * (`dark` 0/1, `accent` ARGB). Changes are also broadcast with [ThemeStore.ACTION_THEME_CHANGED].
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
        return MatrixCursor(arrayOf("dark", "accent")).apply {
            addRow(arrayOf<Any>(if (prefs.getBoolean("dark", true)) 1 else 0, prefs.getInt("accent", 0xFF8AB4F8.toInt())))
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

        fun publish(
            context: Context,
            dark: Boolean,
            accent: Int,
        ) {
            context
                .getSharedPreferences(EFFECTIVE, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("dark", dark)
                .putInt("accent", accent)
                .apply()
            context.contentResolver.notifyChange(URI, null)
            // Running apps listen to the implicit broadcast; manifest receivers (widgets of apps that are not
            // running) only get broadcasts aimed at their package (Android 8+).
            context.sendBroadcast(ThemeStore.themeIntent(dark, accent))
            for (pkg in LIBREHU_PACKAGES) context.sendBroadcast(ThemeStore.themeIntent(dark, accent).setPackage(pkg))
        }

        /** LibreHU apps with widgets to redraw on theme changes. */
        private val LIBREHU_PACKAGES = listOf("org.librehu.fm", "org.librehu.widgets")
    }
}
