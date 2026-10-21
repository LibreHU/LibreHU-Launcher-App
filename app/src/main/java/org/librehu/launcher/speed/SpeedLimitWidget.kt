package org.librehu.launcher.speed

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import org.librehu.launcher.R
import org.librehu.launcher.data.ThemeProvider

/**
 * Speed limit sign of the road ([SpeedLimitManager], OpenStreetMap), red while too fast; launcher slots or any home
 * screen. Needs the speed limit turned on (Settings → Driving).
 */
class SpeedLimitWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        SpeedLimitManager.get(context).start()
        appWidgetIds.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, SpeedLimitWidget::class.java))
            if (ids.isEmpty()) return
            val v = views(context)
            ids.forEach { manager.updateAppWidget(it, v) }
        }

        private fun views(context: Context): RemoteViews {
            val m = SpeedLimitManager.get(context)
            val st = m.state.value
            val dark = context.getSharedPreferences(ThemeProvider.EFFECTIVE, Context.MODE_PRIVATE).getBoolean("dark", true)
            return RemoteViews(context.packageName, R.layout.widget_speed_limit).apply {
                setInt(R.id.limit_root, "setBackgroundResource", if (dark) R.drawable.widget_background else R.drawable.widget_background_light)
                setTextViewText(R.id.limit_value, st.kmh?.toString() ?: "—")
                setTextColor(R.id.limit_value, if (st.over) 0xFFE53935.toInt() else 0xFF202124.toInt())
                setTextViewText(
                    R.id.limit_road,
                    when {
                        !m.settings.value.enabled -> context.getString(R.string.limit_off)
                        st.kmh == null -> context.getString(R.string.limit_unknown)
                        else -> st.road
                    },
                )
            }
        }
    }
}
