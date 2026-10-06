package org.librehu.launcher.speed

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Speeds in km/h, null when unknown (no fix, ELM327 not connected, stale for [SpeedMonitor.STALE_MS]). */
data class SpeedState(
    val gpsKmh: Float? = null,
    val obdKmh: Float? = null,
    val gpsPermission: Boolean = true,
)

/**
 * GPS speed of the head unit (LocationManager, speed of the fixes) and OBD speed sent by LibreHU-service
 * (`org.librehu.action.OBD` broadcast, about once a second, extra `SPEED`). Reference counted (widgets, cards).
 */
class SpeedMonitor private constructor(
    context: Context,
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(SpeedState())
    val state: StateFlow<SpeedState> = _state.asStateFlow()
    private var users = 0
    private var gpsAt = 0L
    private var obdAt = 0L

    /** Called on each change (widget redraw). */
    @Volatile
    var onChange: ((SpeedState) -> Unit)? = null

    private val gps =
        object : LocationListener {
            override fun onLocationChanged(l: Location) {
                if (!l.hasSpeed()) return
                gpsAt = System.currentTimeMillis()
                set(_state.value.copy(gpsKmh = l.speed * 3.6f))
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(
                provider: String?,
                status: Int,
                extras: Bundle?,
            ) = Unit

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = set(_state.value.copy(gpsKmh = null))
        }

    private val obd =
        object : BroadcastReceiver() {
            override fun onReceive(
                c: Context,
                intent: Intent,
            ) {
                val v = intent.getDoubleExtra(EXTRA_SPEED, Double.NaN)
                if (v.isNaN()) return
                obdAt = System.currentTimeMillis()
                set(_state.value.copy(obdKmh = v.toFloat()))
            }
        }

    private val stale =
        object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                var s = _state.value
                if (s.gpsKmh != null && now - gpsAt > STALE_MS) s = s.copy(gpsKmh = null)
                if (s.obdKmh != null && now - obdAt > STALE_MS) s = s.copy(obdKmh = null)
                set(s)
                main.postDelayed(this, 2_000)
            }
        }

    @SuppressLint("MissingPermission")
    fun start() {
        if (users++ > 0) return
        ContextCompat.registerReceiver(app, obd, IntentFilter(ACTION_OBD), ContextCompat.RECEIVER_EXPORTED)
        val granted =
            ContextCompat.checkSelfPermission(app, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        set(_state.value.copy(gpsPermission = granted))
        if (granted) {
            try {
                app
                    .getSystemService(
                        LocationManager::class.java,
                    ).requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, gps, Looper.getMainLooper())
            } catch (e: Exception) {
                Log.w("LibreHU-Launcher", "GPS speed: ${e.message}")
            }
        }
        main.postDelayed(stale, 2_000)
    }

    fun stop() {
        if (users == 0 || --users > 0) return
        runCatching { app.unregisterReceiver(obd) }
        runCatching { app.getSystemService(LocationManager::class.java).removeUpdates(gps) }
        main.removeCallbacks(stale)
    }

    private fun set(s: SpeedState) {
        if (s == _state.value) return
        _state.value = s
        onChange?.invoke(s)
    }

    companion object {
        const val ACTION_OBD = "org.librehu.action.OBD"
        const val EXTRA_SPEED = "SPEED"
        const val STALE_MS = 5_000L

        @Volatile
        private var instance: SpeedMonitor? = null

        fun get(context: Context): SpeedMonitor =
            instance ?: synchronized(this) { instance ?: SpeedMonitor(context).also { instance = it } }
    }
}
