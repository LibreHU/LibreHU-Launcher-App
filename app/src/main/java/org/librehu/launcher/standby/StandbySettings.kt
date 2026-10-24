package org.librehu.launcher.standby

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ClockStyle { DIGITAL, ANALOG }

/** How the OBD values are drawn under the clock: one line, or tiles side by side. */
enum class ObdClockStyle { LINE, TILES }

/**
 * Standby clock (ivi-services: "screen protection" timer and the power key's "clock" actions, drawn by Jancar's
 * launcher). Here: after [idleMinutes] without touching the home screen, from the rail clock, from
 * `org.librehu.action.STANDBY_CLOCK` (key mapping), and as Android's screen saver.
 */
data class StandbySettings(
    /** 0: never from the home screen. */
    val idleMinutes: Int = 0,
    val style: ClockStyle = ClockStyle.DIGITAL,
    val showSeconds: Boolean = false,
    val showDate: Boolean = true,
    val showMedia: Boolean = true,
    /** Phone status (signal, battery, operator / name) above the clock. */
    val showPhone: Boolean = false,
    /** Car battery (LibreHU-service's OBD adapter) under the clock, when the adapter is connected. */
    val showCarBattery: Boolean = true,
    /**
     * OBD values under the clock, in this order: LibreHU-service's standard names (SPEED, RPM, COOLANT_TEMP…) and
     * manufacturer values as `car:<ECU>/<value>`. Shown while the adapter is connected; up to [MAX_OBD].
     */
    val obdValues: List<String> = emptyList(),
    val obdStyle: ObdClockStyle = ObdClockStyle.LINE,
    /** Names of the values next to them (else only value and unit). */
    val obdLabels: Boolean = true,
    /** Accent colour instead of white. */
    val accentColor: Boolean = false,
    /** Screen brightness while shown (0..1), or [KEEP_BRIGHTNESS]. */
    val brightness: Float = KEEP_BRIGHTNESS,
    /** Pause the media while shown, resume after (ivi's "mute and pause media and clock"). */
    val pauseMedia: Boolean = false,
    /** Moves the clock a little every minute (LCD image retention). */
    val shift: Boolean = true,
) {
    companion object {
        const val KEEP_BRIGHTNESS = -1f
        const val MAX_OBD = 6
        val IDLE_CHOICES = listOf(0, 1, 2, 5, 10, 30)
        val BRIGHTNESS_CHOICES = listOf(KEEP_BRIGHTNESS, 0.5f, 0.2f, 0.05f)
    }
}

class StandbyStore private constructor(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences("standby", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<StandbySettings> = _settings.asStateFlow()

    fun update(transform: (StandbySettings) -> StandbySettings) {
        val s = transform(_settings.value)
        prefs
            .edit()
            .putInt("idle", s.idleMinutes)
            .putString("style", s.style.name)
            .putBoolean("seconds", s.showSeconds)
            .putBoolean("date", s.showDate)
            .putBoolean("media", s.showMedia)
            .putBoolean("phone", s.showPhone)
            .putBoolean("car_battery", s.showCarBattery)
            .putString("obd_values", s.obdValues.joinToString("\n"))
            .putString("obd_style", s.obdStyle.name)
            .putBoolean("obd_labels", s.obdLabels)
            .putBoolean("accent", s.accentColor)
            .putFloat("brightness", s.brightness)
            .putBoolean("pause_media", s.pauseMedia)
            .putBoolean("shift", s.shift)
            .apply()
        _settings.value = s
    }

    fun reset() {
        prefs.edit().clear().apply()
        _settings.value = StandbySettings()
    }

    private fun load(): StandbySettings {
        val d = StandbySettings()
        return StandbySettings(
            idleMinutes = prefs.getInt("idle", d.idleMinutes),
            style = runCatching { ClockStyle.valueOf(prefs.getString("style", null)!!) }.getOrDefault(d.style),
            showSeconds = prefs.getBoolean("seconds", d.showSeconds),
            showDate = prefs.getBoolean("date", d.showDate),
            showMedia = prefs.getBoolean("media", d.showMedia),
            showPhone = prefs.getBoolean("phone", d.showPhone),
            showCarBattery = prefs.getBoolean("car_battery", d.showCarBattery),
            obdValues = prefs.getString("obd_values", "").orEmpty().split('\n').filter { it.isNotBlank() },
            obdStyle = runCatching { ObdClockStyle.valueOf(prefs.getString("obd_style", null)!!) }.getOrDefault(d.obdStyle),
            obdLabels = prefs.getBoolean("obd_labels", d.obdLabels),
            accentColor = prefs.getBoolean("accent", d.accentColor),
            brightness = prefs.getFloat("brightness", d.brightness),
            pauseMedia = prefs.getBoolean("pause_media", d.pauseMedia),
            shift = prefs.getBoolean("shift", d.shift),
        )
    }

    companion object {
        /** Opens the standby clock (exported: key mapping apps, LibreHU-service touch keys…). */
        const val ACTION_STANDBY_CLOCK = "org.librehu.action.STANDBY_CLOCK"

        @Volatile
        private var instance: StandbyStore? = null

        fun get(context: Context): StandbyStore =
            instance ?: synchronized(this) { instance ?: StandbyStore(context).also { instance = it } }
    }
}
