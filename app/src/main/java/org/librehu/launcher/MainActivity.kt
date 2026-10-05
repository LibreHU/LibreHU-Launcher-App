package org.librehu.launcher

import android.app.Activity
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.librehu.launcher.data.AppsRepository
import org.librehu.launcher.data.LauncherPrefs
import org.librehu.launcher.data.MediaRepository
import org.librehu.launcher.data.ThemeController
import org.librehu.launcher.data.ThemeStore
import org.librehu.launcher.data.WallpaperKind
import org.librehu.launcher.data.WidgetHost
import org.librehu.launcher.headunit.HeadUnitBridge
import org.librehu.launcher.sos.SosActivity
import org.librehu.launcher.sos.SosStore
import org.librehu.launcher.standby.StandbyActivity
import org.librehu.launcher.standby.StandbyStore
import org.librehu.launcher.tpms.TpmsManager
import org.librehu.launcher.tpms.TpmsWidget
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
    private lateinit var theme: ThemeStore
    private lateinit var themeController: ThemeController
    private val screen = mutableStateOf(Screen.HOME)

    /** Widget being added: (slot, appWidgetId). */
    private var pending: Pair<LauncherPrefs.Slot, Int>? = null

    private val bindWidget =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val (slot, id) = pending ?: return@registerForActivityResult
            if (result.resultCode == Activity.RESULT_OK) configureOrSave(slot, id) else cancelPending()
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) importWallpaper(R.string.wallpaper_error) { theme.setImage(uri) }
        }

    private val askPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private val main = Handler(Looper.getMainLooper())

    /** Standby clock after a while without touching the home screen. */
    private val idle = Runnable { if (screen.value != Screen.SETUP) StandbyActivity.show(this) }

    private val pickVideo =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) importWallpaper(R.string.wallpaper_video_error) { theme.setVideo(uri) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apps = AppsRepository(this)
        prefs = LauncherPrefs(this)
        media = MediaRepository(this)
        widgets = WidgetHost(this)
        headUnit = HeadUnitBridge.create(this)
        theme = ThemeStore(this)
        themeController = ThemeController(applicationContext, theme, headUnit.headlights)
        themeController.start(lifecycleScope)
        TpmsManager.get(this).start()
        apps.start()
        lifecycleScope.launch {
            theme.settings
                .map { it.wallpaper == WallpaperKind.SYSTEM }
                .distinctUntilChanged()
                .collect(::showSystemWallpaper)
        }
        hideSystemBars()
        if (intent?.action == TpmsWidget.ACTION_SHOW_TPMS) screen.value = Screen.TPMS
        if (!prefs.setupDone) screen.value = Screen.SETUP

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
                uninstall = { pkg -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))) },
                forceStop = { pkg ->
                    val full = headUnit.forceStop(pkg)
                    Toast.makeText(this, if (full) R.string.force_stopped else R.string.force_stop_partial, Toast.LENGTH_LONG).show()
                },
                appInfo = { pkg ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
                },
                setTheme = theme::update,
                pickImage = { pickImage.launch("image/*") },
                pickVideo = { pickVideo.launch("video/*") },
                setWallpaper = theme::setWallpaperKind,
                chooseLiveWallpaper = ::chooseLiveWallpaper,
                resetAll = {
                    prefs.reset().forEach(widgets::delete)
                    theme.reset()
                    StandbyStore.get(this).reset()
                    SosStore.get(this).reset()
                    screen.value = Screen.SETUP
                },
                standby = { StandbyActivity.show(this) },
                sos = { startActivity(Intent(this, SosActivity::class.java)) },
                sosTest = { startActivity(Intent(this, SosActivity::class.java).putExtra(SosActivity.EXTRA_TEST, true)) },
                requestPermissions = { askPermissions.launch(it) },
                openHomeSettings = { openSettings(Settings.ACTION_HOME_SETTINGS) },
                openDreamSettings = { openSettings(Settings.ACTION_DREAM_SETTINGS) },
                finishSetup = {
                    prefs.setupDone = true
                    screen.value = Screen.HOME
                },
            )
        setContent {
            CarTheme {
                LauncherScreen(apps, prefs, media, widgets, theme, themeController, screen.value, actions)
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
        // USB TPMS receiver plugged in (device filter): permission granted, connect.
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) TpmsManager.get(this).connect()
        // TPMS widget tapped.
        if (intent.action == TpmsWidget.ACTION_SHOW_TPMS) screen.value = Screen.TPMS
    }

    override fun onResume() {
        super.onResume()
        scheduleIdle()
    }

    override fun onPause() {
        main.removeCallbacks(idle)
        super.onPause()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        scheduleIdle()
    }

    private fun scheduleIdle() {
        main.removeCallbacks(idle)
        val minutes =
            StandbyStore
                .get(this)
                .settings.value.idleMinutes
        if (minutes > 0) main.postDelayed(idle, minutes * 60_000L)
    }

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
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

    // --- Wallpaper -----------------------------------------------------------------------------------------------

    private fun importWallpaper(
        error: Int,
        work: () -> Boolean,
    ) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching(work).getOrDefault(false) }
            if (!ok) Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
        }
    }

    /** Lets the Android wallpaper (static or live) show through the launcher window, like Launcher3 does. */
    private fun showSystemWallpaper(show: Boolean) {
        if (show) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        }
    }

    private fun chooseLiveWallpaper() {
        val choices =
            listOf(
                Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER),
                Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.wallpaper_live_choose)),
            )
        for (intent in choices) {
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
        Toast.makeText(this, R.string.wallpaper_live_error, Toast.LENGTH_LONG).show()
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
