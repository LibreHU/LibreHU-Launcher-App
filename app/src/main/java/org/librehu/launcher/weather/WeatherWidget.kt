package org.librehu.launcher.weather

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.librehu.launcher.R
import org.librehu.launcher.data.ThemeProvider

/** Weather at the car's position ([WeatherRepo], Open-Meteo): symbol, temperature, min / max. A touch refreshes it. */
class WeatherWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WeatherRepo.get(context).start()
        appWidgetIds.forEach { manager.updateAppWidget(it, views(context)) }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == ACTION_REFRESH) {
            WeatherRepo.get(context).refresh()
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_REFRESH = "org.librehu.launcher.action.WEATHER_REFRESH"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidget::class.java))
            if (ids.isEmpty()) return
            val v = views(context)
            ids.forEach { manager.updateAppWidget(it, v) }
        }

        private fun views(context: Context): RemoteViews {
            val repo = WeatherRepo.get(context)
            val w = repo.now.value
            val s = repo.settings.value
            val dark = context.getSharedPreferences(ThemeProvider.EFFECTIVE, Context.MODE_PRIVATE).getBoolean("dark", true)
            val text = if (dark) 0xFFE8EAED.toInt() else 0xFF202124.toInt()
            val dim = if (dark) 0xFF9AA0A6.toInt() else 0xFF5F6368.toInt()
            return RemoteViews(context.packageName, R.layout.widget_weather).apply {
                setInt(R.id.weather_root, "setBackgroundResource", if (dark) R.drawable.widget_background else R.drawable.widget_background_light)
                setTextColor(R.id.weather_temp, text)
                setTextColor(R.id.weather_label, dim)
                setTextColor(R.id.weather_minmax, dim)
                if (!s.enabled || w == null) {
                    setTextViewText(R.id.weather_symbol, "🌡️")
                    setTextViewText(R.id.weather_temp, "—")
                    setTextViewText(R.id.weather_label, context.getString(if (s.enabled) R.string.weather_waiting else R.string.weather_off))
                    setTextViewText(R.id.weather_minmax, "")
                } else {
                    setTextViewText(R.id.weather_symbol, WeatherRepo.symbol(w.code, w.day))
                    setTextViewText(R.id.weather_temp, WeatherRepo.temp(w.tempC, s.fahrenheit))
                    setTextViewText(R.id.weather_label, context.getString(weatherLabel(w.code)))
                    val mm =
                        if (w.minC != null && w.maxC != null) {
                            WeatherRepo.temp(w.minC, s.fahrenheit) + " / " + WeatherRepo.temp(w.maxC, s.fahrenheit)
                        } else {
                            ""
                        }
                    setTextViewText(R.id.weather_minmax, mm)
                }
                setOnClickPendingIntent(
                    R.id.weather_root,
                    PendingIntent.getBroadcast(
                        context,
                        0,
                        Intent(context, WeatherWidget::class.java).setAction(ACTION_REFRESH),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
        }

        /** WMO code → short text. */
        fun weatherLabel(code: Int): Int =
            when (code) {
                0 -> R.string.weather_clear
                1, 2 -> R.string.weather_partly
                3 -> R.string.weather_cloudy
                45, 48 -> R.string.weather_fog
                51, 53, 55, 56, 57 -> R.string.weather_drizzle
                61, 63, 65, 66, 67, 80, 81, 82 -> R.string.weather_rain
                71, 73, 75, 77, 85, 86 -> R.string.weather_snow
                95, 96, 97, 99 -> R.string.weather_storm
                else -> R.string.weather_unknown
            }
    }
}
