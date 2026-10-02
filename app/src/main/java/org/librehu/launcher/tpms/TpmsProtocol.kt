package org.librehu.launcher.tpms

/**
 * USB TPMS receivers sold for Android head units ("USB TPMS", apps com.cz.usbserial.tpms and com.syt.tmps):
 * a USB-serial adapter (CH340, CP210x, PL2303, FTDI…) at 19200 8N1, frames
 *
 * ```
 * 55 AA LEN TYPE … CS      LEN = whole frame length, CS = XOR of all the previous bytes
 * ```
 * Received:
 * - LEN 8 / 10, tyre: `55 AA LEN POS P T FLAGS [BAT ?] CS`, pressure = P × 3.44 kPa, temperature = T − 50 °C,
 *   flags bit 3 leak, bit 4 pressure warning, bit 5 no signal; battery = BAT × 0.1 V (10-byte frames only);
 * - LEN 6, `55 AA 06 18 POS CS`: pairing done for POS; `55 AA 06 A5 X` / `55 AA 06 B5 X`: handshake answers;
 * - LEN 9, `55 AA 09 IDX ID0 ID1 ID2 ID3 CS`: sensor id (IDX 1 FL, 2 FR, 3 RL, 4 RR, 5 spare).
 * Sent: `06 19` heartbeat / data request, `06 01 POS` pair, `06 06` stop pairing, `06 07` query ids,
 * `07 03 A B` swap two positions, `06 5A T` handshake seed.
 * Positions: 0x00 FL, 0x01 FR, 0x10 RL, 0x11 RR, 0x05 spare.
 */
enum class TyrePos(
    val code: Int,
    val idIndex: Int,
) {
    FL(0x00, 1),
    FR(0x01, 2),
    RL(0x10, 3),
    RR(0x11, 4),
    SPARE(0x05, 5),
    ;

    companion object {
        fun of(code: Int): TyrePos? = entries.firstOrNull { it.code == code }

        fun ofIdIndex(i: Int): TyrePos? = entries.firstOrNull { it.idIndex == i }
    }
}

data class TyreReading(
    val pos: TyrePos,
    val kpa: Int,
    val celsius: Int,
    val leak: Boolean,
    val pressureWarning: Boolean,
    val noSignal: Boolean,
    /** Sensor battery in volts (10-byte frames only). */
    val battery: Float?,
    val time: Long = System.currentTimeMillis(),
)

sealed interface TpmsFrame {
    data class Tyre(
        val reading: TyreReading,
    ) : TpmsFrame

    data class Paired(
        val pos: TyrePos,
    ) : TpmsFrame

    data class SensorId(
        val pos: TyrePos,
        val id: String,
    ) : TpmsFrame

    data class Handshake(
        val type: Int,
        val value: Int,
    ) : TpmsFrame

    data class Other(
        val bytes: ByteArray,
    ) : TpmsFrame
}

object TpmsProtocol {
    const val BAUD = 19200
    private const val H1 = 0x55
    private const val H2 = 0xAA

    fun frame(vararg body: Int): ByteArray {
        val f = ByteArray(body.size + 4)
        f[0] = H1.toByte()
        f[1] = H2.toByte()
        f[2] = f.size.toByte()
        body.forEachIndexed { i, b -> f[3 + i] = b.toByte() }
        var cs = 0
        for (i in 0 until f.size - 1) cs = cs xor (f[i].toInt() and 0xFF)
        f[f.size - 1] = cs.toByte()
        return f
    }

    fun heartbeat() = frame(0x19, 0x00)

    fun pair(pos: TyrePos) = frame(0x01, pos.code)

    fun stopPairing() = frame(0x06, 0x00)

    fun queryIds() = frame(0x07, 0x00)

    fun swap(
        a: TyrePos,
        b: TyrePos,
    ) = frame(0x03, a.code, b.code)

    fun handshake(seed: Int) = frame(0x5A, seed and 0xFF)

    fun decode(f: ByteArray): TpmsFrame {
        fun u(i: Int) = f[i].toInt() and 0xFF
        val len = u(2)
        if ((len == 8 || len == 10) && TyrePos.of(u(3)) != null) {
            val flags = u(6)
            return TpmsFrame.Tyre(
                TyreReading(
                    pos = TyrePos.of(u(3))!!,
                    kpa = Math.round(u(4) * 3.44).toInt(),
                    celsius = u(5) - 50,
                    leak = flags and 0x08 != 0,
                    pressureWarning = flags and 0x10 != 0,
                    noSignal = flags and 0x20 != 0,
                    battery = if (len == 10) u(7) / 10f else null,
                ),
            )
        }
        if (len == 6 && u(3) == 0x18) TyrePos.of(u(4))?.let { return TpmsFrame.Paired(it) }
        if (len == 6 && (u(3) == 0xA5 || u(3) == 0xB5)) return TpmsFrame.Handshake(u(3), u(4))
        if (len == 9) {
            TyrePos.ofIdIndex(u(3))?.let { pos ->
                return TpmsFrame.SensorId(pos, (4..7).joinToString("") { "%02X".format(u(it)) })
            }
        }
        return TpmsFrame.Other(f)
    }
}

/** Reassembles frames from the serial byte stream (resynchronises on 55 AA, checks the XOR). */
class TpmsParser(
    private val onFrame: (ByteArray) -> Unit,
) {
    private val buf = ByteArray(256)
    private var n = 0

    fun feed(bytes: ByteArray) {
        for (b in bytes) {
            if (n == buf.size) n = 0
            buf[n++] = b
            while (scan()) Unit
        }
    }

    private fun u(i: Int) = buf[i].toInt() and 0xFF

    /** True when something was consumed and scanning should continue. */
    private fun scan(): Boolean {
        if (n >= 1 && u(0) != 0x55) return drop(1)
        if (n >= 2 && u(1) != 0xAA) return drop(1)
        if (n < 3) return false
        val len = u(2)
        if (len < 5 || len > 32) return drop(1)
        if (n < len) return false
        var cs = 0
        for (i in 0 until len - 1) cs = cs xor u(i)
        if (cs == u(len - 1)) {
            onFrame(buf.copyOf(len))
            return drop(len)
        }
        return drop(1)
    }

    private fun drop(k: Int): Boolean {
        System.arraycopy(buf, k, buf, 0, n - k)
        n -= k
        return true
    }
}
