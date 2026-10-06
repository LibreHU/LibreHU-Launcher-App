package org.librehu.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.librehu.launcher.R
import org.librehu.launcher.tpms.PressureUnit
import org.librehu.launcher.tpms.TpmsManager
import org.librehu.launcher.tpms.TpmsSettings
import org.librehu.launcher.tpms.TpmsState
import org.librehu.launcher.tpms.TyrePos
import org.librehu.launcher.tpms.TyreReading
import org.librehu.launcher.tpms.alarms
import kotlin.math.roundToInt

private val AlarmRed = Color(0xFFF44336)
private val OkGreen = Color(0xFF4CAF50)

/** Readings older than this are shown as stale (sensors report every few seconds while driving). */
private const val STALE_MS = 10 * 60 * 1000L

/** Compact dashboard card: four tyres around a car outline. */
@Composable
fun TpmsCard(
    modifier: Modifier,
    onOpen: () -> Unit,
) {
    val tpms = TpmsManager.get(LocalContext.current)
    val st by tpms.state.collectAsStateWithLifecycle()
    val s by tpms.settings.collectAsStateWithLifecycle()
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(28.dp))
                .background(CarColors.Surface)
                .clickable(onClick = onOpen)
                .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.tpms_title),
                color = CarColors.Text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(if (st.connected) R.string.tpms_connected else R.string.tpms_disconnected),
                color = if (st.connected) CarColors.TextDim else AlarmRed,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                MiniTyre(st.tyres[TyrePos.FL], s)
                MiniTyre(st.tyres[TyrePos.RL], s)
            }
            CarOutline(Modifier.width(44.dp).fillMaxHeight(0.85f))
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.End,
            ) {
                MiniTyre(st.tyres[TyrePos.FR], s, end = true)
                MiniTyre(st.tyres[TyrePos.RR], s, end = true)
            }
        }
    }
}

@Composable
private fun MiniTyre(
    r: TyreReading?,
    s: TpmsSettings,
    end: Boolean = false,
) {
    val alarm = r != null && r.alarms(s).isNotEmpty()
    Column(horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        Text(
            r?.let { TpmsManager.formatPressure(it.kpa, s.unit) } ?: "—",
            color = if (alarm) AlarmRed else CarColors.Text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(r?.let { TpmsManager.formatTemp(it.celsius, s.fahrenheit) } ?: "", color = CarColors.TextDim, fontSize = 13.sp)
    }
}

@Composable
private fun CarOutline(modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .border(2.dp, CarColors.TextDim, RoundedCornerShape(18.dp)),
    )
}

/** Full TPMS screen: tyres, sensor ids, pairing, swaps and settings. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TpmsScreen() {
    val tpms = TpmsManager.get(LocalContext.current)
    val st by tpms.state.collectAsStateWithLifecycle()
    val s by tpms.settings.collectAsStateWithLifecycle()
    var swapFirst by remember { mutableStateOf<TyrePos?>(null) }
    var tab by remember { mutableStateOf(if (s.bleEnabled && !st.connected) TpmsTab.BLE else TpmsTab.USB) }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Car view.
        Column(
            Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(28.dp))
                .background(CarColors.Surface)
                .padding(20.dp),
        ) {
            Text(stringResource(R.string.tpms_title), color = CarColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Text(
                when {
                    st.connected -> stringResource(R.string.tpms_receiver, st.device)
                    s.bleEnabled -> stringResource(R.string.tpms_ble_status, st.bleIds.size)
                    else -> stringResource(R.string.tpms_plug_hint)
                },
                color = if (st.connected || s.bleEnabled) CarColors.TextDim else AlarmRed,
                fontSize = 14.sp,
            )
            if (st.tyres.values.any { it.alarms(s).isNotEmpty() } && s.alarmSound && !st.silenced) {
                Spacer(Modifier.height(6.dp))
                Pill(stringResource(R.string.tpms_silence), true) { tpms.silence() }
            }
            if (st.message.isNotEmpty()) Text(st.message, color = CarColors.Accent, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                    TyreTile(TyrePos.FL, st.tyres[TyrePos.FL], st.ids[TyrePos.FL], s, st.pairing == TyrePos.FL, swapFirst == TyrePos.FL)
                    TyreTile(TyrePos.RL, st.tyres[TyrePos.RL], st.ids[TyrePos.RL], s, st.pairing == TyrePos.RL, swapFirst == TyrePos.RL)
                }
                CarOutline(Modifier.width(90.dp).fillMaxHeight(0.8f).padding(horizontal = 12.dp))
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                    TyreTile(TyrePos.FR, st.tyres[TyrePos.FR], st.ids[TyrePos.FR], s, st.pairing == TyrePos.FR, swapFirst == TyrePos.FR)
                    TyreTile(TyrePos.RR, st.tyres[TyrePos.RR], st.ids[TyrePos.RR], s, st.pairing == TyrePos.RR, swapFirst == TyrePos.RR)
                }
            }
            if (s.showSpare) {
                TyreTile(
                    TyrePos.SPARE,
                    st.tyres[TyrePos.SPARE],
                    st.ids[TyrePos.SPARE],
                    s,
                    st.pairing == TyrePos.SPARE,
                    swapFirst == TyrePos.SPARE,
                )
            }
        }
        // Actions and settings.
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(28.dp))
                .background(CarColors.Surface)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TpmsTab.entries.forEach { t -> Pill(stringResource(t.label), tab == t) { tab = t } }
            }
            when (tab) {
                TpmsTab.USB -> {
                    Section(stringResource(R.string.tpms_pairing))
                    Hint(stringResource(R.string.tpms_pairing_hint))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TyrePos.entries.filter { it != TyrePos.SPARE || s.showSpare }.forEach { p ->
                            Pill(stringResource(TpmsManager.posLabel(p)), st.pairing == p) {
                                if (st.pairing ==
                                    p
                                ) {
                                    tpms.stopPairing()
                                } else {
                                    tpms.pair(p)
                                }
                            }
                        }
                        Pill(stringResource(R.string.tpms_query_ids), false) { tpms.queryIds() }
                    }
                    Section(stringResource(R.string.tpms_swap))
                    Hint(stringResource(R.string.tpms_swap_hint))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TyrePos.entries.filter { it != TyrePos.SPARE || s.showSpare }.forEach { p ->
                            Pill(stringResource(TpmsManager.posLabel(p)), swapFirst == p) {
                                val first = swapFirst
                                swapFirst =
                                    when {
                                        first == null -> {
                                            p
                                        }

                                        first == p -> {
                                            null
                                        }

                                        else -> {
                                            tpms.swap(first, p)
                                            null
                                        }
                                    }
                            }
                        }
                    }
                }

                TpmsTab.BLE -> {
                    BleSensors(tpms, st, s)
                }

                TpmsTab.SETTINGS -> {
                    Section(stringResource(R.string.tpms_thresholds))
                    Threshold(
                        stringResource(R.string.tpms_low_threshold),
                        s.lowKpa,
                        100f..300f,
                        TpmsManager.formatPressure(s.lowKpa, s.unit),
                    ) { v ->
                        tpms.updateSettings { it.copy(lowKpa = v) }
                    }
                    Threshold(
                        stringResource(R.string.tpms_high_threshold),
                        s.highKpa,
                        200f..450f,
                        TpmsManager.formatPressure(s.highKpa, s.unit),
                    ) { v ->
                        tpms.updateSettings { it.copy(highKpa = v) }
                    }
                    Threshold(
                        stringResource(R.string.tpms_hot_threshold),
                        s.highCelsius,
                        50f..100f,
                        TpmsManager.formatTemp(s.highCelsius, s.fahrenheit),
                    ) { v ->
                        tpms.updateSettings { it.copy(highCelsius = v) }
                    }
                    Section(stringResource(R.string.tpms_display))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PressureUnit.entries.forEach { u ->
                            Pill(u.name.lowercase().replace("kpa", "kPa"), s.unit == u) { tpms.updateSettings { it.copy(unit = u) } }
                        }
                        Pill("°C", !s.fahrenheit) { tpms.updateSettings { it.copy(fahrenheit = false) } }
                        Pill("°F", s.fahrenheit) { tpms.updateSettings { it.copy(fahrenheit = true) } }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(
                            stringResource(R.string.tpms_show_spare),
                            s.showSpare,
                        ) { tpms.updateSettings { it.copy(showSpare = !it.showSpare) } }
                        Pill(stringResource(R.string.tpms_alarms), s.alarms) { tpms.updateSettings { it.copy(alarms = !it.alarms) } }
                        Pill(
                            stringResource(R.string.tpms_on_dashboard),
                            s.enabled,
                        ) { tpms.updateSettings { it.copy(enabled = !it.enabled) } }
                    }
                    Section(stringResource(R.string.tpms_sound))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(
                            stringResource(R.string.tpms_sound_on),
                            s.alarmSound,
                        ) { tpms.updateSettings { it.copy(alarmSound = !it.alarmSound) } }
                        Pill(stringResource(R.string.tpms_sound_test), false) { tpms.beep() }
                    }
                    Hint(stringResource(R.string.tpms_sound_repeat))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TpmsSettings.REPEAT_CHOICES.forEach { r ->
                            Pill(if (r == 0) stringResource(R.string.tpms_sound_once) else "$r s", s.soundRepeatSec == r) {
                                tpms.updateSettings { it.copy(soundRepeatSec = r) }
                            }
                        }
                    }
                    Section(stringResource(R.string.tpms_refresh))
                    Hint(stringResource(R.string.tpms_refresh_hint))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TpmsSettings.REFRESH_CHOICES.forEach { r ->
                            Pill(if (r < 60) "$r s" else "1 min", s.refreshSec == r) { tpms.updateSettings { it.copy(refreshSec = r) } }
                        }
                    }
                }
            }
        }
    }
}

private enum class TpmsTab(
    val label: Int,
) {
    USB(R.string.tpms_tab_usb),
    BLE(R.string.tpms_tab_ble),
    SETTINGS(R.string.tpms_tab_settings),
}

/** Bluetooth LE sensors: on / off, sensors heard (format, values, signal), assignment to a tyre. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BleSensors(
    tpms: TpmsManager,
    st: TpmsState,
    s: TpmsSettings,
) {
    Section(stringResource(R.string.tpms_ble_title))
    Hint(stringResource(R.string.tpms_ble_hint))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(stringResource(R.string.tpms_ble_enable), s.bleEnabled) { tpms.updateSettings { it.copy(bleEnabled = !it.bleEnabled) } }
        if (st.bleSeen.isNotEmpty()) Pill(stringResource(R.string.tpms_ble_clear), false) { tpms.forgetSeen() }
    }
    if (st.bleError.isNotEmpty() && s.bleEnabled) Text(st.bleError, color = AlarmRed, fontSize = 14.sp)
    if (!s.bleEnabled) return
    if (st.bleSeen.isEmpty()) Hint(stringResource(R.string.tpms_ble_none))
    val positions = TyrePos.entries.filter { it != TyrePos.SPARE || s.showSpare }
    for (r in st.bleSeen.values.sortedByDescending { it.rssi }) {
        val assigned =
            st.bleIds.entries
                .firstOrNull { it.value == r.id }
                ?.key
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(CarColors.SurfaceHigh)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("${r.format.name} · ${r.id}", color = CarColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            val battery = r.batteryVolts?.let { "%.1f V".format(it) } ?: r.batteryPercent?.let { "$it %" } ?: "—"
            val age = ((System.currentTimeMillis() - r.time) / 1000).coerceAtLeast(0)
            Text(
                "${TpmsManager.formatPressure(
                    r.kpa,
                    s.unit,
                )} · ${TpmsManager.formatTemp(r.celsius, s.fahrenheit)} · 🔋 $battery · ${r.rssi} dBm · ${age}s",
                color = if (r.alarm) AlarmRed else CarColors.TextDim,
                fontSize = 14.sp,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                positions.forEach { p ->
                    Pill(stringResource(TpmsManager.posLabel(p)), assigned == p) { tpms.assignBle(p, if (assigned == p) null else r.id) }
                }
            }
        }
    }
}

@Composable
private fun TyreTile(
    pos: TyrePos,
    r: TyreReading?,
    id: String?,
    s: TpmsSettings,
    pairing: Boolean,
    selected: Boolean,
) {
    val context = LocalContext.current
    val alarms = r?.alarms(s).orEmpty()
    val stale = r != null && System.currentTimeMillis() - r.time > STALE_MS
    val accent =
        when {
            pairing || selected -> CarColors.Accent
            alarms.isNotEmpty() -> AlarmRed
            r != null && !stale -> OkGreen
            else -> CarColors.SurfaceHigh
        }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CarColors.SurfaceHigh.copy(alpha = 0.6f))
            .border(3.dp, accent, RoundedCornerShape(20.dp))
            .padding(12.dp),
    ) {
        Text(stringResource(TpmsManager.posLabel(pos)), color = CarColors.TextDim, fontSize = 14.sp)
        Text(
            r?.let { TpmsManager.formatPressure(it.kpa, s.unit) } ?: "—",
            color = if (alarms.isNotEmpty()) AlarmRed else CarColors.Text,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
        )
        val details =
            listOfNotNull(
                r?.let { TpmsManager.formatTemp(it.celsius, s.fahrenheit) },
                r?.battery?.let { "%.1f V".format(it) },
                id,
            ).joinToString("  ·  ")
        if (details.isNotEmpty()) Text(details, color = CarColors.TextDim, fontSize = 13.sp)
        when {
            pairing -> Text(stringResource(R.string.tpms_pairing_now), color = CarColors.Accent, fontSize = 13.sp)
            alarms.isNotEmpty() -> Text(alarms.joinToString(", ") { context.getString(it) }, color = AlarmRed, fontSize = 13.sp)
            stale -> Text(stringResource(R.string.tpms_stale), color = CarColors.TextDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Threshold(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    shown: String,
    onChange: (Int) -> Unit,
) {
    Column {
        Row {
            Text(label, color = CarColors.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(shown, color = CarColors.Accent, fontSize = 16.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(((it / 5).roundToInt()) * 5) },
            valueRange = range,
            colors =
                SliderDefaults.colors(
                    thumbColor = CarColors.Accent,
                    activeTrackColor = CarColors.Accent,
                    inactiveTrackColor = CarColors.SurfaceHigh,
                ),
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = CarColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, color = CarColors.TextDim, fontSize = 14.sp)
}

@Composable
private fun Pill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = if (selected) CarColors.OnAccent else CarColors.Text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected) CarColors.Accent else CarColors.SurfaceHigh)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
