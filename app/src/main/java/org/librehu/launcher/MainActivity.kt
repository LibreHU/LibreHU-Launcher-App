package org.librehu.launcher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.librehu.launcher.data.AppsRepository
import org.librehu.launcher.data.LauncherPrefs
import org.librehu.launcher.data.MediaRepository
import org.librehu.launcher.data.WidgetHost
import org.librehu.launcher.headunit.HeadUnitBridge
import org.librehu.launcher.ui.CarTheme
import org.librehu.launcher.ui.LauncherActions
import org.librehu.launcher.ui.LauncherScreen
import org.librehu.launcher.ui.Screen

/** Home screen: dashboard (widgets + now playing), app grid, and the shortcut rail. */
class MainActivity : ComponentActivity() {
    private lateinit var apps: AppsRepository
    private lateinit var prefs: LauncherPrefs
    private lateinit var media: MediaRepository
    private lateinit var widgets: WidgetHost
    private lateinit var headUnit: HeadUnitBridge
    private val screen = mutableStateOf(Screen.HOME)

    /** Widget being added: (slot, appWidgetId). */
    private var pending: Pair<LauncherPrefs.Slot, Int>? = null

    private val bindWidget =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val (slot, id) = pending ?: return@registerForActivityResult
            if (result.resultCode == Activity.RESULT_OK) configureOrSave(slot, id) else cancelPending()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apps = AppsRepository(this)
        prefs = LauncherPrefs(this)
        media = MediaRepository(this)
        widgets = WidgetHost(this)
        headUnit = HeadUnitBridge.create(this)
        apps.start()
        hideSystemBars()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                // A launcher never finishes on back: go back to the dashboard.
                override fun handleOnBackPressed() {
                    screen.value = Screen.HOME
                }
            },
        )

        val actions =
            LauncherActions(
                launch = { c -> AppsRepository.launch(this, c) },
                togglePin = prefs::togglePin,
                movePin = prefs::movePin,
                show = { screen.value = it },
                volumeUp = headUnit::volumeUp,
                volumeDown = headUnit::volumeDown,
                mediaToggle = media::togglePlay,
                mediaNext = media::next,
                mediaPrevious = media::previous,
                mediaOpen = media::open,
                grantMediaAccess = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                addWidget = ::addWidget,
                removeWidget = { slot ->
                    widgets.delete(prefs.slots.value[slot] ?: LauncherPrefs.NO_WIDGET)
                    prefs.setSlot(slot, LauncherPrefs.NO_WIDGET)
                },
            )
        setContent {
            CarTheme {
                LauncherScreen(apps, prefs, media, widgets, screen.value, actions)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        widgets.host.startListening()
        media.start()
    }

    override fun onStop() {
        media.stop()
        widgets.host.stopListening()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already home: back to the dashboard.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) screen.value = Screen.HOME
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        apps.stop()
        headUnit.release()
        super.onDestroy()
    }

    // --- Widgets -------------------------------------------------------------------------------------------------

    private fun addWidget(
        slot: LauncherPrefs.Slot,
        provider: ComponentName,
    ) {
        val id = widgets.allocate()
        pending = slot to id
        if (widgets.bindIfAllowed(id, provider)) {
            configureOrSave(slot, id)
        } else {
            bindWidget.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider),
            )
        }
    }

    private fun configureOrSave(
        slot: LauncherPrefs.Slot,
        id: Int,
    ) {
        if (widgets.info(id)?.configure != null) {
            @Suppress("DEPRECATION")
            widgets.host.startAppWidgetConfigureActivityForResult(this, id, 0, REQUEST_CONFIGURE, null)
        } else {
            save(slot, id)
        }
    }

    @Deprecated("Widget configuration activities only report through onActivityResult")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CONFIGURE) return
        val (slot, id) = pending ?: return
        if (resultCode == Activity.RESULT_OK) save(slot, id) else cancelPending()
    }

    private fun save(
        slot: LauncherPrefs.Slot,
        id: Int,
    ) {
        val old = prefs.slots.value[slot] ?: LauncherPrefs.NO_WIDGET
        if (old != id) widgets.delete(old)
        prefs.setSlot(slot, id)
        pending = null
    }

    private fun cancelPending() {
        pending?.let { widgets.delete(it.second) }
        pending = null
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private companion object {
        const val REQUEST_CONFIGURE = 2
    }
}
