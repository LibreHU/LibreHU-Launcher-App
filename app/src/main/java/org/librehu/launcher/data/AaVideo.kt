package org.librehu.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import android.view.Surface

/**
 * Android Auto's picture in the dashboard card (experimental): the card's surface goes to Open Headunit LibreHU
 * (`LibreHuVideoService`, signature permission `org.librehu.headunit.permission.CONFIGURE`), whose decoder renders
 * there while its projection screen is not shown; touches go back as fractions of the card. One card at a time.
 */
class AaVideo(
    context: Context,
) {
    private val app = context.applicationContext
    private var service: Messenger? = null
    private var pending: Surface? = null
    private var bound = false

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName,
                binder: IBinder,
            ) {
                service = Messenger(binder)
                pending?.let { send(MSG_ATTACH, Bundle().apply { putParcelable("surface", it) }) }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                service = null
            }

            override fun onNullBinding(name: ComponentName) {
                // Switched off in Android Auto (Settings > Car and keys).
                Log.i(TAG, "picture refused by Android Auto")
            }
        }

    /** The card's surface is ready (SurfaceHolder.surfaceCreated). */
    fun attach(surface: Surface) {
        pending = surface
        if (!bound) {
            bound =
                try {
                    app.bindService(Intent().setComponent(ComponentName(PACKAGE, SERVICE)), connection, Context.BIND_AUTO_CREATE)
                } catch (e: SecurityException) {
                    // The signature permission is granted only when Android Auto was installed before the launcher.
                    Log.w(TAG, "bind: ${e.message}")
                    false
                }
        } else {
            send(MSG_ATTACH, Bundle().apply { putParcelable("surface", surface) })
        }
    }

    /** The card's surface is going away (SurfaceHolder.surfaceDestroyed): the decoder must let it go first. */
    fun detach() {
        pending = null
        send(MSG_DETACH, null)
        if (bound) {
            runCatching { app.unbindService(connection) }
            bound = false
        }
        service = null
    }

    /** A touch on the card: [action] is the MotionEvent action, [x] and [y] fractions of the card. */
    fun touch(
        action: Int,
        x: Float,
        y: Float,
    ) {
        send(
            MSG_TOUCH,
            Bundle().apply {
                putInt("action", action)
                putFloat("x", x)
                putFloat("y", y)
            },
        )
    }

    private fun send(
        what: Int,
        data: Bundle?,
    ) {
        val s = service ?: return
        try {
            s.send(Message.obtain(null, what).apply { if (data != null) this.data = data })
        } catch (e: RemoteException) {
            Log.w(TAG, "send $what: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "LibreHU-AA-video"
        private const val PACKAGE = "org.librehu.headunit"
        private const val SERVICE = "com.andrerinas.openheadunit.librehu.LibreHuVideoService"
        private const val MSG_ATTACH = 1
        private const val MSG_DETACH = 2
        private const val MSG_TOUCH = 3
    }
}
