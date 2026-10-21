package org.librehu.launcher.ui

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.librehu.launcher.R
import org.librehu.launcher.speed.LimitSound
import org.librehu.launcher.speed.LimitSpeedSource
import org.librehu.launcher.speed.SpeedLimitManager
import org.librehu.launcher.speed.SpeedLimitSettings
import org.librehu.launcher.weather.WeatherRepo
import org.librehu.launcher.weather.WeatherSettings
import org.librehu.launcher.weather.WeatherWidget

/** Settings → Driving: weather at the car's position (Open-Meteo) and the road's speed limit (OpenStreetMap). */
@Composable
fun DrivingSettingsPage(actions: LauncherActions) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val weather = WeatherRepo.get(context)
    val w by weather.settings.collectAsStateWithLifecycle()
    val now by weather.now.collectAsStateWithLifecycle()
    val wMessage by weather.message.collectAsStateWithLifecycle()
    val limits = SpeedLimitManager.get(context)
    val l by limits.settings.collectAsStateWithLifecycle()
    val lst by limits.state.collectAsStateWithLifecycle()

    SettingSection(stringResource(R.string.weather_title))
    SettingSwitch(stringResource(R.string.weather_enable), stringResource(R.string.weather_enable_hint), w.enabled) { on ->
        if (on) actions.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        weather.update { it.copy(enabled = on) }
    }
    if (w.enabled) {
        val n = now
        SettingHint(
            when {
                n != null ->
                    WeatherRepo.symbol(n.code, n.day) + "  " + WeatherRepo.temp(n.tempC, w.fahrenheit) + "  " +
                        stringResource(WeatherWidget.weatherLabel(n.code)) +
                        (n.feelsC?.let { "  ·  " + stringResource(R.string.weather_feels, WeatherRepo.temp(it, w.fahrenheit)) } ?: "")
                wMessage == "position" -> stringResource(R.string.weather_no_position)
                wMessage.isNotEmpty() -> stringResource(R.string.weather_error, wMessage)
                else -> stringResource(R.string.weather_waiting)
            },
        )
        SettingSwitch(stringResource(R.string.weather_status_bar), null, w.statusBar) { on -> weather.update { it.copy(statusBar = on) } }
        SettingChoices(listOf(false to "°C", true to "°F"), w.fahrenheit) { f -> weather.update { it.copy(fahrenheit = f) } }
        SettingSection(stringResource(R.string.weather_refresh))
        SettingChoices(WeatherSettings.REFRESH_CHOICES.map { it to stringResource(R.string.minutes_short, it) }, w.refreshMinutes) { m ->
            weather.update { it.copy(refreshMinutes = m) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingChoice(stringResource(R.string.weather_refresh_now), false) { weather.refresh() }
        }
    }
    SettingHint(stringResource(R.string.weather_credit))

    SettingSection(stringResource(R.string.limit_title))
    SettingSwitch(stringResource(R.string.limit_enable), stringResource(R.string.limit_enable_hint), l.enabled) { on ->
        if (on) actions.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        limits.update { it.copy(enabled = on) }
    }
    if (l.enabled) {
        SettingHint(
            when {
                lst.message.isNotEmpty() -> stringResource(R.string.limit_error, lst.message)
                lst.kmh != null -> stringResource(R.string.limit_now, lst.kmh!!, lst.road.ifEmpty { "—" })
                else -> stringResource(R.string.limit_unknown)
            },
        )
        SettingSwitch(stringResource(R.string.limit_status_bar), null, l.statusBar) { on -> limits.update { it.copy(statusBar = on) } }
        SettingSwitch(stringResource(R.string.limit_alert), stringResource(R.string.limit_alert_hint), l.alert) { on -> limits.update { it.copy(alert = on) } }
        if (l.alert) {
            SettingSection(stringResource(R.string.limit_tolerance))
            SettingChoices(listOf(0, 3, 5, 7, 10).map { it to "+$it km/h" }, l.toleranceKmh) { v -> limits.update { it.copy(toleranceKmh = v) } }
            SettingChoices(listOf(0, 5, 10).map { it to "+$it %" }, l.tolerancePercent) { v -> limits.update { it.copy(tolerancePercent = v) } }
            SettingHint(stringResource(R.string.limit_tolerance_hint))
            SettingSection(stringResource(R.string.limit_delay))
            SettingChoices(listOf(0, 2, 5, 10).map { it to "$it s" }, l.delaySeconds) { v -> limits.update { it.copy(delaySeconds = v) } }
            SettingSection(stringResource(R.string.limit_sound))
            SettingChoices(
                listOf(
                    LimitSound.BEEP to stringResource(R.string.limit_sound_beep),
                    LimitSound.DOUBLE to stringResource(R.string.limit_sound_double),
                    LimitSound.TRIPLE to stringResource(R.string.limit_sound_triple),
                    LimitSound.CHIME to stringResource(R.string.limit_sound_chime),
                    LimitSound.ALARM to stringResource(R.string.limit_sound_alarm),
                    LimitSound.NOTIFICATION to stringResource(R.string.limit_sound_notification),
                    LimitSound.NONE to stringResource(R.string.limit_sound_none),
                ),
                l.sound,
            ) { v -> limits.update { it.copy(sound = v) } }
            SettingChoices(listOf(30, 50, 80, 100).map { it to "$it %" }, l.volume) { v -> limits.update { it.copy(volume = v) } }
            Row { SettingChoice(stringResource(R.string.limit_sound_test), false) { limits.test() } }
            SettingSection(stringResource(R.string.limit_repeat))
            SettingChoices(
                listOf(0 to stringResource(R.string.limit_repeat_once)) + listOf(5, 10, 30).map { it to stringResource(R.string.limit_repeat_every, it) },
                l.repeatSeconds,
            ) { v -> limits.update { it.copy(repeatSeconds = v) } }
            SettingSwitch(stringResource(R.string.limit_change_chime), stringResource(R.string.limit_change_chime_hint), l.changeChime) { on ->
                limits.update { it.copy(changeChime = on) }
            }
        }
        SettingSection(stringResource(R.string.limit_source))
        SettingChoices(
            listOf(
                LimitSpeedSource.AUTO to stringResource(R.string.limit_source_auto),
                LimitSpeedSource.GPS to stringResource(R.string.speed_gps),
                LimitSpeedSource.OBD to stringResource(R.string.speed_obd),
            ),
            l.source,
        ) { v -> limits.update { it.copy(source = v) } }
        SettingHint(stringResource(R.string.limit_source_hint))
        SettingSection(stringResource(R.string.limit_server))
        SettingChoices(SpeedLimitSettings.SERVERS.map { it to it.removePrefix("https://").substringBefore('/') }, l.server) { v ->
            limits.update { it.copy(server = v) }
        }
    }
    SettingHint(stringResource(R.string.limit_credit))
}
