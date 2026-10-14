package org.librehu.launcher.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.librehu.launcher.R
import org.librehu.launcher.status.PhoneStatusWatcher

/**
 * First start assistant: pairing the phone, step by step (like a phone's own "quick setup"). The head unit's
 * Bluetooth name (the car's name when LibreHU-service renamed it), Bluetooth on, visible for two minutes, what to do
 * on the phone, and the paired devices with the connected phone first. Pairing itself is Android's (code confirmed
 * on both screens).
 */
@SuppressLint("MissingPermission")
@Composable
fun BluetoothSetupPage() {
    val context = LocalContext.current
    val adapter = remember { BluetoothAdapter.getDefaultAdapter() }
    var revision by remember { mutableIntStateOf(0) }
    val phone by PhoneStatusWatcher
        .get(context)
        .state
        .collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        val watcher = PhoneStatusWatcher.get(context)
        watcher.start()
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context,
                    intent: Intent,
                ) {
                    revision++
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
                addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            watcher.stop()
        }
    }

    if (adapter == null) {
        SettingHint(stringResource(R.string.bt_setup_none))
        return
    }
    // Read again on every Bluetooth change.
    val on = remember(revision) { runCatching { adapter.isEnabled }.getOrDefault(false) }
    val name = remember(revision) { runCatching { adapter.name }.getOrNull().orEmpty() }
    val visible = remember(revision) { runCatching { adapter.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE }.getOrDefault(false) }
    val bonded = remember(revision) { runCatching { adapter.bondedDevices.toList() }.getOrDefault(emptyList()) }

    SettingHint(stringResource(R.string.bt_setup_hint))

    // The head unit as the phone will see it.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CarColors.SurfaceHigh)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Bluetooth, null, tint = CarColors.Accent, modifier = Modifier.size(40.dp))
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(name.ifEmpty { "—" }, color = CarColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Text(
                stringResource(
                    when {
                        !on -> R.string.bt_setup_off
                        visible -> R.string.bt_setup_visible
                        else -> R.string.bt_setup_hidden
                    },
                ),
                color = if (visible) CarColors.Accent else CarColors.TextDim,
                fontSize = 15.sp,
            )
        }
        if (!on) {
            SettingChoice(stringResource(R.string.bt_setup_turn_on), true) {
                runCatching { context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        } else {
            SettingChoice(stringResource(R.string.bt_setup_make_visible), !visible) {
                runCatching {
                    context.startActivity(
                        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, VISIBLE_SECONDS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
    }

    SettingSection(stringResource(R.string.bt_setup_steps_title))
    listOf(
        stringResource(R.string.bt_setup_step1),
        stringResource(R.string.bt_setup_step2, name.ifEmpty { "LibreHU" }),
        stringResource(R.string.bt_setup_step3),
        stringResource(R.string.bt_setup_step4),
    ).forEachIndexed { i, step ->
        Row(verticalAlignment = Alignment.Top) {
            Text("${i + 1}.", color = CarColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 10.dp))
            Text(step, color = CarColors.Text, fontSize = 17.sp)
        }
    }

    SettingSection(stringResource(R.string.bt_setup_paired))
    if (bonded.isEmpty()) {
        SettingHint(stringResource(R.string.bt_setup_paired_none))
    } else {
        val connectedName = phone.name.takeIf { phone.connected }
        for (d in bonded.sortedByDescending { runCatching { it.name }.getOrNull() == connectedName }) {
            val label = runCatching { d.name }.getOrNull().orEmpty().ifEmpty { d.address }
            val connected = connectedName != null && label == connectedName
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Icon(
                    if (connected) Icons.Default.BluetoothConnected else Icons.Default.Smartphone,
                    null,
                    tint = if (connected) CarColors.Accent else CarColors.TextDim,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.size(12.dp))
                Text(label, color = CarColors.Text, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Text(
                    stringResource(if (connected) R.string.bt_setup_connected else R.string.bt_setup_paired_one),
                    color = if (connected) CarColors.Accent else CarColors.TextDim,
                    fontSize = 15.sp,
                )
            }
        }
    }
    SettingChoice(stringResource(R.string.bt_setup_android_settings), false) {
        runCatching {
            context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    SettingHint(stringResource(R.string.bt_setup_after))
}

private const val VISIBLE_SECONDS = 120
