package org.librehu.launcher.lock

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LockLook { CLOCK, BLACK }

data class LockSettings(
    /** Digits to unlock, empty = slide to unlock. */
    val pin: String = "",
    val look: LockLook = LockLook.CLOCK,
    /** Pause the media while locked (resumed after if it was playing). */
    val pauseMedia: Boolean = false,
    /** With no PIN, the lock action again (power key) unlocks. */
    val toggle: Boolean = true,
)

/**
 * Lock screen of the launcher (not Android's keyguard): covers the screen until unlocked, comes back over the home
 * screen (the launcher re-opens it on Home). Opened by the power menu or `org.librehu.action.LOCK` (front panel touch
 * keys of LibreHU-service, key mapping apps).
 */
class LockStore private constructor(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences("lock", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<LockSettings> = _settings.asStateFlow()

    /** Survives a restart of the process (the screen must stay locked). */
    var locked: Boolean
        get() = prefs.getBoolean("locked", false)
        set(value) = prefs.edit().putBoolean("locked", value).apply()

    fun update(transform: (LockSettings) -> LockSettings) {
        val s = transform(_settings.value)
        prefs
            .edit()
            .putString("pin", s.pin)
            .putString("look", s.look.name)
            .putBoolean("pause_media", s.pauseMedia)
            .putBoolean("toggle", s.toggle)
            .apply()
        _settings.value = s
    }

    fun reset() {
        prefs.edit().clear().apply()
        _settings.value = LockSettings()
    }

    private fun load(): LockSettings {
        val d = LockSettings()
        return LockSettings(
            pin = prefs.getString("pin", null).orEmpty().filter { it.isDigit() },
            look = runCatching { LockLook.valueOf(prefs.getString("look", null)!!) }.getOrDefault(d.look),
            pauseMedia = prefs.getBoolean("pause_media", d.pauseMedia),
            toggle = prefs.getBoolean("toggle", d.toggle),
        )
    }

    companion object {
        const val ACTION_LOCK = "org.librehu.action.LOCK"

        @Volatile
        private var instance: LockStore? = null

        fun get(context: Context): LockStore = instance ?: synchronized(this) { instance ?: LockStore(context).also { instance = it } }
    }
}
