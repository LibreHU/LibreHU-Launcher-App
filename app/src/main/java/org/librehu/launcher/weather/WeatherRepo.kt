package org.librehu.launcher.weather

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/** Weather at the head unit's position (Open-Meteo), temperatures in °C. */
data class WeatherNow(
    val tempC: Double,
    val feelsC: Double?,
    val code: Int,
    val day: Boolean,
    val windKmh: Double?,
    val minC: Double?,
    val maxC: Double?,
    val at: Long,
    val lat: Double,
    val lon: Double,
)

data class WeatherSettings(
    val enabled: Boolean = false,
    /** Icon and temperature next to the shortcut bar's clock. */
    val statusBar: Boolean = true,
    val fahrenheit: Boolean = false,
    /** Minutes between two forecasts (also fetched again after 5 km). */
    val refreshMinutes: Int = 30,
) {
    companion object {
        val REFRESH_CHOICES = listOf(15, 30, 60, 120)
    }
}

/**
 * Current weather from Open-Meteo (api.open-meteo.com, free for non-commercial use, no key, CC BY 4.0: "Weather data
 * by Open-Meteo.com"), at the last position known to Android (the GPS of the head unit, rounded to 0.01°, about 1 km).
 * Fetched every [WeatherSettings.refreshMinutes] or after 5 km, kept for the next start; nothing is sent without
 * [WeatherSettings.enabled].
 */
class WeatherRepo private constructor(
    context: Context,
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<WeatherSettings> = _settings.asStateFlow()

    private val _now = MutableStateFlow(cached())
    val now: StateFlow<WeatherNow?> = _now.asStateFlow()

    /** Last problem (no position, no network…), empty when fine. */
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    /** Called on each new forecast (widgets). */
    @Volatile
    var onChange: (() -> Unit)? = null

    fun update(transform: (WeatherSettings) -> WeatherSettings) {
        val s = transform(_settings.value)
        prefs
            .edit()
            .putBoolean("enabled", s.enabled)
            .putBoolean("status_bar", s.statusBar)
            .putBoolean("fahrenheit", s.fahrenheit)
            .putInt("refresh", s.refreshMinutes)
            .apply()
        _settings.value = s
        if (s.enabled) start() else stop()
        onChange?.invoke()
    }

    /** Follows the weather while enabled (the launcher is the home app: its process stays). */
    fun start() {
        if (!_settings.value.enabled || job?.isActive == true) return
        job =
            scope.launch {
                while (isActive) {
                    val loc = location()
                    val cur = _now.value
                    val age = System.currentTimeMillis() - (cur?.at ?: 0)
                    when {
                        loc == null -> _message.value = "position"
                        cur == null || age > _settings.value.refreshMinutes * 60_000L || distanceKm(cur, loc) > 5 -> fetch(loc)
                    }
                    delay(60_000)
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Fetches now (settings button). */
    fun refresh() {
        scope.launch { location()?.let { fetch(it) } ?: run { _message.value = "position" } }
    }

    private suspend fun fetch(loc: Location) {
        val lat = (loc.latitude * 100).roundToInt() / 100.0
        val lon = (loc.longitude * 100).roundToInt() / 100.0
        val url =
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m" +
                "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto"
        val result =
            withContext(Dispatchers.IO) {
                runCatching {
                    val c = URL(url).openConnection() as HttpURLConnection
                    c.connectTimeout = 10_000
                    c.readTimeout = 10_000
                    c.setRequestProperty("User-Agent", userAgent(app))
                    try {
                        if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
                        c.inputStream.bufferedReader().use { it.readText() }
                    } finally {
                        c.disconnect()
                    }
                }
            }
        result
            .mapCatching { parse(it, lat, lon) }
            .onSuccess {
                _now.value = it
                _message.value = ""
                prefs.edit().putString("cache", it.toJson()).apply()
                onChange?.invoke()
            }.onFailure {
                Log.w(TAG, "Open-Meteo: ${it.message}")
                _message.value = it.message ?: "network"
            }
    }

    @SuppressLint("MissingPermission")
    private fun location(): Location? {
        val fine = ContextCompat.checkSelfPermission(app, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine) return null
        val lm = app.getSystemService(LocationManager::class.java) ?: return null
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    private fun distanceKm(
        w: WeatherNow,
        l: Location,
    ): Float {
        val out = FloatArray(1)
        Location.distanceBetween(w.lat, w.lon, l.latitude, l.longitude, out)
        return out[0] / 1000f
    }

    private fun cached(): WeatherNow? = prefs.getString("cache", null)?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() }

    private fun load(): WeatherSettings {
        val d = WeatherSettings()
        return WeatherSettings(
            enabled = prefs.getBoolean("enabled", d.enabled),
            statusBar = prefs.getBoolean("status_bar", d.statusBar),
            fahrenheit = prefs.getBoolean("fahrenheit", d.fahrenheit),
            refreshMinutes = prefs.getInt("refresh", d.refreshMinutes),
        )
    }

    companion object {
        private const val TAG = "LibreHU-Weather"
        const val PREFS = "weather"

        @Volatile
        private var instance: WeatherRepo? = null

        fun get(context: Context): WeatherRepo = instance ?: synchronized(this) { instance ?: WeatherRepo(context).also { instance = it } }

        /** "LibreHU-Launcher/<version> (+github.com/LibreHU)": the public APIs ask clients to say who they are. */
        fun userAgent(context: Context): String {
            val v = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
            return "LibreHU-Launcher/$v (+https://github.com/LibreHU)"
        }

        fun parse(
            text: String,
            lat: Double,
            lon: Double,
        ): WeatherNow {
            val o = JSONObject(text)
            val cur = o.getJSONObject("current")
            val daily = o.optJSONObject("daily")
            return WeatherNow(
                tempC = cur.getDouble("temperature_2m"),
                feelsC = cur.optDouble("apparent_temperature").takeIf { !it.isNaN() },
                code = cur.optInt("weather_code", -1),
                day = cur.optInt("is_day", 1) == 1,
                windKmh = cur.optDouble("wind_speed_10m").takeIf { !it.isNaN() },
                minC = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)?.takeIf { !it.isNaN() },
                maxC = daily?.optJSONArray("temperature_2m_max")?.optDouble(0)?.takeIf { !it.isNaN() },
                at = System.currentTimeMillis(),
                lat = lat,
                lon = lon,
            )
        }

        private fun WeatherNow.toJson(): String =
            JSONObject()
                .put("t", tempC)
                .put("f", feelsC ?: Double.NaN)
                .put("c", code)
                .put("d", day)
                .put("w", windKmh ?: Double.NaN)
                .put("min", minC ?: Double.NaN)
                .put("max", maxC ?: Double.NaN)
                .put("at", at)
                .put("lat", lat)
                .put("lon", lon)
                .toString()

        private fun fromJson(o: JSONObject) =
            WeatherNow(
                o.getDouble("t"),
                o.optDouble("f").takeIf { !it.isNaN() },
                o.getInt("c"),
                o.getBoolean("d"),
                o.optDouble("w").takeIf { !it.isNaN() },
                o.optDouble("min").takeIf { !it.isNaN() },
                o.optDouble("max").takeIf { !it.isNaN() },
                o.getLong("at"),
                o.getDouble("lat"),
                o.getDouble("lon"),
            )

        /** Temperature as shown: "12°" (°C) or "54°" (°F). */
        fun temp(
            c: Double,
            fahrenheit: Boolean,
        ): String = String.format(Locale.ROOT, "%d°", (if (fahrenheit) c * 9 / 5 + 32 else c).roundToInt())

        /** WMO weather code (Open-Meteo's table) → symbol. */
        fun symbol(
            code: Int,
            day: Boolean,
        ): String =
            when (code) {
                0 -> if (day) "☀️" else "🌙"
                1, 2 -> if (day) "⛅" else "☁️"
                3 -> "☁️"
                45, 48 -> "🌫️"
                51, 53, 55, 56, 57 -> "🌦️"
                61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
                71, 73, 75, 77, 85, 86 -> "🌨️"
                95, 96, 97, 99 -> "⛈️"
                else -> "🌡️"
            }
    }
}
