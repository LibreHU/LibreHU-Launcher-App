package org.librehu.launcher.lock

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.librehu.launcher.R
import org.librehu.launcher.data.MediaRepository
import org.librehu.launcher.standby.StandbyFace
import org.librehu.launcher.standby.StandbyStore
import org.librehu.launcher.ui.CarColors
import org.librehu.launcher.ui.CarTheme
import kotlin.math.roundToInt

/**
 * Lock screen: clock (or black screen), unlocked by sliding or with a PIN. Keys other than volume are swallowed;
 * the launcher brings it back when Home is pressed while locked.
 */
class LockActivity : ComponentActivity() {
    private lateinit var store: LockStore
    private lateinit var media: MediaRepository
    private lateinit var audio: AudioManager
    private var paused = false
    private val awake = mutableStateOf(true)
    private val wakeSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LockStore.get(this)
        media = MediaRepository(this)
        audio = getSystemService(AudioManager::class.java)
        setShowWhenLocked(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )
        if (!store.locked) {
            store.locked = true
            val s = store.settings.value
            if (s.pauseMedia && audio.isMusicActive) {
                mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
                paused = true
            }
        }
        setContent { CarTheme { Screen() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Lock action again (power key): unlocks when there is no PIN and the toggle is on.
        val s = store.settings.value
        if (intent.action == LockStore.ACTION_LOCK && s.toggle && s.pin.isEmpty()) unlock() else wake()
    }

    override fun onStart() {
        super.onStart()
        media.start()
    }

    override fun onStop() {
        media.stop()
        super.onStop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_UP) wake()
        return true
    }

    private fun wake() {
        awake.value = true
        wakeSignal.intValue++
        applyBrightness()
    }

    private fun applyBrightness() {
        val black = store.settings.value.look == LockLook.BLACK && !awake.value
        window.attributes =
            window.attributes.apply {
                screenBrightness = if (black) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
    }

    private fun unlock() {
        store.locked = false
        if (paused) mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        paused = false
        finish()
    }

    private fun mediaKey(code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    @Composable
    private fun Screen() {
        val s by store.settings.collectAsStateWithLifecycle()
        val standby by StandbyStore
            .get(this)
            .settings
            .collectAsStateWithLifecycle()
        val np by media.nowPlaying.collectAsStateWithLifecycle()
        // Black look: the controls hide (and the screen dims) 10 s after the last touch.
        LaunchedEffect(wakeSignal.intValue, s.look) {
            awake.value = true
            applyBrightness()
            if (s.look == LockLook.BLACK) {
                delay(10_000)
                awake.value = false
                applyBrightness()
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(interactionSource = null, indication = null) { wake() },
        ) {
            if (awake.value) {
                if (s.look == LockLook.CLOCK) StandbyFace(standby.copy(showMedia = true), np)
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (s.pin.isEmpty()) {
                        SlideToUnlock { unlock() }
                    } else {
                        PinPad(s.pin) { unlock() }
                    }
                }
            }
        }
    }

    @Composable
    private fun SlideToUnlock(onUnlock: () -> Unit) {
        BoxWithConstraints(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.6f)
                .height(80.dp)
                .clip(RoundedCornerShape(40.dp))
                .background(Color(0x33FFFFFF)),
            contentAlignment = Alignment.CenterStart,
        ) {
            val density = LocalDensity.current
            val thumb = 72.dp
            val maxPx = with(density) { (maxWidth - thumb - 8.dp).toPx() }
            var x by remember { mutableFloatStateOf(0f) }
            Text(
                stringResource(R.string.lock_slide),
                color = Color(0xCCFFFFFF),
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.Center),
            )
            Box(
                Modifier
                    .offset { IntOffset(x.roundToInt() + with(density) { 4.dp.roundToPx() }, 0) }
                    .size(thumb)
                    .clip(CircleShape)
                    .background(CarColors.Accent)
                    .pointerInput(maxPx) {
                        detectHorizontalDragGestures(
                            onDragEnd = { if (x > maxPx * 0.85f) onUnlock() else x = 0f },
                            onDragCancel = { x = 0f },
                        ) { _, dx ->
                            wake()
                            x = (x + dx).coerceIn(0f, maxPx)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.LockOpen, null, tint = CarColors.OnAccent, modifier = Modifier.size(34.dp))
            }
        }
    }

    @Composable
    private fun PinPad(
        pin: String,
        onUnlock: () -> Unit,
    ) {
        var typed by remember { mutableStateOf("") }
        var wrong by remember { mutableStateOf(false) }
        Column(
            Modifier.clip(RoundedCornerShape(28.dp)).background(Color(0xCC202124)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (wrong) {
                        stringResource(
                            R.string.lock_wrong_pin,
                        )
                    } else {
                        "•".repeat(typed.length).ifEmpty { stringResource(R.string.lock_enter_pin) }
                    },
                    color = if (wrong) Color(0xFFF28B82) else Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            val rows = listOf(listOf("1", "2", "3", "4", "5"), listOf("6", "7", "8", "9", "0"))
            for (row in rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (d in row) {
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFFFFF))
                                .clickable {
                                    wake()
                                    wrong = false
                                    typed += d
                                    if (typed.length >= pin.length) {
                                        if (typed == pin) onUnlock() else wrong = true
                                        typed = ""
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text(d, color = Color.White, fontSize = 26.sp) }
                    }
                }
            }
            Box(
                Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { typed = typed.dropLast(1) }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.lock_erase), tint = Color.White) }
        }
    }

    companion object {
        /** Brings the lock screen back (launcher shown while locked): never toggles. */
        fun show(context: Context) {
            context.startActivity(Intent(context, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        /** Locks; when already locked, the same action unlocks if there is no PIN (power key toggle). */
        fun lock(context: Context) {
            context.startActivity(
                Intent(context, LockActivity::class.java)
                    .setAction(LockStore.ACTION_LOCK)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
