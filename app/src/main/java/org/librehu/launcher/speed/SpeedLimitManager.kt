package org.librehu.launcher.speed

import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.librehu.launcher.weather.WeatherRepo
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.max
import kotlin.math.roundToInt

/** Sound of the speed alert. */
enum class LimitSound { BEEP, DOUBLE, TRIPLE, CHIME, ALARM, NOTIFICATION, NONE }

/** Speed compared with the limit. */
enum class LimitSpeedSource { AUTO, GPS, OBD }

data class SpeedLimitSettings(
    /** Reads the limits of the road (OpenStreetMap through an Overpass server); nothing is sent without it. */
    val enabled: Boolean = false,
    val alert: Boolean = true,
    /** Allowed above the limit before the alert: the larger of km/h and percent. */
    val toleranceKmh: Int = 3,
    val tolerancePercent: Int = 0,
    /** Seconds above the limit before the alert (a short overtaking does not ring). */
    val delaySeconds: Int = 2,
    val sound: LimitSound = LimitSound.DOUBLE,
    val volume: Int = 80,
    /** Repeat every N seconds while still too fast, 0 = once until back under the limit. */
    val repeatSeconds: Int = 0,
    /** A short sound when the limit of the road changes. */
    val changeChime: Boolean = false,
    val source: LimitSpeedSource = LimitSpeedSource.AUTO,
    /** Limit sign next to the shortcut bar's clock. */
    val statusBar: Boolean = true,
    val server: String = DEFAULT_SERVER,
) {
    companion object {
        const val DEFAULT_SERVER = "https://overpass-api.de/api/interpreter"
        val SERVERS =
            listOf(
                DEFAULT_SERVER,
                "https://overpass.private.coffee/api/interpreter",
                "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
            )
    }
}

/** Limit of the road under the car, the speed compared with it, and whether it is exceeded. */
data class SpeedLimitState(
    val kmh: Int? = null,
    val road: String = "",
    val speedKmh: Float? = null,
    val over: Boolean = false,
    /** Last problem with the server (empty when fine). */
    val message: String = "",
)

/**
 * Speed limit of the road (OpenStreetMap `maxspeed`, [SpeedLimits]) with an over-speed alert. Roads within 500 m are
 * fetched from an Overpass server (fair use of overpass-api.de: about 10 000 requests and 1 GB a day per user) and
 * fetched again 300 m before leaving that area, at most every 10 s; the car is matched to the closest road going
 * its way. Speed limit alerts only: no speed camera positions (forbidden in France, Code de la route R413-15).
 */
class SpeedLimitManager private constructor(
    context: Context,
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val monitor = SpeedMonitor.get(app)
    private var job: Job? = null

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<SpeedLimitSettings> = _settings.asStateFlow()

    private val _state = MutableStateFlow(SpeedLimitState())
    val state: StateFlow<SpeedLimitState> = _state.asStateFlow()

    /** Called on each change of the limit or of the over-speed state (widgets). */
    @Volatile
    var onChange: (() -> Unit)? = null

    private var ways: List<OsmWay> = emptyList()
    private var areaLat = 0.0
    private var areaLon = 0.0
    private var lastQuery = 0L
    private var fetching = false
    private var backoffUntil = 0L
    private var candidate: Long? = null
    private var currentWay: Long? = null
    private var overSince = 0L
    private var lastAlert = 0L
    private var alerted = false

    fun update(transform: (SpeedLimitSettings) -> SpeedLimitSettings) {
        val s = transform(_settings.value)
        prefs
            .edit()
            .putBoolean("enabled", s.enabled)
            .putBoolean("alert", s.alert)
            .putInt("tol_kmh", s.toleranceKmh)
            .putInt("tol_pct", s.tolerancePercent)
            .putInt("delay", s.delaySeconds)
            .putString("sound", s.sound.name)
            .putInt("volume", s.volume)
            .putInt("repeat", s.repeatSeconds)
            .putBoolean("change_chime", s.changeChime)
            .putString("source", s.source.name)
            .putBoolean("status_bar", s.statusBar)
            .putString("server", s.server)
            .apply()
        _settings.value = s
        if (s.enabled) start() else stop()
        onChange?.invoke()
    }

    fun start() {
        if (!_settings.value.enabled || job?.isActive == true) return
        monitor.start()
        job = scope.launch { monitor.state.collect { onSpeed(it) } }
    }

    fun stop() {
        if (job == null) return
        job?.cancel()
        job = null
        monitor.stop()
        ways = emptyList()
        currentWay = null
        _state.value = SpeedLimitState()
        onChange?.invoke()
    }

    /** Plays the chosen sound (settings button). */
    fun test() = play(_settings.value.sound, _settings.value.volume)

    private fun onSpeed(s: SpeedState) {
        val set = _settings.value
        val speed =
            when (set.source) {
                LimitSpeedSource.GPS -> s.gpsKmh
                LimitSpeedSource.OBD -> s.obdKmh
                LimitSpeedSource.AUTO -> s.gpsKmh ?: s.obdKmh
            }
        val fix = s.fix
        if (fix == null) {
            publish(_state.value.copy(speedKmh = speed, over = false))
            return
        }
        val now = System.currentTimeMillis()
        val fromArea = distance(areaLat, areaLon, fix.lat, fix.lon)
        if (!fetching && now >= backoffUntil && now - lastQuery > MIN_QUERY_MS && (ways.isEmpty() || fromArea > REFETCH_M)) {
            fetch(fix.lat, fix.lon)
        }
        val match = SpeedLimits.match(ways, fix.lat, fix.lon, fix.bearing, max(MATCH_M, fix.accuracy.toDouble().coerceAtMost(50.0)))
        // Another road must win twice in a row (junctions, parallel roads).
        val way = match?.way?.id
        val keep =
            if (way != currentWay && way != null && currentWay != null && candidate != way) {
                candidate = way
                true
            } else {
                false
            }
        if (keep) return
        candidate = null
        val oldLimit = _state.value.kmh
        currentWay = way
        val limit = match?.kmh
        if (set.changeChime && limit != null && oldLimit != null && limit != oldLimit) play(LimitSound.BEEP, set.volume)
        val margin = if (limit == null) 0 else max(set.toleranceKmh, (limit * set.tolerancePercent / 100.0).roundToInt())
        val over = limit != null && speed != null && speed > limit + margin
        if (over) {
            if (overSince == 0L) overSince = now
            val long = now - overSince >= set.delaySeconds * 1000L
            val again = set.repeatSeconds > 0 && now - lastAlert >= set.repeatSeconds * 1000L
            if (set.alert && long && (!alerted || again)) {
                alerted = true
                lastAlert = now
                play(set.sound, set.volume)
            }
        } else if (speed == null || limit == null || speed <= limit) {
            overSince = 0L
            alerted = false
        }
        publish(SpeedLimitState(limit, match?.way?.name.orEmpty(), speed, over, _state.value.message))
    }

    private fun publish(s: SpeedLimitState) {
        val old = _state.value
        _state.value = s
        if (old.kmh != s.kmh || old.over != s.over || old.road != s.road) onChange?.invoke()
    }

    private fun fetch(
        lat: Double,
        lon: Double,
    ) {
        fetching = true
        lastQuery = System.currentTimeMillis()
        val server = _settings.value.server
        scope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val body = "data=" + URLEncoder.encode(SpeedLimits.query(lat, lon, AREA_M), "UTF-8")
                        val c = URL(server).openConnection() as HttpURLConnection
                        c.connectTimeout = 10_000
                        c.readTimeout = 25_000
                        c.requestMethod = "POST"
                        c.doOutput = true
                        c.setRequestProperty("User-Agent", WeatherRepo.userAgent(app))
                        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                        try {
                            c.outputStream.use { it.write(body.toByteArray()) }
                            if (c.responseCode == 429 || c.responseCode == 504) throw Busy(c.responseCode)
                            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
                            SpeedLimits.ways(c.inputStream.bufferedReader().use { it.readText() })
                        } finally {
                            c.disconnect()
                        }
                    }
                }
            result
                .onSuccess {
                    ways = it
                    areaLat = lat
                    areaLon = lon
                    _state.value = _state.value.copy(message = "")
                }.onFailure {
                    Log.w(TAG, "Overpass: ${it.message}")
                    if (it is Busy) backoffUntil = System.currentTimeMillis() + BUSY_BACKOFF_MS
                    _state.value = _state.value.copy(message = it.message ?: "network")
                }
            fetching = false
        }
    }

    private class Busy(
        code: Int,
    ) : Exception("HTTP $code (server busy)")

    private fun play(
        sound: LimitSound,
        volume: Int,
    ) {
        if (sound == LimitSound.NONE) return
        if (sound == LimitSound.NOTIFICATION) {
            runCatching { RingtoneManager.getRingtone(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play() }
            return
        }
        scope.launch(Dispatchers.Default) {
            val tg = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, volume.coerceIn(0, 100)) }.getOrNull() ?: return@launch
            try {
                when (sound) {
                    LimitSound.BEEP -> beep(tg, ToneGenerator.TONE_PROP_BEEP, 200, 1)
                    LimitSound.DOUBLE -> beep(tg, ToneGenerator.TONE_PROP_BEEP, 150, 2)
                    LimitSound.TRIPLE -> beep(tg, ToneGenerator.TONE_PROP_BEEP, 120, 3)
                    LimitSound.CHIME -> beep(tg, ToneGenerator.TONE_PROP_ACK, 400, 1)
                    LimitSound.ALARM -> beep(tg, ToneGenerator.TONE_CDMA_HIGH_L, 700, 1)
                    else -> {}
                }
                delay(200)
            } finally {
                tg.release()
            }
        }
    }

    private suspend fun beep(
        tg: ToneGenerator,
        tone: Int,
        ms: Int,
        times: Int,
    ) {
        repeat(times) {
            tg.startTone(tone, ms)
            delay(ms + 120L)
        }
    }

    private fun distance(
        aLat: Double,
        aLon: Double,
        bLat: Double,
        bLon: Double,
    ): Double {
        val out = FloatArray(1)
        android.location.Location.distanceBetween(aLat, aLon, bLat, bLon, out)
        return out[0].toDouble()
    }

    private fun load(): SpeedLimitSettings {
        val d = SpeedLimitSettings()
        return SpeedLimitSettings(
            enabled = prefs.getBoolean("enabled", d.enabled),
            alert = prefs.getBoolean("alert", d.alert),
            toleranceKmh = prefs.getInt("tol_kmh", d.toleranceKmh),
            tolerancePercent = prefs.getInt("tol_pct", d.tolerancePercent),
            delaySeconds = prefs.getInt("delay", d.delaySeconds),
            sound = runCatching { LimitSound.valueOf(prefs.getString("sound", null)!!) }.getOrDefault(d.sound),
            volume = prefs.getInt("volume", d.volume),
            repeatSeconds = prefs.getInt("repeat", d.repeatSeconds),
            changeChime = prefs.getBoolean("change_chime", d.changeChime),
            source = runCatching { LimitSpeedSource.valueOf(prefs.getString("source", null)!!) }.getOrDefault(d.source),
            statusBar = prefs.getBoolean("status_bar", d.statusBar),
            server = prefs.getString("server", null) ?: d.server,
        )
    }

    companion object {
        private const val TAG = "LibreHU-Limit"
        const val PREFS = "speed_limit"
        private const val AREA_M = 500
        private const val REFETCH_M = 300.0
        private const val MATCH_M = 25.0
        private const val MIN_QUERY_MS = 10_000L
        private const val BUSY_BACKOFF_MS = 60_000L

        @Volatile
        private var instance: SpeedLimitManager? = null

        fun get(context: Context): SpeedLimitManager = instance ?: synchronized(this) { instance ?: SpeedLimitManager(context).also { instance = it } }
    }
}
