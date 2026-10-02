package org.librehu.launcher.headunit

import android.content.Context
import android.media.AudioManager

/**
 * Head unit functions used by the launcher rail (volume). Each branch provides its implementation: `main` Android
 * media volume, `ivi` Jancar ivi-services, `librehu-service` LibreHU-service.
 */
interface HeadUnitBridge {
    fun volumeUp()

    fun volumeDown()

    fun release() {}

    companion object {
        fun create(context: Context): HeadUnitBridge = AndroidVolume(context)
    }
}

/** Android media stream volume, with the system volume panel. */
class AndroidVolume(
    context: Context,
) : HeadUnitBridge {
    private val audio = context.getSystemService(AudioManager::class.java)

    override fun volumeUp() = audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)

    override fun volumeDown() = audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
}
