package org.librehu.launcher.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.librehu.launcher.R
import org.librehu.launcher.system.RegionSettings
import java.util.Calendar

/**
 * Language, time zone, date and time (first start assistant and Settings → Language and time). Set as root; without
 * root each choice opens Android's own screen.
 */
@Composable
fun RegionSettingsPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Read again after each change (and every second for the clock).
    var revision by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val language = remember(revision) { RegionSettings.currentLanguage() }
    val zone = remember(revision, now / 60_000) { RegionSettings.currentZone() }
    val autoTime = remember(revision) { RegionSettings.autoTime(context) }
    val autoZone = remember(revision) { RegionSettings.autoZone(context) }
    val h24 = remember(revision) { RegionSettings.is24h(context) }
    val noRoot = stringResource(R.string.region_no_root)
    val done = stringResource(R.string.region_done)

    fun apply(
        fallback: String?,
        block: () -> Boolean,
    ) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { block() }
            status = if (ok) done else noRoot
            if (!ok && fallback != null) openAndroid(context, fallback)
            revision++
        }
    }

    SettingHint(stringResource(R.string.region_hint))
    status?.let { SettingHint(it) }

    SettingSection(stringResource(R.string.region_language))
    SettingHint(stringResource(R.string.region_current, RegionSettings.languageLabel(language)))
    SettingChoices(
        RegionSettings.LANGUAGES.map { it to RegionSettings.languageLabel(it) } +
            listOf(OTHER to stringResource(R.string.region_other)),
        RegionSettings.LANGUAGES.firstOrNull { it.equals(language, ignoreCase = true) } ?: "",
    ) { tag ->
        if (tag == OTHER) {
            openAndroid(context, android.provider.Settings.ACTION_LOCALE_SETTINGS)
        } else {
            apply(android.provider.Settings.ACTION_LOCALE_SETTINGS) { RegionSettings.setLanguage(context, tag) }
        }
    }

    SettingSection(stringResource(R.string.region_zone))
    SettingSwitch(stringResource(R.string.region_auto_zone), stringResource(R.string.region_auto_zone_hint), autoZone) { on ->
        apply(android.provider.Settings.ACTION_DATE_SETTINGS) { RegionSettings.setAutoZone(on) }
    }
    SettingHint(stringResource(R.string.region_current, RegionSettings.zoneLabel(zone)))
    SettingChoices(
        RegionSettings.TIME_ZONES.map { it to RegionSettings.zoneLabel(it) } + listOf(OTHER to stringResource(R.string.region_other)),
        if (zone in RegionSettings.TIME_ZONES) zone else "",
    ) { id ->
        if (id == OTHER) {
            openAndroid(context, android.provider.Settings.ACTION_DATE_SETTINGS)
        } else {
            apply(android.provider.Settings.ACTION_DATE_SETTINGS) { RegionSettings.setZone(context, id) }
        }
    }

    SettingSection(stringResource(R.string.region_time))
    SettingHint(
        stringResource(
            R.string.region_now,
            DateFormat.getLongDateFormat(context).format(now) + "  " + DateFormat.getTimeFormat(context).format(now),
        ),
    )
    SettingSwitch(stringResource(R.string.region_auto_time), stringResource(R.string.region_auto_time_hint), autoTime) { on ->
        apply(android.provider.Settings.ACTION_DATE_SETTINGS) { RegionSettings.setAutoTime(on) }
    }
    SettingSwitch(stringResource(R.string.region_24h), null, h24) { on ->
        apply(null) { RegionSettings.set24h(context, on) }
    }
    SettingChoices(
        listOf(
            DATE to stringResource(R.string.region_set_date),
            TIME to stringResource(R.string.region_set_time),
        ),
        "",
    ) { what ->
        val c = Calendar.getInstance()
        if (what == DATE) {
            DatePickerDialog(context, { _, y, m, d ->
                c.set(y, m, d)
                apply(android.provider.Settings.ACTION_DATE_SETTINGS) { RegionSettings.setTime(context, c.timeInMillis) }
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        } else {
            TimePickerDialog(context, { _, h, min ->
                c.set(Calendar.HOUR_OF_DAY, h)
                c.set(Calendar.MINUTE, min)
                c.set(Calendar.SECOND, 0)
                c.set(Calendar.MILLISECOND, 0)
                apply(android.provider.Settings.ACTION_DATE_SETTINGS) { RegionSettings.setTime(context, c.timeInMillis) }
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), RegionSettings.is24h(context)).show()
        }
    }
    SettingHint(stringResource(R.string.region_time_hint))
}

private const val OTHER = "other"
private const val DATE = "date"
private const val TIME = "time"

private fun openAndroid(
    context: Context,
    action: String,
) {
    runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
