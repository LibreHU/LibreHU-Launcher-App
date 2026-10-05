package org.librehu.launcher.standby

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ClockStyle { DIGITAL, ANALOG }

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
