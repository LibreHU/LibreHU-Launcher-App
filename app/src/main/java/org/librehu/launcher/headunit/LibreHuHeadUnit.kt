package org.librehu.launcher.headunit

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.librehu.service.ILibreHuCallback
import org.librehu.service.ILibreHuService

/**
 * LibreHU-service (https://github.com/LibreHU/LibreHU-service): master volume of the audio processor and
 * headlight state from the MCU (vehicle flags) for the automatic dark mode. Force stop: plain Android (privileged
 * install).
 */
class LibreHuHeadUnit(
    private val context: Context,
) : AndroidHeadUnit(context) {
    @Volatile
    private var service: ILibreHuService? = null
    private var bound = false
    private var toast: Toast? = null

    private val _headlights = MutableStateFlow<Boolean?>(null)
    override val headlights: StateFlow<Boolean?> = _headlights

    private val callback =
        object : ILibreHuCallback.Stub() {
            override fun onVehicleFlags(flags: Int) = updateFlags(flags)

            override fun onAudioChanged() {}

            override fun onMcuFrame(
                cmd: Int,
                data: ByteArray?,
                fromMcu: Boolean,
            ) {}

            override fun onKey(
                channel: Int,
                values: IntArray?,
                released: Boolean,
                learning: Boolean,
            ) {}

            override fun onCanData(data: ByteArray?) {}
        }

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                binder: IBinder?,
            ) {
                val s = binder?.let { ILibreHuService.Stub.asInterface(it) } ?: return
                service = s
                try {
                    s.registerCallback(callback)
                    updateFlags(s.vehicleFlags)
                } catch (e: RemoteException) {
                    Log.w(TAG, "LibreHU-service: ${e.message}")
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                _headlights.value = null
            }
        }

    init {
        bound =
            try {
                context.bindService(Intent(ACTION_BIND).setPackage(PACKAGE), connection, Context.BIND_AUTO_CREATE)
            } catch (e: SecurityException) {
                Log.w(TAG, "LibreHU-service: ${e.message}")
                false
            }
    }

    /** Headlights are known only while the MCU link is up. */
    private fun updateFlags(flags: Int) {
        _headlights.value = if (flags and FLAG_MCU_ONLINE != 0) flags and FLAG_HEADLIGHT != 0 else null
    }

    override fun volumeUp() = step(+1)

    override fun volumeDown() = step(-1)

    private fun step(delta: Int) {
        val s = service
        if (s == null) {
            if (delta > 0) super.volumeUp() else super.volumeDown()
            return
        }
        try {
            val v = (s.volume + delta).coerceIn(0, s.maxVolume)
            s.setVolume(v)
            toast?.cancel()
            toast = Toast.makeText(context, "Volume $v / ${s.maxVolume}", Toast.LENGTH_SHORT).also { it.show() }
        } catch (e: RemoteException) {
            Log.w(TAG, "volume: ${e.message}")
        }
    }

    /** Master volume of the audio processor (the slider of the control center). */
    override fun volume(): Pair<Int, Int> {
        val s = service ?: return super.volume()
        return try {
            s.volume to s.maxVolume
        } catch (e: RemoteException) {
            super.volume()
        }
    }

    override fun setVolume(step: Int) {
        val s = service
        if (s == null) {
            super.setVolume(step)
            return
        }
        try {
            s.setVolume(step.coerceIn(0, s.maxVolume))
        } catch (e: RemoteException) {
            Log.w(TAG, "volume: ${e.message}")
        }
    }

    /** API 5: the service asks its MCU for a power cycle of the SoC (command 0E). */
    override val canResetSoc: Boolean get() = runCatching { (service?.apiVersion ?: 0) >= 5 }.getOrDefault(false)

    override fun resetSoc(): Boolean {
        val s = service ?: return false
        return try {
            if (s.apiVersion < 5) return false
            s.resetSoc()
            true
        } catch (e: RemoteException) {
            Log.w(TAG, "resetSoc: ${e.message}")
            false
        }
    }

    override fun release() {
        try {
            service?.unregisterCallback(callback)
        } catch (_: RemoteException) {
        }
        if (bound) {
            try {
                context.unbindService(connection)
            } catch (_: IllegalArgumentException) {
            }
        }
        bound = false
    }

    private companion object {
        const val TAG = "LibreHU-Launcher"
        const val PACKAGE = "org.librehu.service"
        const val ACTION_BIND = "org.librehu.service.BIND"
        const val FLAG_MCU_ONLINE = 1 shl 0
        const val FLAG_HEADLIGHT = 1 shl 3
    }
}
