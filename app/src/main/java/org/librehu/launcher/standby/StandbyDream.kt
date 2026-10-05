package org.librehu.launcher.standby

import android.service.dreams.DreamService
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import org.librehu.launcher.data.MediaRepository
import org.librehu.launcher.ui.CarTheme

/**
 * The standby clock as Android's screen saver (Settings → Display → Screen saver → LibreHU): shown by the system when
 * the screen would turn off, everywhere, not only on the home screen.
 */
class StandbyDream :
    DreamService(),
    LifecycleOwner,
    SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    private var media: MediaRepository? = null

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val store = StandbyStore.get(this)
        val repo = MediaRepository(this).also { media = it }
        window.decorView.setViewTreeLifecycleOwner(this)
        window.decorView.setViewTreeSavedStateRegistryOwner(this)
        setContentView(
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@StandbyDream)
                setViewTreeSavedStateRegistryOwner(this@StandbyDream)
                setContent {
                    CarTheme {
                        val s by store.settings.collectAsStateWithLifecycle()
                        val np by repo.nowPlaying.collectAsStateWithLifecycle()
                        StandbyFace(s, np)
                    }
                }
            },
        )
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        media?.start()
        registry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDreamingStopped() {
        registry.currentState = Lifecycle.State.CREATED
        media?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        registry.currentState = Lifecycle.State.DESTROYED
        super.onDetachedFromWindow()
    }
}
