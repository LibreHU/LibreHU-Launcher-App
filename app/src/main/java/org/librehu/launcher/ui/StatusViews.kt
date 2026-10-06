package org.librehu.launcher.ui

import android.Manifest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.librehu.launcher.R
import org.librehu.launcher.status.GpsFix
import org.librehu.launcher.status.GpsStatusWatcher
import org.librehu.launcher.status.PhoneStatusWatcher

/** Phone of the car (Bluetooth): signal bars, battery, operator. Nothing when no phone is connected. */
@Composable
fun PhoneStatusView(compact: Boolean) {
    val context = LocalContext.current
    val watcher = PhoneStatusWatcher.get(context)
    DisposableEffect(Unit) {
        watcher.start()
        onDispose { watcher.stop() }
    }
    val s by watcher.state.collectAsStateWithLifecycle()
    if (!s.connected) return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SignalBars(s.signal)
            BatteryGauge(s.battery)
        }
        val label = s.operator.ifBlank { s.name } + if (s.roaming) " R" else ""
        if (label.isNotBlank()) {
            Text(
                label,
                color = CarColors.TextDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (compact) Modifier.width(96.dp) else Modifier.width(140.dp),
            )
        }
    }
}

/** 5 bars, HFP scale 0..5 (-1 unknown: all dim). */
@Composable
private fun SignalBars(level: Int) {
    val on = CarColors.Text
    val off = CarColors.TextDim.copy(alpha = 0.35f)
    Canvas(Modifier.size(width = 24.dp, height = 16.dp)) {
        val n = 5
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val h = size.height * (i + 1) / n
            drawRoundRect(
                if (i < level) on else off,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
        }
    }
}

/** Battery outline filled to the HFP level 0..5; red at 1 and below. */
@Composable
private fun BatteryGauge(level: Int) {
    val color = if (level in 0..1) Color(0xFFF28B82) else CarColors.Text
    val dim = CarColors.TextDim
    Canvas(Modifier.size(width = 26.dp, height = 14.dp)) {
        val tip = 3.dp.toPx()
        val stroke = 1.5.dp.toPx()
        val body = Size(size.width - tip, size.height)
        drawRoundRect(dim, size = body, cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(stroke))
        drawRect(dim, topLeft = Offset(body.width, size.height * 0.3f), size = Size(tip, size.height * 0.4f))
        if (level >= 0) {
            val inset = stroke * 2
            val w = (body.width - inset * 2) * (level.coerceIn(0, 5) / 5f)
            drawRect(color, topLeft = Offset(inset, inset), size = Size(w, body.height - inset * 2))
        }
    }
}

/** GNSS of the head unit: off / searching (satellites in view) / fix (satellites used). Touch: asks the permission. */
@Composable
fun GpsStatusView(onAskPermission: () -> Unit) {
    val context = LocalContext.current
    val watcher = GpsStatusWatcher.get(context)
    DisposableEffect(Unit) {
        watcher.start()
        onDispose { watcher.stop() }
    }
    val s by watcher.state.collectAsStateWithLifecycle()
    val (icon, tint, text) =
        when (s.fix) {
            GpsFix.NO_PERMISSION -> Triple(Icons.Default.GpsOff, CarColors.TextDim, "")
            GpsFix.OFF -> Triple(Icons.Default.GpsOff, CarColors.TextDim, "")
            GpsFix.SEARCHING -> Triple(Icons.Default.GpsNotFixed, Color(0xFFFDD663), if (s.inView > 0) "${s.inView}" else "")
            GpsFix.FIX -> Triple(Icons.Default.GpsFixed, Color(0xFF81C995), "${s.used}")
        }
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = s.fix == GpsFix.NO_PERMISSION, onClick = onAskPermission)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, stringResource(R.string.gps_status), tint = tint, modifier = Modifier.size(20.dp))
        if (text.isNotEmpty()) {
            Spacer(Modifier.width(3.dp))
            Text(text, color = CarColors.TextDim, fontSize = 13.sp)
        }
    }
}

/** Permission of [GpsStatusView]. */
val GPS_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

enum class PowerChoice { LOCK, STANDBY, REBOOT, SHUTDOWN, MCU_RESET }

/** Power menu: lock, standby clock, restart, shut down, restart through the MCU (when the head unit can). */
@Composable
fun PowerMenu(
    mcuReset: Boolean,
    onDismiss: () -> Unit,
    onChoice: (PowerChoice) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.PowerSettingsNew, null, tint = CarColors.Accent) },
        title = { Text(stringResource(R.string.power_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val items =
                    listOfNotNull(
                        PowerChoice.LOCK to R.string.power_lock,
                        PowerChoice.STANDBY to R.string.power_standby,
                        PowerChoice.REBOOT to R.string.power_reboot,
                        PowerChoice.SHUTDOWN to R.string.power_shutdown,
                        if (mcuReset) PowerChoice.MCU_RESET to R.string.power_mcu_reset else null,
                    )
                for ((choice, label) in items) {
                    SettingChoice(stringResource(label), choice == PowerChoice.LOCK) { onChoice(choice) }
                }
                Text(stringResource(R.string.power_hint), color = CarColors.TextDim, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
