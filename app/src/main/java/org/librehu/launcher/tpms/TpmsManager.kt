package org.librehu.launcher.tpms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.librehu.launcher.R
import java.io.File

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
    /** Beeps on a new alarm, again every [soundRepeatSec] s while it lasts (0 = once). */
    val alarmSound: Boolean = true,
    val soundRepeatSec: Int = 30,
    /** Readings shown at most every [refreshSec] s per tyre (alarms are checked at once); BLE scan rhythm. */
    val refreshSec: Int = 5,
    /** Bluetooth LE sensors (no USB receiver needed). */
    val bleEnabled: Boolean = false,
    /** Imported car picture: wheel axles, in % of its height from the nose. */
    val frontAxle: Int = 22,
    val rearAxle: Int = 76,
) {
    companion object {
        val REFRESH_CHOICES = listOf(1, 5, 15, 30, 60)
        val REPEAT_CHOICES = listOf(0, 10, 30, 60)
    }
}

data class TpmsState(
    val connected: Boolean = false,
    val device: String = "",
    val tyres: Map<TyrePos, TyreReading> = emptyMap(),
    val ids: Map<TyrePos, String> = emptyMap(),
    /** Position being paired, null when not pairing. */
    val pairing: TyrePos? = null,
    val message: String = "",
    /** BLE sensors heard lately, by id. */
    val bleSeen: Map<String, BleTpmsReading> = emptyMap(),
    /** BLE sensor assigned to each tyre. */
    val bleIds: Map<TyrePos, String> = emptyMap(),
    val bleError: String = "",
    /** Alarm sound silenced until the alarms change. */
    val silenced: Boolean = false,
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
    private val lastShown = HashMap<TyrePos, Long>()
    private val pending = HashMap<TyrePos, TyreReading>()
    private val ble = TpmsBleScanner(::onBleReading) { e -> _state.value = _state.value.copy(bleError = e) }
    private val flush =
        Runnable {
            val now = System.currentTimeMillis()
            pending.values.toList().forEach { publish(it, now) }
            pending.clear()
        }
    private val beepAgain =
        object : Runnable {
            override fun run() {
                val s = _settings.value
                if (alarmed.isEmpty() || !s.alarmSound || s.soundRepeatSec <= 0 || _state.value.silenced) return
                beep()
                main.postDelayed(this, s.soundRepeatSec * 1000L)
            }
        }

    private val _state = MutableStateFlow(TpmsState(bleIds = loadBleIds()))
    val state: StateFlow<TpmsState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())

    private val carFile = File(context.filesDir, "tpms_car.png")
    private val _carImage = MutableStateFlow(if (carFile.exists()) BitmapFactory.decodeFile(carFile.path) else null)

    /** Car seen from above imported by the user (PNG, transparency kept), null = drawn car. */
    val carImage: StateFlow<Bitmap?> = _carImage.asStateFlow()
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
        // Redraw the widgets when what they show changes (not on every frame: the reading time is left out).
        scope.launch {
            combine(_state, _settings) { st, s ->
                st.copy(tyres = st.tyres.mapValues { it.value.copy(time = 0) }, message = "") to s
            }.distinctUntilChanged().collect { (st, s) -> TpmsWidget.updateAll(context, st, s) }
        }
        connect()
        if (_settings.value.bleEnabled) ble.start(_settings.value.refreshSec)
    }

    // --- Bluetooth LE sensors -------------------------------------------------------------------------------------

    private fun onBleReading(r: BleTpmsReading) {
        val s = _state.value
        _state.value = s.copy(bleSeen = s.bleSeen + (r.id to r), bleError = "")
        val pos =
            s.bleIds.entries
                .firstOrNull { it.value == r.id }
                ?.key ?: return
        accept(
            TyreReading(
                pos = pos,
                kpa = r.kpa,
                celsius = r.celsius,
                leak = r.alarm,
                pressureWarning = false,
                noSignal = false,
                battery = r.batteryVolts,
                time = r.time,
            ),
        )
    }

    /** Puts BLE sensor [id] on [pos] (null id: no sensor there). */
    fun assignBle(
        pos: TyrePos,
        id: String?,
    ) {
        val ids =
            _state.value.bleIds
                .filterValues { it != id }
                .toMutableMap()
        if (id == null) ids.remove(pos) else ids[pos] = id
        prefs
            .edit()
            .apply {
                TyrePos.entries.forEach { p -> ids[p]?.let { putString("ble_${p.name}", it) } ?: remove("ble_${p.name}") }
            }.apply()
        _state.value = _state.value.copy(bleIds = ids, tyres = _state.value.tyres - pos)
        _state.value.bleSeen[id]?.let { onBleReading(it) }
    }

    fun forgetSeen() {
        _state.value = _state.value.copy(bleSeen = emptyMap())
    }

    private fun loadBleIds(): Map<TyrePos, String> =
        TyrePos.entries
            .mapNotNull { p ->
                prefs.getString("ble_${p.name}", null)?.let {
                    p to it
                }
            }.toMap()

    // --- Readings, alarms --------------------------------------------------------------------------------------------

    /** Alarm checked at once; shown at most every [TpmsSettings.refreshSec] per tyre. */
    private fun accept(r: TyreReading) {
        checkAlarm(r)
        val now = System.currentTimeMillis()
        val every = _settings.value.refreshSec * 1000L
        val last = lastShown[r.pos] ?: 0
        if (now - last >= every) {
            pending.remove(r.pos)
            publish(r, now)
        } else {
            pending[r.pos] = r
            main.removeCallbacks(flush)
            main.postDelayed(flush, every - (now - last))
        }
    }

    private fun publish(
        r: TyreReading,
        now: Long,
    ) {
        lastShown[r.pos] = now
        _state.value = _state.value.copy(tyres = _state.value.tyres + (r.pos to r))
    }

    /**
     * Uses the picture at [uri] as the car (nose at the top), scaled down to [CAR_MAX_PX] and saved as PNG so that
     * transparency is kept. Blocking (decodes), call it off the main thread.
     */
    fun setCarImage(uri: Uri): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return false
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= CAR_MAX_PX) sample *= 2
        val decoded =
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return false
        val scale = minOf(1f, CAR_MAX_PX.toFloat() / maxOf(decoded.width, decoded.height))
        val bmp =
            if (scale < 1f) {
                Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
            } else {
                decoded
            }
        carFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        _carImage.value = bmp
        main.post { TpmsWidget.refresh(context) }
        return true
    }

    fun clearCarImage() {
        carFile.delete()
        _carImage.value = null
        TpmsWidget.refresh(context)
    }

    /** Stops the alarm sound until the alarms change. */
    fun silence() {
        main.removeCallbacks(beepAgain)
        _state.value = _state.value.copy(silenced = true)
    }

    /** Three beeps on the media stream (the one the car speakers always play). */
    fun beep() {
        Thread {
            try {
                val tg = ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)
                repeat(3) {
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
                    Thread.sleep(400)
                }
                tg.release()
            } catch (e: Exception) {
                Log.w(TAG, "TPMS beep: ${e.message}")
            }
        }.start()
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
        val old = _settings.value
        val s = transform(old)
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
            .putBoolean("alarm_sound", s.alarmSound)
            .putInt("sound_repeat", s.soundRepeatSec)
            .putInt("refresh", s.refreshSec)
            .putBoolean("ble", s.bleEnabled)
            .putInt("front_axle", s.frontAxle)
            .putInt("rear_axle", s.rearAxle)
            .apply()
        _settings.value = s
        if (started && (s.bleEnabled != old.bleEnabled || s.refreshSec != old.refreshSec)) {
            if (s.bleEnabled) ble.start(s.refreshSec) else ble.stop()
        }
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
                accept(f.reading)
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
            if (alarmed.remove(r.pos)) {
                nm().cancel(NOTIFICATION_BASE + r.pos.ordinal)
                if (alarmed.isEmpty()) {
                    main.removeCallbacks(beepAgain)
                    _state.value = _state.value.copy(silenced = false)
                }
            }
            return
        }
        if (!s.alarms || r.pos in alarmed) return
        alarmed += r.pos
        // New alarm: sound again even if silenced before.
        _state.value = _state.value.copy(silenced = false)
        if (s.alarmSound) {
            beep()
            main.removeCallbacks(beepAgain)
            if (s.soundRepeatSec > 0) main.postDelayed(beepAgain, s.soundRepeatSec * 1000L)
        }
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
            alarmSound = prefs.getBoolean("alarm_sound", d.alarmSound),
            soundRepeatSec = prefs.getInt("sound_repeat", d.soundRepeatSec),
            refreshSec = prefs.getInt("refresh", d.refreshSec),
            bleEnabled = prefs.getBoolean("ble", d.bleEnabled),
            frontAxle = prefs.getInt("front_axle", d.frontAxle),
            rearAxle = prefs.getInt("rear_axle", d.rearAxle),
        )
    }

    companion object {
        private const val TAG = "LibreHU-TPMS"
        private const val ACTION_PERMISSION = "org.librehu.launcher.TPMS_USB_PERMISSION"
        private const val CHANNEL_ID = "tpms"
        private const val NOTIFICATION_BASE = 100
        private const val HEARTBEAT_MS = 2000L
        private const val WRITE_TIMEOUT_MS = 200
        private const val CAR_MAX_PX = 800

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
