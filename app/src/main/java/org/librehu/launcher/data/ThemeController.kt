package org.librehu.launcher.data

import android.app.UiModeManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.librehu.launcher.ui.CarColors
import org.librehu.launcher.ui.CarPalette
import java.util.Calendar

/**
 * Computes the effective light / dark theme (setting, headlights, or time of day when the head unit does not
 * report the headlights), applies it to the launcher, publishes it to the other LibreHU apps and, when allowed,
 * switches the system night mode so that every app following Android's dark theme changes too.
 */
class ThemeController(
    private val context: Context,
    private val store: ThemeStore,
    private val headlights: StateFlow<Boolean?>,
) {
    private val hour = MutableStateFlow(currentHour())

    private val _dark = MutableStateFlow(true)
    val dark: StateFlow<Boolean> = _dark.asStateFlow()

    /** False when the system night mode could not be changed (not a privileged install). */
    private val _systemWideAllowed = MutableStateFlow(true)
    val systemWideAllowed: StateFlow<Boolean> = _systemWideAllowed.asStateFlow()

    fun start(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                delay(60_000)
                hour.value = currentHour()
            }
        }
        scope.launch {
            combine(store.settings, headlights, hour) { s, lights, h ->
                val dark =
                    when (s.mode) {
                        ThemeMode.LIGHT -> false
                        ThemeMode.DARK -> true
                        ThemeMode.AUTO -> lights ?: (h >= NIGHT_FROM || h < NIGHT_TO)
                    }
                Triple(dark, s, s.hasWallpaper)
            }.distinctUntilChanged().collect { (dark, s, wallpaper) ->
                _dark.value = dark
                val palette = CarPalette.of(dark, s.accent, translucent = wallpaper)
                CarColors.palette = palette
                ThemeProvider.publish(context, dark, argb(palette))
                if (s.systemWide) setSystemNightMode(dark)
            }
        }
    }

    private fun argb(p: CarPalette): Int {
        val c = p.accent
        return android.graphics.Color.argb(
            (c.alpha * 255).toInt(),
            (c.red * 255).toInt(),
            (c.green * 255).toInt(),
            (c.blue * 255).toInt(),
        )
    }

    private fun setSystemNightMode(dark: Boolean) {
        val ui = context.getSystemService(UiModeManager::class.java)
        val mode = if (dark) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO
        try {
            ui.nightMode = mode
            // Without MODIFY_DAY_NIGHT_MODE the call is ignored when the night mode is locked: check it.
            _systemWideAllowed.value = ui.nightMode == mode
        } catch (e: SecurityException) {
            _systemWideAllowed.value = false
            Log.i("LibreHU-Launcher", "setNightMode: ${e.message}")
        }
    }

    private fun currentHour() = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    private companion object {
        const val NIGHT_FROM = 19
        const val NIGHT_TO = 7
    }
}
