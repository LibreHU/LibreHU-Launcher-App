package org.librehu.launcher.ui

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.WifiTethering
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
    var hotspot by remember { mutableStateOf(Hotspot.isOn(context)) }
    var volume by remember { mutableStateOf(actions.volume()) }
    var draggingVolume by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            wifi = wifiOn(context)
            bt = btOn()
            canWrite = Settings.System.canWrite(context)
            hotspot = Hotspot.isOn(context)
            if (!draggingVolume) volume = actions.volume()
            delay(1000)
        }
    }

    fun close(then: () -> Unit) {
        onDismiss()
        then()
    }

    /** Long press on a tile or a slider: the matching Android settings page. */
    fun openSettings(action: String) =
        close {
            runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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

                // Volume (head unit volume: LibreHU-service's audio chip, else Android's media volume).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeDown,
                        stringResource(R.string.volume_down),
                        tint = CarColors.TextDim,
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = actions.volumeDown,
                                    onLongClick = { openSettings(Settings.ACTION_SOUND_SETTINGS) },
                                ).padding(6.dp),
                    )
                    Slider(
                        value = volume.first.toFloat(),
                        onValueChange = {
                            draggingVolume = true
                            volume = it.toInt() to volume.second
                            actions.setVolume(it.toInt())
                        },
                        onValueChangeFinished = { draggingVolume = false },
                        valueRange = 0f..volume.second.coerceAtLeast(1).toFloat(),
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        colors = SliderDefaults.colors(thumbColor = CarColors.Accent, activeTrackColor = CarColors.Accent),
                    )
                    Text("${volume.first}", color = CarColors.TextDim, fontSize = 16.sp, modifier = Modifier.width(32.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        stringResource(R.string.volume_up),
                        tint = CarColors.TextDim,
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = actions.volumeUp,
                                    onLongClick = { openSettings(Settings.ACTION_SOUND_SETTINGS) },
                                ).padding(6.dp),
                    )
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CcTile(
                        if (wifi) Icons.Default.Wifi else Icons.Default.WifiOff,
                        stringResource(R.string.cc_wifi),
                        wifi,
                        onLongClick = { openSettings(Settings.ACTION_WIFI_SETTINGS) },
                    ) {
                        setWifi(context, !wifi)
                        wifi = !wifi
                    }
                    CcTile(
                        Icons.Default.WifiTethering,
                        stringResource(R.string.cc_hotspot),
                        hotspot,
                        onLongClick = { close { Hotspot.openSettings(context) } },
                    ) {
                        // Needs a privileged install (TETHER_PRIVILEGED): otherwise Android's hotspot page opens.
                        if (Hotspot.set(context, !hotspot)) hotspot = !hotspot else close { Hotspot.openSettings(context) }
                    }
                    CcTile(
                        if (bt) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                        stringResource(R.string.cc_bluetooth),
                        bt,
                        onLongClick = { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS) },
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
                    CcTile(Icons.Default.Brightness4, themeLabel, dark, onLongClick = { openSettings(Settings.ACTION_DISPLAY_SETTINGS) }) {
                        val next =
                            when (look.mode) {
                                ThemeMode.AUTO -> ThemeMode.DARK
                                ThemeMode.DARK -> ThemeMode.LIGHT
                                ThemeMode.LIGHT -> ThemeMode.AUTO
                            }
                        actions.setTheme { it.copy(mode = next) }
                    }
                    CcTile(
                        Icons.Default.AccessTime,
                        stringResource(R.string.standby_title),
                        false,
                        onLongClick = { openSettings(Settings.ACTION_DATE_SETTINGS) },
                    ) { close(actions.standby) }
                    CcTile(
                        Icons.Default.Lock,
                        stringResource(R.string.power_lock),
                        false,
                        onLongClick = { openSettings(Settings.ACTION_SECURITY_SETTINGS) },
                    ) { close { actions.power(PowerChoice.LOCK) } }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CcTile(
    icon: ImageVector,
    label: String,
    on: Boolean,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(132.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (on) CarColors.Accent else CarColors.SurfaceHigh)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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

/**
 * Wi-Fi hotspot (connection sharing). State through the hidden WifiManager.isWifiApEnabled; switching through
 * IConnectivityManager.startTethering / stopTethering (Android 9: callerPkg). On Android 9 Android lets an app with
 * "Modify system settings" (already asked for the brightness) do it, unless the ROM requires tethering provisioning;
 * when it refuses, the hotspot settings page opens instead.
 */
private object Hotspot {
    private const val TETHERING_WIFI = 0

    fun isOn(context: Context): Boolean =
        runCatching {
            val wm = context.applicationContext.getSystemService(WifiManager::class.java)
            wm.javaClass.getMethod("isWifiApEnabled").invoke(wm) as Boolean
        }.getOrDefault(false)

    private fun service(): Any {
        val binder =
            Class
                .forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, Context.CONNECTIVITY_SERVICE) as android.os.IBinder
        return Class
            .forName("android.net.IConnectivityManager\$Stub")
            .getMethod("asInterface", android.os.IBinder::class.java)
            .invoke(null, binder)!!
    }

    fun set(
        context: Context,
        on: Boolean,
    ): Boolean =
        runCatching {
            val icm = service()
            val pkg = context.packageName
            if (on) {
                val receiver = android.os.ResultReceiver(android.os.Handler(android.os.Looper.getMainLooper()))
                icm.javaClass
                    .getMethod(
                        "startTethering",
                        Int::class.javaPrimitiveType,
                        android.os.ResultReceiver::class.java,
                        Boolean::class.javaPrimitiveType,
                        String::class.java,
                    ).invoke(icm, TETHERING_WIFI, receiver, false, pkg)
            } else {
                icm.javaClass
                    .getMethod("stopTethering", Int::class.javaPrimitiveType, String::class.java)
                    .invoke(icm, TETHERING_WIFI, pkg)
            }
            true
        }.getOrElse {
            android.util.Log.i("LibreHU-Launcher", "hotspot: ${it.cause ?: it}")
            false
        }

    fun openSettings(context: Context) {
        val intents =
            listOf(
                Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings"),
                Intent(Settings.ACTION_WIRELESS_SETTINGS),
            )
        for (i in intents) {
            if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }
}
