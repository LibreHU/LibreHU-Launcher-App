package org.librehu.launcher.status

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
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
    /** Network service: false = no service, null unknown. */
    val service: Boolean? = null,
    /**
     * Probably charging: HFP has no charging indicator, so this is deduced: the phone is plugged in the head unit's
     * USB, or its battery level went up (and did not go down since).
     */
    val charging: Boolean = false,
    val usbPlugged: Boolean = false,
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
                    UsbManager.ACTION_USB_DEVICE_ATTACHED, UsbManager.ACTION_USB_DEVICE_DETACHED -> main.postDelayed({ checkUsb() }, 500)
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
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        checkUsb()
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
                lastBattery = -1
                rising = false
                _state.value = PhoneStatus(usbPlugged = _state.value.usbPlugged)
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

    private var lastBattery = -1
    private var rising = false

    /** A phone on the head unit's USB port (charged by it): Apple / Android vendors, or an MTP / PTP interface. */
    private fun checkUsb() {
        val usb = app.getSystemService(UsbManager::class.java) ?: return
        val plugged =
            runCatching {
                usb.deviceList.values.any { d ->
                    d.vendorId in PHONE_VENDORS ||
                        (0 until d.interfaceCount).any { d.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE }
                }
            }.getOrDefault(false)
        val s = _state.value
        _state.value = s.copy(usbPlugged = plugged, charging = plugged || rising)
    }

    private fun applyEvents(b: Bundle) {
        main.post {
            var s = _state.value
            if (b.containsKey(EXTRA_SIGNAL)) s = s.copy(signal = b.getInt(EXTRA_SIGNAL, -1))
            if (b.containsKey(EXTRA_BATTERY)) {
                val level = b.getInt(EXTRA_BATTERY, -1)
                if (lastBattery >= 0 && level >= 0 && level != lastBattery) rising = level > lastBattery
                if (level >= 0) lastBattery = level
                s = s.copy(battery = level)
            }
            if (b.containsKey(EXTRA_NETWORK_STATUS)) s = s.copy(service = b.getInt(EXTRA_NETWORK_STATUS, 1) != 0)
            if (b.containsKey(EXTRA_OPERATOR)) s = s.copy(operator = b.getString(EXTRA_OPERATOR).orEmpty())
            if (b.containsKey(EXTRA_ROAMING)) s = s.copy(roaming = b.getInt(EXTRA_ROAMING, 0) == 1)
            _state.value = s.copy(charging = s.usbPlugged || rising)
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
        private const val EXTRA_NETWORK_STATUS = "android.bluetooth.headsetclient.extra.NETWORK_STATUS"

        /** USB vendor ids of phone makers: Apple, Google, Samsung, Xiaomi, Huawei, OnePlus, Motorola, LG, Sony, Oppo, HTC. */
        private val PHONE_VENDORS = setOf(0x05AC, 0x18D1, 0x04E8, 0x2717, 0x12D1, 0x2A70, 0x22B8, 0x1004, 0x0FCE, 0x22D9, 0x0BB4)

        @Volatile
        private var instance: PhoneStatusWatcher? = null

        fun get(context: Context): PhoneStatusWatcher =
            instance ?: synchronized(this) { instance ?: PhoneStatusWatcher(context).also { instance = it } }
    }
}
