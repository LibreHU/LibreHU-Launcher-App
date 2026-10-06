package org.librehu.launcher.status

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The phone connected in Bluetooth (hands-free): operator, network signal and battery, as it reports them. */
data class PhoneStatus(
    val connected: Boolean = false,
    val name: String = "",
    val operator: String = "",
    /** 0..5, -1 unknown. */
    val signal: Int = -1,
    /** 0..5, -1 unknown. */
    val battery: Int = -1,
    val roaming: Boolean = false,
)

/**
 * Reads the HFP client of Android (BluetoothProfile 16, hidden constant): the phone sends its indicators (signal,
 * battery, operator) as "AG events" (`android.bluetooth.headsetclient.profile.action.AG_EVENT`); the current ones are
 * read once with the hidden `getCurrentAgEvents(device)`. Works whichever app drives the Bluetooth (Jancar btservice,
 * LibreHU-service).
 */
class PhoneStatusWatcher private constructor(
    context: Context,
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(PhoneStatus())
    val state: StateFlow<PhoneStatus> = _state.asStateFlow()
    private var proxy: BluetoothProfile? = null
    private var users = 0

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                c: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    ACTION_AG_EVENT -> intent.extras?.let(::applyEvents)
                    else -> refresh()
                }
            }
        }

    /** Reference counted: the launcher and the lock screen both show it. */
    fun start() {
        if (users++ > 0) return
        val filter =
            IntentFilter().apply {
                addAction(ACTION_CONNECTION)
                addAction(ACTION_AG_EVENT)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        try {
            BluetoothAdapter.getDefaultAdapter()?.getProfileProxy(
                app,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(
                        profile: Int,
                        p: BluetoothProfile,
                    ) {
                        proxy = p
                        refresh()
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        proxy = null
                        refresh()
                    }
                },
                HEADSET_CLIENT,
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "HFP client: ${e.message}")
        }
    }

    fun stop() {
        if (users == 0 || --users > 0) return
        runCatching { app.unregisterReceiver(receiver) }
        proxy?.let { runCatching { BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(HEADSET_CLIENT, it) } }
        proxy = null
    }

    @SuppressLint("MissingPermission")
    private fun refresh() {
        main.post {
            val device: BluetoothDevice? = runCatching { proxy?.connectedDevices?.firstOrNull() }.getOrNull()
            if (device == null) {
                _state.value = PhoneStatus()
                return@post
            }
            val name = runCatching { device.name }.getOrNull().orEmpty()
            _state.value = _state.value.copy(connected = true, name = name)
            currentEvents(device)?.let(::applyEvents)
        }
    }

    private fun currentEvents(device: BluetoothDevice): Bundle? =
        try {
            proxy?.javaClass?.getMethod("getCurrentAgEvents", BluetoothDevice::class.java)?.invoke(proxy, device) as? Bundle
        } catch (e: Exception) {
            Log.w(TAG, "getCurrentAgEvents: ${e.cause ?: e}")
            null
        }

    private fun applyEvents(b: Bundle) {
        main.post {
            var s = _state.value
            if (b.containsKey(EXTRA_SIGNAL)) s = s.copy(signal = b.getInt(EXTRA_SIGNAL, -1))
            if (b.containsKey(EXTRA_BATTERY)) s = s.copy(battery = b.getInt(EXTRA_BATTERY, -1))
            if (b.containsKey(EXTRA_OPERATOR)) s = s.copy(operator = b.getString(EXTRA_OPERATOR).orEmpty())
            if (b.containsKey(EXTRA_ROAMING)) s = s.copy(roaming = b.getInt(EXTRA_ROAMING, 0) == 1)
            _state.value = s
        }
    }

    companion object {
        private const val TAG = "LibreHU-Launcher"
        private const val HEADSET_CLIENT = 16
        private const val ACTION_CONNECTION = "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED"
        private const val ACTION_AG_EVENT = "android.bluetooth.headsetclient.profile.action.AG_EVENT"
        private const val EXTRA_SIGNAL = "android.bluetooth.headsetclient.extra.NETWORK_SIGNAL_STRENGTH"
        private const val EXTRA_BATTERY = "android.bluetooth.headsetclient.extra.BATTERY_LEVEL"
        private const val EXTRA_OPERATOR = "android.bluetooth.headsetclient.extra.OPERATOR_NAME"
        private const val EXTRA_ROAMING = "android.bluetooth.headsetclient.extra.NETWORK_ROAMING"

        @Volatile
        private var instance: PhoneStatusWatcher? = null

        fun get(context: Context): PhoneStatusWatcher =
            instance ?: synchronized(this) { instance ?: PhoneStatusWatcher(context).also { instance = it } }
    }
}
