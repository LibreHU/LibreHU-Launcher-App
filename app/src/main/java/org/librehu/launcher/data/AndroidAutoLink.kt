package org.librehu.launcher.data

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/** Android Auto session reported by Open Headunit. */
data class AaState(
    /** connecting, connected, projecting; empty when there is no session. */
    val state: String = "",
    /** usb, wifi, self. */
    val transport: String = "",
) {
    val active get() = state == "connecting" || state == "connected" || state == "projecting"
}

/** Next maneuver of Android Auto's navigation (NAVIGATION_UPDATE of Open Headunit LibreHU). */
data class AaManeuver(
    /** "Turn", "Exit ramp"… in the phone's language. */
    val action: String,
    /** 1 left, 2 right, 3 unknown. */
    val side: Int,
    val distanceMeters: Int,
    val road: String,
    val totalDistanceMeters: Int,
    val arrival: String,
)

/**
 * Android Auto through Open Headunit (or its LibreHU fork) for the dashboard: its session
 * (`com.andrerinas.headunitrevived.SESSION_STATE`), the next maneuver of the navigation (`NAVIGATION_UPDATE`, sent
 * by the fork with a signature permission the LibreHU apps hold) and two commands (show, disconnect).
 */
object AndroidAutoLink {
    private const val TAG = "LibreHU-AA"
    private const val SESSION_STATE = "com.andrerinas.headunitrevived.SESSION_STATE"
    private const val NAVIGATION_UPDATE = "com.andrerinas.headunitrevived.NAVIGATION_UPDATE"
    private const val RECEIVER = "com.andrerinas.openheadunit.automation.AutomationReceiver"
    private const val RAISE = "com.andrerinas.openheadunit.ACTION_RAISE_PROJECTION"
    private const val DISCONNECT = "com.andrerinas.openheadunit.ACTION_DISCONNECT"
    private const val GET_SETTINGS = "com.andrerinas.openheadunit.ACTION_GET_SETTINGS"
    private const val SET_SETTINGS = "com.andrerinas.openheadunit.ACTION_SET_SETTINGS"

    /** The fork, which takes settings commands from the apps signed with the LibreHU key. */
    private const val FORK = "org.librehu.headunit"

    /** Open Headunit's decoder low-latency request ("vdec-lowlatency" on MediaTek decoders below Android 11). */
    private const val LOW_LATENCY = "debug-video-low-latency"

    /** The fork first, then Open Headunit. */
    private val PACKAGES = listOf("org.librehu.headunit", "com.andrerinas.headunitrevived")

    private val _state = MutableStateFlow(AaState())
    val state: StateFlow<AaState> = _state.asStateFlow()

    private val _maneuver = MutableStateFlow<AaManeuver?>(null)
    val maneuver: StateFlow<AaManeuver?> = _maneuver.asStateFlow()

    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        ContextCompat.registerReceiver(
            app,
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context,
                    intent: Intent,
                ) {
                    val s = AaState(intent.getStringExtra("state").orEmpty(), intent.getStringExtra("transport").orEmpty())
                    _state.value = if (s.active) s else AaState()
                    if (!s.active) _maneuver.value = null
                }
            },
            IntentFilter(SESSION_STATE),
            ContextCompat.RECEIVER_EXPORTED,
        )
        ContextCompat.registerReceiver(
            app,
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context,
                    i: Intent,
                ) {
                    val distance = i.getIntExtra("distance_meters", -1)
                    val action = i.getStringExtra("action_text").orEmpty()
                    _maneuver.value =
                        if (action.isEmpty() && distance < 0) {
                            null
                        } else {
                            AaManeuver(
                                action,
                                i.getIntExtra("turn_side", 3),
                                distance,
                                i.getStringExtra("road").orEmpty(),
                                i.getIntExtra("total_distance_meters", -1),
                                i.getStringExtra("estimated_arrival").orEmpty(),
                            )
                        }
                }
            },
            // Delivered only to apps holding NAV_PERMISSION (manifest), which the fork sends it with.
            IntentFilter(NAVIGATION_UPDATE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    /** Projection to the front. */
    fun show(context: Context) = send(context, RAISE)

    fun disconnect(context: Context) = send(context, DISCONNECT)

    private fun send(
        context: Context,
        action: String,
    ) {
        val pkg =
            PACKAGES.firstOrNull {
                try {
                    context.packageManager.getPackageInfo(it, 0)
                    true
                } catch (_: PackageManager.NameNotFoundException) {
                    false
                }
            } ?: return
        runCatching { context.sendBroadcast(Intent(action).setComponent(ComponentName(pkg, RECEIVER))) }
            .onFailure { Log.w(TAG, "$action: ${it.message}") }
    }

    /** The fork's decoder low-latency request, or null when it is not installed or does not answer. */
    suspend fun videoLowLatency(context: Context): Boolean? {
        val reply = ask(context, GET_SETTINGS, null) ?: return null
        if (!reply.optBoolean("ok")) return null
        val settings = reply.optJSONObject("settings")?.optJSONObject("settings") ?: return null
        return if (settings.has(LOW_LATENCY)) settings.optBoolean(LOW_LATENCY) else null
    }

    /** Sets it; true when the fork took it (it applies to the next projection). */
    suspend fun setVideoLowLatency(
        context: Context,
        on: Boolean,
    ): Boolean {
        val json =
            JSONObject()
                .put("format", "open-headunit-settings")
                .put("version", 1)
                .put("settings", JSONObject().put(LOW_LATENCY, on))
        return ask(context, SET_SETTINGS, Bundle().apply { putString("json", json.toString()) })?.optBoolean("ok") == true
    }

    /** A settings command to the fork with its JSON reply (ordered broadcast), null when refused or not installed. */
    private suspend fun ask(
        context: Context,
        action: String,
        extras: Bundle?,
    ): JSONObject? {
        if (runCatching { context.packageManager.getPackageInfo(FORK, 0) }.isFailure) return null
        return suspendCancellableCoroutine { cont ->
            try {
                context.sendOrderedBroadcast(
                    Intent(action).setComponent(ComponentName(FORK, RECEIVER)).apply { extras?.let { putExtras(it) } },
                    null,
                    object : BroadcastReceiver() {
                        override fun onReceive(
                            c: Context,
                            i: Intent,
                        ) {
                            val data = resultData
                            if (cont.isActive) cont.resume(data?.let { runCatching { JSONObject(it) }.getOrNull() })
                        }
                    },
                    Handler(Looper.getMainLooper()),
                    Activity.RESULT_CANCELED,
                    null,
                    null,
                )
            } catch (e: Exception) {
                Log.w(TAG, "$action: ${e.message}")
                if (cont.isActive) cont.resume(null)
            }
        }
    }
}
