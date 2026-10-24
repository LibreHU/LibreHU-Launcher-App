package org.librehu.launcher.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.librehu.launcher.R
import org.librehu.launcher.obd.ObdLive
import org.librehu.launcher.obd.ObdLiveState
import java.util.Locale

private val Red = Color(0xFFF44336)
private val Amber = Color(0xFFF9AB00)
private val Green = Color(0xFF34A853)

/** Values of LibreHU-service's OBD pages: label, unit, decimals. */
private val PIDS =
    linkedMapOf(
        "SPEED" to Triple(R.string.obd_speed, "km/h", 0),
        "RPM" to Triple(R.string.obd_rpm, "rpm", 0),
        "COOLANT_TEMP" to Triple(R.string.obd_coolant, "°C", 0),
        "OIL_TEMP" to Triple(R.string.obd_oil, "°C", 0),
        "INTAKE_TEMP" to Triple(R.string.obd_intake_temp, "°C", 0),
        "AMBIENT_TEMP" to Triple(R.string.obd_ambient, "°C", 0),
        "ENGINE_LOAD" to Triple(R.string.obd_load, "%", 0),
        "THROTTLE" to Triple(R.string.obd_throttle, "%", 0),
        "FUEL_LEVEL" to Triple(R.string.obd_fuel, "%", 0),
        "FUEL_RATE" to Triple(R.string.obd_fuel_rate, "L/h", 1),
        "MAF" to Triple(R.string.obd_maf, "g/s", 1),
        "INTAKE_PRESSURE" to Triple(R.string.obd_intake_pressure, "kPa", 0),
        "BAROMETRIC" to Triple(R.string.obd_barometric, "kPa", 0),
        "TIMING_ADVANCE" to Triple(R.string.obd_timing, "°", 1),
        "MODULE_VOLTAGE" to Triple(R.string.obd_module_voltage, "V", 1),
        "RUN_TIME" to Triple(R.string.obd_run_time, "s", 0),
        "DISTANCE_MIL" to Triple(R.string.obd_distance_mil, "km", 0),
        "DISTANCE_SINCE_CLEAR" to Triple(R.string.obd_distance_clear, "km", 0),
    )

private fun fmt(
    v: Double,
    decimals: Int,
) = String.format(Locale.getDefault(), "%.${decimals}f", v)

/** Starts [ObdLive] while shown. */
@Composable
fun rememberObdLive(): ObdLiveState {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        ObdLive.start(context)
        onDispose { ObdLive.stop(context) }
    }
    val st by ObdLive.state.collectAsStateWithLifecycle()
    return st
}

/**
 * Full screen OBD (like the tyres screen): the car battery on the left (voltage, charge, charging), speed and RPM in
 * big, then every value LibreHU-service reads, standard ones and the manufacturer values of its car (ddt4all). The
 * values and the car are chosen in LibreHU-service → OBD.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ObdScreen() {
    val context = LocalContext.current
    val st = rememberObdLive()
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier
                .weight(0.9f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(28.dp))
                .background(CarColors.Surface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.obd_title), color = CarColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Text(
                stringResource(if (st.connected) R.string.obd_connected else R.string.obd_not_connected),
                color = if (st.connected) CarColors.TextDim else Red,
                fontSize = 14.sp,
            )
            BatteryPanel(st, Modifier.weight(1f).fillMaxWidth())
            SettingChoice(stringResource(R.string.obd_settings), false) { openServiceObd(context) }
        }
        Column(
            Modifier
                .weight(2f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(28.dp))
                .background(CarColors.Surface)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!st.connected) {
                Text(stringResource(R.string.obd_hint), color = CarColors.TextDim, fontSize = 16.sp)
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (k in listOf("SPEED", "RPM")) {
                    val (label, unit, d) = PIDS.getValue(k)
                    BigValue(stringResource(label), st.values[k]?.let { fmt(it, d) } ?: "—", unitText(unit), Modifier.weight(1f))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((k, p) in PIDS) {
                    if (k == "SPEED" || k == "RPM") continue
                    val v = st.values[k] ?: continue
                    ValueTile(stringResource(p.first), fmt(v, p.third) + " " + unitText(p.second))
                }
            }
            if (st.car.isNotEmpty()) {
                Text(stringResource(R.string.obd_car_values), color = CarColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((key, text) in st.car.toSortedMap()) {
                        ValueTile(key.substringAfter('/'), text, key.substringBefore('/'))
                    }
                }
            }
        }
    }
}

@Composable
private fun unitText(unit: String) = if (unit == "rpm") stringResource(R.string.obd_rpm_unit) else unit

@Composable
private fun BatteryPanel(
    st: ObdLiveState,
    modifier: Modifier,
) {
    val v = st.battery
    val (label, color) = batteryState(st)
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(CarColors.SurfaceHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.obd_battery), color = CarColors.TextDim, fontSize = 16.sp)
        Text(v?.let { fmt(it, 1) + " V" } ?: "—", color = CarColors.Text, fontSize = 56.sp, fontWeight = FontWeight.Light)
        if (label != null) Text(label, color = color, fontSize = 18.sp)
        val percent = st.batteryPercent
        if (percent != null) {
            Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(CarColors.Surface)) {
                Box(Modifier.fillMaxWidth(percent / 100f).fillMaxHeight().clip(RoundedCornerShape(7.dp)).background(color))
            }
        }
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.obd_battery_hint), color = CarColors.TextDim, fontSize = 13.sp)
    }
}

/** Text and colour of the battery state, null text when unknown. */
@Composable
fun batteryState(st: ObdLiveState): Pair<String?, Color> =
    when (st.batteryStatus) {
        "CHARGING" -> stringResource(R.string.obd_bat_charging) to Green
        "NOT_CHARGING" -> stringResource(R.string.obd_bat_not_charging) to Red
        "OVERVOLTAGE" -> stringResource(R.string.obd_bat_over) to Red
        "ENGINE_OFF" -> {
            val p = st.batteryPercent ?: 0
            stringResource(R.string.obd_bat_engine_off, p) to
                when {
                    p < 25 -> Red
                    p < 50 -> Amber
                    else -> Green
                }
        }

        else -> null to CarColors.TextDim
    }

/** One line for the standby clock and the lock screen: "Battery 12.4 V · 75 %", nothing without the adapter. */
@Composable
fun CarBatteryLine(textSize: Int = 20) {
    val st = rememberObdLive()
    val v = st.battery ?: return
    val (label, color) = batteryState(st)
    Row {
        Text(stringResource(R.string.obd_battery) + "  " + fmt(v, 1) + " V", color = Color(0xFF9AA0A6), fontSize = textSize.sp)
        if (label != null) {
            Spacer(Modifier.width(10.dp))
            Text("·  $label", color = color, fontSize = textSize.sp)
        }
    }
}

@Composable
private fun BigValue(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier,
) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(CarColors.SurfaceHigh).padding(16.dp)) {
        Text(label, color = CarColors.TextDim, fontSize = 16.sp)
        Row {
            Text(value, color = CarColors.Text, fontSize = 64.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(8.dp))
            Text(unit, color = CarColors.TextDim, fontSize = 18.sp, modifier = Modifier.padding(top = 40.dp))
        }
    }
}

@Composable
private fun ValueTile(
    label: String,
    value: String,
    sub: String? = null,
) {
    Column(
        Modifier.width(200.dp).clip(RoundedCornerShape(18.dp)).background(CarColors.SurfaceHigh).padding(14.dp),
    ) {
        Text(label, color = CarColors.TextDim, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(value, color = CarColors.Text, fontSize = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, color = CarColors.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** LibreHU-service, where the adapter, the values and the car are chosen. */
fun openServiceObd(context: Context) {
    context.packageManager.getLaunchIntentForPackage(SERVICE)?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

private const val SERVICE = "org.librehu.service"

/** Values that can go under the clock: the standard ones, then the manufacturer values received now. */
@Composable
fun obdClockChoices(st: ObdLiveState): List<Pair<String, String>> =
    PIDS.map { (k, p) -> k to stringResource(p.first) } + st.car.keys.sorted().map { "car:$it" to it.substringAfter('/') }

/** Name of a value chosen for the clock. */
@Composable
fun obdClockName(key: String): String =
    if (key.startsWith("car:")) key.removePrefix("car:").substringAfter('/') else PIDS[key]?.let { stringResource(it.first) } ?: key

/**
 * The chosen OBD values under the standby clock / on the lock screen ([keys] in order), as one line or as tiles;
 * nothing while the adapter is not connected. A value not received now shows "—".
 */
@Composable
fun ObdClockValues(
    keys: List<String>,
    tiles: Boolean,
    labels: Boolean,
    textSize: Int = 20,
) {
    if (keys.isEmpty()) return
    val st = rememberObdLive()
    if (!st.connected) return
    val dim = Color(0xFF9AA0A6)
    val items =
        keys.map { k ->
            val name = obdClockName(k)
            val value =
                if (k.startsWith("car:")) {
                    st.car[k.removePrefix("car:")] ?: "—"
                } else {
                    val p = PIDS[k]
                    val v = st.values[k]
                    if (p == null || v == null) "—" else fmt(v, p.third) + " " + unitText(p.second)
                }
            name to value
        }
    if (!tiles) {
        Text(
            items.joinToString("   ·   ") { (n, v) -> if (labels) "$n  $v" else v },
            color = dim,
            fontSize = textSize.sp,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        for ((n, v) in items) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF16181C))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(v, color = Color(0xFFE8EAED), fontSize = (textSize + 6).sp, maxLines = 1)
                if (labels) Text(n, color = dim, fontSize = (textSize - 6).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

