package org.librehu.launcher.status

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.Method

/**
 * "Where is my phone": Bluetooth has no profile to ring or vibrate a phone, so the head unit sends, through the phone
 * itself (MAP client, the profile cars use for text messages), a text message to the phone's own number. The phone
 * plays its message sound and / or vibrates, as set on it (nothing in "do not disturb").
 *
 * The number is the one the phone gives over hands-free (AT+CNUM, "subscriber number"), or typed in the settings.
 * The phone must allow access to messages for the head unit (asked on the phone the first time). An SMS may cost.
 */
object FindPhone {
    private const val TAG = "LibreHU-Launcher"
    private const val MAP_CLIENT = 18
    private const val PREFS = "find_phone"
    private const val KEY_NUMBER = "number"
    private const val KEY_DETECTED = "detected"
    private const val CONNECT_WAIT_MS = 10_000L
    private const val POLL_MS = 500L

    enum class Result { SENT, NO_NUMBER, NO_PHONE, NO_MAP, REFUSED }

    /** Number typed in the settings, else the one the phone gave. */
    fun number(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getString(KEY_NUMBER, null)?.takeIf { it.isNotBlank() } ?: p.getString(KEY_DETECTED, "").orEmpty()
    }

    fun typedNumber(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NUMBER, "").orEmpty()

    fun detectedNumber(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DETECTED, "").orEmpty()

    fun setNumber(
        context: Context,
        number: String,
    ) = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_NUMBER, number.trim())
        .apply()

    /** The phone's own number from a hands-free event (subscriber info). */
    fun onSubscriberNumber(
        context: Context,
        number: String?,
    ) {
        val n = number?.trim().orEmpty()
        if (n.length < 4) return
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DETECTED, n)
            .apply()
    }

    /** Sends the message; [done] on the main thread. */
    @SuppressLint("MissingPermission")
    fun ring(
        context: Context,
        message: String,
        done: (Result) -> Unit,
    ) {
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        val number = number(app)
        if (number.isBlank()) return done(Result.NO_NUMBER)
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return done(Result.NO_PHONE)
        exemptHiddenApi()
        val ok =
            try {
                adapter.getProfileProxy(
                    app,
                    object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(
                            profile: Int,
                            proxy: BluetoothProfile,
                        ) {
                            Thread({
                                val r = send(adapter, proxy, number, message)
                                main.post {
                                    runCatching { adapter.closeProfileProxy(MAP_CLIENT, proxy) }
                                    done(r)
                                }
                            }, "find-phone").start()
                        }

                        override fun onServiceDisconnected(profile: Int) {}
                    },
                    MAP_CLIENT,
                )
            } catch (e: Exception) {
                Log.w(TAG, "MAP client: ${e.message}")
                false
            }
        if (!ok) done(Result.NO_MAP)
    }

    @SuppressLint("MissingPermission")
    private fun send(
        adapter: BluetoothAdapter,
        proxy: BluetoothProfile,
        number: String,
        message: String,
    ): Result {
        var device = runCatching { proxy.connectedDevices.firstOrNull() }.getOrNull()
        if (device == null) {
            // The phone connected for hands-free (or the first paired one), then the MAP link to it.
            val target =
                runCatching {
                    adapter.bondedDevices.firstOrNull { d -> call(d, "isConnected") == true } ?: adapter.bondedDevices.firstOrNull()
                }.getOrNull() ?: return Result.NO_PHONE
            call(proxy, "connect", arrayOf(BluetoothDevice::class.java), target)
            val until = System.currentTimeMillis() + CONNECT_WAIT_MS
            while (device == null && System.currentTimeMillis() < until) {
                Thread.sleep(POLL_MS)
                device = runCatching { proxy.connectedDevices.firstOrNull() }.getOrNull()
            }
            if (device == null) return Result.REFUSED
        }
        val sent =
            call(
                proxy,
                "sendMessage",
                arrayOf(
                    BluetoothDevice::class.java,
                    Array<Uri>::class.java,
                    String::class.java,
                    android.app.PendingIntent::class.java,
                    android.app.PendingIntent::class.java,
                ),
                device,
                arrayOf(Uri.parse("tel:" + number.filter { it.isDigit() || it == '+' })),
                message,
                null,
                null,
            )
        return if (sent == true) Result.SENT else Result.REFUSED
    }

    private fun call(
        target: Any,
        name: String,
        types: Array<Class<*>> = emptyArray(),
        vararg args: Any?,
    ): Any? =
        try {
            var c: Class<*>? = target.javaClass
            var m: Method? = null
            while (c != null && m == null) {
                m = runCatching { c.getDeclaredMethod(name, *types) }.getOrNull()
                c = c.superclass
            }
            m?.apply { isAccessible = true }?.invoke(target, *args)
        } catch (e: Exception) {
            Log.w(TAG, "$name: ${e.cause ?: e}")
            null
        }

    /** Android 9 hides the MAP client's methods; lifted for this process (as LibreHU-service does for HFP). */
    private fun exemptHiddenApi() {
        try {
            val forName = Class::class.java.getDeclaredMethod("forName", String::class.java)
            val getDeclaredMethod =
                Class::class.java.getDeclaredMethod("getDeclaredMethod", String::class.java, arrayOf<Class<*>>()::class.java)
            val vmRuntime = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
            val getRuntime = getDeclaredMethod.invoke(vmRuntime, "getRuntime", null) as Method
            val setExemptions =
                getDeclaredMethod.invoke(vmRuntime, "setHiddenApiExemptions", arrayOf<Class<*>>(Array<String>::class.java)) as Method
            setExemptions.invoke(getRuntime.invoke(null), *arrayOf<Any?>(arrayOf("L")))
        } catch (e: Throwable) {
            Log.w(TAG, "Hidden API exemption: $e")
        }
    }
}
