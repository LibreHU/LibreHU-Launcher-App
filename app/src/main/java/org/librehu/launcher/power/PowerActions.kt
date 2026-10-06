package org.librehu.launcher.power

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

/**
 * Restart / shut down Android. REBOOT and SHUTDOWN are granted to privileged installs only; otherwise root (`su`,
 * Magisk) is used, else nothing happens and false is returned.
 */
object PowerActions {
    private const val TAG = "LibreHU-Launcher"
    private const val ACTION_REQUEST_SHUTDOWN = "com.android.internal.intent.action.REQUEST_SHUTDOWN"
    private const val EXTRA_KEY_CONFIRM = "android.intent.extra.KEY_CONFIRM"

    fun reboot(context: Context): Boolean {
        try {
            context.getSystemService(PowerManager::class.java).reboot(null)
            return true
        } catch (e: SecurityException) {
            Log.i(TAG, "reboot: ${e.message}")
        }
        return root("svc power reboot || reboot")
    }

    fun shutdown(context: Context): Boolean {
        try {
            context.startActivity(
                Intent(ACTION_REQUEST_SHUTDOWN)
                    .putExtra(EXTRA_KEY_CONFIRM, false)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return true
        } catch (e: Exception) {
            Log.i(TAG, "shutdown: ${e.message}")
        }
        return root("svc power shutdown || reboot -p")
    }

    private fun root(command: String): Boolean =
        try {
            val p = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            p.outputStream.close()
            // The command does not return when it works: give up waiting after a few seconds.
            !p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) || p.exitValue() == 0
        } catch (e: Exception) {
            Log.w(TAG, "su $command: ${e.message}")
            false
        }
}
