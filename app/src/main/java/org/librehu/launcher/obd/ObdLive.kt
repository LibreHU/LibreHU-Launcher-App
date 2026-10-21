package org.librehu.launcher.obd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Last OBD values sent by LibreHU-service; empty when nothing came for [STALE_MS] (adapter off). */
data class ObdLiveState(
    /** ObdPid names of the service (RPM, SPEED…) and BATTERY → value. */
    val values: Map<String, Double> = emptyMap(),
    /** Manufacturer values: "<ECU>/<value>" → text shown (with its unit). */
    val car: Map<String, String> = emptyMap(),
    /** Car battery charge, engine off (null engine running or unknown). */
    val batteryPercent: Int? = null,
    /** ENGINE_OFF, CHARGING, NOT_CHARGING, OVERVOLTAGE; null unknown. */
    val batteryStatus: String? = null,
) {
    val connected: Boolean get() = values.isNotEmpty()
    val battery: Double? get() = values[BATTERY]

    companion object {
        const val BATTERY = "BATTERY"
    }
}

/**
 * LibreHU-service's `org.librehu.action.OBD` broadcast (about once a second while its ELM327 adapter is connected):
 * standard values, manufacturer values (`car:` numbers, `cartext:` texts) and the car battery. Reference counted
 * (OBD screen, lock screen, standby clock).
 */
object ObdLive {
    const val ACTION_OBD = "org.librehu.action.OBD"
    private const val STALE_MS = 5_000L

    private val _state = MutableStateFlow(ObdLiveState())
    val state: StateFlow<ObdLiveState> = _state.asStateFlow()

    private var users = 0
    private var lastAt = 0L
    private val main = Handler(Looper.getMainLooper())

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                c: Context,
                intent: Intent,
            ) {
                val extras = intent.extras ?: return
                val values = HashMap<String, Double>()
                val car = HashMap<String, String>()
                for (k in extras.keySet()) {
                    when {
                        k.startsWith("cartext:") -> extras.getString(k)?.let { car[k.removePrefix("cartext:")] = it }
                        k.startsWith("car:") -> {}
                        k == "BATTERY_PERCENT" || k == "BATTERY_STATUS" -> {}
                        else -> (extras.get(k) as? Double)?.let { values[k] = it }
                    }
                }
                lastAt = System.currentTimeMillis()
                _state.value =
                    ObdLiveState(
                        values = values,
                        car = car,
                        batteryPercent = extras.getInt("BATTERY_PERCENT", -1).takeIf { it >= 0 },
                        batteryStatus = extras.getString("BATTERY_STATUS"),
                    )
            }
        }

    private val stale =
        object : Runnable {
            override fun run() {
                if (_state.value.connected && System.currentTimeMillis() - lastAt > STALE_MS) _state.value = ObdLiveState()
                main.postDelayed(this, 2_000)
            }
        }

    fun start(context: Context) {
        if (users++ > 0) return
        ContextCompat.registerReceiver(context.applicationContext, receiver, IntentFilter(ACTION_OBD), ContextCompat.RECEIVER_EXPORTED)
        main.postDelayed(stale, 2_000)
    }

    fun stop(context: Context) {
        if (users == 0 || --users > 0) return
        runCatching { context.applicationContext.unregisterReceiver(receiver) }
        main.removeCallbacks(stale)
        _state.value = ObdLiveState()
    }
}
