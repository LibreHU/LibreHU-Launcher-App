package org.librehu.launcher.bridge

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import org.librehu.launcher.R
import org.librehu.launcher.speed.LimitSound
import org.librehu.launcher.speed.SpeedLimitManager
import org.librehu.launcher.weather.WeatherRepo

/**
 * The launcher's settings for LibreHU's phone app, through LibreHU-service (its bridge asks `org.librehu.launcher.bridge`,
 * signature permission `org.librehu.permission.BRIDGE`): `call("pages")` → Bundle "json" = the pages with their items
 * (format of LibreHU-service's docs/bridge.md), `call("set", "<page>/<item>", value)`.
 */
class BridgeProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle? {
        val ctx = context ?: return null
        val weather = WeatherRepo.get(ctx)
        val limits = SpeedLimitManager.get(ctx)
        val w = weather.settings.value
        val l = limits.settings.value
        when (method) {
            "set" -> {
                val item = arg?.substringAfter('/') ?: return null
                val v = extras?.getString("value")
                val on = v == "true"
                when (item) {
                    "weather" -> weather.update { it.copy(enabled = on) }
                    "weather_bar" -> weather.update { it.copy(statusBar = on) }
                    "fahrenheit" -> weather.update { it.copy(fahrenheit = on) }
                    "limit" -> limits.update { it.copy(enabled = on) }
                    "limit_alert" -> limits.update { it.copy(alert = on) }
                    "limit_bar" -> limits.update { it.copy(statusBar = on) }
                    "limit_tolerance" -> v?.toIntOrNull()?.let { t -> limits.update { it.copy(toleranceKmh = t.coerceIn(0, 20)) } }
                    "limit_sound" -> runCatching { LimitSound.valueOf(v!!) }.getOrNull()?.let { s -> limits.update { it.copy(sound = s) } }
                }
                return null
            }
            "action" -> {
                if (arg?.endsWith("/limit_test") == true) limits.test()
                return null
            }
            "pages" -> {
                fun item(
                    id: String,
                    type: String,
                    label: Int,
                    value: Any? = null,
                ) = JSONObject().put("id", id).put("type", type).put("label", ctx.getString(label)).apply { if (value != null) put("value", value) }
                val items =
                    JSONArray()
                        .put(item("s1", "section", R.string.weather_title))
                        .put(item("weather", "switch", R.string.weather_enable, w.enabled))
                        .put(item("weather_bar", "switch", R.string.weather_status_bar, w.statusBar))
                        .put(item("fahrenheit", "switch", R.string.bridge_fahrenheit, w.fahrenheit))
                        .put(item("s2", "section", R.string.limit_title))
                        .put(item("limit", "switch", R.string.limit_enable, l.enabled))
                        .put(item("limit_alert", "switch", R.string.limit_alert, l.alert))
                        .put(item("limit_bar", "switch", R.string.limit_status_bar, l.statusBar))
                        .put(item("limit_tolerance", "slider", R.string.limit_tolerance, l.toleranceKmh).put("min", 0).put("max", 20).put("step", 1).put("unit", "km/h"))
                        .put(
                            item("limit_sound", "choice", R.string.limit_sound, l.sound.name).put(
                                "options",
                                JSONArray(
                                    listOf(
                                        LimitSound.BEEP to R.string.limit_sound_beep,
                                        LimitSound.DOUBLE to R.string.limit_sound_double,
                                        LimitSound.TRIPLE to R.string.limit_sound_triple,
                                        LimitSound.CHIME to R.string.limit_sound_chime,
                                        LimitSound.ALARM to R.string.limit_sound_alarm,
                                        LimitSound.NOTIFICATION to R.string.limit_sound_notification,
                                        LimitSound.NONE to R.string.limit_sound_none,
                                    ).map { (s, r) -> JSONObject().put("v", s.name).put("l", ctx.getString(r)) },
                                ),
                            ),
                        ).put(item("limit_test", "button", R.string.limit_sound_test))
                val page = JSONObject().put("id", "driving").put("title", ctx.getString(R.string.driving_title)).put("icon", "car").put("items", items)
                return Bundle().apply { putString("json", JSONArray().put(page).toString()) }
            }
        }
        return null
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

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
}
