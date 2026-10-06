package org.librehu.launcher.tpms

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import org.librehu.launcher.MainActivity
import org.librehu.launcher.R
import org.librehu.launcher.data.ThemeProvider

/**
 * TPMS widget (launcher slots or any other home screen): the four tyres around the car, red when in alarm.
 * Redrawn by [TpmsManager] on every new reading and by [ThemeProvider.publish] on light / dark changes.
 */
class TpmsWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // Process started for the widget: open the receiver too (it then redraws on each reading).
        val tpms = TpmsManager.get(context)
        tpms.start()
        manager.updateAppWidget(appWidgetIds, views(context, tpms.state.value, tpms.settings.value))
    }

    companion object {
        /** Opens the launcher on the TPMS screen. */
        const val ACTION_SHOW_TPMS = "org.librehu.launcher.action.SHOW_TPMS"

        private data class Cell(
            val pos: TyrePos,
            val root: Int,
            val pressure: Int,
            val temp: Int,
        )

        private val cells =
            listOf(
                Cell(TyrePos.FL, R.id.tpms_fl, R.id.tpms_fl_pressure, R.id.tpms_fl_temp),
                Cell(TyrePos.FR, R.id.tpms_fr, R.id.tpms_fr_pressure, R.id.tpms_fr_temp),
                Cell(TyrePos.RL, R.id.tpms_rl, R.id.tpms_rl_pressure, R.id.tpms_rl_temp),
                Cell(TyrePos.RR, R.id.tpms_rr, R.id.tpms_rr_pressure, R.id.tpms_rr_temp),
            )

        fun refresh(context: Context) {
            val tpms = TpmsManager.get(context)
            updateAll(context, tpms.state.value, tpms.settings.value)
        }

        fun updateAll(
            context: Context,
            st: TpmsState,
            s: TpmsSettings,
        ) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TpmsWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context, st, s))
        }

        private fun views(
            context: Context,
            st: TpmsState,
            s: TpmsSettings,
        ): RemoteViews {
            val dark = context.getSharedPreferences(ThemeProvider.EFFECTIVE, Context.MODE_PRIVATE).getBoolean("dark", true)
            val text = if (dark) 0xFFE8EAED.toInt() else 0xFF202124.toInt()
            val dim = if (dark) 0xFF9AA0A6.toInt() else 0xFF5F6368.toInt()
            val accent = if (dark) 0xFF8AB4F8.toInt() else 0xFF1A73E8.toInt()
            val white = 0xFFFFFFFF.toInt()
            val red = 0xFFF44336.toInt()
            return RemoteViews(context.packageName, R.layout.widget_tpms).apply {
                setInt(
                    R.id.tpms_root,
                    "setBackgroundResource",
                    if (dark) R.drawable.widget_background else R.drawable.widget_background_light,
                )
                setTextColor(R.id.tpms_title, text)
                setInt(R.id.tpms_icon, "setColorFilter", accent)
                val car = TpmsManager.get(context).carImageFor(dark)
                if (car != null) {
                    // Small copy: widget updates go through a binder transaction.
                    val h = 200
                    setImageViewBitmap(
                        R.id.tpms_car,
                        Bitmap.createScaledBitmap(car, (car.width * h / car.height).coerceAtLeast(1), h, true),
                    )
                    setInt(R.id.tpms_car, "setColorFilter", 0)
                } else {
                    setImageViewResource(R.id.tpms_car, R.drawable.widget_car)
                    setInt(R.id.tpms_car, "setColorFilter", dim)
                }
                setTextViewText(
                    R.id.tpms_status,
                    context.getString(if (st.connected) R.string.tpms_connected else R.string.tpms_disconnected),
                )
                setTextColor(R.id.tpms_status, if (st.connected) dim else red)
                for (c in cells) {
                    val r = st.tyres[c.pos]
                    val alarm = r != null && r.alarms(s).isNotEmpty()
                    setInt(
                        c.root,
                        "setBackgroundResource",
                        when {
                            alarm -> R.drawable.widget_cell_alarm
                            dark -> R.drawable.widget_cell
                            else -> R.drawable.widget_cell_light
                        },
                    )
                    setTextViewText(c.pressure, r?.let { TpmsManager.formatPressure(it.kpa, s.unit) } ?: "—")
                    setTextViewText(c.temp, r?.let { TpmsManager.formatTemp(it.celsius, s.fahrenheit) } ?: "")
                    setTextColor(c.pressure, if (alarm) white else text)
                    setTextColor(c.temp, if (alarm) white else dim)
                }
                val spare = st.tyres[TyrePos.SPARE]
                if (s.showSpare && spare != null) {
                    val alarm = spare.alarms(s).isNotEmpty()
                    setViewVisibility(R.id.tpms_spare, View.VISIBLE)
                    setTextViewText(
                        R.id.tpms_spare,
                        context.getString(TpmsManager.posLabel(TyrePos.SPARE)) + "  " +
                            TpmsManager.formatPressure(spare.kpa, s.unit) + " · " + TpmsManager.formatTemp(spare.celsius, s.fahrenheit),
                    )
                    setTextColor(R.id.tpms_spare, if (alarm) red else dim)
                } else {
                    setViewVisibility(R.id.tpms_spare, View.GONE)
                }
                setOnClickPendingIntent(
                    R.id.tpms_root,
                    PendingIntent.getActivity(
                        context,
                        1,
                        Intent(context, MainActivity::class.java)
                            .setAction(ACTION_SHOW_TPMS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
        }
    }
}
