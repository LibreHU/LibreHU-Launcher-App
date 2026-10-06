package org.librehu.launcher.speed

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import org.librehu.launcher.R
import org.librehu.launcher.data.ThemeProvider
import kotlin.math.roundToInt

/** What a speed widget shows; a touch on the widget goes to the next mode. */
enum class SpeedMode { GPS, OBD, BOTH }

/**
 * Speed widget of the launcher (its slots or any home screen): GPS speed, OBD speed (LibreHU-service with an
 * ELM327), or both side by side. Redrawn by [SpeedMonitor] on each change, in the launcher theme.
 */
class SpeedWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        ensureRunning(context)
        appWidgetIds.forEach { manager.updateAppWidget(it, views(context, it, SpeedMonitor.get(context).state.value)) }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == ACTION_CYCLE) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val next = SpeedMode.entries[(mode(context, id).ordinal + 1) % SpeedMode.entries.size]
                prefs(context).edit().putString("mode_$id", next.name).apply()
                ensureRunning(context)
                AppWidgetManager.getInstance(context).updateAppWidget(id, views(context, id, SpeedMonitor.get(context).state.value))
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        prefs(context).edit().apply { appWidgetIds.forEach { remove("mode_$it") } }.apply()
    }

    override fun onDisabled(context: Context) {
        if (running) {
            running = false
            SpeedMonitor.get(context).stop()
        }
    }

    companion object {
        private const val ACTION_CYCLE = "org.librehu.launcher.action.SPEED_CYCLE"

        @Volatile
        private var running = false

        private fun prefs(context: Context) = context.getSharedPreferences("speed_widget", Context.MODE_PRIVATE)

        private fun mode(
            context: Context,
            id: Int,
        ) = runCatching { SpeedMode.valueOf(prefs(context).getString("mode_$id", null)!!) }.getOrDefault(SpeedMode.GPS)

        /** Speeds followed while at least one widget exists (the launcher process lives as the home app). */
        fun ensureRunning(context: Context) {
            if (running || ids(context).isEmpty()) return
            running = true
            val m = SpeedMonitor.get(context)
            m.onChange = { s -> updateAll(context.applicationContext, s) }
            m.start()
        }

        private fun ids(context: Context): IntArray {
            val manager = AppWidgetManager.getInstance(context) ?: return IntArray(0)
            return manager.getAppWidgetIds(ComponentName(context, SpeedWidget::class.java))
        }

        fun refresh(context: Context) = updateAll(context, SpeedMonitor.get(context).state.value)

        fun updateAll(
            context: Context,
            s: SpeedState,
        ) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            ids(context).forEach { manager.updateAppWidget(it, views(context, it, s)) }
        }

        private fun views(
            context: Context,
            id: Int,
            s: SpeedState,
        ): RemoteViews {
            val theme = context.getSharedPreferences(ThemeProvider.EFFECTIVE, Context.MODE_PRIVATE)
            val dark = theme.getBoolean("dark", true)
            val accent = theme.getInt("accent", 0).takeIf { it != 0 } ?: if (dark) 0xFF8AB4F8.toInt() else 0xFF1A73E8.toInt()
            val text = if (dark) 0xFFE8EAED.toInt() else 0xFF202124.toInt()
            val dim = if (dark) 0xFF9AA0A6.toInt() else 0xFF5F6368.toInt()
            val mode = mode(context, id)

            fun shown(v: Float?) = v?.roundToInt()?.toString() ?: "—"
            return RemoteViews(context.packageName, R.layout.widget_speed).apply {
                setInt(
                    R.id.speed_root,
                    "setBackgroundResource",
                    if (dark) R.drawable.widget_background else R.drawable.widget_background_light,
                )
                setViewVisibility(R.id.speed_gps, if (mode == SpeedMode.OBD) View.GONE else View.VISIBLE)
                setViewVisibility(R.id.speed_obd, if (mode == SpeedMode.GPS) View.GONE else View.VISIBLE)
                setViewVisibility(R.id.speed_divider, if (mode == SpeedMode.BOTH) View.VISIBLE else View.GONE)
                val big = if (mode == SpeedMode.BOTH) 44f else 64f
                setTextViewText(R.id.speed_gps_value, shown(s.gpsKmh))
                setTextViewTextSize(R.id.speed_gps_value, android.util.TypedValue.COMPLEX_UNIT_SP, big)
                setTextColor(R.id.speed_gps_value, if (s.gpsKmh != null) text else dim)
                setTextViewText(
                    R.id.speed_gps_label,
                    context.getString(if (s.gpsPermission) R.string.speed_gps else R.string.speed_gps_permission),
                )
                setTextColor(R.id.speed_gps_label, accent)
                setTextColor(R.id.speed_gps_unit, dim)
                setTextViewText(R.id.speed_obd_value, shown(s.obdKmh))
                setTextViewTextSize(R.id.speed_obd_value, android.util.TypedValue.COMPLEX_UNIT_SP, big)
                setTextColor(R.id.speed_obd_value, if (s.obdKmh != null) text else dim)
                setTextColor(R.id.speed_obd_label, accent)
                setTextColor(R.id.speed_obd_unit, dim)
                setInt(R.id.speed_divider, "setBackgroundColor", dim)
                setOnClickPendingIntent(
                    R.id.speed_root,
                    PendingIntent.getBroadcast(
                        context,
                        id,
                        Intent(context, SpeedWidget::class.java)
                            .setAction(ACTION_CYCLE)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
        }
    }
}
