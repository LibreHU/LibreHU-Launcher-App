package org.librehu.launcher.headunit

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Jancar ivi-services, raw binder calls (reference: LibreHU-service docs/ivi-services/api.md):
 * - volume: IAudio 8 getParam / 6 getParamMaxValue / 9 setParam (param 10 = master volume), 39 showVolumeBar;
 * - headlights: ICar 14 getHeadLightStatus, polled;
 * - force stop: ISystem 15 closeApp(package), done by ivi-services with its system rights.
 */
class IviHeadUnit(
    private val context: Context,
) : AndroidHeadUnit(context) {
    private val audio = Remote(context, "audio", "com.jancar.services.audio.IAudio")
    private val car = Remote(context, "car", "com.jancar.services.car.ICar")
    private val system = Remote(context, "system", "com.jancar.services.system.ISystem")
    private val main = Handler(Looper.getMainLooper())

    private val _headlights = MutableStateFlow<Boolean?>(null)
    override val headlights: StateFlow<Boolean?> = _headlights

    private val poll =
        object : Runnable {
            override fun run() {
                car.call(TX_GET_HEADLIGHT, {}) { _headlights.value = it.readInt() != 0 }
                main.postDelayed(this, HEADLIGHT_POLL_MS)
            }
        }

    init {
        main.postDelayed(poll, 1000)
    }

    override fun volumeUp() = step(+1)

    override fun volumeDown() = step(-1)

    private fun step(delta: Int) {
        if (!audio.connected) {
            if (delta > 0) super.volumeUp() else super.volumeDown()
            return
        }
        var current: Int? = null
        var max: Int? = null
        audio.call(TX_GET_PARAM, { it.writeInt(PARAM_VOLUME) }) { current = it.readInt() }
        audio.call(TX_GET_PARAM_MAX, { it.writeInt(PARAM_VOLUME) }) { max = it.readInt() }
        val c = current ?: return
        val m = max ?: return
        audio.call(TX_SET_PARAM, {
            it.writeInt(PARAM_VOLUME)
            it.writeInt((c + delta).coerceIn(0, m))
        })
        audio.call(TX_SHOW_VOLUME_BAR, {})
    }

    override fun forceStop(packageName: String): Boolean =
        if (system.call(TX_CLOSE_APP, { it.writeString(packageName) })) true else super.forceStop(packageName)

    /** ivi-services' ISystem.reboot(): mute, power off the modules, then MCU reset of the SoC (PowerUtil.reboot()). */
    override val canResetSoc: Boolean get() = system.connected

    override fun resetSoc(): Boolean = system.call(TX_REBOOT, {})

    override fun release() {
        main.removeCallbacks(poll)
        audio.release()
        car.release()
        system.release()
    }

    /** One bound ivi-services interface. */
    private class Remote(
        private val context: Context,
        service: String,
        private val descriptor: String,
    ) {
        @Volatile
        private var binder: IBinder? = null
        private var bound = false
        val connected: Boolean get() = binder != null

        private val connection =
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName?,
                    service: IBinder?,
                ) {
                    binder = service
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    binder = null
                }
            }

        init {
            bound =
                try {
                    context.bindService(
                        Intent("com.jancar.services.action.$service").setPackage(PACKAGE),
                        connection,
                        Context.BIND_AUTO_CREATE,
                    )
                } catch (e: SecurityException) {
                    Log.w(TAG, "ivi-services $service: ${e.message}")
                    false
                }
        }

        /** True when the call went through. */
        fun call(
            code: Int,
            args: (Parcel) -> Unit,
            read: (Parcel) -> Unit = {},
        ): Boolean {
            val b = binder ?: return false
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeInterfaceToken(descriptor)
                args(data)
                b.transact(code, data, reply, 0)
                reply.readException()
                read(reply)
                true
            } catch (e: Exception) {
                Log.w(TAG, "$descriptor $code: ${e.message}")
                false
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        fun release() {
            if (bound) {
                try {
                    context.unbindService(connection)
                } catch (_: IllegalArgumentException) {
                }
            }
            bound = false
        }
    }

    private companion object {
        const val TAG = "LibreHU-Launcher"
        const val PACKAGE = "com.jancar.services"
        const val TX_GET_PARAM_MAX = 6
        const val TX_GET_PARAM = 8
        const val TX_SET_PARAM = 9
        const val TX_SHOW_VOLUME_BAR = 39
        const val PARAM_VOLUME = 10
        const val TX_GET_HEADLIGHT = 14
        const val TX_CLOSE_APP = 15
        const val TX_REBOOT = 9
        const val HEADLIGHT_POLL_MS = 3000L
    }
}
