package org.librehu.launcher.tpms

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.roundToInt

/** Format of a Bluetooth LE tyre sensor (they only advertise: no pairing, no connection). */
enum class BleTpmsFormat {
    /** ZEEPIN, TP630 and alike: 18 bytes of manufacturer data, sensor number 0x80.., pressure in Pa, 0.01 °C. */
    ZEEPIN,

    /** Name "BR" (SYTPMS app): 7 bytes, status, battery 0.1 V, °C, absolute pressure 0.1 psi. */
    BR,
}

/** One advertisement of a BLE tyre sensor. [id]: stable identity used to assign it to a tyre. */
data class BleTpmsReading(
    val id: String,
    val format: BleTpmsFormat,
    val kpa: Int,
    val celsius: Int,
    val batteryVolts: Float? = null,
    val batteryPercent: Int? = null,
    /** No pressure / pressure alarm reported by the sensor. */
    val alarm: Boolean = false,
    val rssi: Int = 0,
    val time: Long = System.currentTimeMillis(),
)

/**
 * Decoding of the advertisements, from the formats documented by ra6070/BLE-TPMS (ZEEPIN, TP630) and andi38/TPMS
 * ("BR" sensors). Pure function of the raw advertisement bytes (AD structures), testable without Bluetooth.
 */
object BleTpmsParser {
    private const val AD_NAME_SHORT = 0x08
    private const val AD_NAME_FULL = 0x09
    private const val AD_UUID16 = 0x03
    private const val AD_MANUFACTURER = 0xFF
    private const val BR_SERVICE = 0x27A5

    /** Absolute → gauge pressure: atmosphere = 14.5 psi. */
    private const val ATMOSPHERE_PSI = 14.5f
    private const val KPA_PER_PSI = 6.89476f

    fun parse(
        record: ByteArray,
        address: String,
        rssi: Int = 0,
    ): BleTpmsReading? {
        var name = ""
        var manufacturer: ByteArray? = null
        var brService = false
        var i = 0
        while (i < record.size) {
            val len = record[i].toInt() and 0xFF
            if (len == 0 || i + 1 >= record.size) break
            val type = record[i + 1].toInt() and 0xFF
            val end = minOf(record.size, i + 1 + len)
            val data = if (end > i + 2) record.copyOfRange(i + 2, end) else ByteArray(0)
            when (type) {
                AD_NAME_SHORT, AD_NAME_FULL -> name = String(data, Charsets.US_ASCII)
                AD_MANUFACTURER -> manufacturer = data
                AD_UUID16, 0x02 -> if (data.size >= 2 && u16le(data, 0) == BR_SERVICE) brService = true
            }
            i += len + 1
        }
        val m = manufacturer ?: return null
        return when {
            m.size == 18 && (m[2].toInt() and 0xF8) == 0x80 -> zeepin(m, rssi)
            m.size == 7 && (name == "BR" || brService) -> br(m, address, rssi)
            else -> null
        }
    }

    /** `00 01 | 80 | EA CA | 10 8A 78 | pressure Pa (LE32) | temp 0.01 °C (LE32) | battery % | alarm`. */
    private fun zeepin(
        m: ByteArray,
        rssi: Int,
    ): BleTpmsReading {
        val id = (2..7).joinToString("") { "%02X".format(m[it].toInt() and 0xFF) }
        return BleTpmsReading(
            id = id,
            format = BleTpmsFormat.ZEEPIN,
            kpa = (s32le(m, 8) / 1000f).roundToInt(),
            celsius = (s32le(m, 12) / 100f).roundToInt(),
            batteryPercent = m[16].toInt() and 0xFF,
            alarm = (m[17].toInt() and 0xFF) != 0,
            rssi = rssi,
        )
    }

    /** `status | battery 0.1 V | °C | absolute pressure 0.1 psi (BE16) | checksum (BE16)`. */
    private fun br(
        m: ByteArray,
        address: String,
        rssi: Int,
    ): BleTpmsReading {
        val status = m[0].toInt() and 0xFF
        val psiAbs = (((m[3].toInt() and 0xFF) shl 8) or (m[4].toInt() and 0xFF)) / 10f
        return BleTpmsReading(
            id = address.uppercase(),
            format = BleTpmsFormat.BR,
            kpa = ((psiAbs - ATMOSPHERE_PSI).coerceAtLeast(0f) * KPA_PER_PSI).roundToInt(),
            celsius = m[2].toInt(),
            batteryVolts = (m[1].toInt() and 0xFF) / 10f,
            // Bit 7: alarm zero pressure.
            alarm = status and 0x80 != 0,
            rssi = rssi,
        )
    }

    private fun u16le(
        b: ByteArray,
        o: Int,
    ) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun s32le(
        b: ByteArray,
        o: Int,
    ) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or ((b[o + 2].toInt() and 0xFF) shl 16) or
        (b[o + 3].toInt() shl 24)
}

/**
 * Bluetooth LE scan for tyre sensors. Continuous (low power) when [refreshSeconds] is short, else bursts of
 * [BURST_MS] every [refreshSeconds]. Needs location permission and location on (Android 9 BLE scans).
 */
class TpmsBleScanner(
    private val onReading: (BleTpmsReading) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var scanning = false
    private var running = false
    private var refreshSeconds = 5

    private val callback =
        object : ScanCallback() {
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult,
            ) {
                val bytes = result.scanRecord?.bytes ?: return
                BleTpmsParser.parse(bytes, result.device.address, result.rssi)?.let { r -> main.post { onReading(r) } }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { onScanResult(0, it) }

            override fun onScanFailed(errorCode: Int) {
                scanning = false
                main.post { onError("BLE scan failed ($errorCode)") }
            }
        }

    private val cycle =
        object : Runnable {
            override fun run() {
                if (!running) return
                if (refreshSeconds < BURST_THRESHOLD_S) {
                    startScan()
                    return
                }
                startScan()
                main.postDelayed({ stopScan() }, BURST_MS)
                main.postDelayed(this, refreshSeconds * 1000L)
            }
        }

    fun start(refresh: Int) {
        refreshSeconds = refresh
        if (running) stop()
        running = true
        main.post(cycle)
    }

    fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
        stopScan()
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (scanning) return
        val scanner = BluetoothAdapter.getDefaultAdapter()?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            onError("Bluetooth off")
            return
        }
        try {
            val mode = if (refreshSeconds < BURST_THRESHOLD_S) ScanSettings.SCAN_MODE_LOW_POWER else ScanSettings.SCAN_MODE_LOW_LATENCY
            scanner.startScan(null, ScanSettings.Builder().setScanMode(mode).build(), callback)
            scanning = true
        } catch (e: Exception) {
            Log.w("LibreHU-TPMS", "BLE scan: ${e.message}")
            onError(e.message ?: "BLE scan")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!scanning) return
        scanning = false
        runCatching { BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner?.stopScan(callback) }
    }

    private companion object {
        const val BURST_MS = 8_000L
        const val BURST_THRESHOLD_S = 15
    }
}
