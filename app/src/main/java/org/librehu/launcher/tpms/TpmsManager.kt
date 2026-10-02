package org.librehu.launcher.tpms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.librehu.launcher.R

enum class PressureUnit { KPA, BAR, PSI }

data class TpmsSettings(
    val enabled: Boolean = false,
    val lowKpa: Int = 180,
    val highKpa: Int = 320,
    val highCelsius: Int = 75,
    val unit: PressureUnit = PressureUnit.BAR,
    val fahrenheit: Boolean = false,
    val showSpare: Boolean = false,
    val alarms: Boolean = true,
)

data class TpmsState(
    val connected: Boolean = false,
    val device: String = "",
    val tyres: Map<TyrePos, TyreReading> = emptyMap(),
    val ids: Map<TyrePos, String> = emptyMap(),
    /** Position being paired, null when not pairing. */
    val pairing: TyrePos? = null,
    val message: String = "",
)

/** Alarm of one tyre, from the readings and the thresholds. */
fun TyreReading.alarms(s: TpmsSettings): List<Int> =
    buildList {
        if (noSignal) add(R.string.tpms_no_signal)
        if (leak) add(R.string.tpms_leak)
        if (kpa in 1 until s.lowKpa) add(R.string.tpms_low)
        if (kpa > s.highKpa) add(R.string.tpms_high)
        if (celsius > s.highCelsius) add(R.string.tpms_hot)
        if (pressureWarning && isEmpty()) add(R.string.tpms_warning)
    }

/**
 * USB TPMS receiver: finds the USB-serial adapter, asks for the permission, reads the frames, sends the heartbeat,
 * and raises a notification when a tyre goes into alarm.
 */
class TpmsManager private constructor(
    private val context: Context,
) {
    private val usb = context.getSystemService(UsbManager::class.java)
    private val prefs = context.getSharedPreferences("tpms", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private var port: UsbSerialPort? = null
    private var io: SerialInputOutputManager? = null
    private val parser = TpmsParser { f -> main.post { onFrame(TpmsProtocol.decode(f)) } }
    private val alarmed = mutableSetOf<TyrePos>()

    private val _state = MutableStateFlow(TpmsState())
    val state: StateFlow<TpmsState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<TpmsSettings> = _settings.asStateFlow()

    private val prober =
        UsbSerialProber(
            UsbSerialProber.getDefaultProbeTable().apply {
                // VOTI / Teensy-like id listed by the original apps.
                addProduct(0x16C0, 0x0483, CdcAcmSerialDriver::class.java)
            },
        )

    private val heartbeat =
        object : Runnable {
            override fun run() {
                write(TpmsProtocol.heartbeat())
                main.postDelayed(this, HEARTBEAT_MS)
            }
        }

    private val usbReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                c: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    ACTION_PERMISSION, UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        connect()
                    }

                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        val d: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        if (d != null && d.deviceName == _state.value.device) disconnect()
                    }
                }
            }
        }

    private var started = false

    fun start() {
        if (started) return
        started = true
        val filter =
            IntentFilter().apply {
                addAction(ACTION_PERMISSION)
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
        ContextCompat.registerReceiver(context, usbReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        connect()
    }

    /** Opens the first USB-serial adapter found (asks the permission if needed). */
    fun connect() {
        if (port != null) return
        val driver = prober.findAllDrivers(usb).firstOrNull() ?: return
        val device = driver.device
        if (!_settings.value.enabled) updateSettings { it.copy(enabled = true) } // a receiver is plugged: show the card
        if (!usb.hasPermission(device)) {
            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(context, 0, Intent(ACTION_PERMISSION).setPackage(context.packageName), flags)
            usb.requestPermission(device, pi)
            return
        }
        val connection = usb.openDevice(device) ?: return
        try {
            val p = driver.ports.first()
            p.open(connection)
            p.setParameters(TpmsProtocol.BAUD, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            port = p
            io =
                SerialInputOutputManager(
                    p,
                    object : SerialInputOutputManager.Listener {
                        override fun onNewData(data: ByteArray) = parser.feed(data)

                        override fun onRunError(e: Exception) {
                            Log.w(TAG, "TPMS read: ${e.message}")
                            main.post { disconnect() }
                        }
                    },
                ).also { it.start() }
            _state.value = _state.value.copy(connected = true, device = device.deviceName, message = "")
            write(TpmsProtocol.handshake((System.currentTimeMillis() and 0xFF).toInt()))
            main.post(heartbeat)
        } catch (e: Exception) {
            Log.w(TAG, "TPMS open: ${e.message}")
            disconnect()
        }
    }

    fun disconnect() {
        main.removeCallbacks(heartbeat)
        io?.stop()
        io = null
        try {
            port?.close()
        } catch (_: Exception) {
        }
        port = null
        _state.value = _state.value.copy(connected = false, device = "")
    }

    fun pair(pos: TyrePos) {
        _state.value = _state.value.copy(pairing = pos)
        write(TpmsProtocol.pair(pos))
    }

    fun stopPairing() {
        _state.value = _state.value.copy(pairing = null)
        write(TpmsProtocol.stopPairing())
    }

    fun queryIds() = write(TpmsProtocol.queryIds())

    fun swap(
        a: TyrePos,
        b: TyrePos,
    ) {
        write(TpmsProtocol.swap(a, b))
        // Readings come back with the new positions; forget the old ones.
        _state.value = _state.value.copy(tyres = _state.value.tyres - a - b)
    }

    fun updateSettings(transform: (TpmsSettings) -> TpmsSettings) {
        val s = transform(_settings.value)
        prefs
            .edit()
            .putBoolean("enabled", s.enabled)
            .putInt("low", s.lowKpa)
            .putInt("high", s.highKpa)
            .putInt("hot", s.highCelsius)
            .putString("unit", s.unit.name)
            .putBoolean("fahrenheit", s.fahrenheit)
            .putBoolean("spare", s.showSpare)
            .putBoolean("alarms", s.alarms)
            .apply()
        _settings.value = s
    }

    private fun write(bytes: ByteArray) {
        try {
            port?.write(bytes, WRITE_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.w(TAG, "TPMS write: ${e.message}")
        }
    }

    private fun onFrame(f: TpmsFrame) {
        val s = _state.value
        when (f) {
            is TpmsFrame.Tyre -> {
                _state.value = s.copy(tyres = s.tyres + (f.reading.pos to f.reading))
                checkAlarm(f.reading)
            }

            is TpmsFrame.Paired -> {
                _state.value = s.copy(pairing = null, message = context.getString(R.string.tpms_paired))
                queryIds()
            }

            is TpmsFrame.SensorId -> {
                _state.value = s.copy(ids = s.ids + (f.pos to f.id))
            }

            else -> {}
        }
    }

    private fun checkAlarm(r: TyreReading) {
        val s = _settings.value
        val alarms = r.alarms(s)
        if (alarms.isEmpty()) {
            if (alarmed.remove(r.pos)) nm().cancel(NOTIFICATION_BASE + r.pos.ordinal)
            return
        }
        if (!s.alarms || r.pos in alarmed) return
        alarmed += r.pos
        nm().notify(
            NOTIFICATION_BASE + r.pos.ordinal,
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tyre)
                .setContentTitle(context.getString(R.string.tpms_alarm_title, context.getString(posLabel(r.pos))))
                .setContentText(alarms.joinToString(", ") { context.getString(it) })
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun nm(): NotificationManager {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.tpms_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        return nm
    }

    private fun loadSettings(): TpmsSettings {
        val d = TpmsSettings()
        return TpmsSettings(
            enabled = prefs.getBoolean("enabled", d.enabled),
            lowKpa = prefs.getInt("low", d.lowKpa),
            highKpa = prefs.getInt("high", d.highKpa),
            highCelsius = prefs.getInt("hot", d.highCelsius),
            unit = runCatching { PressureUnit.valueOf(prefs.getString("unit", null)!!) }.getOrDefault(d.unit),
            fahrenheit = prefs.getBoolean("fahrenheit", d.fahrenheit),
            showSpare = prefs.getBoolean("spare", d.showSpare),
            alarms = prefs.getBoolean("alarms", d.alarms),
        )
    }

    companion object {
        private const val TAG = "LibreHU-TPMS"
        private const val ACTION_PERMISSION = "org.librehu.launcher.TPMS_USB_PERMISSION"
        private const val CHANNEL_ID = "tpms"
        private const val NOTIFICATION_BASE = 100
        private const val HEARTBEAT_MS = 2000L
        private const val WRITE_TIMEOUT_MS = 200

        @Volatile
        private var instance: TpmsManager? = null

        fun get(context: Context): TpmsManager =
            instance ?: synchronized(this) { instance ?: TpmsManager(context.applicationContext).also { instance = it } }

        fun posLabel(p: TyrePos): Int =
            when (p) {
                TyrePos.FL -> R.string.tyre_fl
                TyrePos.FR -> R.string.tyre_fr
                TyrePos.RL -> R.string.tyre_rl
                TyrePos.RR -> R.string.tyre_rr
                TyrePos.SPARE -> R.string.tyre_spare
            }

        fun formatPressure(
            kpa: Int,
            unit: PressureUnit,
        ): String =
            when (unit) {
                PressureUnit.KPA -> "$kpa kPa"
                PressureUnit.BAR -> "%.1f bar".format(kpa / 100f)
                PressureUnit.PSI -> "%.0f psi".format(kpa * 0.145038f)
            }

        fun formatTemp(
            c: Int,
            fahrenheit: Boolean,
        ): String = if (fahrenheit) "${Math.round(c * 1.8 + 32)} °F" else "$c °C"
    }
}
