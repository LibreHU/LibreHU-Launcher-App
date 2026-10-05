package org.librehu.launcher.standby

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.librehu.launcher.data.MediaRepository
import org.librehu.launcher.ui.CarTheme

/**
 * Full screen standby clock. Any touch or key closes it. Optionally pauses the media while shown and resumes it after
 * (only if it was playing), and lowers the screen brightness for this window only.
 */
class StandbyActivity : ComponentActivity() {
    private lateinit var media: MediaRepository
    private lateinit var audio: AudioManager
    private var paused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = StandbyStore.get(this)
        val s = store.settings.value
        media = MediaRepository(this)
        audio = getSystemService(AudioManager::class.java)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(true)
        if (s.brightness >= 0f) {
            window.attributes = window.attributes.apply { screenBrightness = s.brightness.coerceIn(0.01f, 1f) }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (s.pauseMedia && audio.isMusicActive) {
            mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
            paused = true
        }
        setContent {
            CarTheme {
                val settings by store.settings.collectAsStateWithLifecycle()
                val np by media.nowPlaying.collectAsStateWithLifecycle()
                StandbyFace(settings, np)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        media.start()
    }

    override fun onStop() {
        media.stop()
        super.onStop()
        // Leaving (home, another app, screen off…): the clock is not a place to come back to.
        if (!isChangingConfigurations) finish()
    }

    override fun onDestroy() {
        if (paused) mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_UP) finish()
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Volume keys keep working; any other key wakes up.
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        ) {
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_UP) finish()
        return true
    }

    private fun mediaKey(code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    companion object {
        fun show(context: Context) {
            context.startActivity(Intent(context, StandbyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
