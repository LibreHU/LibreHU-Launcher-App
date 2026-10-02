package org.librehu.launcher.headunit

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import android.widget.Toast
import org.librehu.service.ILibreHuService

/**
 * Head unit volume through LibreHU-service (https://github.com/LibreHU/LibreHU-service): master volume of the
 * audio processor (BD37534), shown in a short toast.
 */
class LibreHuVolume(
    private val context: Context,
) : HeadUnitBridge {
    @Volatile
    private var service: ILibreHuService? = null
    private var bound = false
    private val fallback = AndroidVolume(context)
    private var toast: Toast? = null

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                binder: IBinder?,
            ) {
                service = binder?.let { ILibreHuService.Stub.asInterface(it) }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
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

    override fun volumeUp() = step(+1)

    override fun volumeDown() = step(-1)

    private fun step(delta: Int) {
        val s = service
        if (s == null) {
            if (delta > 0) fallback.volumeUp() else fallback.volumeDown()
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

    override fun release() {
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
    }
}
