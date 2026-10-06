package org.librehu.launcher.ui

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.librehu.launcher.R
import org.librehu.launcher.data.ThemeController
import org.librehu.launcher.data.ThemeMode
import org.librehu.launcher.data.ThemeStore
import java.text.DateFormat
import java.util.Date

/**
 * Control center (touch on the rail clock): phone status, brightness, volume, Wi-Fi, Bluetooth, theme, and the
 * standby clock / lock / power menu / settings shortcuts, Android Auto style.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlCenter(
    controller: ThemeController,
    theme: ThemeStore,
    actions: LauncherActions,
    onPowerMenu: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val look by theme.settings.collectAsStateWithLifecycle()
    val dark by controller.dark.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(Date()) }
    var wifi by remember { mutableStateOf(wifiOn(context)) }
    var bt by remember { mutableStateOf(btOn()) }
    var canWrite by remember { mutableStateOf(Settings.System.canWrite(context)) }
    var brightness by remember { mutableFloatStateOf(readBrightness(context).toFloat()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            wifi = wifiOn(context)
            bt = btOn()
            canWrite = Settings.System.canWrite(context)
            delay(1000)
        }
    }

    fun close(then: () -> Unit) {
        onDismiss()
        then()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = null, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth(0.85f)
                        .clip(RoundedCornerShape(32.dp))
                        .background(CarColors.Surface)
                        .clickable(interactionSource = null, indication = null) {}
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            android.text.format.DateFormat
                                .getTimeFormat(context)
                                .format(now),
                            color = CarColors.Text,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Light,
                        )
                        Text(DateFormat.getDateInstance(DateFormat.FULL).format(now), color = CarColors.TextDim, fontSize = 18.sp)
                    }
                    PhoneStatusView(compact = false)
                }

                // Brightness (Android setting; "Modify system settings" asked once).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BrightnessLow, null, tint = CarColors.TextDim, modifier = Modifier.size(28.dp))
                    if (canWrite) {
                        Slider(
                            value = brightness,
                            onValueChange = {
                                brightness = it
                                writeBrightness(context, it.toInt())
                            },
                            valueRange = 10f..255f,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            colors = SliderDefaults.colors(thumbColor = CarColors.Accent, activeTrackColor = CarColors.Accent),
                        )
                    } else {
                        Text(
                            stringResource(R.string.cc_brightness_grant),
                            color = CarColors.Accent,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        context.startActivity(
                                            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }.padding(12.dp),
                        )
                    }
                    Icon(Icons.Default.BrightnessHigh, null, tint = CarColors.TextDim, modifier = Modifier.size(28.dp))
                }

                // Volume.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.cc_volume), color = CarColors.Text, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    RoundButton(Icons.AutoMirrored.Filled.VolumeDown, R.string.volume_down, actions.volumeDown)
                    RoundButton(Icons.AutoMirrored.Filled.VolumeUp, R.string.volume_up, actions.volumeUp)
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CcTile(if (wifi) Icons.Default.Wifi else Icons.Default.WifiOff, stringResource(R.string.cc_wifi), wifi) {
                        setWifi(context, !wifi)
                        wifi = !wifi
                    }
                    CcTile(
                        if (bt) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                        stringResource(R.string.cc_bluetooth),
                        bt,
                    ) {
                        setBluetooth(!bt)
                        bt = !bt
                    }
                    val themeLabel =
                        stringResource(
                            when (look.mode) {
                                ThemeMode.LIGHT -> R.string.theme_light
                                ThemeMode.DARK -> R.string.theme_dark
                                ThemeMode.AUTO -> R.string.theme_auto
                            },
                        )
                    CcTile(Icons.Default.Brightness4, themeLabel, dark) {
                        val next =
                            when (look.mode) {
                                ThemeMode.AUTO -> ThemeMode.DARK
                                ThemeMode.DARK -> ThemeMode.LIGHT
                                ThemeMode.LIGHT -> ThemeMode.AUTO
                            }
                        actions.setTheme { it.copy(mode = next) }
                    }
                    CcTile(Icons.Default.AccessTime, stringResource(R.string.standby_title), false) { close(actions.standby) }
                    CcTile(Icons.Default.Lock, stringResource(R.string.power_lock), false) { close { actions.power(PowerChoice.LOCK) } }
                    CcTile(Icons.Default.PowerSettingsNew, stringResource(R.string.power_title), false) { onPowerMenu() }
                    CcTile(Icons.Default.Tune, stringResource(R.string.settings), false) { close { actions.show(Screen.SETTINGS) } }
                    CcTile(Icons.Default.Settings, stringResource(R.string.cc_android_settings), false) {
                        close { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CcTile(
    icon: ImageVector,
    label: String,
    on: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(132.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (on) CarColors.Accent else CarColors.SurfaceHigh)
                .clickable(onClick = onClick)
                .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (on) CarColors.OnAccent else CarColors.Text, modifier = Modifier.size(32.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            label,
            color = if (on) CarColors.OnAccent else CarColors.Text,
            fontSize = 15.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    label: Int,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(CarColors.SurfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, stringResource(label), tint = CarColors.Text, modifier = Modifier.size(30.dp)) }
}

@Suppress("DEPRECATION")
private fun wifiOn(context: Context): Boolean =
    runCatching { context.applicationContext.getSystemService(WifiManager::class.java).isWifiEnabled }.getOrDefault(false)

/** Allowed to normal apps up to Android 9 (CHANGE_WIFI_STATE). */
@Suppress("DEPRECATION")
private fun setWifi(
    context: Context,
    on: Boolean,
) {
    runCatching { context.applicationContext.getSystemService(WifiManager::class.java).isWifiEnabled = on }
}

@Suppress("DEPRECATION", "MissingPermission")
private fun btOn(): Boolean = runCatching { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true }.getOrDefault(false)

/** BLUETOOTH_ADMIN (normal permission up to Android 11). */
@Suppress("DEPRECATION", "MissingPermission")
private fun setBluetooth(on: Boolean) {
    runCatching {
        val a = BluetoothAdapter.getDefaultAdapter() ?: return
        if (on) a.enable() else a.disable()
    }
}

private fun readBrightness(context: Context): Int = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)

private fun writeBrightness(
    context: Context,
    value: Int,
) {
    runCatching {
        val cr = context.contentResolver
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(10, 255))
    }
}
