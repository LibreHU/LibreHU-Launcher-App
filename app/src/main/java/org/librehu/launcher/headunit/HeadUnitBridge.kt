package org.librehu.launcher.headunit

import android.app.ActivityManager
import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Head unit functions used by the launcher: volume, headlight state (automatic dark mode) and force stop. Each
 * branch provides its implementation: `main` plain Android, `ivi` Jancar ivi-services, `librehu-service`
 * LibreHU-service.
 */
interface HeadUnitBridge {
    fun volumeUp()

    fun volumeDown()

    /** Current volume step and its maximum (control center slider). */
    fun volume(): Pair<Int, Int>

    fun setVolume(step: Int)

    /** Headlights on / off, or null when the head unit does not report them (time of day is used instead). */
    val headlights: StateFlow<Boolean?> get() = NO_HEADLIGHTS

    /** Force stops [packageName]; false when only background processes could be killed. */
    fun forceStop(packageName: String): Boolean

    /** The head unit can restart through its MCU (power cycle of the SoC, works when Android hangs). */
    val canResetSoc: Boolean get() = false

    /** Restart through the MCU; false when not possible. */
    fun resetSoc(): Boolean = false

    fun release() {}

    companion object {
        private val NO_HEADLIGHTS = MutableStateFlow<Boolean?>(null)

        fun create(context: Context): HeadUnitBridge = AndroidHeadUnit(context)
    }
}

/** Plain Android: media stream volume, no headlight input, force stop when installed as a privileged app. */
open class AndroidHeadUnit(
    context: Context,
) : HeadUnitBridge {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val am = context.getSystemService(ActivityManager::class.java)

    override fun volumeUp() = audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)

    override fun volumeDown() = audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)

    override fun volume(): Pair<Int, Int> =
        audio.getStreamVolume(AudioManager.STREAM_MUSIC) to audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    override fun setVolume(step: Int) = audio.setStreamVolume(AudioManager.STREAM_MUSIC, step, AudioManager.FLAG_SHOW_UI)

    override fun forceStop(packageName: String): Boolean {
        // ActivityManager.forceStopPackage is a hidden API guarded by FORCE_STOP_PACKAGES (signature|privileged).
        try {
            ActivityManager::class.java.getMethod("forceStopPackage", String::class.java).invoke(am, packageName)
            return true
        } catch (e: Exception) {
            Log.i("LibreHU-Launcher", "forceStopPackage: ${e.cause ?: e}")
        }
        am.killBackgroundProcesses(packageName)
        return false
    }
}
