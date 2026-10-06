package org.librehu.launcher.status

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GpsFix { NO_PERMISSION, OFF, SEARCHING, FIX }

/** The head unit's own GNSS receiver (not the phone's). */
data class GpsStatus(
    val fix: GpsFix = GpsFix.OFF,
    val inView: Int = 0,
    val used: Int = 0,
    val accuracyM: Float = 0f,
)

/**
 * GNSS state of the head unit: satellites in view / used ([GnssStatus]) and the age of the last fix. The receiver only
 * reports satellites while a location request is active: one is kept (2 s) while the launcher shows the status.
 */
class GpsStatusWatcher private constructor(
    context: Context,
) {
    private val app = context.applicationContext
    private val lm = app.getSystemService(LocationManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(GpsStatus())
    val state: StateFlow<GpsStatus> = _state.asStateFlow()
    private var users = 0
    private var lastFixAt = 0L
    private var accuracy = 0f
    private var inView = 0
    private var used = 0

    private val gnss =
        object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                inView = status.satelliteCount
                used = (0 until status.satelliteCount).count { status.usedInFix(it) }
                publish()
            }

            override fun onStopped() {
                inView = 0
                used = 0
                publish()
            }
        }

    private val listener =
        object : LocationListener {
            override fun onLocationChanged(location: Location) {
                lastFixAt = SystemClock.elapsedRealtime()
                accuracy = location.accuracy
                publish()
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(
                provider: String?,
                status: Int,
                extras: Bundle?,
            ) = Unit

            override fun onProviderEnabled(provider: String) = publish()

            override fun onProviderDisabled(provider: String) = publish()
        }

    /** A fix older than this is lost. */
    private val tick =
        object : Runnable {
            override fun run() {
                publish()
                main.postDelayed(this, 5_000)
            }
        }

    fun allowed() = app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (users++ > 0) return
        if (allowed()) {
            runCatching {
                lm.registerGnssStatusCallback(gnss, main)
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 0f, listener, Looper.getMainLooper())
            }
        }
        main.post(tick)
    }

    /** Permission just granted: listen now. */
    fun restart() {
        if (users == 0) return
        val n = users
        users = 1
        stop()
        start()
        users = n
    }

    fun stop() {
        if (users == 0 || --users > 0) return
        runCatching {
            lm.unregisterGnssStatusCallback(gnss)
            lm.removeUpdates(listener)
        }
        main.removeCallbacks(tick)
    }

    private fun publish() {
        val enabled = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val fresh = lastFixAt > 0 && SystemClock.elapsedRealtime() - lastFixAt < FIX_MAX_AGE_MS
        _state.value =
            GpsStatus(
                fix =
                    when {
                        !allowed() -> GpsFix.NO_PERMISSION
                        !enabled -> GpsFix.OFF
                        fresh -> GpsFix.FIX
                        else -> GpsFix.SEARCHING
                    },
                inView = inView,
                used = used,
                accuracyM = accuracy,
            )
    }

    companion object {
        private const val FIX_MAX_AGE_MS = 10_000L

        @Volatile
        private var instance: GpsStatusWatcher? = null

        fun get(context: Context): GpsStatusWatcher =
            instance ?: synchronized(this) { instance ?: GpsStatusWatcher(context).also { instance = it } }
    }
}
