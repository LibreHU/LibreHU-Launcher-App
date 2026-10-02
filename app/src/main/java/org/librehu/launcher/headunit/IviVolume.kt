package org.librehu.launcher.headunit

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * Head unit volume through Jancar ivi-services (`com.jancar.services.audio.IAudio`, raw binder calls): master
 * volume parameter 10 of the audio processor, then Jancar's own volume bar. Reference: LibreHU-service
 * docs/ivi-services/api.md (IAudio 6 = getParamMaxValue, 8 = getParam, 9 = setParam, 39 = showVolumeBar).
 */
class IviVolume(
    private val context: Context,
) : HeadUnitBridge {
    @Volatile
    private var audio: IBinder? = null
    private var bound = false
    private val fallback = AndroidVolume(context)

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                audio = service
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                audio = null
            }
        }

    init {
        bound =
            try {
                context.bindService(Intent(ACTION).setPackage(PACKAGE), connection, Context.BIND_AUTO_CREATE)
            } catch (e: SecurityException) {
                Log.w(TAG, "ivi-services audio: ${e.message}")
                false
            }
    }

    override fun volumeUp() = step(+1)

    override fun volumeDown() = step(-1)

    private fun step(delta: Int) {
        if (audio == null) {
            if (delta > 0) fallback.volumeUp() else fallback.volumeDown()
            return
        }
        val current = callInt(TX_GET_PARAM, PARAM_VOLUME) ?: return
        val max = callInt(TX_GET_PARAM_MAX, PARAM_VOLUME) ?: return
        call(TX_SET_PARAM) {
            it.writeInt(PARAM_VOLUME)
            it.writeInt((current + delta).coerceIn(0, max))
        }
        call(TX_SHOW_VOLUME_BAR) {}
    }

    override fun release() {
        if (bound) {
            try {
                context.unbindService(connection)
            } catch (_: IllegalArgumentException) {
            }
        }
        bound = false
    }

    private fun callInt(
        code: Int,
        arg: Int,
    ): Int? {
        var result: Int? = null
        call(code, { it.writeInt(arg) }) { result = it.readInt() }
        return result
    }

    private fun call(
        code: Int,
        args: (Parcel) -> Unit,
        read: (Parcel) -> Unit = {},
    ) {
        val b = audio ?: return
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            args(data)
            b.transact(code, data, reply, 0)
            reply.readException()
            read(reply)
        } catch (e: Exception) {
            Log.w(TAG, "IAudio $code: ${e.message}")
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private companion object {
        const val TAG = "LibreHU-Launcher"
        const val PACKAGE = "com.jancar.services"
        const val ACTION = "com.jancar.services.action.audio"
        const val DESCRIPTOR = "com.jancar.services.audio.IAudio"
        const val TX_GET_PARAM_MAX = 6
        const val TX_GET_PARAM = 8
        const val TX_SET_PARAM = 9
        const val TX_SHOW_VOLUME_BAR = 39
        const val PARAM_VOLUME = 10
    }
}
